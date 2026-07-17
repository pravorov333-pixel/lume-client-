'use strict';

const { Client } = require('minecraft-launcher-core');
const { app } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { spawn } = require('child_process');

// Mod auto-update lives on LumeFriendsServer (not the key server) — see
// LumeFriendsServer/README.md "Publishing a mod update".
const UPDATE_SERVER = 'https://friends-lume-server-production.up.railway.app';

// --- Supported versions ----------------------------------------------------
// Each entry: the MC + Fabric loader coords, which bundled jars to install, and
// which Java to run with (1.16.5 needs Java 8; 1.21.4 needs Java 17+).
const JAVA21 = 'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.11.10-hotspot\\bin\\java.exe';
const JAVA8 = 'C:\\Program Files\\Java\\jre-1.8\\bin\\java.exe';

const VERSIONS = {
  '1.21.4': {
    mc: '1.21.4', loader: '0.16.10', java: JAVA21,
    fabricApi: 'fabric-api.jar', lume: 'lume-client.jar', perf: 'perf-mods',
  },
  '1.16.5': {
    mc: '1.16.5', loader: '0.16.10', java: JAVA8,
    fabricApi: 'fabric-api-1165.jar', lume: 'lume-client-1165.jar', perf: null,
  },
};
const DEFAULT_VERSION = '1.21.4';

// --- Launch cancellation ----------------------------------------------------
let installerProc = null;   // fabric installer child process (if running)
let gameProc = null;        // mclc game process (once started)
let cancelled = false;      // user pressed Cancel during this launch

/** Abort the current launch: stop the installer / game process and flag the run. */
function cancelLaunch() {
  cancelled = true;
  try { if (installerProc) installerProc.kill(); } catch (e) {}
  try { if (gameProc) gameProc.kill(); } catch (e) {}
}

function resolveVersion(v) {
  return VERSIONS[v] ? v : DEFAULT_VERSION;
}
function fabricId(cfg) {
  return `fabric-loader-${cfg.loader}-${cfg.mc}`;
}

// Aikar's GC flags — smoother frametimes, fewer GC stutters. (Valid on Java 8 & 21.)
const JVM_FLAGS = [
  // LWJGL defaults to its bundled jemalloc for native (off-heap) allocations — textures, GL
  // buffers, image decoding, etc. On one user's machine this crashed (EXCEPTION_ACCESS_VIOLATION
  // inside jemalloc.dll) from two completely unrelated call sites (loading a custom PNG particle,
  // freeing a texture on world exit), while non-Minecraft software on the same PC never crashes —
  // pointing at jemalloc itself, not general hardware/RAM. Forcing LWJGL's plain "system" allocator
  // (regular malloc/free via the CRT) trades a little allocator performance for using a much more
  // battle-tested code path than jemalloc on whatever's unusual about that machine's memory setup.
  '-Dorg.lwjgl.system.allocator=system',
  '-XX:+UseG1GC',
  '-XX:+ParallelRefProcEnabled',
  '-XX:MaxGCPauseMillis=200',
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC',
  // AlwaysPreTouch deliberately dropped: it forces the JVM to commit + zero the ENTIRE heap
  // up front at startup (great for avoiding GC-time page faults on a server with RAM to spare,
  // needless memory pressure on a laptop with only a few GB total) — removed while chasing
  // native-allocator crashes (jemalloc.dll) on a low-RAM machine; lazy commit is the safer default.
  '-XX:G1NewSizePercent=30',
  '-XX:G1MaxNewSizePercent=40',
  '-XX:G1HeapRegionSize=8M',
  '-XX:G1ReservePercent=20',
  '-XX:G1HeapWastePercent=5',
  '-XX:G1MixedGCCountTarget=4',
  '-XX:InitiatingHeapOccupancyPercent=15',
  '-XX:G1MixedGCLiveThresholdPercent=90',
  '-XX:G1RSetUpdatingPauseTimePercent=5',
  '-XX:SurvivorRatio=32',
  '-XX:+PerfDisableSharedMem',
  '-XX:MaxTenuringThreshold=1',
];

// Shared root: vanilla assets/libraries/versions live here (no per-version dupes).
function rootDir() {
  return path.join(app.getPath('appData'), '.lumeclient');
}
// Per-version run directory: mods/, options.txt, saves — isolated so each version
// only sees its own (incompatible) mods.
function profileDir(version) {
  return path.join(rootDir(), 'profiles', version);
}

// Bundled files shipped with the launcher (resources/).
function resourcesDir() {
  const dev = path.join(__dirname, '..', 'resources');
  if (fs.existsSync(dev)) return dev;
  return path.join(process.resourcesPath, 'resources');
}

function send(win, channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}
function status(win, text) { send(win, 'status', { text }); }

// Offline auth object (TLauncher-style — no Mojang account needed).
function offlineAuth(username) {
  const hash = crypto.createHash('md5').update('OfflinePlayer:' + username).digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = hash.toString('hex');
  const uuid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  return {
    access_token: '0', client_token: '0', uuid, name: username,
    user_properties: '{}', meta: { type: 'mojang', demo: false },
  };
}

function ensureFabricInstalled(win, cfg) {
  return new Promise((resolve, reject) => {
    const dir = rootDir();
    const id = fabricId(cfg);
    const versionJson = path.join(dir, 'versions', id, `${id}.json`);
    if (fs.existsSync(versionJson)) {
      status(win, `Fabric ${cfg.mc} already installed.`);
      return resolve();
    }
    fs.mkdirSync(dir, { recursive: true });
    const installer = path.join(resourcesDir(), 'fabric-installer.jar');
    status(win, `Installing Fabric for ${cfg.mc}…`);
    const p = spawn(cfg.java, [
      '-jar', installer, 'client',
      '-mcversion', cfg.mc,
      '-loader', cfg.loader,
      '-dir', dir,
      '-noprofile',
    ]);
    installerProc = p;
    p.stdout.on('data', (d) => send(win, 'log', { text: d.toString() }));
    p.stderr.on('data', (d) => send(win, 'log', { text: d.toString() }));
    p.on('close', (code) => {
      installerProc = null;
      if (cancelled) { reject(new Error('cancelled')); return; }
      if (code === 0) { status(win, 'Fabric installed.'); resolve(); }
      else reject(new Error('Fabric installer exited with code ' + code));
    });
    p.on('error', reject);
  });
}

// Auto-updated jars land here (separate from the dev override folder and the
// bundled resources/ jar shipped in the installer) — see checkForUpdate().
function updatesDir() {
  return path.join(rootDir(), 'updates');
}

/**
 * Asks LumeKeyServer whether a newer Lume jar has been published for this MC
 * version; downloads it into updatesDir() if so. Best-effort: any failure
 * (offline, server down) just leaves whatever's already cached/bundled in
 * place — never blocks launching the game.
 */
async function checkForUpdate(win, cfg) {
  try {
    status(win, 'Checking for updates…');
    const res = await fetch(`${UPDATE_SERVER}/api/version?mcVersion=${encodeURIComponent(cfg.mc)}`);
    if (!res.ok) return; // nothing published for this version yet, or server hiccup
    const data = await res.json();
    if (!data || !data.version || !data.jarUrl) return;

    const dir = updatesDir();
    fs.mkdirSync(dir, { recursive: true });
    const versionFile = path.join(dir, cfg.lume + '.version');
    const current = fs.existsSync(versionFile) ? fs.readFileSync(versionFile, 'utf8').trim() : null;
    if (current === data.version && fs.existsSync(path.join(dir, cfg.lume))) {
      status(win, `Lume ${data.version} — up to date.`);
      return;
    }

    status(win, `Downloading Lume update ${data.version}…`);
    const jarRes = await fetch(data.jarUrl);
    if (!jarRes.ok) throw new Error('download failed: ' + jarRes.status);
    const buf = Buffer.from(await jarRes.arrayBuffer());
    fs.writeFileSync(path.join(dir, cfg.lume), buf);
    fs.writeFileSync(versionFile, data.version);
    status(win, `Updated to Lume ${data.version}.`);
  } catch (e) {
    status(win, 'Update check skipped (offline?).');
  }
}

function ensureMods(win, cfg, version) {
  const modsDir = path.join(profileDir(version), 'mods');
  // fresh mods each launch so a version never inherits another's jars
  fs.rmSync(modsDir, { recursive: true, force: true });
  fs.mkdirSync(modsDir, { recursive: true });
  const res = resourcesDir();

  for (const f of [cfg.fabricApi, cfg.lume]) {
    const src = path.join(res, f);
    if (fs.existsSync(src)) fs.copyFileSync(src, path.join(modsDir, f));
  }

  // Auto-updated jar (see checkForUpdate) takes priority over the one bundled
  // in the installer, if one's been downloaded.
  const updated = path.join(updatesDir(), cfg.lume);
  if (fs.existsSync(updated)) {
    fs.copyFileSync(updated, path.join(modsDir, cfg.lume));
    status(win, 'Using auto-updated Lume jar.');
  }

  // DEV override: a jar dropped at <root>/override/<lume jar> replaces everything
  // above — lets a fresh local build be tested with just a relaunch (no exe rebuild).
  const override = path.join(rootDir(), 'override', cfg.lume);
  if (fs.existsSync(override)) {
    fs.copyFileSync(override, path.join(modsDir, cfg.lume));
    status(win, 'Using override Lume jar.');
  }

  if (cfg.perf) {
    const perfDir = path.join(res, cfg.perf);
    if (fs.existsSync(perfDir)) {
      for (const f of fs.readdirSync(perfDir)) {
        if (f.endsWith('.jar')) fs.copyFileSync(path.join(perfDir, f), path.join(modsDir, f));
      }
    }
  }
  status(win, 'Mods ready.');
}

// Write tuned video settings on first run only (never overrides the user's own).
function writeOptions(win, version) {
  const f = path.join(profileDir(version), 'options.txt');
  if (fs.existsSync(f)) return;
  fs.mkdirSync(path.dirname(f), { recursive: true });
  // No Sodium bundled any more (dropped — it was crashing on some Intel iGPU drivers), so vanilla's
  // own renderer carries all of this alone. Tuned a notch more aggressive than before to compensate:
  // renderClouds/ao off, lower mipmaps, no debug verbosity.
  const opts = [
    'renderDistance:8', 'simulationDistance:6', 'maxFps:260', 'graphicsMode:0',
    'particles:1', 'entityShadows:false', 'mipmapLevels:2', 'enableVsync:false',
    'gamma:1.0', 'guiScale:0', 'renderClouds:"false"', 'ao:false', 'glDebugVerbosity:0',
  ].join('\n') + '\n';
  fs.writeFileSync(f, opts);
  status(win, 'Optimised settings applied.');
}

/**
 * Set up (if needed) and launch the game. Resolves when the game process starts.
 */
async function launchGame(win, { username, memory, version }) {
  const ver = resolveVersion(version);
  const cfg = VERSIONS[ver];

  if (!fs.existsSync(cfg.java)) {
    throw new Error(`Java for ${ver} not found at: ${cfg.java}`);
  }

  cancelled = false;   // fresh run
  const abort = () => { if (cancelled) { status(win, 'Отменено.'); return true; } return false; };

  await ensureFabricInstalled(win, cfg);
  if (abort()) return { cancelled: true };
  await checkForUpdate(win, cfg);
  if (abort()) return { cancelled: true };
  ensureMods(win, cfg, ver);
  writeOptions(win, ver);
  if (abort()) return { cancelled: true };

  // Whole MB, not "G" suffix — the memory slider allows half-GB steps (e.g. 2.5) and
  // -Xmx/-Xms don't reliably accept fractional G values across JVM builds.
  const ram = Math.round((memory || 4) * 1024) + 'M';
  const launcher = new Client();
  const opts = {
    authorization: Promise.resolve(offlineAuth(username || 'LumePlayer')),
    root: rootDir(),
    // mods/options/saves live in a per-version profile dir
    overrides: { gameDirectory: profileDir(ver) },
    version: { number: cfg.mc, type: 'release', custom: fabricId(cfg) },
    memory: { max: ram, min: ram },
    javaPath: cfg.java,
    customArgs: JVM_FLAGS,
  };

  let hidden = false;
  const hideLauncher = () => {
    if (!cancelled && !hidden && win && !win.isDestroyed()) { hidden = true; win.hide(); }
  };

  launcher.on('progress', (e) => send(win, 'progress', e));
  launcher.on('download-status', (e) => send(win, 'progress', e));
  launcher.on('data', (line) => { send(win, 'log', { text: String(line) }); hideLauncher(); });
  launcher.on('debug', (line) => send(win, 'log', { text: String(line) }));
  launcher.on('close', (code) => {
    if (win && !win.isDestroyed()) { win.show(); win.focus(); }
    send(win, 'game-closed', { code });
  });

  status(win, `Launching Minecraft ${cfg.mc}…`);
  const proc = await launcher.launch(opts);
  gameProc = proc || null;
  // if the user cancelled while assets were downloading, kill the freshly-started game
  if (cancelled && gameProc) { try { gameProc.kill(); } catch (e) {} return { cancelled: true }; }
  status(win, 'Minecraft is starting…');
  return { ok: true };
}

// Writes {key, hwid} into this version's profile config/lume.json under "license" so the
// MOD (which has no other way to know the key) can check subscription status against
// LumeKeyServer itself for the account widget. Merges with whatever the mod already wrote
// there (module settings etc.) instead of clobbering the file — read-modify-write.
function writeLicense(version, key, hwid) {
  try {
    const ver = resolveVersion(version);
    const cfgDir = path.join(profileDir(ver), 'config');
    fs.mkdirSync(cfgDir, { recursive: true });
    const file = path.join(cfgDir, 'lume.json');
    let data = {};
    if (fs.existsSync(file)) {
      try { data = JSON.parse(fs.readFileSync(file, 'utf8')); } catch (e) { data = {}; }
    }
    data.license = { key, hwid };
    fs.writeFileSync(file, JSON.stringify(data, null, 2));
  } catch (e) { /* best-effort — don't block launch over this */ }
}

module.exports = { launchGame, cancelLaunch, rootDir, profileDir, writeLicense };

'use strict';

const { Client } = require('minecraft-launcher-core');
const { app } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { spawn } = require('child_process');

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
  '-XX:+UseG1GC',
  '-XX:+ParallelRefProcEnabled',
  '-XX:MaxGCPauseMillis=200',
  '-XX:+UnlockExperimentalVMOptions',
  '-XX:+DisableExplicitGC',
  '-XX:+AlwaysPreTouch',
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

  // DEV override: a jar dropped at <root>/override/<lume jar> replaces the bundled
  // Lume mod — lets a fresh build be tested with just a relaunch (no exe rebuild).
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
  const opts = [
    'renderDistance:8', 'simulationDistance:6', 'maxFps:260', 'graphicsMode:0',
    'particles:1', 'entityShadows:false', 'mipmapLevels:2', 'enableVsync:false',
    'gamma:1.0', 'guiScale:0',
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
  ensureMods(win, cfg, ver);
  writeOptions(win, ver);
  if (abort()) return { cancelled: true };

  const ram = (memory || 4) + 'G';
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

module.exports = { launchGame, cancelLaunch, rootDir, profileDir };

'use strict';

const { Client } = require('minecraft-launcher-core');
const { app } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { spawn } = require('child_process');

// --- Fixed coordinates for this build (match the LumeClient mod) ---
const MC_VERSION = '1.21.4';
const LOADER_VERSION = '0.16.10';
const FABRIC_VERSION_ID = `fabric-loader-${LOADER_VERSION}-${MC_VERSION}`;

// Java 21 (installed via winget). Adjust if the path differs.
const JAVA_PATH = 'C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.11.10-hotspot\\bin\\java.exe';

// Aikar's GC flags — smoother frametimes, fewer GC stutters.
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

// Isolated game directory — fully self-contained, independent of TLauncher.
function gameDir() {
  return path.join(app.getPath('appData'), '.lumeclient');
}

// Bundled files shipped with the launcher (resources/).
function resourcesDir() {
  // In dev: <project>/resources. When packaged: process.resourcesPath/resources.
  const dev = path.join(__dirname, '..', 'resources');
  if (fs.existsSync(dev)) return dev;
  return path.join(process.resourcesPath, 'resources');
}

function send(win, channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

function status(win, text) {
  send(win, 'status', { text });
}

// Offline auth object (TLauncher-style — no Mojang account needed).
function offlineAuth(username) {
  const hash = crypto.createHash('md5').update('OfflinePlayer:' + username).digest();
  hash[6] = (hash[6] & 0x0f) | 0x30; // version 3
  hash[8] = (hash[8] & 0x3f) | 0x80; // variant
  const hex = hash.toString('hex');
  const uuid = `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
  return {
    access_token: '0',
    client_token: '0',
    uuid,
    name: username,
    user_properties: '{}',
    meta: { type: 'mojang', demo: false },
  };
}

function ensureFabricInstalled(win) {
  return new Promise((resolve, reject) => {
    const dir = gameDir();
    const versionJson = path.join(dir, 'versions', FABRIC_VERSION_ID, `${FABRIC_VERSION_ID}.json`);
    if (fs.existsSync(versionJson)) {
      status(win, 'Fabric already installed.');
      return resolve();
    }
    fs.mkdirSync(dir, { recursive: true });
    const installer = path.join(resourcesDir(), 'fabric-installer.jar');
    status(win, 'Installing Fabric loader…');
    const p = spawn(JAVA_PATH, [
      '-jar', installer, 'client',
      '-mcversion', MC_VERSION,
      '-loader', LOADER_VERSION,
      '-dir', dir,
      '-noprofile',
    ]);
    p.stdout.on('data', (d) => send(win, 'log', { text: d.toString() }));
    p.stderr.on('data', (d) => send(win, 'log', { text: d.toString() }));
    p.on('close', (code) => {
      if (code === 0) { status(win, 'Fabric installed.'); resolve(); }
      else reject(new Error('Fabric installer exited with code ' + code));
    });
    p.on('error', reject);
  });
}

function ensureMods(win) {
  const modsDir = path.join(gameDir(), 'mods');
  fs.mkdirSync(modsDir, { recursive: true });
  const res = resourcesDir();

  // Core mods
  const files = ['fabric-api.jar', 'lume-client.jar'];
  for (const f of files) {
    const src = path.join(res, f);
    if (fs.existsSync(src)) fs.copyFileSync(src, path.join(modsDir, f));
  }

  // Bundled performance mods (Sodium, Lithium, FerriteCore, Indium, ...)
  const perfDir = path.join(res, 'perf-mods');
  if (fs.existsSync(perfDir)) {
    for (const f of fs.readdirSync(perfDir)) {
      if (f.endsWith('.jar')) fs.copyFileSync(path.join(perfDir, f), path.join(modsDir, f));
    }
  }
  status(win, 'Mods & performance pack ready.');
}

// Write tuned video settings on first run only (never overrides the user's own).
function writeOptions(win) {
  const f = path.join(gameDir(), 'options.txt');
  if (fs.existsSync(f)) return;
  const opts = [
    'renderDistance:8',
    'simulationDistance:6',
    'maxFps:260',
    'graphicsMode:0',
    'particles:1',
    'entityShadows:false',
    'mipmapLevels:2',
    'enableVsync:false',
    'gamma:1.0',
    'guiScale:0',
  ].join('\n') + '\n';
  fs.writeFileSync(f, opts);
  status(win, 'Optimised settings applied.');
}

/**
 * Set up (if needed) and launch the game. Resolves when the game process starts.
 */
async function launchGame(win, { username, memory }) {
  if (!fs.existsSync(JAVA_PATH)) {
    throw new Error('Java 21 not found at: ' + JAVA_PATH);
  }

  await ensureFabricInstalled(win);
  ensureMods(win);
  writeOptions(win);

  const ram = (memory || 4) + 'G';
  const launcher = new Client();
  const opts = {
    authorization: Promise.resolve(offlineAuth(username || 'LumePlayer')),
    root: gameDir(),
    version: { number: MC_VERSION, type: 'release', custom: FABRIC_VERSION_ID },
    // min == max so AlwaysPreTouch reserves the heap up-front (smoother FPS).
    memory: { max: ram, min: ram },
    javaPath: JAVA_PATH,
    customArgs: JVM_FLAGS,
  };

  let hidden = false;
  const hideLauncher = () => {
    if (!hidden && win && !win.isDestroyed()) { hidden = true; win.hide(); }
  };

  launcher.on('progress', (e) => send(win, 'progress', e));
  launcher.on('download-status', (e) => send(win, 'progress', e));
  launcher.on('data', (line) => { send(win, 'log', { text: String(line) }); hideLauncher(); });
  launcher.on('debug', (line) => send(win, 'log', { text: String(line) }));
  launcher.on('close', (code) => {
    // Game closed -> bring the launcher back to the front.
    if (win && !win.isDestroyed()) { win.show(); win.focus(); }
    send(win, 'game-closed', { code });
  });

  status(win, 'Downloading game files & launching…');
  await launcher.launch(opts);
  status(win, 'Minecraft is starting…');
}

module.exports = { launchGame, gameDir };

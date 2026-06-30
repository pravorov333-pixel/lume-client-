'use strict';

const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { checkKey } = require('./keys');
const { launchGame, cancelLaunch } = require('./launcher');
const tg = require('./telegram');

let win;

function createWindow() {
  win = new BrowserWindow({
    width: 920,
    height: 600,
    resizable: false,
    frame: false,
    transparent: false,
    backgroundColor: '#eafff6',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));
}

// Stable hardware id for this machine (for HWID display / future server bind).
function getHwid() {
  const cpus = os.cpus();
  const seed = [os.hostname(), os.platform(), os.arch(), (cpus[0] && cpus[0].model) || '', os.totalmem()].join('|');
  return crypto.createHash('sha256').update(seed).digest('hex').slice(0, 16).toUpperCase();
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

ipcMain.handle('check-key', (_e, key) => checkKey(key));
ipcMain.handle('get-hwid', () => getHwid());
ipcMain.handle('window-close', () => win && win.close());
ipcMain.handle('window-min', () => win && win.minimize());

ipcMain.handle('launch', async (_e, payload) => {
  try {
    const res = await launchGame(win, payload || {});
    return res || { ok: true };
  } catch (err) {
    const msg = String(err && err.message ? err.message : err);
    if (msg === 'cancelled') return { cancelled: true };
    return { ok: false, error: msg };
  }
});

ipcMain.handle('cancel-launch', () => cancelLaunch());

// --- Telegram events integration ---
tg.setLogSink((m) => { if (win && !win.isDestroyed()) win.webContents.send('tg-log', { text: m }); });
ipcMain.handle('tg-status', () => tg.status());
ipcMain.handle('tg-start-login', (_e, p) => tg.startLogin(p.apiId, p.apiHash, p.phone));
ipcMain.handle('tg-code', (_e, code) => tg.submitCode(code));
ipcMain.handle('tg-password', (_e, pw) => tg.submitPassword(pw));
ipcMain.handle('tg-refresh', () => tg.refresh());
ipcMain.handle('tg-unlink', () => tg.unlink());

// auto-connect with a saved session on startup
app.whenReady().then(() => { try { if (tg.isLinked()) tg.connectSaved().catch(() => {}); } catch (e) {} });

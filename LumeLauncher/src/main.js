'use strict';

const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const os = require('os');
const fs = require('fs');
const crypto = require('crypto');
const { checkKey } = require('./keys');
const { launchGame, cancelLaunch, writeLicense, writePerfMode, rootDir } = require('./launcher');

// --- Launcher-wide theme (persisted independently of any MC profile, since it
// covers the launcher's OWN screens too) — also read directly by the LumeClient
// mod itself (Theme.java) so the in-game ClickGUI stays in sync automatically. ---
const DEFAULT_THEME = {
  mode: 'light',                                    // 'light' | 'dark'
  style: 'default',                                 // 'default' | 'fullGlass' | 'noGlass'
  glassBlur: 0.5,                                   // fullGlass only — same schema as Theme.java
  glassDistort: 0.35,                               // (real refraction is in-game only; here it's a lighter blur boost)
  light: { bg: '#F2EBDD', accent: '#A99BC7', activeText: '#FFFFFF' },
  dark: { bg: '#221F1A', accent: '#B7AAD9', activeText: '#FFFFFF' },
};

function themeFile() {
  return path.join(rootDir(), 'theme.json');
}

function readTheme() {
  try {
    const f = themeFile();
    if (fs.existsSync(f)) {
      const saved = JSON.parse(fs.readFileSync(f, 'utf8'));
      return {
        ...DEFAULT_THEME, ...saved,
        light: { ...DEFAULT_THEME.light, ...(saved.light || {}) },
        dark: { ...DEFAULT_THEME.dark, ...(saved.dark || {}) },
      };
    }
  } catch (e) { /* fall through to defaults */ }
  return { ...DEFAULT_THEME };
}

function writeTheme(theme) {
  fs.mkdirSync(rootDir(), { recursive: true });
  fs.writeFileSync(themeFile(), JSON.stringify(theme, null, 2));
}

// --- Launcher settings (memory allocation, etc.) — kept in their OWN file rather than
// theme.json: theme.json is rewritten wholesale by the in-game ColorsScreen (ThemeSync.java)
// every time a colour changes, so a launcher-only field living there would get silently
// dropped on the next in-game colour edit. ---
const DEFAULT_SETTINGS = {
  memory: 4,        // GB allocated to the JVM heap (-Xmx/-Xms) — see launcher.js launchGame()
  wallpaper: null,  // filename inside the wallpapers/ folder, or null for the default animated glow
  wallpaperDim: 35, // 0–100 % dark overlay over a custom wallpaper, keeps the glass UI readable
  perfMode: 'default', // 'default' | 'ultra' — read by the mod at startup (see writePerfMode) to
                        // strip glass/animations from ClickGUI/HUD/CustomMenu for max FPS
};

// --- Custom launcher wallpapers ---------------------------------------------------
// Users drop image files into %APPDATA%/.lumeclient/wallpapers and pick one from the
// launcher; we read the chosen file and hand the renderer a data: URL, because the
// renderer's CSP (img-src 'self' data:) blocks file:// — a raw path would just silently
// fail to paint. A README is seeded in the folder so people know what won't glitch.
const IMAGE_EXTS = ['.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp'];
const WALLPAPER_MIME = {
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.webp': 'image/webp', '.gif': 'image/gif', '.bmp': 'image/bmp',
};
const WALLPAPER_README = [
  'КАК ДОБАВИТЬ СВОИ ОБОИ В LUME',
  '===================================',
  '',
  'Просто положи картинку в ЭТУ папку, затем открой лаунчер →',
  'кнопка «Customize Colors» (иконка палитры слева) → раздел «Wallpaper»',
  'и выбери свою картинку. Готово.',
  '',
  'ЧТОБЫ ОБОИ НЕ БАГАЛИСЬ, СОБЛЮДАЙ ЭТО:',
  '',
  '1. ФОРМАТ: только .png, .jpg, .jpeg, .webp, .bmp или .gif.',
  '   Другие форматы (.psd, .tiff, .heic, .mp4 и т.п.) лаунчер НЕ увидит.',
  '',
  '2. РАЗМЕР ОКНА: окно лаунчера 920 x 600 (соотношение примерно 3:2).',
  '   Лучше всего подходят картинки в таком же соотношении, например',
  '   1840 x 1200, 1920 x 1280 или 2760 x 1800. Другое соотношение не',
  '   сломается — картинка обрежется по краям (режим «cover»), но часть',
  '   изображения не будет видна.',
  '',
  '3. МИНИМАЛЬНОЕ РАЗРЕШЕНИЕ: не меньше 920 x 600. Меньше — будет',
  '   размыто и в пикселях.',
  '',
  '4. ВЕС ФАЙЛА: держи до ~8 МБ. Очень тяжёлые картинки (30+ МБ)',
  '   грузятся медленно и лаунчер может подвиснуть при выборе.',
  '',
  '5. ИМЯ ФАЙЛА: можно любое, но лучше латиницей без спецсимволов',
  '   (например my-wallpaper.png). Эмодзи в имени лучше не использовать.',
  '',
  '6. АНИМАЦИЯ: .gif работает, но анимированные обои сильнее грузят',
  '   систему. Если лаунчер тормозит — возьми обычный .png/.jpg.',
  '',
  '7. ЧИТАЕМОСТЬ: если поверх ярких обоев плохо видно текст — открой',
  '   «Customize Colors» → «Wallpaper» и увеличь ползунок «Dim».',
  '',
  'Совет: чтобы вернуть стандартный фон с плавным свечением —',
  'выбери «None» в разделе Wallpaper.',
].join('\r\n');

function wallpaperDir() {
  return path.join(rootDir(), 'wallpapers');
}

// Creates the folder (first run) and seeds the how-to README once. Safe to call often.
function ensureWallpaperDir() {
  const dir = wallpaperDir();
  fs.mkdirSync(dir, { recursive: true });
  const readme = path.join(dir, 'КАК-ДОБАВИТЬ-ОБОИ.txt');
  if (!fs.existsSync(readme)) {
    try { fs.writeFileSync(readme, WALLPAPER_README, 'utf8'); } catch (e) { /* non-fatal */ }
  }
  return dir;
}

function listWallpapers() {
  const dir = ensureWallpaperDir();
  try {
    return fs.readdirSync(dir).filter((f) => IMAGE_EXTS.includes(path.extname(f).toLowerCase()));
  } catch (e) {
    return [];
  }
}

// Returns a data: URL for one wallpaper by name, or null if it's missing / not an image.
// path.basename() strips any directory parts so a crafted name can't escape the folder.
function readWallpaperDataUrl(name) {
  if (!name) return null;
  const safe = path.basename(String(name));
  const ext = path.extname(safe).toLowerCase();
  if (!IMAGE_EXTS.includes(ext)) return null;
  const file = path.join(wallpaperDir(), safe);
  try {
    if (!fs.existsSync(file)) return null;
    const b64 = fs.readFileSync(file).toString('base64');
    return `data:${WALLPAPER_MIME[ext] || 'image/png'};base64,${b64}`;
  } catch (e) {
    return null;
  }
}

function settingsFile() {
  return path.join(rootDir(), 'settings.json');
}

function readSettings() {
  try {
    const f = settingsFile();
    if (fs.existsSync(f)) return { ...DEFAULT_SETTINGS, ...JSON.parse(fs.readFileSync(f, 'utf8')) };
  } catch (e) { /* fall through to defaults */ }
  return { ...DEFAULT_SETTINGS };
}

function writeSettings(settings) {
  fs.mkdirSync(rootDir(), { recursive: true });
  fs.writeFileSync(settingsFile(), JSON.stringify(settings, null, 2));
}

let win;

function createWindow() {
  win = new BrowserWindow({
    width: 920,
    height: 600,
    resizable: false,
    frame: false,
    transparent: false,
    backgroundColor: '#eafff6',
    icon: path.join(__dirname, '..', 'resources', 'lume.ico'),
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

app.whenReady().then(() => {
  ensureWallpaperDir();   // create the folder + seed the README before any window asks for it
  createWindow();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

// Cached so `launch` can write it into the mod's config without the renderer having to
// re-send the key (it already only lives in the login form, not the play-screen payload).
let lastValidKey = null;

ipcMain.handle('check-key', async (_e, key) => {
  const res = await checkKey(key, getHwid());
  if (res.ok) lastValidKey = String(key || '').trim().toUpperCase();
  return res;
});
ipcMain.handle('get-hwid', () => getHwid());
ipcMain.handle('window-close', () => win && win.close());
ipcMain.handle('window-min', () => win && win.minimize());

ipcMain.handle('get-theme', () => readTheme());
ipcMain.handle('set-theme', (_e, theme) => {
  writeTheme({ ...DEFAULT_THEME, ...theme });
  return { ok: true };
});

ipcMain.handle('get-settings', () => readSettings());
ipcMain.handle('set-settings', (_e, settings) => {
  writeSettings({ ...DEFAULT_SETTINGS, ...settings });
  return { ok: true };
});
ipcMain.handle('get-sysinfo', () => ({ totalMemGB: Math.round((os.totalmem() / (1024 ** 3)) * 10) / 10 }));

ipcMain.handle('launch', async (_e, payload) => {
  try {
    if (lastValidKey) writeLicense(payload && payload.version, lastValidKey, getHwid());
    const settings = readSettings();
    writePerfMode(payload && payload.version, settings.perfMode === 'ultra');
    const res = await launchGame(win, { ...payload, memory: (payload && payload.memory) || settings.memory });
    return res || { ok: true };
  } catch (err) {
    const msg = String(err && err.message ? err.message : err);
    if (msg === 'cancelled') return { cancelled: true };
    return { ok: false, error: msg };
  }
});

ipcMain.handle('cancel-launch', () => cancelLaunch());

ipcMain.handle('open-licenses', () => {
  shell.openPath(path.join(__dirname, '..', 'resources', 'THIRD-PARTY-NOTICES.md'));
});

ipcMain.handle('list-wallpapers', () => listWallpapers());
ipcMain.handle('read-wallpaper', (_e, name) => readWallpaperDataUrl(name));
ipcMain.handle('open-wallpaper-folder', () => shell.openPath(ensureWallpaperDir()));

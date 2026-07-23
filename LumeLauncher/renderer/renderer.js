'use strict';

const $ = (id) => document.getElementById(id);

$('min').onclick = () => window.lume.minWindow();
$('close').onclick = () => window.lume.closeWindow();

window.lume.getHwid().then((h) => { $('hwid').textContent = h; });

$('licensesLink').onclick = (e) => { e.preventDefault(); window.lume.openLicenses(); };

// --- Theme (light/dark, per-theme bg+accent, glass style) — shared with the in-game
// LumeClient mod via theme.json (see main.js get-theme/set-theme); this file only ever
// reads/writes through those two IPC calls so both sides stay in sync automatically. ---
let currentTheme = null;

// Single source of truth for every themed CSS variable — colour 1 (background) and
// colour 2 (accent: glow/outlines/lines/button fills) both flow ONLY through here, so
// nothing can end up with a stray hardcoded rgba() that silently ignores "Customize
// Colors" (that's exactly what happened before: the Play button/version pill's glow
// box-shadow had a literal purple rgba() baked in, so changing the accent visibly
// re-tinted the button fill but never touched its glow — --glow/--glowSoft below are
// computed from the SAME accent value specifically to close that gap).
function applyTheme(theme) {
  currentTheme = theme;
  const body = document.body;
  const dark = theme.mode === 'dark';
  const style = theme.style || 'default';
  body.classList.toggle('dark', dark);
  body.classList.remove('style-default', 'style-fullGlass', 'style-noGlass');
  body.classList.add('style-' + style);

  const c = theme[theme.mode] || theme.light;
  const root = document.documentElement.style;
  root.setProperty('--bg', c.bg);
  root.setProperty('--mint', c.accent);
  root.setProperty('--blue', shade(c.accent, -14));
  root.setProperty('--mintLight', shade(c.accent, 20));
  root.setProperty('--glow', rgba(c.accent, 0.55));
  root.setProperty('--glowSoft', rgba(c.accent, 0.22));
  root.setProperty('--activeText', c.activeText || '#FFFFFF');

  root.setProperty('--ink', dark ? '#F2ECE0' : '#4A4133');
  root.setProperty('--ink2', dark ? '#C9BEAD' : '#8C8170');
  root.setProperty('--ink3', dark ? '#8C8170' : '#b0a594');

  // Glass panel alpha — noGlass flattens to fully opaque (no translucency, no blur —
  // the blur presence itself is toggled by the body.style-* CSS classes). fullGlass used
  // to share the exact same alpha numbers as default (bug: picking it visibly did nothing
  // beyond adding blur to .vers/.statusbox) — now genuinely more see-through than default.
  const tint = dark ? '40,36,30' : '255,255,255';
  const a = style === 'noGlass' ? [1, 1, 1, 1, 1]
    : style === 'fullGlass' ? (dark ? [.32, .20, .26, .06, .05] : [.30, .16, .20, .55, .40])
    : (dark ? [.6, .45, .5, .10, .08] : [.6, .35, .4, .9, .7]);
  root.setProperty('--glass', `rgba(${tint},${a[0]})`);
  root.setProperty('--glass2', `rgba(${tint},${a[1]})`);
  root.setProperty('--glass3', `rgba(${tint},${a[2]})`);
  root.setProperty('--glassBorder', `rgba(255,255,255,${a[3]})`);
  root.setProperty('--glassBorder2', `rgba(255,255,255,${a[4]})`);
  root.setProperty('--statusBg', style === 'noGlass' ? `rgba(${tint},1)`
    : style === 'fullGlass' ? (dark ? 'rgba(0,0,0,.14)' : 'rgba(255,255,255,.35)')
    : (dark ? 'rgba(0,0,0,.28)' : 'rgba(255,255,255,.7)'));
  root.setProperty('--statusBorder', dark ? 'rgba(255,255,255,.07)' : 'rgba(0,0,0,.06)');

  // Full Glass blur/distortion — CSS backdrop-filter has no true refraction hook (that's
  // in-game only, via GlassRenderer's own shader), so "distortion" here is honestly just a
  // second, lighter blur contribution rather than pretending to bend anything. Other styles
  // RESET the var explicitly: .card reads it in every style, so leaving a Full Glass value
  // behind would silently carry that blur amount into Default after switching back.
  if (style === 'fullGlass') {
    const blur = theme.glassBlur != null ? theme.glassBlur : 0.5;
    const distort = theme.glassDistort != null ? theme.glassDistort : 0.35;
    root.setProperty('--glassBlurPx', `${6 + blur * 24 + distort * 6}px`);
  } else {
    root.setProperty('--glassBlurPx', '14px');
  }

  document.querySelectorAll('.themeToggle').forEach((btn) => {
    btn.querySelector('.iconSun').style.display = dark ? 'none' : '';
    btn.querySelector('.iconMoon').style.display = dark ? '' : 'none';
  });
}

// Darkens (negative) or lightens (positive) a #rrggbb hex color by `pct` percent per channel.
function shade(hex, pct) {
  const n = parseInt(hex.slice(1), 16);
  const clamp = (v) => Math.max(0, Math.min(255, v));
  const f = pct / 100;
  const r = clamp(Math.round(((n >> 16) & 255) * (1 + f)));
  const g = clamp(Math.round(((n >> 8) & 255) * (1 + f)));
  const b = clamp(Math.round((n & 255) * (1 + f)));
  return '#' + [r, g, b].map((v) => v.toString(16).padStart(2, '0')).join('');
}

// #rrggbb -> "rgba(r,g,b,alpha)".
function rgba(hex, alpha) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${alpha})`;
}

window.lume.getTheme().then(applyTheme);

// Both theme buttons (the floating corner one and the one inline on the home screen)
// share the .themeToggle class and toggle the SAME global theme.
document.querySelectorAll('.themeToggle').forEach((btn) => {
  btn.onclick = async () => {
    if (!currentTheme) return;
    currentTheme.mode = currentTheme.mode === 'dark' ? 'light' : 'dark';
    applyTheme(currentTheme);
    await window.lume.setTheme(currentTheme);
  };
});

// "Enter a different key" — only meaningful (and shown) once you're past the key
// screen; takes you back to it without losing the play-screen state underneath.
$('keyBtn').onclick = () => {
  $('home').classList.add('hidden');
  $('login').classList.remove('hidden');
  $('keyBtn').classList.add('hidden');
};

// --- Customize Colors (direct button now, no Settings dropdown) ---
let colorsReturnCard = null;   // whichever of #login/#home was showing before Customize Colors opened

// --- Launcher settings (currently just memory allocation) — kept in their own settings.json,
// separate from theme.json, since theme.json is rewritten wholesale by the in-game
// ColorsScreen and would silently drop a launcher-only field on the next colour edit. ---
let currentSettings = { memory: 4, perfMode: 'default' };
let systemTotalMemGB = null;

function refreshColorsUI() {
  const c = currentTheme[currentTheme.mode];
  $('colorBg').value = c.bg;
  $('colorAccent').value = c.accent;
  $('colorActiveText').value = c.activeText || '#FFFFFF';
  $('colorsModeRow').querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.mode === currentTheme.mode));
  const style = currentTheme.style || 'default';
  $('stylesRow').querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.style === style));
  $('glassSliders').classList.toggle('hidden', style !== 'fullGlass');
  $('glassBlur').value = Math.round((currentTheme.glassBlur != null ? currentTheme.glassBlur : 0.5) * 100);
  $('glassDistort').value = Math.round((currentTheme.glassDistort != null ? currentTheme.glassDistort : 0.35) * 100);
  refreshMemoryUI();
}

function refreshMemoryUI() {
  // Both -Xmx AND -Xms get set to this value (see launcher.js), so the JVM tries to reserve it
  // ALL immediately at startup — asking for more than the machine can actually spare crashes with
  // a native "insufficient memory" error before the game even gets to load a single mod, which
  // just reads as "won't launch" with no obvious cause. Clamp the slider itself to a safe max
  // (leaving ~1.5GB headroom for Windows/GPU driver/the launcher) so that can't be picked in the
  // first place, and silently correct+persist an already-saved value above that ceiling (e.g. one
  // set before this clamp existed, or on a different/smaller machine) instead of letting the very
  // next Play repeat the same crash.
  if (systemTotalMemGB) {
    const safeMax = Math.max(1, Math.floor(systemTotalMemGB - 1.5));
    $('memorySlider').max = safeMax;
    if (currentSettings.memory > safeMax) {
      currentSettings.memory = safeMax;
      window.lume.setSettings(currentSettings);
    }
  }
  $('memorySlider').value = currentSettings.memory;
  $('memoryLabel').textContent = 'Memory  ' + currentSettings.memory + ' GB';
  $('memoryHint').textContent = systemTotalMemGB
    ? `Your system has ${systemTotalMemGB} GB total — capped below that to leave headroom for Windows and background apps.`
    : '';
}

function refreshPerfModeUI() {
  const mode = currentSettings.perfMode === 'ultra' ? 'ultra' : 'default';
  $('perfModeRow').querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.perf === mode));
}

function refreshWallpaperUI() {
  const dim = currentSettings.wallpaperDim != null ? currentSettings.wallpaperDim : 35;
  $('wpDimSlider').value = dim;
  $('wpDimLabel').textContent = 'Dim  ' + dim + '%';
}

window.lume.getSettings().then((s) => {
  currentSettings = s;
  refreshMemoryUI();
  refreshPerfModeUI();
  refreshWallpaperUI();
  applyWallpaper();   // restore the saved wallpaper on launch
});
window.lume.getSysInfo().then((info) => { systemTotalMemGB = info.totalMemGB; refreshMemoryUI(); });

$('memorySlider').addEventListener('input', () => {
  currentSettings.memory = Number($('memorySlider').value);
  $('memoryLabel').textContent = 'Memory  ' + currentSettings.memory + ' GB';
});
$('memorySlider').addEventListener('change', () => window.lume.setSettings(currentSettings));

$('perfModeRow').querySelectorAll('button').forEach((b) => {
  b.onclick = () => {
    currentSettings.perfMode = b.dataset.perf;
    refreshPerfModeUI();
    window.lume.setSettings(currentSettings);
  };
});

// --- Custom wallpaper -------------------------------------------------------------
// The chosen file lives in %APPDATA%/.lumeclient/wallpapers; main.js hands us a data:
// URL for it (file:// is blocked by our CSP). `wallpaper` is the filename or null, and
// `wallpaperDim` (0–100) is a black overlay % so bright photos don't kill readability.
function applyWallpaper() {
  const el = $('wallpaper');
  const dim = (currentSettings.wallpaperDim != null ? currentSettings.wallpaperDim : 35) / 100;
  document.documentElement.style.setProperty('--wpDim', String(dim));
  if (!currentSettings.wallpaper) {
    el.style.backgroundImage = '';
    document.body.classList.remove('has-wallpaper');
    return;
  }
  window.lume.readWallpaper(currentSettings.wallpaper).then((dataUrl) => {
    if (!dataUrl || currentSettings.wallpaper == null) {   // file was deleted/renamed since it was picked
      el.style.backgroundImage = '';
      document.body.classList.remove('has-wallpaper');
      return;
    }
    el.style.backgroundImage = `url("${dataUrl}")`;
    document.body.classList.add('has-wallpaper');
  });
}

// Rebuilds the tile grid from the folder contents: a "None" tile + one per image file.
// Thumbnails are the same data: URLs main.js returns (fine for a small personal folder).
async function refreshWallpaperGrid() {
  const grid = $('wallpaperGrid');
  const files = await window.lume.listWallpapers();
  grid.innerHTML = '';

  const none = document.createElement('div');
  none.className = 'wpTile' + (currentSettings.wallpaper ? '' : ' on');
  none.textContent = 'None';
  none.title = 'Стандартный фон';
  none.onclick = () => selectWallpaper(null);
  grid.appendChild(none);

  if (!files.length) {
    const hint = document.createElement('div');
    hint.className = 'wpEmpty';
    hint.textContent = 'Папка пуста. Нажми «Open folder», положи туда картинку (.png/.jpg) и нажми ⟳.';
    grid.appendChild(hint);
    return;
  }

  for (const name of files) {
    const tile = document.createElement('div');
    tile.className = 'wpTile' + (currentSettings.wallpaper === name ? ' on' : '');
    tile.title = name;
    const label = document.createElement('div');
    label.className = 'wpName';
    label.textContent = name;
    tile.appendChild(label);
    tile.onclick = () => selectWallpaper(name);
    grid.appendChild(tile);
    // Lazy thumbnail — don't block grid render on reading every file.
    window.lume.readWallpaper(name).then((url) => { if (url) tile.style.backgroundImage = `url("${url}")`; });
  }
}

function selectWallpaper(name) {
  currentSettings.wallpaper = name;
  applyWallpaper();
  window.lume.setSettings(currentSettings);
  // Refresh only the selection highlight (cheap) without rebuilding/reloading every thumb.
  // Image tiles carry title=filename; the "None" tile carries the default-bg title.
  $('wallpaperGrid').querySelectorAll('.wpTile').forEach((t) => {
    t.classList.toggle('on', name ? t.title === name : t.title === 'Стандартный фон');
  });
}

$('openWpFolder').onclick = () => window.lume.openWallpaperFolder();
$('refreshWp').onclick = () => refreshWallpaperGrid();

$('wpDimSlider').addEventListener('input', () => {
  currentSettings.wallpaperDim = Number($('wpDimSlider').value);
  $('wpDimLabel').textContent = 'Dim  ' + currentSettings.wallpaperDim + '%';
  applyWallpaper();
});
$('wpDimSlider').addEventListener('change', () => window.lume.setSettings(currentSettings));

$('openColors').onclick = () => {
  colorsReturnCard = [$('login'), $('home')].find((c) => !c.classList.contains('hidden')) || $('login');
  colorsReturnCard.classList.add('hidden');
  refreshColorsUI();
  refreshWallpaperUI();
  refreshWallpaperGrid();   // re-scan the folder each open so newly-dropped files show up
  $('colorsCard').classList.remove('hidden');
};

$('colorsClose').onclick = () => {
  $('colorsCard').classList.add('hidden');
  if (colorsReturnCard) colorsReturnCard.classList.remove('hidden');
};

$('previewTabs').querySelectorAll('button').forEach((b) => {
  b.onclick = () => {
    $('previewTabs').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
    $('previewLauncher').classList.toggle('hidden', b.dataset.preview !== 'launcher');
    $('previewIngame').classList.toggle('hidden', b.dataset.preview !== 'ingame');
  };
});

$('colorsModeRow').querySelectorAll('button').forEach((b) => {
  b.onclick = async () => {
    currentTheme.mode = b.dataset.mode;
    applyTheme(currentTheme);
    refreshColorsUI();
    await window.lume.setTheme(currentTheme);
  };
});

$('stylesRow').querySelectorAll('button').forEach((b) => {
  b.onclick = async () => {
    currentTheme.style = b.dataset.style;
    applyTheme(currentTheme);
    refreshColorsUI();
    await window.lume.setTheme(currentTheme);
  };
});

// Same built-in defaults as main.js's DEFAULT_THEME (renderer is sandboxed, can't import
// it directly) and Theme.java's own hardcoded fallbacks on the in-game side — resets
// colours/style for BOTH modes, leaves the current light/dark mode untouched.
const DEFAULT_THEME = {
  style: 'default',
  glassBlur: 0.5,
  glassDistort: 0.35,
  light: { bg: '#F2EBDD', accent: '#A99BC7', activeText: '#FFFFFF' },
  dark: { bg: '#221F1A', accent: '#B7AAD9', activeText: '#FFFFFF' },
};
$('resetColors').onclick = async () => {
  currentTheme = {
    ...currentTheme,
    style: DEFAULT_THEME.style,
    glassBlur: DEFAULT_THEME.glassBlur,
    glassDistort: DEFAULT_THEME.glassDistort,
    light: { ...DEFAULT_THEME.light },
    dark: { ...DEFAULT_THEME.dark },
  };
  applyTheme(currentTheme);
  refreshColorsUI();
  await window.lume.setTheme(currentTheme);
};

// `input` fires continuously while the native colour picker is open (live preview);
// `change` fires once it closes — only persist then, so dragging the picker doesn't
// spam disk writes / IPC calls on every intermediate value.
function wireColorInput(id, key) {
  const el = $(id);
  el.addEventListener('input', () => {
    currentTheme[currentTheme.mode][key] = el.value;
    applyTheme(currentTheme);
  });
  el.addEventListener('change', () => window.lume.setTheme(currentTheme));
}
wireColorInput('colorBg', 'bg');
wireColorInput('colorAccent', 'accent');
wireColorInput('colorActiveText', 'activeText');

// Same "input = live preview, change = persist" pattern as the colour pickers above.
function wireGlassSlider(id, key) {
  const el = $(id);
  el.addEventListener('input', () => {
    currentTheme[key] = Number(el.value) / 100;
    applyTheme(currentTheme);
  });
  el.addEventListener('change', () => window.lume.setTheme(currentTheme));
}
wireGlassSlider('glassBlur', 'glassBlur');
wireGlassSlider('glassDistort', 'glassDistort');

// --- Key activation ---
const loginCard = $('login');
const keyInput = $('key');
const activateBtn = $('activate');
const loginErr = $('loginErr');

function rejectKey(reason) {
  loginErr.textContent = reason || 'Invalid key';
  loginCard.classList.add('shake');
  setTimeout(() => loginCard.classList.remove('shake'), 450);
  activateBtn.disabled = false;
}

const activateHTML = activateBtn.innerHTML;
const loader = $('loader');

function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

// "Subscription: forever" for keys with no expiry, or a day countdown for ones that do.
function applyExpiry(expiresAt) {
  const el = $('expiry');
  if (!el) return;
  if (!expiresAt) { el.textContent = 'Подписка: бессрочно'; return; }
  const days = Math.ceil((expiresAt - Date.now()) / 86400000);
  el.textContent = days > 0 ? `Подписка: осталось ${days} дн.` : 'Подписка истекла';
}

async function activate() {
  loginErr.textContent = '';
  activateBtn.disabled = true;
  // verifying state on the button (this is also where the online check goes in stage 2)
  activateBtn.innerHTML = '<span class="spinner"></span><span>Verifying…</span>';
  const res = await window.lume.checkKey(keyInput.value);
  if (!res.ok) {
    activateBtn.innerHTML = activateHTML;
    rejectKey(res.reason);
    return;
  }

  // Remembered forever — next launch skips straight to the Play screen (see auto-login below).
  localStorage.setItem('lumeKey', keyInput.value.trim().toUpperCase());

  // Valid → play a loading transition before revealing the play screen.
  $('plan').textContent = res.plan || 'standard';
  applyExpiry(res.expiresAt);
  loginCard.classList.add('hidden');
  await sleep(180);                     // let the key card fade out
  loader.classList.add('show');         // pulsing logo + indeterminate bar
  await sleep(1100);                    // loading beat
  loader.classList.remove('show');
  await sleep(300);                     // loader fades out
  $('home').classList.remove('hidden'); // play screen fades in
  $('keyBtn').classList.remove('hidden');
  activateBtn.innerHTML = activateHTML;
  activateBtn.disabled = false;
}
activateBtn.onclick = activate;
keyInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') activate(); });

// --- Auto-login: a previously-accepted key skips the key screen entirely ---
// (still re-validated online each launch — a revoked/expired key falls back
// to the manual key screen, which is already the default visible state.)
(async () => {
  const savedKey = localStorage.getItem('lumeKey');
  if (!savedKey) return;
  const res = await window.lume.checkKey(savedKey);
  if (!res.ok) return; // leave the key screen showing, let the user re-enter
  $('plan').textContent = res.plan || 'standard';
  applyExpiry(res.expiresAt);
  loginCard.classList.add('hidden');
  $('home').classList.remove('hidden');
  $('keyBtn').classList.remove('hidden');
})();

// --- Version selector (home screen) ---
let selectedVersion = '1.21.4';
$('vers').querySelectorAll('button').forEach((b) => {
  b.onclick = () => {
    selectedVersion = b.dataset.v;
    $('vers').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
  };
});

// --- Nickname: empty on first run, required, remembered forever ---
const savedNick = localStorage.getItem('lumeNick');
if (savedNick) $('nick').value = savedNick;
$('nick').addEventListener('input', () => { $('nick').style.color = ''; });

// --- Play / launch ---
const playBtn = $('play');
const playHTML = playBtn.innerHTML;
const statusBox = $('status');
const prog = $('prog');
const progLabel = $('progLabel');
const progPct = $('progPct');

function log(text) {
  if (!text) return;
  statusBox.textContent += '\n' + text;
  statusBox.scrollTop = statusBox.scrollHeight;
}

// While launching, the Play button becomes a red Cancel button so the user can abort.
let launching = false;
function setLaunching(on) {
  launching = on;
  playBtn.disabled = false;
  if (on) {
    playBtn.classList.add('cancel');
    playBtn.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M6 6l12 12M18 6L6 18" stroke-linecap="round"/></svg><span>Отмена</span>';
  } else {
    playBtn.classList.remove('cancel');
    playBtn.innerHTML = playHTML;
  }
}

window.lume.onStatus((d) => { statusBox.textContent = d.text; progLabel.textContent = d.text; });
window.lume.onLog((d) => log(String(d.text).trim()));
window.lume.onProgress((d) => {
  if (d && d.total) {
    const pct = Math.min(100, Math.round((d.task / d.total) * 100));
    prog.style.width = pct + '%';
    progPct.textContent = pct + '%';
    if (d.type) progLabel.textContent = 'Downloading ' + d.type;
  }
});
window.lume.onLaunching(() => {
  progLabel.textContent = 'Launching Minecraft…';
  log('JVM started — waiting to confirm the game actually opened before closing this window…');
});
window.lume.onLaunchFailed((d) => {
  progLabel.textContent = 'Launch failed (code ' + d.code + ')';
  statusBox.textContent = 'Minecraft closed unexpectedly before finishing startup:\n\n' + (d.log || '(no output captured)');
  statusBox.scrollTop = statusBox.scrollHeight;
  setLaunching(false);
  prog.style.width = '0%';
  progPct.textContent = '';
});
window.lume.onGameClosed((d) => {
  log('Game closed (code ' + d.code + ').');
  progLabel.textContent = 'Closed';
  setLaunching(false);
  // The in-game Colors screen writes to the SAME theme.json while we're hidden for the
  // whole play session — our own currentTheme is just a stale in-memory snapshot from
  // whenever we last read/wrote it, so without re-fetching here, the next colour edit made
  // in the launcher would silently stomp whatever was changed in-game back to pre-session
  // values (the actual cause of "colours don't fully sync between game and launcher").
  window.lume.getTheme().then((t) => {
    currentTheme = t;
    applyTheme(currentTheme);
    if (!$('colorsCard').classList.contains('hidden')) refreshColorsUI();
  });
});

playBtn.onclick = async () => {
  if (launching) {                       // button is acting as Cancel
    window.lume.cancelLaunch();
    setLaunching(false);
    progLabel.textContent = 'Отменено';
    prog.style.width = '0%';
    progPct.textContent = '';
    log('Отменено пользователем.');
    return;
  }
  const nick = $('nick').value.trim();
  if (!nick) {                                   // nickname is required
    $('nick').style.color = '#e05656';
    $('nick').placeholder = 'Сначала введите ник!';
    $('nick').focus();
    progLabel.textContent = 'Введите ник';
    return;
  }
  localStorage.setItem('lumeNick', nick);        // remember forever
  setLaunching(true);
  statusBox.textContent = 'Preparing…';
  prog.style.width = '0%';
  progPct.textContent = '';
  const res = await window.lume.launch({ username: nick, memory: currentSettings.memory, version: selectedVersion });
  if (res && res.cancelled) { setLaunching(false); return; }
  if (!res || !res.ok) {
    log('ERROR: ' + (res && res.error));
    progLabel.textContent = 'Error';
    setLaunching(false);
  }
  // on success the game starts (launcher hides); onGameClosed restores the Play button
};

'use strict';

const $ = (id) => document.getElementById(id);

$('min').onclick = () => window.lume.minWindow();
$('close').onclick = () => window.lume.closeWindow();

window.lume.getHwid().then((h) => { $('hwid').textContent = h; });

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

  // Valid → play a loading transition before revealing the play screen.
  $('plan').textContent = res.plan || 'standard';
  loginCard.classList.add('hidden');
  await sleep(180);                     // let the key card fade out
  loader.classList.add('show');         // pulsing logo + indeterminate bar
  await sleep(1100);                    // loading beat
  loader.classList.remove('show');
  await sleep(300);                     // loader fades out
  $('home').classList.remove('hidden'); // play screen fades in
  activateBtn.innerHTML = activateHTML;
  activateBtn.disabled = false;
}
activateBtn.onclick = activate;
keyInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') activate(); });

// --- Version selector (home screen) ---
let selectedVersion = '1.21.4';
$('vers').querySelectorAll('button').forEach((b) => {
  b.onclick = () => {
    selectedVersion = b.dataset.v;
    $('vers').querySelectorAll('button').forEach((x) => x.classList.toggle('on', x === b));
  };
});

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
window.lume.onGameClosed((d) => {
  log('Game closed (code ' + d.code + ').');
  progLabel.textContent = 'Closed';
  setLaunching(false);
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
  setLaunching(true);
  statusBox.textContent = 'Preparing…';
  prog.style.width = '0%';
  progPct.textContent = '';
  const res = await window.lume.launch({ username: $('nick').value.trim() || 'LumePlayer', memory: 4, version: selectedVersion });
  if (res && res.cancelled) { setLaunching(false); return; }
  if (!res || !res.ok) {
    log('ERROR: ' + (res && res.error));
    progLabel.textContent = 'Error';
    setLaunching(false);
  }
  // on success the game starts (launcher hides); onGameClosed restores the Play button
};

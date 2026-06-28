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

async function activate() {
  loginErr.textContent = '';
  activateBtn.disabled = true;
  const res = await window.lume.checkKey(keyInput.value);
  if (res.ok) {
    $('plan').textContent = res.plan || 'standard';
    loginCard.classList.add('hidden');
    $('home').classList.remove('hidden');
  } else {
    rejectKey(res.reason);
  }
}
activateBtn.onclick = activate;
keyInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') activate(); });

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

function setPlaying(on) {
  playBtn.disabled = on;
  playBtn.innerHTML = on ? '<span class="spinner"></span><span>Launching…</span>' : playHTML;
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
  setPlaying(false);
});

playBtn.onclick = async () => {
  setPlaying(true);
  statusBox.textContent = 'Preparing…';
  prog.style.width = '0%';
  progPct.textContent = '';
  const res = await window.lume.launch({ username: $('nick').value.trim() || 'LumePlayer', memory: 4 });
  if (!res.ok) {
    log('ERROR: ' + res.error);
    progLabel.textContent = 'Error';
    setPlaying(false);
  }
};

'use strict';

/**
 * Lume FunTime events backend.
 *
 * Runs on the OWNER's machine with the OWNER's Telegram account (one account acts
 * as the "server" for every client). It logs into Telegram (gramjs), opens the
 * @FunTimeEventRobot Mini App with proper auth, scrapes the rendered all-anarchy
 * event list, parses it and serves it as JSON at  http://<host>:8077/events .
 *
 * The Lume client just does an HTTP GET to that URL — no per-user Telegram login.
 *
 * Setup (one time):
 *   1) npm install
 *   2) put api_id / api_hash / phone into config.json   (get them at my.telegram.org)
 *   3) npm start   → enter the code Telegram sends (and 2FA password if any)
 *   It saves the session and keeps serving events. Host it (VPS / always-on PC) and
 *   point the client's EVENTS_URL at it.
 *
 * NOTE: this is a Telegram userbot — against Telegram ToS; use a secondary account.
 */

const { app, BrowserWindow } = require('electron');
const http = require('http');
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { TelegramClient, Api } = require('telegram');
const { StringSession } = require('telegram/sessions');

const BOT = 'FunTimeEventRobot';
const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 8077;
const CONFIG = path.join(__dirname, 'config.json');

let client = null;
let latest = { updated: 0, events: [] };

function loadConfig() {
  try { return JSON.parse(fs.readFileSync(CONFIG, 'utf8')); }
  catch (e) { return {}; }
}
function saveConfig(cfg) {
  fs.writeFileSync(CONFIG, JSON.stringify(cfg, null, 2));
}

/**
 * Ask for a login value. You can EITHER type it in the terminal, OR (easier)
 * paste it into a file next to the server — whichever comes first wins. This lets
 * the OWNER enter their own Telegram code without fighting the console.
 */
function ask(question, hide, file) {
  const fpath = file ? path.join(__dirname, file) : null;
  if (fpath) { try { if (fs.existsSync(fpath)) fs.unlinkSync(fpath); } catch (e) {} }
  console.log('\n[Lume TG] ' + question.trim());
  if (fpath) console.log('          (или впиши значение в файл  tg-server/' + file + '  и сохрани)\n');
  return new Promise((resolve) => {
    let done = false;
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    if (hide) { const so = process.stdout; rl._writeToOutput = (s) => { if (s.includes(question)) so.write(s); }; }
    const finish = (v) => { if (done) return; done = true; clearInterval(timer); try { rl.close(); } catch (e) {} resolve(String(v).trim()); };
    rl.question('> ', finish);
    const timer = setInterval(() => {
      if (!fpath) return;
      try {
        if (fs.existsSync(fpath)) {
          const v = fs.readFileSync(fpath, 'utf8').trim();
          if (v) { try { fs.unlinkSync(fpath); } catch (e) {} finish(v); }
        }
      } catch (e) {}
    }, 800);
  });
}

async function login() {
  const cfg = loadConfig();
  if (!cfg.apiId || !cfg.apiHash || !cfg.phone) {
    console.log('\n[Lume TG] fill config.json with apiId, apiHash, phone (from my.telegram.org) and restart.\n');
    process.exit(1);
  }
  client = new TelegramClient(new StringSession(cfg.session || ''), parseInt(cfg.apiId, 10), cfg.apiHash, { connectionRetries: 5 });

  if (cfg.session) {
    await client.connect();
    console.log('[Lume TG] connected with saved session.');
    return;
  }

  await client.start({
    phoneNumber: async () => cfg.phone,
    phoneCode: async () => await ask('Введи код, который Telegram прислал тебе в приложение:', false, 'code.txt'),
    password: async () => await ask('Введи облачный пароль (2FA), если он есть:', true, 'password.txt'),
    onError: (err) => console.log('[Lume TG] login error:', err),
  });
  cfg.session = client.session.save();
  saveConfig(cfg);
  console.log('[Lume TG] logged in & session saved.');
}

async function getWebAppUrl() {
  const res = await client.invoke(new Api.messages.RequestMainWebView({
    peer: BOT, bot: BOT, platform: 'android',
  }));
  return res.url;
}

function scrape(url) {
  return new Promise((resolve) => {
    const win = new BrowserWindow({ show: false, webPreferences: { offscreen: true } });
    let done = false;
    const finish = async () => {
      if (done) return; done = true;
      let text = '';
      try { text = await win.webContents.executeJavaScript('document.body && document.body.innerText || ""'); }
      catch (e) { console.log('[Lume TG] scrape eval failed:', e.message); }
      try { win.destroy(); } catch (e) {}
      resolve(String(text || ''));
    };
    win.webContents.on('did-finish-load', () => setTimeout(finish, 4000));
    setTimeout(finish, 12000);
    win.loadURL(url).catch((e) => { console.log('[Lume TG] loadURL failed:', e.message); finish(); });
  });
}

function parseEvents(text) {
  const out = [];
  const lines = text.split('\n').map((l) => l.trim()).filter(Boolean);
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^(.*?)\s*\|\s*Анарх\S*\s*(\d+)/i);
    if (!m) continue;
    const name = m[1].replace(/^[^\p{L}]*/u, '').trim();
    const anarchy = m[2];
    let time = '', phase = '', rarity = '';
    const next = lines[i + 1] || '';
    if (next.includes('⏳') || next.includes('|')) {
      const parts = next.replace('⏳', '').split('|').map((s) => s.trim());
      time = parts[0] || '';
      phase = parts[1] || '';
      rarity = (parts[2] || '').replace(/^[^\p{L}]*/u, '').trim();
      i++;
    }
    out.push({ anarchy, name, time, phase, rarity });
  }
  return out;
}

async function refresh() {
  try {
    const url = await getWebAppUrl();
    const text = await scrape(url);
    const events = parseEvents(text);
    if (events.length) {
      latest = { updated: Date.now(), events };
      console.log(`[Lume TG] ${new Date().toLocaleTimeString()} — events: ${events.length}`);
    } else {
      console.log(`[Lume TG] no events parsed (rawLen=${text.length}). First 200 chars:\n` + text.slice(0, 200));
    }
  } catch (e) {
    console.log('[Lume TG] refresh failed:', e.message);
  }
}

function startServer() {
  http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    if (req.url.startsWith('/events')) {
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.end(JSON.stringify(latest));
    } else {
      res.statusCode = 404; res.end('Lume FunTime events server. GET /events');
    }
  }).listen(PORT, () => console.log(`[Lume TG] serving events at http://localhost:${PORT}/events`));
}

app.disableHardwareAcceleration();
app.whenReady().then(async () => {
  try {
    await login();
    startServer();
    await refresh();
    setInterval(refresh, 45000);
  } catch (e) {
    console.log('[Lume TG] fatal:', e);
    process.exit(1);
  }
});

app.on('window-all-closed', () => { /* keep running (headless service) */ });

'use strict';

/**
 * Telegram integration for the FunTime events feed.
 *
 * The FunTime events live inside the @FunTimeEventRobot Telegram Mini App, which
 * needs a logged-in Telegram session. We can't read it from the game directly, so
 * the launcher logs into the user's Telegram (gramjs / MTProto), opens the bot's
 * Mini App with proper auth, scrapes the rendered event list, parses it per
 * anarchy and writes <appData>/.lumeclient/events.json. The mod reads that file.
 *
 * Everything runs locally on the user's machine — nothing leaves their PC.
 *
 * NOTE: this is a Telegram userbot (automation of a personal account) which is
 * against Telegram ToS; use a secondary account. The user supplies their own
 * api_id/api_hash from https://my.telegram.org (one-time).
 */

const path = require('path');
const fs = require('fs');
const { app, BrowserWindow } = require('electron');
const { TelegramClient, Api } = require('telegram');
const { StringSession } = require('telegram/sessions');

const BOT = 'FunTimeEventRobot';

let client = null;            // active TelegramClient
let cfg = null;               // { apiId, apiHash, phone, session }
let pending = null;           // login state: { resolveCode, resolvePassword, needPassword }
let loopTimer = null;
let lastEvents = [];

function configFile() {
  return path.join(app.getPath('appData'), '.lumeclient', 'telegram.json');
}
function eventsFile() {
  return path.join(app.getPath('appData'), '.lumeclient', 'events.json');
}

function loadConfig() {
  try {
    if (fs.existsSync(configFile())) cfg = JSON.parse(fs.readFileSync(configFile(), 'utf8'));
  } catch (e) { cfg = null; }
  return cfg;
}
function saveConfig() {
  try {
    fs.mkdirSync(path.dirname(configFile()), { recursive: true });
    fs.writeFileSync(configFile(), JSON.stringify(cfg, null, 2));
  } catch (e) { log('config save failed: ' + e); }
}

let logSink = () => {};
function setLogSink(fn) { logSink = fn || (() => {}); }
function log(msg) { console.log('[Lume TG] ' + msg); try { logSink(String(msg)); } catch (e) {} }

function isLinked() {
  const c = loadConfig();
  return !!(c && c.session && c.apiId && c.apiHash);
}

/** Begin login: requests the SMS/app code. Resolves when a code is needed. */
async function startLogin(apiId, apiHash, phone) {
  apiId = parseInt(apiId, 10);
  cfg = { apiId, apiHash, phone, session: '' };
  client = new TelegramClient(new StringSession(''), apiId, apiHash, { connectionRetries: 3 });

  // gramjs client.start drives the flow via these async callbacks
  pending = {};
  const codePromise = new Promise((res) => { pending.resolveCode = res; });
  const passPromise = new Promise((res) => { pending.resolvePassword = res; });

  // run start() in the background; it will await our callbacks
  pending.startPromise = client.start({
    phoneNumber: async () => phone,
    phoneCode: async () => {
      log('waiting for code…');
      return await codePromise;
    },
    password: async () => {
      pending.needPassword = true;
      log('waiting for 2FA password…');
      return await passPromise;
    },
    onError: (err) => { log('login error: ' + err); },
  }).then(async () => {
    cfg.session = client.session.save();
    saveConfig();
    log('logged in & session saved');
    pending = null;
    startLoop();
    return { ok: true };
  }).catch((err) => {
    log('login failed: ' + err);
    pending = null;
    return { ok: false, error: String(err) };
  });

  return { ok: true, needCode: true };
}

function submitCode(code) {
  if (pending && pending.resolveCode) { pending.resolveCode(String(code).trim()); return { ok: true }; }
  return { ok: false, error: 'no pending login' };
}
function submitPassword(pw) {
  if (pending && pending.resolvePassword) { pending.resolvePassword(String(pw)); return { ok: true }; }
  return { ok: false, error: 'no pending password' };
}

/** Connect with the saved session (no code needed). */
async function connectSaved() {
  const c = loadConfig();
  if (!c || !c.session) return { ok: false, error: 'not linked' };
  client = new TelegramClient(new StringSession(c.session), parseInt(c.apiId, 10), c.apiHash, { connectionRetries: 3 });
  await client.connect();
  log('connected with saved session');
  startLoop();
  return { ok: true };
}

/** Ask Telegram for the bot's authed Mini App URL. */
async function getWebAppUrl() {
  // RequestMainWebView opens the bot's main mini app with valid initData in the URL
  const res = await client.invoke(new Api.messages.RequestMainWebView({
    peer: BOT,
    bot: BOT,
    platform: 'android',
  }));
  return res.url;
}

/** Load the authed Mini App in a hidden window and scrape its rendered text. */
function scrapeMiniApp(url) {
  return new Promise((resolve) => {
    const win = new BrowserWindow({
      show: false,
      webPreferences: { offscreen: true, javascript: true },
    });
    let done = false;
    const finish = async () => {
      if (done) return; done = true;
      try {
        const text = await win.webContents.executeJavaScript('document.body && document.body.innerText || ""');
        resolve(String(text || ''));
      } catch (e) { log('scrape eval failed: ' + e); resolve(''); }
      try { win.destroy(); } catch (e) {}
    };
    win.webContents.on('did-finish-load', () => setTimeout(finish, 4000)); // let the app render
    setTimeout(finish, 12000); // hard timeout
    win.loadURL(url).catch((e) => { log('loadURL failed: ' + e); finish(); });
  });
}

/** Parse the scraped event text into per-anarchy entries. */
function parseEvents(text) {
  const out = [];
  const lines = text.split('\n').map((l) => l.trim()).filter(Boolean);
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^(.*?)\s*\|\s*Анарх\S*\s*(\d+)/i);
    if (!m) continue;
    const name = m[1].replace(/^[^\p{L}]*/u, '').trim(); // strip leading emoji
    const anarchy = m[2];
    // the time/phase line usually follows
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
  if (!client) return;
  try {
    const url = await getWebAppUrl();
    const text = await scrapeMiniApp(url);
    const events = parseEvents(text);
    if (events.length) {
      lastEvents = events;
      fs.mkdirSync(path.dirname(eventsFile()), { recursive: true });
      fs.writeFileSync(eventsFile(), JSON.stringify({ updated: Date.now(), events }, null, 2));
      log('events updated: ' + events.length);
    } else {
      log('no events parsed (rawLen=' + text.length + ')');
    }
  } catch (e) {
    log('refresh failed: ' + e);
  }
}

function startLoop() {
  if (loopTimer) clearInterval(loopTimer);
  refresh();
  loopTimer = setInterval(refresh, 45000);
}

function status() {
  return { linked: isLinked(), connected: !!client, needPassword: !!(pending && pending.needPassword), events: lastEvents.length };
}

async function unlink() {
  try { if (client) await client.disconnect(); } catch (e) {}
  client = null; cfg = null; pending = null;
  if (loopTimer) { clearInterval(loopTimer); loopTimer = null; }
  try { fs.existsSync(configFile()) && fs.unlinkSync(configFile()); } catch (e) {}
  return { ok: true };
}

module.exports = { setLogSink, isLinked, startLogin, submitCode, submitPassword, connectSaved, refresh, status, unlink, eventsFile };

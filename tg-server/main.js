'use strict';

/**
 * Lume FunTime events backend.
 *
 * Runs with the OWNER's Telegram account (one account acts as the "server" for
 * every client). It logs into Telegram (gramjs), opens the @FunTimeEventRobot
 * Mini App with proper auth, scrapes the rendered all-anarchy event list via a
 * headless Chromium (Playwright — no real display needed, works in a plain
 * Docker container), parses it and serves it as JSON at GET /events.
 *
 * The Lume client just does an HTTP GET to that URL — no per-user Telegram login.
 *
 * Setup (one time, LOCAL — Telegram's login code needs an interactive terminal):
 *   1) npm install
 *   2) put api_id / api_hash / phone into config.json   (get them at my.telegram.org)
 *   3) npm start   → enter the code Telegram sends (and 2FA password if any)
 *   This saves `session` into config.json.
 *
 * Deploy (cloud, e.g. Railway — see Dockerfile): set env vars TG_API_ID,
 * TG_API_HASH, TG_PHONE, TG_SESSION (copy `session` from the local config.json
 * above) instead of shipping config.json itself. No interactive step needed
 * once TG_SESSION is set — see loadConfig()/login().
 *
 * NOTE: this is a Telegram userbot — against Telegram ToS; use a secondary account.
 */

const { chromium } = require('playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { TelegramClient, Api } = require('telegram');
const { StringSession } = require('telegram/sessions');

const BOT = 'NightEventRobot';
const PORT = process.env.PORT ? parseInt(process.env.PORT, 10) : 8077;
const CONFIG = path.join(__dirname, 'config.json');

let client = null;
let latest = { updated: 0, events: [] };

/**
 * Cloud deploys (Railway) configure via env vars — TG_API_ID/TG_API_HASH/TG_PHONE/TG_SESSION
 * — instead of config.json, since there's no interactive terminal to log in with there.
 * Log in locally once first (npm start, with config.json filled in), then copy the
 * resulting `session` value into the TG_SESSION env var on the deployed service.
 */
function loadConfig() {
  if (process.env.TG_API_ID) {
    return {
      apiId: process.env.TG_API_ID,
      apiHash: process.env.TG_API_HASH,
      phone: process.env.TG_PHONE,
      session: process.env.TG_SESSION || '',
      fromEnv: true,
    };
  }
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

  if (cfg.fromEnv) {
    console.log('[Lume TG] fatal: TG_SESSION is empty. This deploy has no interactive terminal to log in with —');
    console.log('          log in locally first (fill config.json, `npm start`, enter the code once),');
    console.log('          then copy the saved `session` value from config.json into the TG_SESSION env var here.');
    process.exit(1);
  }

  await client.start({
    phoneNumber: async () => cfg.phone,
    phoneCode: async (isCodeViaApp) => {
      console.log(`[Lume TG] Telegram says isCodeViaApp=${isCodeViaApp} (true = check the "Telegram" service chat inside the app; false = check SMS/incoming call).`);
      return await ask('Введи код, который Telegram прислал тебе:', false, 'code.txt');
    },
    password: async () => await ask('Введи облачный пароль (2FA), если он есть:', true, 'password.txt'),
    // IMPORTANT: must return true to stop gramjs's internal retry loop — returning
    // falsy here makes it call sendCode again immediately, which is exactly what
    // turns one FLOOD_WAIT into an ever-growing one (learned the hard way).
    onError: (err) => {
      if (err && err.seconds) {
        console.log(`[Lume TG] Telegram flood-wait: must wait ${err.seconds}s before trying again. Stopping — restart after that, don't retry immediately.`);
      } else {
        console.log('[Lume TG] login error:', err);
      }
      return true;
    },
  });
  cfg.session = client.session.save();
  saveConfig(cfg);
  console.log('[Lume TG] logged in & session saved.');
}

/**
 * The bot's Mini App is opened via an inline button INSIDE one of its own
 * messages (a "KeyboardButtonWebView"/"KeyboardButtonSimpleWebView"), not the
 * persistent menu-button web app — that's a different Telegram API call
 * (messages.RequestWebView, needs the button's own url + the message it's
 * attached to) than the "always available" one (messages.RequestMainWebView,
 * which is what kept returning BOT_INVALID — wrong call for this bot).
 */
async function getWebAppUrl() {
  const entity = await client.getInputEntity(BOT);
  const messages = await client.getMessages(entity, { limit: 20 });
  let found = null;
  for (const msg of messages) {
    const rows = msg.replyMarkup && msg.replyMarkup.rows;
    if (!rows) continue;
    for (const row of rows) {
      for (const btn of row.buttons) {
        if (btn.className === 'KeyboardButtonWebView' || btn.className === 'KeyboardButtonSimpleWebView') {
          found = { url: btn.url, msgId: msg.id };
          break;
        }
      }
      if (found) break;
    }
    if (found) break;
  }
  if (!found) throw new Error('no web-app button found in the last 20 messages from the bot — send it a message/command first so it replies with the button');

  const res = await client.invoke(new Api.messages.RequestWebView({
    peer: entity, bot: entity, url: found.url, platform: 'android',
    replyTo: new Api.InputReplyToMessage({ replyToMsgId: found.msgId }),
  }));
  return res.url;
}

// The Mini App opens on its home menu (Текущие ивенты / Шахты / ...) — this
// clicks the "Текущие ивенты" nav item programmatically inside the headless
// page before reading the page text, since the actual event list only
// renders after navigating into that section.
const CLICK_LABEL = 'Текущие ивенты';

/** Runs inside the page (Playwright page.evaluate) — find + click the nav item by its exact text. */
function clickLabelInPage(label) {
  const all = Array.from(document.querySelectorAll('*'));
  const target = all.find((el) => el.children.length === 0 && el.textContent && el.textContent.trim() === label);
  if (!target) return false;
  let el = target;
  for (let i = 0; i < 5 && el; i++) { el.click(); el = el.parentElement; }
  return true;
}

let browser = null;
async function ensureBrowser() {
  if (!browser) browser = await chromium.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] });
  return browser;
}

async function scrape(url) {
  const br = await ensureBrowser();
  const page = await br.newPage();
  let text = '';
  try {
    await page.goto(url, { waitUntil: 'load', timeout: 15000 });
    await page.waitForTimeout(4000);
    let clicked = false;
    try { clicked = await page.evaluate(clickLabelInPage, CLICK_LABEL); } catch (e) {}
    if (clicked) await page.waitForTimeout(1500);
    else console.log(`[Lume TG] scrape: could not find "${CLICK_LABEL}" nav item to click — reading the home menu as-is.`);
    text = await page.evaluate(() => (document.body && document.body.innerText) || '');
  } catch (e) {
    console.log('[Lume TG] scrape failed:', e.message);
  } finally {
    await page.close().catch(() => {});
  }
  return String(text || '');
}

// Real layout (found 2026-07-09 from an actual scrape), one block per event,
// each on its own line:
//   #11                     <- anarchy number
//   📦                      <- icon-only line (no letters) — skipped
//   АирДроп                 <- event name
//   Сбор лута · 24м 25с     <- phase, "·" time (time omitted for some phases, e.g. "Ожидание")
//   🎁 Элитный              <- rarity (leading icon stripped)
function parseEvents(text) {
  const out = [];
  const lines = text.split('\n').map((l) => l.trim()).filter(Boolean);
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^#(\d+)$/);
    if (!m) continue;
    const anarchy = m[1];
    let j = i + 1;
    if (j < lines.length && !/\p{L}/u.test(lines[j])) j++;   // icon-only line
    const name = lines[j] || ''; j++;
    let phase = '', time = '';
    if (j < lines.length) {
      const parts = lines[j].split('·').map((s) => s.trim());
      phase = parts[0] || '';
      time = parts[1] || '';
      j++;
    }
    const rarity = (lines[j] || '').replace(/^[^\p{L}]*/u, '').trim();
    out.push({ anarchy, name, time, phase, rarity });
    i = j;
  }
  // the page repeats identical cards for some events — collapse exact duplicates,
  // keeping the last (freshest time) one seen for a given anarchy+name+phase
  const dedup = new Map();
  for (const e of out) dedup.set(`${e.anarchy}|${e.name}|${e.phase}`, e);
  return Array.from(dedup.values());
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
    if (req.url.startsWith('/health')) {
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.end(JSON.stringify({ ok: true }));
    } else if (req.url.startsWith('/events')) {
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.end(JSON.stringify(latest));
    } else {
      res.statusCode = 404; res.end('Lume FunTime events server. GET /events');
    }
  }).listen(PORT, () => console.log(`[Lume TG] serving events at http://localhost:${PORT}/events`));
}

(async () => {
  try {
    await login();
    startServer();
    await refresh();
    setInterval(refresh, 45000);
  } catch (e) {
    console.log('[Lume TG] fatal:', e);
    process.exit(1);
  }
})();

'use strict';

const BASE = process.env.KEY_SERVER_URL || 'http://localhost:3000';
const TOKEN = process.env.KEY_SERVER_ADMIN_TOKEN || '';

async function adminRequest(path, options = {}) {
  const res = await fetch(BASE + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${TOKEN}`,
      ...(options.headers || {}),
    },
  });
  if (!res.ok) throw new Error(`LumeKeyServer ${path} -> ${res.status}`);
  return res.json();
}

/** Generates a new key, tagging the note with the buyer's Telegram id so /mykey can find it later. */
function generateKey({ plan = 'standard', note }) {
  return adminRequest('/api/admin/keys', { method: 'POST', body: JSON.stringify({ plan, note }) });
}

function listKeys() {
  return adminRequest('/api/admin/keys');
}

/** Finds keys previously issued to this Telegram user (note starts with "tg:<chatId>"). */
async function findKeysForTelegramUser(chatId) {
  const all = await listKeys();
  return all.filter((k) => (k.note || '').startsWith(`tg:${chatId} `));
}

module.exports = { generateKey, listKeys, findKeysForTelegramUser };

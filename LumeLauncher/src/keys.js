'use strict';

/**
 * Key verification — stage 2: calls the deployed LumeKeyServer over HTTPS
 * (HWID bind on first activation, one key = one PC). See
 * /LumeKeyServer/README.md for the API. `hwid` is generated in main.js
 * (getHwid) and passed in.
 */

const BASE_URL = 'https://lume-key-server-production.up.railway.app';

const REASONS = {
  not_found: 'Key not found',
  revoked: 'This key was revoked',
  expired: 'This key expired',
  hwid_mismatch: 'This key is already activated on another PC',
};

/**
 * @param {string} rawKey
 * @param {string} hwid
 * @returns {Promise<{ok: boolean, reason?: string, plan?: string}>}
 */
async function checkKey(rawKey, hwid) {
  const key = String(rawKey || '').trim().toUpperCase();
  if (!key) return { ok: false, reason: 'Enter a key' };

  try {
    const res = await fetch(`${BASE_URL}/api/keys/validate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, hwid }),
    });
    const data = await res.json();
    if (data.valid) return { ok: true, plan: data.plan, expiresAt: data.expiresAt || null };
    return { ok: false, reason: REASONS[data.reason] || 'Invalid key' };
  } catch (e) {
    return { ok: false, reason: 'Could not reach the license server — check your connection' };
  }
}

module.exports = { checkKey, BASE_URL };

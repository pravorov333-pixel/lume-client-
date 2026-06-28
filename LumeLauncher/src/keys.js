'use strict';

/**
 * LOCAL key verification (stage 1).
 *
 * For now keys are checked on the user's machine against a small embedded list
 * and a format/checksum rule. This is only for testing the launcher flow.
 * Stage 2 will replace `checkKey` with an online request to our auth server
 * (HWID bind + signed response). The rest of the launcher will not change.
 */

// Test keys that unlock the client locally. Replace / remove for production.
const VALID_KEYS = new Set([
  'LUME-TEST-2026-DEMO',
  'LUME-DEV0-ACCESS-0001',
]);

// Accept LUME-XXXX-XXXX-XXXX where the last group's chars sum is even (toy checksum).
function matchesFormat(key) {
  const re = /^LUME-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/;
  if (!re.test(key)) return false;
  const last = key.split('-').pop();
  let sum = 0;
  for (const ch of last) sum += ch.charCodeAt(0);
  return sum % 2 === 0;
}

/**
 * @returns {{ok: boolean, reason?: string, plan?: string}}
 */
function checkKey(rawKey) {
  const key = String(rawKey || '').trim().toUpperCase();
  if (!key) return { ok: false, reason: 'Enter a key' };

  if (VALID_KEYS.has(key)) {
    return { ok: true, plan: 'tester' };
  }
  if (matchesFormat(key)) {
    return { ok: true, plan: 'standard' };
  }
  return { ok: false, reason: 'Invalid or expired key' };
}

module.exports = { checkKey };

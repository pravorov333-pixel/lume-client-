'use strict';

const express = require('express');
const { getKey, bindHwid, logValidation } = require('../db');

const router = express.Router();

/**
 * POST /api/keys/validate  { key, hwid }
 * First activation on a machine binds the HWID to the key; every activation
 * after that must come from the SAME hwid, or it's rejected (one key = one PC).
 */
router.post('/validate', (req, res) => {
  const key = String(req.body?.key || '').trim().toUpperCase();
  const hwid = String(req.body?.hwid || '').trim();

  if (!key) return res.status(400).json({ valid: false, reason: 'missing_key' });
  if (!hwid) return res.status(400).json({ valid: false, reason: 'missing_hwid' });

  const row = getKey(key);
  if (!row) {
    logValidation(key, hwid, 'not_found');
    return res.json({ valid: false, reason: 'not_found' });
  }
  if (!row.active) {
    logValidation(key, hwid, 'revoked');
    return res.json({ valid: false, reason: 'revoked' });
  }
  if (row.expires_at && row.expires_at < Date.now()) {
    logValidation(key, hwid, 'expired');
    return res.json({ valid: false, reason: 'expired' });
  }
  if (!row.hwid) {
    bindHwid(key, hwid);
    logValidation(key, hwid, 'bound');
    return res.json({ valid: true, plan: row.plan, expiresAt: row.expires_at, firstActivation: true });
  }
  if (row.hwid !== hwid) {
    logValidation(key, hwid, 'hwid_mismatch');
    return res.json({ valid: false, reason: 'hwid_mismatch' });
  }
  logValidation(key, hwid, 'ok');
  return res.json({ valid: true, plan: row.plan, expiresAt: row.expires_at, firstActivation: false });
});

module.exports = router;

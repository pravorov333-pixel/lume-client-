'use strict';

const express = require('express');
const { generateKey, listKeys, getKey, revokeKey, reactivateKey, resetHwid } = require('../db');

const router = express.Router();

/** Every route below requires header: Authorization: Bearer <ADMIN_TOKEN> */
router.use((req, res, next) => {
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  if (!process.env.ADMIN_TOKEN || token !== process.env.ADMIN_TOKEN) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  next();
});

// POST /api/admin/keys  { plan?, expiresInDays?, note? } — manual issuing until payment is wired up
router.post('/keys', (req, res) => {
  const { plan, expiresInDays, note } = req.body || {};
  const row = generateKey({ plan, expiresInDays, note });
  res.json(row);
});

// GET /api/admin/keys — list everything (for the admin panel)
router.get('/keys', (req, res) => {
  res.json(listKeys());
});

// GET /api/admin/keys/:key
router.get('/keys/:key', (req, res) => {
  const row = getKey(req.params.key);
  if (!row) return res.status(404).json({ error: 'not_found' });
  res.json(row);
});

// POST /api/admin/keys/:key/revoke
router.post('/keys/:key/revoke', (req, res) => {
  revokeKey(req.params.key);
  res.json(getKey(req.params.key));
});

// POST /api/admin/keys/:key/reactivate
router.post('/keys/:key/reactivate', (req, res) => {
  reactivateKey(req.params.key);
  res.json(getKey(req.params.key));
});

// POST /api/admin/keys/:key/reset-hwid — let a customer move the key to a new PC
router.post('/keys/:key/reset-hwid', (req, res) => {
  resetHwid(req.params.key);
  res.json(getKey(req.params.key));
});

module.exports = router;

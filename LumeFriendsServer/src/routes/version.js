'use strict';

const express = require('express');
const { getVersion, setVersion, listVersions } = require('../db');

const router = express.Router();

/**
 * GET /api/version?mcVersion=1.21.4
 * The launcher calls this before Play to see if a newer mod jar is published.
 * Returns 404 if nothing has been published for that MC version yet.
 */
router.get('/version', (req, res) => {
  const mcVersion = String(req.query.mcVersion || '').trim();
  if (!mcVersion) return res.status(400).json({ error: 'missing_mcVersion' });
  const row = getVersion(mcVersion);
  if (!row) return res.status(404).json({ error: 'not_found' });
  const base = `${req.protocol}://${req.get('host')}`;
  res.json({ version: row.version, jarUrl: `${base}/downloads/${row.file}` });
});

/** Every /api/admin/* route below requires header: Authorization: Bearer <ADMIN_TOKEN> */
function requireAdmin(req, res, next) {
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  if (!process.env.ADMIN_TOKEN || token !== process.env.ADMIN_TOKEN) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  next();
}

/**
 * POST /api/admin/version  { mcVersion, version, file }
 * Publishes a new jar version — `file` must already exist under public/downloads/
 * (upload it there first — see LumeFriendsServer/README.md "Publishing a mod update").
 */
router.post('/admin/version', requireAdmin, (req, res) => {
  const { mcVersion, version, file } = req.body || {};
  if (!mcVersion || !version || !file) return res.status(400).json({ error: 'missing_fields' });
  res.json(setVersion(mcVersion, version, file));
});

router.get('/admin/versions', requireAdmin, (req, res) => {
  res.json(listVersions());
});

module.exports = router;

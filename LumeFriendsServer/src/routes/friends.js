'use strict';

const express = require('express');
const db = require('../db');

const router = express.Router();

function need(res, val, name) {
  if (!val) { res.status(400).json({ ok: false, reason: `missing_${name}` }); return false; }
  return true;
}

/** POST /api/heartbeat { username, deviceId, server, dimension, x, y, z } */
router.post('/heartbeat', (req, res) => {
  const { username, deviceId, server, dimension, x, y, z } = req.body || {};
  if (!need(res, username, 'username')) return;
  db.heartbeat({ username, display: username, deviceId, server, dimension, x, y, z });
  res.json({ ok: true });
});

/** GET /api/status?names=a,b,c */
router.get('/status', (req, res) => {
  const names = String(req.query.names || '').split(',').map(s => s.trim()).filter(Boolean);
  res.json(db.getPresence(names));
});

/** POST /api/friend/request { from, to } */
router.post('/friend/request', (req, res) => {
  const { from, to } = req.body || {};
  if (!need(res, from, 'from') || !need(res, to, 'to')) return;
  res.json(db.sendRequest(from, to));
});

/** POST /api/friend/accept { username, from } — `username` (me) accepts a request sent by `from` */
router.post('/friend/accept', (req, res) => {
  const { username, from } = req.body || {};
  if (!need(res, username, 'username') || !need(res, from, 'from')) return;
  db.acceptRequest(from, username);
  res.json({ ok: true });
});

/** POST /api/friend/decline { username, from } */
router.post('/friend/decline', (req, res) => {
  const { username, from } = req.body || {};
  if (!need(res, username, 'username') || !need(res, from, 'from')) return;
  db.declineRequest(from, username);
  res.json({ ok: true });
});

/** POST /api/friend/remove { username, friend } */
router.post('/friend/remove', (req, res) => {
  const { username, friend } = req.body || {};
  if (!need(res, username, 'username') || !need(res, friend, 'friend')) return;
  db.removeFriend(username, friend);
  res.json({ ok: true });
});

/** GET /api/friend/list?username=me */
router.get('/friend/list', (req, res) => {
  const username = req.query.username;
  if (!need(res, username, 'username')) return;
  res.json(db.listFriends(username));
});

/** POST /api/point/share { from, deviceId, to, name, x, y, z, server, dimension } */
router.post('/point/share', (req, res) => {
  const { from, deviceId, to, name, x, y, z, server, dimension } = req.body || {};
  if (!need(res, from, 'from') || !need(res, name, 'name')) return;
  res.json(db.sharePoint({ from, deviceId, to: to || 'all', name, x, y, z, server, dimension }));
});

/** GET /api/point/list?username=me */
router.get('/point/list', (req, res) => {
  const username = req.query.username;
  if (!need(res, username, 'username')) return;
  res.json(db.listPoints(username));
});

/** POST /api/point/delete { id, username, deviceId } */
router.post('/point/delete', (req, res) => {
  const { id, username, deviceId } = req.body || {};
  if (!need(res, id, 'id') || !need(res, username, 'username')) return;
  res.json(db.deletePoint(id, username, deviceId));
});

module.exports = router;

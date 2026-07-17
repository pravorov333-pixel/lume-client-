'use strict';

const path = require('path');
const fs = require('fs');
const { DatabaseSync } = require('node:sqlite');   // built into Node 22.5+, no native build tools needed

const dbPath = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'keys.db');
fs.mkdirSync(path.dirname(dbPath), { recursive: true });
const db = new DatabaseSync(dbPath);
db.exec('PRAGMA journal_mode = WAL;');

db.exec(`
  CREATE TABLE IF NOT EXISTS keys (
    key         TEXT PRIMARY KEY,
    plan        TEXT NOT NULL DEFAULT 'standard',
    created_at  INTEGER NOT NULL,
    expires_at  INTEGER,
    active      INTEGER NOT NULL DEFAULT 1,
    hwid        TEXT,
    bound_at    INTEGER,
    note        TEXT
  );

  CREATE TABLE IF NOT EXISTS validation_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    key         TEXT NOT NULL,
    hwid        TEXT,
    result      TEXT NOT NULL,
    at          INTEGER NOT NULL
  );
`);

const CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no O/0/I/1 — avoids look-alike typos

function randomGroup(len) {
  let s = '';
  for (let i = 0; i < len; i++) s += CHARS[Math.floor(Math.random() * CHARS.length)];
  return s;
}

/** Generates a fresh, unused LUME-XXXX-XXXX-XXXX key. */
function generateKey({ plan = 'standard', expiresInDays = null, note = null } = {}) {
  let key;
  const exists = db.prepare('SELECT 1 FROM keys WHERE key = ?');
  do {
    key = `LUME-${randomGroup(4)}-${randomGroup(4)}-${randomGroup(4)}`;
  } while (exists.get(key));

  const now = Date.now();
  const expiresAt = expiresInDays ? now + expiresInDays * 86400000 : null;
  db.prepare(
    'INSERT INTO keys (key, plan, created_at, expires_at, note) VALUES (?, ?, ?, ?, ?)'
  ).run(key, plan, now, expiresAt, note);
  return getKey(key);
}

function getKey(key) {
  return db.prepare('SELECT * FROM keys WHERE key = ?').get(String(key || '').trim().toUpperCase());
}

function listKeys() {
  return db.prepare('SELECT * FROM keys ORDER BY created_at DESC').all();
}

function revokeKey(key) {
  return db.prepare('UPDATE keys SET active = 0 WHERE key = ?').run(key.trim().toUpperCase());
}

function reactivateKey(key) {
  return db.prepare('UPDATE keys SET active = 1 WHERE key = ?').run(key.trim().toUpperCase());
}

/** Clears the HWID bind so the key can be activated on a different machine (support action). */
function resetHwid(key) {
  return db.prepare('UPDATE keys SET hwid = NULL, bound_at = NULL WHERE key = ?').run(key.trim().toUpperCase());
}

function bindHwid(key, hwid) {
  return db.prepare('UPDATE keys SET hwid = ?, bound_at = ? WHERE key = ?').run(hwid, Date.now(), key);
}

function logValidation(key, hwid, result) {
  db.prepare('INSERT INTO validation_log (key, hwid, result, at) VALUES (?, ?, ?, ?)').run(key, hwid || null, result, Date.now());
}

module.exports = { db, generateKey, getKey, listKeys, revokeKey, reactivateKey, resetHwid, bindHwid, logValidation };

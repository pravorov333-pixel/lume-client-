'use strict';

const path = require('path');
const fs = require('fs');
const { DatabaseSync } = require('node:sqlite');   // built into Node 22.5+, no native build tools needed (see LumeKeyServer)

const dbPath = process.env.DB_PATH || path.join(__dirname, '..', 'data', 'friends.db');
fs.mkdirSync(path.dirname(dbPath), { recursive: true });
const db = new DatabaseSync(dbPath);
db.exec('PRAGMA journal_mode = WAL;');

db.exec(`
  CREATE TABLE IF NOT EXISTS presence (
    username    TEXT PRIMARY KEY,   -- lowercase key
    display     TEXT NOT NULL,      -- exact-case name as reported by the client
    device_id   TEXT,
    server      TEXT,
    dimension   TEXT,
    x           REAL, y REAL, z REAL,
    last_seen   INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS friend_requests (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    from_user   TEXT NOT NULL,
    to_user     TEXT NOT NULL,
    created_at  INTEGER NOT NULL,
    UNIQUE(from_user, to_user)
  );

  CREATE TABLE IF NOT EXISTS friends (
    user_a      TEXT NOT NULL,
    user_b      TEXT NOT NULL,
    created_at  INTEGER NOT NULL,
    PRIMARY KEY (user_a, user_b)
  );

  CREATE TABLE IF NOT EXISTS points (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    from_user   TEXT NOT NULL,
    from_device TEXT,
    to_user     TEXT NOT NULL,      -- specific lowercase username, or 'all'
    name        TEXT NOT NULL,
    x           REAL, y REAL, z REAL,
    server      TEXT,
    dimension   TEXT,
    created_at  INTEGER NOT NULL
  );

  -- Mod auto-update pointer, one row per Minecraft version the launcher supports.
  -- "file" is the jar's name under public/downloads/ (see server.js static serving).
  CREATE TABLE IF NOT EXISTS versions (
    mc_version  TEXT PRIMARY KEY,
    version     TEXT NOT NULL,
    file        TEXT NOT NULL,
    updated_at  INTEGER NOT NULL
  );
`);

const norm = (s) => String(s || '').trim().toLowerCase();

// ---- presence ----------------------------------------------------------

function heartbeat({ username, display, deviceId, server, dimension, x, y, z }) {
  const u = norm(username);
  db.prepare(`
    INSERT INTO presence (username, display, device_id, server, dimension, x, y, z, last_seen)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(username) DO UPDATE SET
      display=excluded.display, device_id=excluded.device_id, server=excluded.server,
      dimension=excluded.dimension, x=excluded.x, y=excluded.y, z=excluded.z, last_seen=excluded.last_seen
  `).run(u, display || username, deviceId || null, server || null, dimension || null, x ?? null, y ?? null, z ?? null, Date.now());
}

const ONLINE_WINDOW_MS = 25000; // heartbeat every ~15s client-side; 25s covers one missed beat

function getPresence(usernames) {
  const out = {};
  const now = Date.now();
  const stmt = db.prepare('SELECT * FROM presence WHERE username = ?');
  for (const raw of usernames) {
    const u = norm(raw);
    const row = stmt.get(u);
    if (!row) { out[u] = { online: false }; continue; }
    out[u] = {
      online: now - row.last_seen < ONLINE_WINDOW_MS,
      display: row.display,
      server: row.server,
      dimension: row.dimension,
      x: row.x, y: row.y, z: row.z,
      lastSeen: row.last_seen,
    };
  }
  return out;
}

// ---- friends ------------------------------------------------------------

function areFriends(a, b) {
  return !!db.prepare('SELECT 1 FROM friends WHERE user_a = ? AND user_b = ?').get(norm(a), norm(b));
}

function sendRequest(from, to) {
  const f = norm(from), t = norm(to);
  if (f === t) return { ok: false, reason: 'self' };
  if (areFriends(f, t)) return { ok: false, reason: 'already_friends' };
  // if the other side already sent us a request, auto-accept instead of a duplicate
  const reverse = db.prepare('SELECT 1 FROM friend_requests WHERE from_user = ? AND to_user = ?').get(t, f);
  if (reverse) {
    acceptRequest(t, f); // (from=t, to=f) accepted by f
    return { ok: true, autoAccepted: true };
  }
  db.prepare(`
    INSERT INTO friend_requests (from_user, to_user, created_at) VALUES (?, ?, ?)
    ON CONFLICT(from_user, to_user) DO NOTHING
  `).run(f, t, Date.now());
  return { ok: true, autoAccepted: false };
}

function acceptRequest(from, to) {
  const f = norm(from), t = norm(to);
  db.prepare('DELETE FROM friend_requests WHERE from_user = ? AND to_user = ?').run(f, t);
  const now = Date.now();
  db.prepare('INSERT OR IGNORE INTO friends (user_a, user_b, created_at) VALUES (?, ?, ?)').run(f, t, now);
  db.prepare('INSERT OR IGNORE INTO friends (user_a, user_b, created_at) VALUES (?, ?, ?)').run(t, f, now);
}

function declineRequest(from, to) {
  db.prepare('DELETE FROM friend_requests WHERE from_user = ? AND to_user = ?').run(norm(from), norm(to));
}

function removeFriend(username, friend) {
  const u = norm(username), f = norm(friend);
  db.prepare('DELETE FROM friends WHERE user_a = ? AND user_b = ?').run(u, f);
  db.prepare('DELETE FROM friends WHERE user_a = ? AND user_b = ?').run(f, u);
}

function listFriends(username) {
  const u = norm(username);
  const friends = db.prepare('SELECT user_b FROM friends WHERE user_a = ?').all(u).map(r => r.user_b);
  const incoming = db.prepare('SELECT from_user, created_at FROM friend_requests WHERE to_user = ?').all(u);
  const outgoing = db.prepare('SELECT to_user, created_at FROM friend_requests WHERE from_user = ?').all(u);
  return { friends, incoming, outgoing };
}

// ---- shared points --------------------------------------------------------

function sharePoint({ from, deviceId, to, name, x, y, z, server, dimension }) {
  const f = norm(from), t = norm(to) || 'all';
  if (t !== 'all' && !areFriends(f, t)) return { ok: false, reason: 'not_friends' };
  const info = db.prepare(`
    INSERT INTO points (from_user, from_device, to_user, name, x, y, z, server, dimension, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `).run(f, deviceId || null, t, name, x, y, z, server || null, dimension || null, Date.now());
  return { ok: true, id: info.lastInsertRowid };
}

/** Points shared with `username` directly, or broadcast to 'all' by one of their friends —
 *  or by themself, so your own Fast Waypoint pings/shared points show up on your own client too. */
function listPoints(username) {
  const u = norm(username);
  const friendRows = db.prepare('SELECT user_b FROM friends WHERE user_a = ?').all(u).map(r => r.user_b);
  const rows = db.prepare(`SELECT * FROM points WHERE to_user = ? ORDER BY created_at DESC LIMIT 200`).all(u);
  const broadcasters = [...friendRows, u];
  const placeholders = broadcasters.map(() => '?').join(',');
  const broadcast = db.prepare(
    `SELECT * FROM points WHERE to_user = 'all' AND from_user IN (${placeholders}) ORDER BY created_at DESC LIMIT 200`
  ).all(...broadcasters);
  return [...rows, ...broadcast].sort((a, b) => b.created_at - a.created_at).slice(0, 200);
}

function deletePoint(id, username, deviceId) {
  const row = db.prepare('SELECT * FROM points WHERE id = ?').get(id);
  if (!row) return { ok: false, reason: 'not_found' };
  if (row.from_user !== norm(username)) return { ok: false, reason: 'not_owner' };
  db.prepare('DELETE FROM points WHERE id = ?').run(id);
  return { ok: true };
}

// ---- mod version / auto-update -------------------------------------------

function getVersion(mcVersion) {
  return db.prepare('SELECT * FROM versions WHERE mc_version = ?').get(mcVersion);
}

function listVersions() {
  return db.prepare('SELECT * FROM versions ORDER BY mc_version').all();
}

function setVersion(mcVersion, version, file) {
  db.prepare(`
    INSERT INTO versions (mc_version, version, file, updated_at) VALUES (?, ?, ?, ?)
    ON CONFLICT(mc_version) DO UPDATE SET version=excluded.version, file=excluded.file, updated_at=excluded.updated_at
  `).run(mcVersion, version, file, Date.now());
  return getVersion(mcVersion);
}

module.exports = {
  db, heartbeat, getPresence, sendRequest, acceptRequest, declineRequest,
  removeFriend, listFriends, areFriends, sharePoint, listPoints, deletePoint,
  getVersion, listVersions, setVersion,
};

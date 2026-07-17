# Lume Friends Server

Presence + friends + shared cross-server markers backend for Lume Client.
Powers the in-game **Friends** tab: online status (any server, not just yours),
friend requests, and "share my position" markers friends can see from their own
server session.

No accounts — identity is just the current Minecraft username the client reports
on heartbeat, plus a random per-install `deviceId` (stored in `config/lume.json`)
sent along with writes. This is a convenience trust model between actual
friends, not real auth — same honesty assumption as typing a name into vanilla
chat. Good enough for "see when a friend is online / TPA them", not meant to
gate anything sensitive.

## Stack
Node + Express + `node:sqlite` (same choice as `LumeKeyServer` — no native build
tools needed on this machine).

## Run locally
```
cd LumeFriendsServer
npm install
npm start               # http://localhost:8079
```
The mod's `FriendsNet.BASE_URL` defaults to `http://localhost:8079` — fine for
testing two game instances on the same PC, but friends on **different PCs/networks
can't see each other until this is deployed somewhere public** (see below).

## API
- `POST /api/heartbeat` `{ username, deviceId, server, dimension, x, y, z }` — client
  calls this every ~15s while a world is loaded (see `Friends.tick()` in the mod).
- `GET /api/status?names=a,b,c` → `{ name: { online, server, dimension, x, y, z, lastSeen } }`.
  "online" = heartbeat seen in the last 25s.
- `POST /api/friend/request` `{ from, to }` — auto-accepts if `to` already requested `from`.
- `POST /api/friend/accept` / `/decline` `{ username, from }`.
- `POST /api/friend/remove` `{ username, friend }`.
- `GET /api/friend/list?username=me` → `{ friends: [...], incoming: [...], outgoing: [...] }`.
- `POST /api/point/share` `{ from, deviceId, to, name, x, y, z, server, dimension }` —
  `to` is a specific username or `"all"` (broadcast to every friend).
- `GET /api/point/list?username=me` → points shared directly with you, or broadcast
  by one of your friends.
- `POST /api/point/delete` `{ id, username, deviceId }` — only the creator can delete.
- `GET /api/version?mcVersion=1.21.4` → `{ version, jarUrl }` or 404 — called by the
  launcher before every Play to auto-update the mod jar (`LumeLauncher/src/launcher.js`
  `checkForUpdate`/`ensureMods`). Also serves the mod's Telegram-events auto-update in
  the same way it serves everything else here — this server is the shared "misc backend"
  now, not just friends.
- `POST /api/admin/version` `{ mcVersion, version, file }` (needs `Authorization: Bearer <ADMIN_TOKEN>`)
  — publish a new jar version. `file` is just the filename under `public/downloads/`.
- `GET /api/admin/versions` — list every published version (same bearer token).

## Publishing a mod update
1. Build the mod (`LumeClient/`: `gradlew build`) and copy the jar into
   `LumeFriendsServer/public/downloads/lume-client.jar` (or `lume-client-1165.jar` for 1.16.5).
2. `railway up` from `LumeFriendsServer/` — the jar ships as part of the deploy (it's
   static content, not on the volume, so it must be present locally before every publish).
3. Bump the version pointer:
   ```
   curl -X POST https://friends-lume-server-production.up.railway.app/api/admin/version \
     -H "Authorization: Bearer <ADMIN_TOKEN>" -H "Content-Type: application/json" \
     -d '{"mcVersion":"1.21.4","version":"1.0.1","file":"lume-client.jar"}'
   ```
4. Next time anyone hits Play, the launcher fetches this jar automatically (cached under
   `%APPDATA%/.lumeclient/updates/`, only re-downloaded when the version string changes).

## Deployed
Live at **https://friends-lume-server-production.up.railway.app** (Railway, deployed 2026-07-10).
`FriendsNet.BASE_URL` in the mod already points here.

## Deploy (Railway, free tier — same steps as LumeKeyServer)
1. https://railway.app → New Project → Deploy from GitHub repo → root directory `LumeFriendsServer`.
2. Add a **Volume** mounted at `/app/data` (SQLite file — Railway's filesystem is
   otherwise ephemeral and resets every deploy).
3. Set `DB_PATH=/app/data/friends.db` in the Railway dashboard.
4. Nixpacks auto-detects Node and runs `npm start`. You'll get a public URL.
5. Update `FriendsNet.BASE_URL` in `LumeClient/src/main/java/com/lume/client/social/FriendsNet.java`
   to that URL, rebuild, redistribute the jar.

## TODO
- Rate-limit `/api/heartbeat` and `/api/friend/request` (currently open).
- Once the license-key system (`LumeKeyServer`) is actually wired into the client,
  consider tying identity to the license key instead of a bare username (stronger,
  but not needed for the current "friends trust each other" use case).

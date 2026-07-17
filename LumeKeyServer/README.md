# Lume Key Server

License key issuing + validation backend for Lume Client. **Scaffold** — keys are
issued manually via the admin panel for now; payment integration (auto-generate +
email a key on purchase) is a later step.

## Stack
Node + Express + `node:sqlite` (built into Node 22.5+, no C++ build tools needed —
this machine has no Visual Studio Build Tools, which is why `better-sqlite3` was
avoided; `node:sqlite` is a drop-in-compatible API and ships with Node itself).

## Run locally
```
cd LumeKeyServer
npm install
cp .env.example .env    # set a real ADMIN_TOKEN
npm start               # http://localhost:3000
```
Admin panel: http://localhost:3000/admin.html (paste the ADMIN_TOKEN from `.env`).

## API
- `POST /api/keys/validate` `{ key, hwid }` → `{ valid, plan, reason? }` — called by the
  launcher. First call for a key binds that HWID; later calls must match the same HWID
  (one key = one PC). Reasons: `not_found`, `revoked`, `expired`, `hwid_mismatch`.
- `POST /api/admin/keys` `{ plan?, expiresInDays?, note? }` (needs `Authorization: Bearer <ADMIN_TOKEN>`)
  → generates a `LUME-XXXX-XXXX-XXXX` key.
- `GET /api/admin/keys` — list all keys.
- `POST /api/admin/keys/:key/revoke` / `/reactivate` / `/reset-hwid`.

(Mod auto-update / version publishing lives in `LumeFriendsServer`, not here — see its README.)

## Deploy (Railway, free tier)
1. https://railway.app → New Project → **Deploy from GitHub repo** → pick this repo,
   set the root/service directory to `LumeKeyServer`.
   (Or `railway up` from inside `LumeKeyServer/` with the Railway CLI if installed.)
2. Add a **Volume** mounted at `/app/data` so the SQLite file survives redeploys
   (otherwise it resets every deploy — Railway's filesystem is ephemeral).
3. Set env vars in the Railway dashboard: `ADMIN_TOKEN` (long random string),
   `DB_PATH=/app/data/keys.db`.
4. Railway auto-detects Node via `package.json`/`railway.json` (Nixpacks) and runs
   `npm start`. You'll get a public URL like `your-app.up.railway.app`.
5. Point the launcher at it: change `src/keys.js` to call
   `POST <railway-url>/api/keys/validate` instead of the local-only check (not wired
   up yet — the launcher still uses the offline stage-1 check in the meantime).

## TODO (next stages)
- Wire the launcher (`LumeLauncher/src/keys.js`) to call this server instead of the
  local-only check.
- Payment integration → auto-generate a key + email it on successful purchase
  (Stripe, or crypto given the onecrypto project already exists).
- Rate-limit `/api/keys/validate` (currently open, no throttling).
- Move off the free Railway tier once there's real traffic (cold-start/sleep on
  free tier would delay launcher activation).

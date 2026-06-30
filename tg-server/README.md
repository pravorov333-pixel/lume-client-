# Lume FunTime events server

Reads the **@FunTimeEventRobot** Telegram Mini App through **one Telegram account**
(yours — acts as the server) and serves the all-anarchy event list as JSON. Every
Lume client just fetches `http://<host>:8077/events` — no per-user Telegram login.

## Setup (one time)

1. Get `api_id` + `api_hash` at https://my.telegram.org → **API development tools**.
2. Copy `config.example.json` → `config.json` and fill in `apiId`, `apiHash`, `phone`.
3. Install + run:
   ```
   npm install
   npm start
   ```
4. On first run it asks for the **code** Telegram sends you (and a **2FA password**
   if you have one). After that the session is saved to `config.json` — no more codes.
5. It prints `serving events at http://localhost:8077/events` and refreshes every 45s.

## Hosting

- For just you: run it on your always-on PC; the client points at `http://localhost:8077/events`.
- For other users (customers): host it on a VPS with a public IP/domain, and set the
  client's `EVENTS_URL` to `http://<your-domain>:8077/events`.

## Notes

- This is a Telegram **userbot** (automation of a personal account) — against Telegram
  ToS. Use a **secondary account**, not your main one.
- `config.json` holds your session — keep it private (it's git-ignored).
- If `no events parsed` shows up, the printed raw text helps tune the parser in `main.js`.

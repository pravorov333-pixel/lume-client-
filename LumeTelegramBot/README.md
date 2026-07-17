# Lume Telegram Bot

Sells Lume Client license keys inside Telegram — **manual payment confirmation**
(no payment gateway wired up anywhere yet), auto-issues the key the instant you
approve. Talks to `LumeKeyServer`'s existing admin API — nothing new needed there.

## Flow
1. Buyer messages the bot → `/start` → taps **💳 Купить ключ** → sees `PAYMENT_INSTRUCTIONS`
   (your card/crypto/@username — edit this in `.env`).
2. Buyer pays you however you told them to, taps **✅ Я оплатил**, sends a screenshot
   or text as proof.
3. You (the admin, `ADMIN_CHAT_ID`) get a DM with the proof + **✅ Подтвердить / ❌ Отклонить**.
4. Tap Confirm → the bot calls `LumeKeyServer` to generate a key and DMs it straight
   to the buyer. Tap Reject → buyer gets a "not confirmed" message.
5. `/mykey` lets a buyer re-fetch their own key(s) later (matched by their Telegram id,
   stored in the key's `note` field as `tg:<chatId> ...`).

## Setup
```
cd LumeTelegramBot
npm install
cp .env.example .env
```
Fill in `.env`:
- **`BOT_TOKEN`** — message **@BotFather** on Telegram → `/newbot` → follow the prompts →
  paste the token it gives you. (This is the one thing that has to come from you —
  it's tied to your Telegram account, nothing I can generate.)
- **`ADMIN_CHAT_ID`** — start the bot once with this blank, DM it `/id`, it replies with
  your numeric chat id. Paste that in, restart the bot.
- **`KEY_SERVER_URL`** / **`KEY_SERVER_ADMIN_TOKEN`** — must point at a running
  `LumeKeyServer` and match its `ADMIN_TOKEN` (see `../LumeKeyServer/.env`).
- **`PAYMENT_INSTRUCTIONS`** / **`PRICE_LABEL`** — whatever you want shown to buyers.

Run:
```
npm start
```

## Notes / limitations (scaffold)
- Pending payment requests live **in memory** — a bot restart loses any requests that
  haven't been approved/rejected yet (buyer would just need to tap "I paid" again).
- No real payment verification — approval is 100% on you eyeballing the proof. If/when
  a payment gateway gets added, replace the manual `/approve` tap with a webhook that
  calls `keyServer.generateKey()` automatically.
- Uses `telegraf` (not `node-telegram-bot-api`) specifically to avoid a deep dependency
  chain (`request`/`form-data`) with 2 known-critical CVEs.

## Deploy
Same story as `LumeKeyServer` — needs to run somewhere persistent (Railway/Render, or
alongside the key server). Long-polling (`bot.launch()`), no public port/webhook needed,
so it can run anywhere Node runs.

'use strict';

require('dotenv').config();
const path = require('path');
const { Telegraf, Markup } = require('telegraf');
const keyServer = require('./keyServer');

const TOKEN = process.env.BOT_TOKEN;
if (!TOKEN) {
  console.error('[LumeBot] Missing BOT_TOKEN in .env — get one from @BotFather (/newbot) first.');
  process.exit(1);
}

const ADMIN_CHAT_ID = process.env.ADMIN_CHAT_ID ? Number(process.env.ADMIN_CHAT_ID) : null;
const PAYMENT_INSTRUCTIONS = process.env.PAYMENT_INSTRUCTIONS || 'Напишите администратору за реквизитами.';
const PRICE_LABEL = process.env.PRICE_LABEL || '—';
const LOGO_PATH = path.join(__dirname, '..', '..', 'LumeLauncher', 'resources', 'lume.png');

const bot = new Telegraf(TOKEN);

// requestId -> { buyerChatId, buyerName, buyerUsername }
const pending = new Map();
// chatId -> true — set right after the buyer taps "I paid", cleared on their next message
const waitingForProof = new Map();

const mainMenu = Markup.inlineKeyboard([[Markup.button.callback('💳 Купить ключ', 'buy')]]);
const buyMenu = Markup.inlineKeyboard([[Markup.button.callback('✅ Я оплатил', 'paid')]]);
const adminDecisionMenu = (requestId) => Markup.inlineKeyboard([[
  Markup.button.callback('✅ Подтвердить', `approve:${requestId}`),
  Markup.button.callback('❌ Отклонить', `reject:${requestId}`),
]]);

async function sendWelcome(ctx) {
  const caption =
    '◆ *Lume Client*\n' +
    'Легитимный клиент для Minecraft — HUD, визуалы, косметика, помощники для FunTime/HolyWorld.\n\n' +
    `Цена: *${PRICE_LABEL}*\n\n` +
    'Нажми «Купить ключ», чтобы начать.';
  try {
    await ctx.replyWithPhoto({ source: LOGO_PATH }, { caption, parse_mode: 'Markdown', ...mainMenu });
  } catch {
    await ctx.reply(caption, { parse_mode: 'Markdown', ...mainMenu });
  }
}

bot.start((ctx) => sendWelcome(ctx));

bot.help((ctx) => ctx.reply(
  '/start — о клиенте и покупка\n' +
  '/mykey — посмотреть свои ключи\n' +
  '/id — узнать свой Telegram id (для настройки бота)'
));

bot.command('id', (ctx) => ctx.reply(
  `Твой chat id: \`${ctx.chat.id}\`\nВпиши его в LumeTelegramBot/.env как ADMIN_CHAT_ID, если это ты — админ.`,
  { parse_mode: 'Markdown' }
));

bot.command('mykey', async (ctx) => {
  const chatId = ctx.chat.id;
  try {
    const keys = await keyServer.findKeysForTelegramUser(chatId);
    if (keys.length === 0) {
      await ctx.reply('Ключей на твоём аккаунте не найдено. Нажми /start, чтобы купить.');
      return;
    }
    const lines = keys.map((k) => `\`${k.key}\` — ${k.active ? '✅ активен' : '❌ отозван'}${k.hwid ? ' · привязан к ПК' : ' · ещё не активирован'}`);
    await ctx.reply(lines.join('\n'), { parse_mode: 'Markdown' });
  } catch (e) {
    await ctx.reply('Не получилось связаться с сервером ключей. Попробуй позже.');
    console.error('[LumeBot] /mykey failed:', e.message);
  }
});

bot.action('buy', async (ctx) => {
  await ctx.answerCbQuery();
  await ctx.reply(
    `💳 *Оплата*\n\n${PAYMENT_INSTRUCTIONS}\n\nПосле оплаты нажми «Я оплатил» и пришли подтверждение (скриншот или текст).`,
    { parse_mode: 'Markdown', ...buyMenu }
  );
});

bot.action('paid', async (ctx) => {
  await ctx.answerCbQuery();
  waitingForProof.set(ctx.chat.id, true);
  await ctx.reply('Пришли скриншот или сообщение с подтверждением оплаты следующим сообщением.');
});

bot.action(/^approve:(.+)$/, async (ctx) => {
  if (ADMIN_CHAT_ID && ctx.chat.id !== ADMIN_CHAT_ID) { await ctx.answerCbQuery('Только для админа'); return; }
  const requestId = ctx.match[1];
  const req = pending.get(requestId);
  if (!req) { await ctx.answerCbQuery('Заявка уже обработана или устарела'); return; }
  pending.delete(requestId);
  await ctx.answerCbQuery();

  try {
    const note = `tg:${req.buyerChatId} ${req.buyerUsername || req.buyerName || ''}`.trim();
    const row = await keyServer.generateKey({ plan: 'standard', note });
    await bot.telegram.sendMessage(req.buyerChatId,
      `✅ Оплата подтверждена!\n\nТвой ключ:\n\`${row.key}\`\n\nВведи его в лаунчере Lume.`,
      { parse_mode: 'Markdown' });
    await safeAppend(ctx, `✅ Подтверждено, выдан ключ ${row.key}`);
  } catch (e) {
    await ctx.reply(`Не удалось выдать ключ: ${e.message}`);
    console.error('[LumeBot] key generation failed:', e.message);
  }
});

bot.action(/^reject:(.+)$/, async (ctx) => {
  if (ADMIN_CHAT_ID && ctx.chat.id !== ADMIN_CHAT_ID) { await ctx.answerCbQuery('Только для админа'); return; }
  const requestId = ctx.match[1];
  const req = pending.get(requestId);
  if (!req) { await ctx.answerCbQuery('Заявка уже обработана или устарела'); return; }
  pending.delete(requestId);
  await ctx.answerCbQuery();

  await bot.telegram.sendMessage(req.buyerChatId, '❌ Оплата не подтверждена. Если это ошибка — напиши администратору.');
  await safeAppend(ctx, '❌ Отклонено');
});

/** Appends a status line to the admin's original message (works for both photo captions and plain text). */
async function safeAppend(ctx, extra) {
  try {
    const msg = ctx.callbackQuery.message;
    if (msg.caption !== undefined) {
      await ctx.editMessageCaption(`${msg.caption}\n\n${extra}`);
    } else {
      await ctx.editMessageText(`${msg.text}\n\n${extra}`);
    }
  } catch (e) {
    // editing can fail if the message is too old/unchanged — not critical, the buyer DM already went out
    console.error('[LumeBot] could not edit admin message:', e.message);
  }
}

// Any message (text or photo) from a buyer who just tapped "I paid" is treated as payment proof.
bot.on('message', async (ctx, next) => {
  const chatId = ctx.chat.id;
  const msg = ctx.message;
  if (msg.text && msg.text.startsWith('/')) return next();   // let command handlers run
  if (!waitingForProof.get(chatId)) return next();
  waitingForProof.delete(chatId);

  if (!ADMIN_CHAT_ID) {
    await ctx.reply('Бот ещё не настроен (нет админа) — попробуй позже.');
    return;
  }

  const requestId = `${chatId}-${Date.now()}`;
  pending.set(requestId, {
    buyerChatId: chatId,
    buyerName: [msg.from.first_name, msg.from.last_name].filter(Boolean).join(' '),
    buyerUsername: msg.from.username ? `@${msg.from.username}` : null,
  });

  const header = `🧾 Новая заявка на оплату\nОт: ${msg.from.username ? '@' + msg.from.username : msg.from.first_name} (id ${chatId})`;
  try {
    if (msg.photo) {
      const fileId = msg.photo[msg.photo.length - 1].file_id;
      await bot.telegram.sendPhoto(ADMIN_CHAT_ID, fileId, { caption: header, ...adminDecisionMenu(requestId) });
    } else {
      await bot.telegram.sendMessage(ADMIN_CHAT_ID, `${header}\n\n${msg.text || '(без текста)'}`, adminDecisionMenu(requestId));
    }
    await ctx.reply('Спасибо! Заявка отправлена администратору, жди подтверждения.');
  } catch (e) {
    console.error('[LumeBot] failed to notify admin:', e.message);
    await ctx.reply('Не получилось отправить заявку. Попробуй ещё раз позже.');
  }
});

bot.launch()
  .then(() => console.log('[LumeBot] polling started'))
  .catch((e) => {
    console.error('[LumeBot] failed to start —', e.description || e.message);
    console.error('[LumeBot] check BOT_TOKEN in .env is a real token from @BotFather.');
    process.exit(1);
  });

process.once('SIGINT', () => bot.stop('SIGINT'));
process.once('SIGTERM', () => bot.stop('SIGTERM'));

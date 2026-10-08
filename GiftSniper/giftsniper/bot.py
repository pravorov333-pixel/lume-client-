"""Telegram-бот: алерты о находках, команды сравнения цен, ручная покупка по кнопке, дайджест в канал."""
from __future__ import annotations

import asyncio
import logging

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.filters import Command, CommandObject
from aiogram.types import (CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup,
                           LinkPreviewOptions, Message)

from .engine import Engine
from .format import fmt_compare, fmt_finds, fmt_opp, fmt_spreads, fmt_status, mt

log = logging.getLogger("bot")

HELP = """<b>Gift Sniper</b> — снайпер подарков/NFT Telegram и сравнение цен по маркетам.

/floor <i>коллекция</i> [| <i>модель</i>] — где дешевле купить и где выгоднее продать
/arb [N] — топ расхождений флоров между маркетами (с учётом комиссий)
/snipe on|off — включить/выключить снайпер
/autobuy on|off — автопокупка (лимиты — в config.yaml)
/finds — последние находки
/status — состояние маркетов
/digest — отправить арбитражный дайджест в канал

Пример: <code>/floor Plush Pepe | Gold</code>"""


def build(engine: Engine) -> tuple[Bot, Dispatcher, list]:
    """Возвращает бота, диспетчер и список фоновых корутин (дайджест в канал)."""
    cfg = engine.cfg
    bot = Bot(cfg.bot_token, default=DefaultBotProperties(
        parse_mode="HTML", link_preview=LinkPreviewOptions(is_disabled=True)))
    dp = Dispatcher()
    r = Router()
    admins = set(cfg.admin_ids)
    r.message.filter(F.from_user.id.in_(admins))
    r.callback_query.filter(F.from_user.id.in_(admins))

    # ---- уведомления из движка ------------------------------------------
    async def send_all(text: str, kb: InlineKeyboardMarkup | None = None) -> None:
        for chat in cfg.alert_chat_ids or cfg.admin_ids:
            try:
                await bot.send_message(chat, text, reply_markup=kb)
            except Exception as e:
                log.warning("send to %s: %s", chat, e)

    async def on_opportunity(opp) -> None:
        l = opp.listing
        oid = f"{l.market}:{l.listing_id}"[:60]
        kb = InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(
            text=f"Купить на {mt(l.market)}", callback_data=f"b:{oid}")]])
        await send_all(fmt_opp(opp), kb)

    engine.on_opportunity = on_opportunity
    engine.on_message = send_all

    # ---- команды ----------------------------------------------------------
    @r.message(Command("start", "help"))
    async def _help(m: Message):
        await m.answer(HELP)

    @r.message(Command("floor", "compare"))
    async def _floor(m: Message, command: CommandObject):
        if not command.args:
            return await m.answer("Формат: <code>/floor Plush Pepe | Gold</code>")
        col, _, model = (x.strip() for x in command.args.partition("|"))
        wait = await m.answer("⏳ Собираю цены...")
        book = await engine.book(col, model or None)
        await wait.edit_text(fmt_compare(col, model or None, book, engine.live,
                                         cfg.sniper.undercut))

    @r.message(Command("arb"))
    async def _arb(m: Message, command: CommandObject):
        top = int(command.args) if command.args and command.args.isdigit() else 10
        wait = await m.answer("⏳ Сравниваю флоры...")
        await wait.edit_text(fmt_spreads(await engine.spreads(min_profit=0.0), top))

    @r.message(Command("snipe"))
    async def _snipe(m: Message, command: CommandObject):
        if command.args in ("on", "off"):
            engine.snipe_on = command.args == "on"
        await m.answer(f"Снайпер: {'🟢 вкл' if engine.snipe_on else '🔴 выкл'}")

    @r.message(Command("autobuy"))
    async def _autobuy(m: Message, command: CommandObject):
        if command.args in ("on", "off"):
            engine.autobuy_on = command.args == "on"
        dry = " (dry-run, без реальных покупок)" if cfg.autobuy.dry_run else ""
        await m.answer(f"Автобай: {'🟢 вкл' if engine.autobuy_on else '🔴 выкл'}{dry}")

    @r.message(Command("status"))
    async def _status(m: Message):
        await m.answer(fmt_status(engine))

    @r.message(Command("finds"))
    async def _finds(m: Message):
        await m.answer(fmt_finds(engine.store.recent_finds(15)))

    @r.message(Command("digest"))
    async def _digest(m: Message):
        if not cfg.channel_id:
            return await m.answer("channel_id не задан в config.yaml")
        await post_digest()
        await m.answer("Дайджест отправлен в канал ✅")

    # ---- покупка по кнопке (с подтверждением) ------------------------------
    @r.callback_query(F.data.startswith("b:"))
    async def _buy_ask(q: CallbackQuery):
        oid = q.data[2:]
        opp = engine.opps.get(oid)
        if not opp:
            return await q.answer("Находка устарела", show_alert=True)
        kb = InlineKeyboardMarkup(inline_keyboard=[[
            InlineKeyboardButton(text=f"✅ Да, купить за {opp.cost_ton:.2f} TON", callback_data=f"y:{oid}"),
            InlineKeyboardButton(text="Отмена", callback_data="n"),
        ]])
        await q.message.edit_reply_markup(reply_markup=kb)
        await q.answer()

    @r.callback_query(F.data.startswith("y:"))
    async def _buy_do(q: CallbackQuery):
        opp = engine.opps.pop(q.data[2:], None)
        await q.message.edit_reply_markup(reply_markup=None)
        if not opp:
            return await q.answer("Находка устарела", show_alert=True)
        await q.answer("Покупаю...")
        await engine.buy(opp, manual=True)

    @r.callback_query(F.data == "n")
    async def _cancel(q: CallbackQuery):
        await q.message.edit_reply_markup(reply_markup=None)
        await q.answer("Отменено")

    # ---- дайджест в канал ---------------------------------------------------
    async def post_digest() -> None:
        spreads = await engine.spreads(min_profit=0.0)
        await bot.send_message(cfg.channel_id, fmt_spreads(spreads, cfg.digest_top,
                                                           "Арбитраж подарков Telegram"))

    async def digest_loop() -> None:
        while True:
            await asyncio.sleep(cfg.digest_interval_min * 60)
            try:
                await post_digest()
            except Exception as e:
                log.warning("digest: %s", e)

    dp.include_router(r)
    background = [digest_loop()] if cfg.channel_id and cfg.digest_interval_min > 0 else []
    return bot, dp, background

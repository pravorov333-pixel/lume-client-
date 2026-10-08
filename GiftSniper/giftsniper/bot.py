"""Telegram-бот: всё управление с телефона.

Вход в аккаунт, настройки, алерты с кнопкой «Купить», сравнение цен, арбитраж, дайджест в канал.
"""
from __future__ import annotations

import asyncio
import logging

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.filters import Command, CommandObject
from aiogram.types import (BotCommand, CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup,
                           KeyboardButton, LinkPreviewOptions, Message, ReplyKeyboardMarkup,
                           ReplyKeyboardRemove)

from . import settings
from .engine import Engine
from .format import fmt_compare, fmt_finds, fmt_opp, fmt_spreads, fmt_status, mt
from .tgauth import Account

log = logging.getLogger("bot")

HELP = """<b>Gift Sniper</b> — снайпер подарков/NFT Telegram и сравнение цен по маркетам.

<b>Начало работы</b>
1. /api <i>api_id api_hash</i> — ключи с my.telegram.org (один раз)
2. /login — войти в свой Telegram-аккаунт (нужен для маркетов)

<b>Команды</b>
/floor <i>коллекция</i> [| <i>модель</i>] — где дешевле купить и где выгоднее продать
/arb [N] — топ расхождений флоров между маркетами
/snipe on|off — снайпер
/autobuy on|off — автопокупка
/settings — все настройки, /set <i>ключ значение</i> — изменить
/finds — последние находки
/status — состояние маркетов
/digest — дайджест в канал
/logout — выйти из аккаунта

Пример: <code>/floor Plush Pepe | Gold</code>"""

COMMANDS = [
    BotCommand(command="menu", description="Главное меню"),
    BotCommand(command="floor", description="Где купить / где продать"),
    BotCommand(command="arb", description="Арбитраж по флорам"),
    BotCommand(command="status", description="Состояние маркетов"),
    BotCommand(command="settings", description="Настройки"),
    BotCommand(command="finds", description="Последние находки"),
    BotCommand(command="login", description="Войти в Telegram-аккаунт"),
    BotCommand(command="help", description="Помощь"),
]


def menu_kb(engine: Engine) -> InlineKeyboardMarkup:
    b = InlineKeyboardButton
    return InlineKeyboardMarkup(inline_keyboard=[
        [b(text="📊 Арбитраж", callback_data="m:arb"), b(text="⚙️ Статус", callback_data="m:status")],
        [b(text=f"🎯 Снайпер: {'вкл' if engine.snipe_on else 'выкл'}", callback_data="m:snipe"),
         b(text=f"🛒 Автобай: {'вкл' if engine.autobuy_on else 'выкл'}", callback_data="m:autobuy")],
        [b(text="🗂 Находки", callback_data="m:finds"), b(text="🔧 Настройки", callback_data="m:settings")],
        [b(text="📣 Дайджест в канал", callback_data="m:digest")],
    ])


def build(engine: Engine, account: Account) -> tuple[Bot, Dispatcher, list]:
    """Возвращает бота, диспетчер и список фоновых корутин (дайджест в канал)."""
    cfg, store = engine.cfg, engine.store
    bot = Bot(cfg.bot_token, default=DefaultBotProperties(
        parse_mode="HTML", link_preview=LinkPreviewOptions(is_disabled=True)))
    dp = Dispatcher()
    r = Router()
    login_state: dict[int, str] = {}   # user_id -> phone | code | password

    def admins() -> set[int]:
        owner = store.get("owner")
        return set(cfg.admin_ids) | ({int(owner)} if owner else set())

    async def is_admin(obj) -> bool:  # async: aiogram гоняет sync-фильтры в отдельном потоке
        return obj.from_user is not None and obj.from_user.id in admins()

    # ---- уведомления из движка ------------------------------------------
    async def send_all(text: str, kb: InlineKeyboardMarkup | None = None) -> None:
        for chat in cfg.alert_chat_ids or admins():
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

    async def after_login(m: Message) -> None:
        await m.answer(f"✅ Вошли как {await account.me()}. Подключаю маркеты...",
                       reply_markup=ReplyKeyboardRemove())
        await engine.start_markets()
        await m.answer(fmt_status(engine), reply_markup=menu_kb(engine))

    # ---- старт и владелец бота ---------------------------------------------
    @dp.message(Command("start"))
    async def _start(m: Message):
        if not admins():
            # Никто не назначен — первый, кто написал /start, становится владельцем
            store.set("owner", str(m.from_user.id))
            await m.answer("👑 Вы — владелец этого бота. Остальным он отвечать не будет.")
        if not await is_admin(m):
            return await m.answer("Это приватный бот.")
        await m.answer(HELP)
        if not account.has_api:
            await m.answer("Шаг 1: пришлите <code>/api api_id api_hash</code>.\n"
                           "Взять их: my.telegram.org → API development tools.")
        elif not await account.connect():
            await m.answer("Шаг 2: войдите в аккаунт — /login")
        else:
            await m.answer("Главное меню", reply_markup=menu_kb(engine))

    r.message.filter(is_admin)
    r.callback_query.filter(is_admin)

    @r.message(Command("help"))
    async def _help(m: Message):
        await m.answer(HELP)

    @r.message(Command("menu"))
    async def _menu(m: Message):
        await m.answer("Главное меню", reply_markup=menu_kb(engine))

    # ---- вход в аккаунт -----------------------------------------------------
    @r.message(Command("api"))
    async def _api(m: Message, command: CommandObject):
        parts = (command.args or "").split()
        try:
            await m.delete()   # не оставляем api_hash в чате
        except Exception:
            pass
        if len(parts) != 2 or not parts[0].isdigit():
            return await m.answer("Формат: <code>/api 1234567 0123456789abcdef0123456789abcdef</code>")
        store.set("api_id", parts[0])
        store.set("api_hash", parts[1])
        account.client = None
        await m.answer("🔑 Ключи сохранены. Теперь /login")

    @r.message(Command("login"))
    async def _login(m: Message):
        if not account.has_api:
            return await m.answer("Сначала /api api_id api_hash")
        if await account.connect():
            return await m.answer(f"Уже вошли как {await account.me()}. Выйти — /logout")
        login_state[m.from_user.id] = "phone"
        kb = ReplyKeyboardMarkup(resize_keyboard=True, one_time_keyboard=True, keyboard=[[
            KeyboardButton(text="📱 Отправить мой номер", request_contact=True)]])
        await m.answer("Нажмите кнопку ниже или пришлите номер в формате +79991234567", reply_markup=kb)

    @r.message(Command("logout"))
    async def _logout(m: Message):
        await account.logout()
        for mk in engine.markets.values():
            mk.ready = False
        await m.answer("Вышли из аккаунта. Снова войти — /login")

    @r.message(F.from_user.id.func(lambda uid: uid in login_state))
    async def _login_steps(m: Message):
        uid = m.from_user.id
        step = login_state[uid]
        text = m.contact.phone_number if m.contact else (m.text or "")
        if text.startswith("/"):
            login_state.pop(uid, None)
            return await m.answer("Вход отменён.", reply_markup=ReplyKeyboardRemove())
        try:
            if step == "phone":
                await account.send_code(text)
                login_state[uid] = "code"
                await m.answer(
                    "📩 Telegram прислал код в приложение.\n"
                    "⚠️ Отправьте его <b>через пробелы</b>, например <code>1 2 3 4 5</code> — "
                    "иначе Telegram сочтёт код пересланным и аннулирует его.",
                    reply_markup=ReplyKeyboardRemove())
            elif step == "code":
                ok = await account.sign_in_code(text)
                await _try_delete(m)
                if ok:
                    login_state.pop(uid, None)
                    await after_login(m)
                else:
                    login_state[uid] = "password"
                    await m.answer("🔒 Включена двухэтапная защита — пришлите облачный пароль "
                                   "(сообщение сразу удалю).")
            elif step == "password":
                await _try_delete(m)
                await account.sign_in_password(text)
                login_state.pop(uid, None)
                await after_login(m)
        except Exception as e:
            login_state.pop(uid, None)
            await m.answer(f"❌ Не получилось: {e}\nПопробуйте ещё раз — /login",
                           reply_markup=ReplyKeyboardRemove())

    async def _try_delete(m: Message) -> None:
        try:
            await m.delete()
        except Exception:
            pass

    # ---- команды ----------------------------------------------------------
    @r.message(Command("floor", "compare"))
    async def _floor(m: Message, command: CommandObject):
        if not command.args:
            return await m.answer("Формат: <code>/floor Plush Pepe | Gold</code>")
        if not engine.live:
            return await m.answer("Ни один маркет не подключён — проверьте /status и /login")
        col, _, model = (x.strip() for x in command.args.partition("|"))
        wait = await m.answer("⏳ Собираю цены...")
        book = await engine.book(col, model or None)
        await wait.edit_text(fmt_compare(col, model or None, book, engine.live,
                                         cfg.sniper.undercut))

    async def arb_text(top: int = 10) -> str:
        if not engine.live:
            return "Ни один маркет не подключён — проверьте /status и /login"
        return fmt_spreads(await engine.spreads(min_profit=0.0), top)

    @r.message(Command("arb"))
    async def _arb(m: Message, command: CommandObject):
        top = int(command.args) if command.args and command.args.isdigit() else 10
        wait = await m.answer("⏳ Сравниваю флоры...")
        await wait.edit_text(await arb_text(top))

    @r.message(Command("snipe"))
    async def _snipe(m: Message, command: CommandObject):
        if command.args in ("on", "off"):
            engine.snipe_on = command.args == "on"
        await m.answer(f"Снайпер: {'🟢 вкл' if engine.snipe_on else '🔴 выкл'}")

    def autobuy_text() -> str:
        dry = " (dry-run: покупки понарошку, выключить — /set dry_run off)" if cfg.autobuy.dry_run else ""
        return f"Автобай: {'🟢 вкл' if engine.autobuy_on else '🔴 выкл'}{dry}"

    @r.message(Command("autobuy"))
    async def _autobuy(m: Message, command: CommandObject):
        if command.args in ("on", "off"):
            engine.autobuy_on = command.args == "on"
        await m.answer(autobuy_text())

    @r.message(Command("status"))
    async def _status(m: Message):
        who = await account.me()
        await m.answer((f"👤 Аккаунт: {who}\n" if who else "👤 Аккаунт не подключён — /login\n")
                       + fmt_status(engine))

    @r.message(Command("finds"))
    async def _finds(m: Message):
        await m.answer(fmt_finds(store.recent_finds(15)))

    @r.message(Command("settings"))
    async def _settings(m: Message):
        await m.answer("🔧 <b>Настройки</b> (изменить: <code>/set ключ значение</code>)\n\n"
                       + settings.describe(cfg))

    @r.message(Command("set"))
    async def _set(m: Message, command: CommandObject):
        key, _, value = (command.args or "").partition(" ")
        if not key or not value:
            return await m.answer("Формат: <code>/set min_profit 1.5</code>. Все ключи — /settings")
        try:
            shown = settings.apply(cfg, store, key, value)
        except KeyError:
            return await m.answer("Нет такой настройки. Список — /settings")
        except ValueError as e:
            return await m.answer(f"Неверное значение: {e}")
        await m.answer(f"✅ {key} = <b>{shown}</b>")
        if key.startswith("on_"):
            await engine.start_markets()

    @r.message(Command("digest"))
    async def _digest(m: Message):
        await m.answer(await post_digest())

    # ---- кнопки меню ----------------------------------------------------------
    @r.callback_query(F.data.startswith("m:"))
    async def _menu_btn(q: CallbackQuery):
        act = q.data[2:]
        await q.answer()
        if act == "arb":
            await q.message.answer(await arb_text())
        elif act == "status":
            await _status(q.message)
        elif act == "snipe":
            engine.snipe_on = not engine.snipe_on
            await q.message.edit_reply_markup(reply_markup=menu_kb(engine))
        elif act == "autobuy":
            engine.autobuy_on = not engine.autobuy_on
            await q.message.edit_reply_markup(reply_markup=menu_kb(engine))
            await q.message.answer(autobuy_text())
        elif act == "finds":
            await q.message.answer(fmt_finds(store.recent_finds(15)))
        elif act == "settings":
            await q.message.answer("🔧 <b>Настройки</b> (изменить: <code>/set ключ значение</code>)\n\n"
                                   + settings.describe(cfg))
        elif act == "digest":
            await q.message.answer(await post_digest())

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
    async def post_digest() -> str:
        if not cfg.channel_id:
            return "Канал не задан: <code>/set channel @mychannel</code> (бот должен быть админом канала)"
        if not engine.live:
            return "Ни один маркет не подключён — дайджест не из чего собрать"
        spreads = await engine.spreads(min_profit=0.0)
        try:
            await bot.send_message(cfg.channel_id, fmt_spreads(spreads, cfg.digest_top,
                                                               "Арбитраж подарков Telegram"))
        except Exception as e:
            return f"Не удалось отправить в канал: {e}"
        return "Дайджест отправлен в канал ✅"

    async def digest_loop() -> None:
        while True:
            await asyncio.sleep(max(cfg.digest_interval_min, 1) * 60)
            if cfg.channel_id and cfg.digest_interval_min > 0:
                res = await post_digest()
                if "✅" not in res:
                    log.warning("digest: %s", res)

    async def on_startup() -> None:
        try:
            await bot.set_my_commands(COMMANDS)
        except Exception as e:
            log.warning("set_my_commands: %s", e)
        for uid in admins():
            try:
                who = await account.me()
                await bot.send_message(uid, "🟢 Бот запущен" + (f", аккаунт: {who}" if who else
                                                               ". Войдите в аккаунт: /login"),
                                       reply_markup=menu_kb(engine))
            except Exception:
                pass

    dp.startup.register(on_startup)
    dp.include_router(r)
    return bot, dp, [digest_loop()]

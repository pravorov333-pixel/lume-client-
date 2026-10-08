"""CLI.

  python -m giftsniper login                 — войти в Telegram-аккаунт (один раз)
  python -m giftsniper run                   — снайпер + бот (алерты в Telegram)
  python -m giftsniper scan                  — снайпер в консоли, без бота
  python -m giftsniper arb [--top 15]        — разовое сравнение флоров по маркетам
  python -m giftsniper floor "Plush Pepe" [--model Gold]
"""
from __future__ import annotations

import argparse
import asyncio
import logging
import re
from html import unescape

from . import settings
from .config import load_config
from .engine import Engine
from .format import fmt_compare, fmt_opp, fmt_spreads
from .http import Http
from .markets import REGISTRY, Ctx
from .rates import Rates
from .storage import Store
from .tgauth import Account


def plain(html: str) -> str:
    return unescape(re.sub(r"<[^>]+>", "", html))


async def build_engine(cfg) -> tuple[Engine, Account, Http]:
    store = Store(cfg.db_path)
    settings.load_saved(cfg, store)          # настройки, изменённые через /set
    http = Http(proxy=cfg.proxy)
    rates = Rates(cfg.rates, http)
    await rates.refresh(force=True)
    ctx = Ctx(tg=None, rates=rates, proxy=cfg.proxy)
    account = Account(cfg, store, ctx)
    try:
        await account.connect()                # подхватит сохранённую сессию, если есть
    except Exception as e:
        logging.getLogger("main").warning("Telegram: %s", e)
    markets = {n: REGISTRY[n](m, ctx) for n, m in cfg.markets.items() if n in REGISTRY}
    engine = Engine(cfg, markets, store, rates)
    return engine, account, http


async def shutdown(engine: Engine, account: Account, http: Http) -> None:
    for m in engine.markets.values():
        try:
            await m.close()
        except Exception:
            pass
    await http.close()
    await account.close()


async def cmd_login(cfg) -> None:
    """Вход из консоли (альтернатива /login в боте)."""
    engine, account, http = await build_engine(cfg)
    try:
        if not account.has_api:
            raise SystemExit("Нужны api_id и api_hash (config.yaml или TG_API_ID/TG_API_HASH)")
        await account.client.start()
        account._finish()
        print(f"Готово: вошли как {await account.me()}. Сессия сохранена в {cfg.db_path}")
    finally:
        await shutdown(engine, account, http)


async def cmd_run(cfg, with_bot: bool) -> None:
    engine, account, http = await build_engine(cfg)
    try:
        if with_bot:
            if not cfg.bot_token:
                raise SystemExit("Нужен BOT_TOKEN (от @BotFather) — в config.yaml или переменной окружения")
            from .bot import build
            bot, dp, background = build(engine, account)
            await asyncio.gather(engine.run(), dp.start_polling(bot), *background)
        else:
            async def show(opp):
                print("\n" + plain(fmt_opp(opp)) + "\n")

            async def say(text):
                print(text)
            engine.on_opportunity = show
            engine.on_message = say
            await engine.run()
    finally:
        await shutdown(engine, account, http)


async def cmd_arb(cfg, top: int) -> None:
    engine, account, http = await build_engine(cfg)
    try:
        await engine.start_markets()
        print(plain(fmt_spreads(await engine.spreads(), top)))
    finally:
        await shutdown(engine, account, http)


async def cmd_floor(cfg, collection: str, model: str | None) -> None:
    engine, account, http = await build_engine(cfg)
    try:
        await engine.start_markets()
        book = await engine.book(collection, model)
        print(plain(fmt_compare(collection, model, book, engine.live, cfg.sniper.undercut)))
    finally:
        await shutdown(engine, account, http)


def main() -> None:
    p = argparse.ArgumentParser(prog="giftsniper", description="Снайпер подарков Telegram")
    p.add_argument("-c", "--config", default="config.yaml")
    p.add_argument("-v", "--verbose", action="store_true")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("login")
    sub.add_parser("run")
    sub.add_parser("scan")
    a = sub.add_parser("arb")
    a.add_argument("--top", type=int, default=15)
    f = sub.add_parser("floor")
    f.add_argument("collection")
    f.add_argument("--model")
    args = p.parse_args()

    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    logging.getLogger("telethon").setLevel(logging.WARNING)
    cfg = load_config(args.config)

    coro = {
        "login": lambda: cmd_login(cfg),
        "run": lambda: cmd_run(cfg, with_bot=True),
        "scan": lambda: cmd_run(cfg, with_bot=False),
        "arb": lambda: cmd_arb(cfg, args.top),
        "floor": lambda: cmd_floor(cfg, args.collection, args.model),
    }[args.cmd]
    try:
        asyncio.run(coro())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()

"""Telethon-сессия пользователя: initData для мини-аппов маркетов и доступ к маркету Telegram."""
from __future__ import annotations

import logging
from urllib.parse import unquote

from telethon import TelegramClient, functions, types, utils

from .config import Config

log = logging.getLogger(__name__)


def make_client(cfg: Config) -> TelegramClient:
    if not cfg.api_id or not cfg.api_hash:
        raise SystemExit("Нужны api_id и api_hash (https://my.telegram.org) — см. config.example.yaml")
    return TelegramClient(cfg.session, cfg.api_id, cfg.api_hash)


async def get_init_data(client: TelegramClient, bot_username: str, short_name: str,
                        platform: str = "android") -> str:
    """Открывает мини-апп бота от имени аккаунта и возвращает tgWebAppData (initData)."""
    bot = await client.get_input_entity(bot_username)
    res = await client(functions.messages.RequestAppWebViewRequest(
        peer=bot,
        app=types.InputBotAppShortName(bot_id=utils.get_input_user(bot), short_name=short_name),
        platform=platform,
        write_allowed=True,
    ))
    url = res.url
    if "tgWebAppData=" not in url:
        raise RuntimeError(f"В ответе {bot_username} нет tgWebAppData: {url[:120]}")
    return unquote(url.split("tgWebAppData=", 1)[1].split("&tgWebAppVersion", 1)[0])


def _json_tl(v):
    """help.AppConfig приходит в TL-JSON — переводим в обычный dict."""
    if isinstance(v, types.JsonObject):
        return {kv.key: _json_tl(kv.value) for kv in v.value}
    if isinstance(v, types.JsonArray):
        return [_json_tl(x) for x in v.value]
    if isinstance(v, (types.JsonNumber, types.JsonString, types.JsonBool)):
        return v.value
    return None


async def get_app_config(client: TelegramClient) -> dict:
    res = await client(functions.help.GetAppConfigRequest(hash=0))
    cfg = getattr(res, "config", None)
    return _json_tl(cfg) if cfg is not None else {}

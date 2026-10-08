"""Telethon-сессия пользователя: initData для мини-аппов маркетов и доступ к маркету Telegram."""
from __future__ import annotations

import logging
import os
from urllib.parse import unquote

from telethon import TelegramClient, functions, types, utils

from .config import Config

log = logging.getLogger(__name__)


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


class Account:
    """Telegram-аккаунт пользователя: вход по шагам прямо из бота, сессия хранится в базе.

    api_id/api_hash берутся из конфига/окружения или задаются командой /api в боте.
    """

    def __init__(self, cfg: Config, store, ctx):
        self.cfg = cfg
        self.store = store
        self.ctx = ctx
        self.client: TelegramClient | None = None
        self._phone: str | None = None
        self._hash: str | None = None

    @property
    def api(self) -> tuple[int, str]:
        api_id = self.cfg.api_id or int(self.store.get("api_id") or 0)
        return api_id, self.cfg.api_hash or (self.store.get("api_hash") or "")

    @property
    def has_api(self) -> bool:
        return all(self.api)

    def _new_client(self) -> TelegramClient:
        from telethon.sessions import StringSession
        api_id, api_hash = self.api
        saved = os.environ.get("TG_SESSION") or self.store.get("tg_session") or ""
        return TelegramClient(StringSession(saved), api_id, api_hash)

    async def connect(self) -> bool:
        """Подключиться с сохранённой сессией. True — аккаунт уже авторизован."""
        if not self.has_api:
            return False
        if self.client is None:
            self.client = self._new_client()
        if not self.client.is_connected():
            await self.client.connect()
        if await self.client.is_user_authorized():
            self.ctx.tg = self.client
            return True
        return False

    async def send_code(self, phone: str) -> None:
        if not await self.connect() and self.client is None:
            raise RuntimeError("сначала задайте api_id и api_hash: /api")
        phone = "+" + "".join(ch for ch in phone if ch.isdigit())
        sent = await self.client.send_code_request(phone)
        self._phone, self._hash = phone, sent.phone_code_hash

    async def sign_in_code(self, code: str) -> bool:
        """True — вошли; False — нужен пароль двухэтапной аутентификации."""
        from telethon.errors import SessionPasswordNeededError
        code = "".join(ch for ch in code if ch.isdigit())
        try:
            await self.client.sign_in(phone=self._phone, code=code, phone_code_hash=self._hash)
        except SessionPasswordNeededError:
            return False
        self._finish()
        return True

    async def sign_in_password(self, password: str) -> None:
        await self.client.sign_in(password=password)
        self._finish()

    def _finish(self) -> None:
        self.store.set("tg_session", self.client.session.save())
        self.ctx.tg = self.client

    async def me(self) -> str | None:
        if self.ctx.tg is None:
            return None
        u = await self.client.get_me()
        return f"{u.first_name or ''} (@{u.username})" if u.username else (u.first_name or str(u.id))

    async def logout(self) -> None:
        if self.client:
            try:
                await self.client.log_out()
            except Exception:
                pass
        self.store.set("tg_session", None)
        self.ctx.tg = None
        self.client = None

    async def close(self) -> None:
        if self.client:
            await self.client.disconnect()

"""Базовый класс маркета."""
from __future__ import annotations

import logging
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any, Awaitable, Callable, TypeVar

from telethon import TelegramClient

from ..config import MarketCfg
from ..http import HttpError
from ..models import Floor, Listing
from ..rates import Rates
from ..tgauth import get_init_data

T = TypeVar("T")


@dataclass
class Ctx:
    tg: TelegramClient | None
    rates: Rates
    proxy: str | None = None


class Market(ABC):
    name: str = ""

    def __init__(self, cfg: MarketCfg, ctx: Ctx):
        self.cfg = cfg
        self.ctx = ctx
        self.log = logging.getLogger(f"market.{self.name}")
        self.ready = False
        self.last_error: str | None = None

    # ---- жизненный цикл -------------------------------------------------
    async def start(self) -> None:
        await self.authorize()
        self.ready = True

    async def close(self) -> None:
        pass

    async def authorize(self) -> None:
        """По умолчанию — достаём initData мини-аппа (или берём из конфига)."""

    async def fetch_init_data(self) -> str:
        if self.cfg.init_data:
            return self.cfg.init_data
        if not self.ctx.tg:
            raise RuntimeError(f"{self.name}: нет Telegram-сессии и init_data в конфиге")
        return await get_init_data(self.ctx.tg, self.cfg.webapp_bot, self.cfg.webapp_short_name)

    async def with_reauth(self, fn: Callable[[], Awaitable[T]]) -> T:
        """Выполнить запрос; при 401/403 обновить авторизацию и повторить один раз."""
        try:
            return await fn()
        except HttpError as e:
            if e.status not in (401, 403):
                raise
            self.log.info("Авторизация протухла (%s), обновляю", e.status)
            await self.authorize()
            return await fn()

    # ---- данные ---------------------------------------------------------
    @abstractmethod
    async def latest(self) -> list[Listing]:
        """Свежевыставленные лоты (новые сверху)."""

    @abstractmethod
    async def floors(self) -> dict[str, Floor]:
        """Флоры по коллекциям: ключ — norm(collection)."""

    @abstractmethod
    async def cheapest(self, collection: str, model: str | None = None,
                       limit: int = 5) -> list[Listing]:
        """Самые дешёвые лоты коллекции (опционально — модели), по возрастанию цены."""

    async def buy(self, listing: Listing) -> dict[str, Any]:
        raise NotImplementedError(f"{self.name}: автопокупка не реализована")

    # ---- экономика ------------------------------------------------------
    def net_share(self, currency: str = "TON") -> float:
        """Доля от цены, которую видит покупатель, что дойдёт до продавца."""
        return (1.0 - self.cfg.sell_fee) / (1.0 + self.cfg.price_markup)

    @property
    def deposit_cost(self) -> float:
        return self.cfg.deposit_cost_ton

    @property
    def withdraw_cost(self) -> float:
        return self.cfg.withdraw_cost_ton

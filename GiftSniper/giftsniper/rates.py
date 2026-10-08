"""Курсы: TON/USD и пересчёт Telegram Stars в TON."""
from __future__ import annotations

import logging
import time

from .config import RatesCfg
from .http import Http

log = logging.getLogger(__name__)


class Rates:
    def __init__(self, cfg: RatesCfg, http: Http | None = None):
        self.cfg = cfg
        self.http = http
        self.ton_usd = cfg.ton_usd
        self._updated = 0.0

    async def refresh(self, force: bool = False) -> None:
        if not self.http or (not force and time.time() - self._updated < self.cfg.refresh_sec):
            return
        try:
            data = await self.http.get("https://tonapi.io/v2/rates",
                                       params={"tokens": "ton", "currencies": "usd"})
            self.ton_usd = float(data["rates"]["TON"]["prices"]["USD"])
        except Exception as e:
            try:
                data = await self.http.get("https://api.coingecko.com/api/v3/simple/price",
                                           params={"ids": "the-open-network", "vs_currencies": "usd"})
                self.ton_usd = float(data["the-open-network"]["usd"])
            except Exception:
                log.warning("Не удалось обновить курс TON (%s), использую %.3f", e, self.ton_usd)
        self._updated = time.time()

    def stars_cost_ton(self, stars: float) -> float:
        """Сколько TON стоит купить столько Stars (для покупки на маркете Telegram)."""
        return stars * self.cfg.star_buy_usd / self.ton_usd

    def stars_value_ton(self, stars: float) -> float:
        """Сколько TON реально получим, выведя столько Stars (для продажи за Stars)."""
        return stars * self.cfg.star_sell_usd / self.ton_usd

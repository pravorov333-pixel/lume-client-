"""Загрузка config.yaml. Секреты можно передать через переменные окружения."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml


@dataclass
class MarketCfg:
    name: str
    enabled: bool = True
    price_markup: float = 0.0       # наценка покупателя поверх цены продавца (Tonnel: +10%)
    sell_fee: float = 0.05          # доля, которую маркет забирает у продавца
    deposit_cost_ton: float = 0.0   # сколько стоит завести подарок на маркет (перевод из TG и т.п.)
    withdraw_cost_ton: float = 0.0  # сколько стоит вывести подарок с маркета в Telegram
    autobuy: bool = False           # разрешить автопокупку на этом маркете
    poll_limit: int = 30            # сколько свежих лотов брать за один опрос
    impersonate: str = "chrome"     # TLS-отпечаток curl_cffi
    init_data: str = ""             # можно вставить initData вручную вместо автологина
    webapp_bot: str = ""            # бот мини-аппа, из которого берём initData
    webapp_short_name: str = ""
    extra: dict[str, Any] = field(default_factory=dict)


@dataclass
class SniperCfg:
    poll_interval: float = 4.0
    min_profit_ton: float = 0.5
    min_roi: float = 0.08
    max_price_ton: float = 50.0
    undercut: float = 0.01          # продаём на 1% дешевле флора, чтобы ушло быстро
    match_level: str = "model"      # model | collection
    floor_ttl: float = 60.0         # сек, кэш флоров
    collections_whitelist: list[str] = field(default_factory=list)
    collections_blacklist: list[str] = field(default_factory=list)
    min_floor_depth: int = 2        # минимум лотов на маркете продажи, чтобы доверять флору


@dataclass
class AutobuyCfg:
    enabled: bool = False
    dry_run: bool = True
    max_price_ton: float = 5.0
    daily_budget_ton: float = 20.0
    max_buys_per_hour: int = 3
    min_profit_ton: float = 1.0
    min_roi: float = 0.15


@dataclass
class RatesCfg:
    ton_usd: float = 3.0            # запасной курс, если API недоступно
    star_buy_usd: float = 0.015     # почём покупаем Stars
    star_sell_usd: float = 0.013    # почём выводим Stars через Fragment
    refresh_sec: float = 300.0


@dataclass
class Config:
    api_id: int = 0
    api_hash: str = ""
    session: str = "giftsniper"
    bot_token: str = ""
    admin_ids: list[int] = field(default_factory=list)
    alert_chat_ids: list[int] = field(default_factory=list)
    channel_id: str | int | None = None
    digest_interval_min: int = 0
    digest_top: int = 10
    proxy: str | None = None
    db_path: str = "giftsniper.db"
    markets: dict[str, MarketCfg] = field(default_factory=dict)
    sniper: SniperCfg = field(default_factory=SniperCfg)
    autobuy: AutobuyCfg = field(default_factory=AutobuyCfg)
    rates: RatesCfg = field(default_factory=RatesCfg)


MARKET_DEFAULTS: dict[str, dict[str, Any]] = {
    # Комиссии и стоимость переводов — ориентировочные. ПРОВЕРЬТЕ на маркетах и поправьте в config.yaml.
    "portals": {"sell_fee": 0.05, "deposit_cost_ton": 0.1, "withdraw_cost_ton": 0.1,
                "webapp_bot": "portals", "webapp_short_name": "market"},
    "mrkt": {"sell_fee": 0.0, "deposit_cost_ton": 0.1, "withdraw_cost_ton": 0.1,
             "webapp_bot": "mrkt", "webapp_short_name": "app"},
    "tonnel": {"sell_fee": 0.0, "price_markup": 0.10, "impersonate": "firefox133",
               "deposit_cost_ton": 0.1, "withdraw_cost_ton": 0.1,
               "webapp_bot": "Tonnel_Network_bot", "webapp_short_name": "gifts"},
    # Для Telegram доля продавца подтягивается из app config, если он её отдаёт.
    "telegram": {"sell_fee": 0.05, "poll_limit": 20},
}


def _build(cls, data: dict[str, Any] | None):
    data = dict(data or {})
    known = {f for f in cls.__dataclass_fields__}
    return cls(**{k: v for k, v in data.items() if k in known})


def load_config(path: str | Path) -> Config:
    raw: dict[str, Any] = {}
    p = Path(path)
    if p.exists():
        raw = yaml.safe_load(p.read_text(encoding="utf-8")) or {}

    cfg = _build(Config, {k: v for k, v in raw.items()
                          if k not in ("markets", "sniper", "autobuy", "rates")})
    cfg.sniper = _build(SniperCfg, raw.get("sniper"))
    cfg.autobuy = _build(AutobuyCfg, raw.get("autobuy"))
    cfg.rates = _build(RatesCfg, raw.get("rates"))

    markets_raw = raw.get("markets") or {}
    for name, defaults in MARKET_DEFAULTS.items():
        merged = {**defaults, **(markets_raw.get(name) or {}), "name": name}
        known = MarketCfg.__dataclass_fields__
        extra = {k: v for k, v in merged.items() if k not in known}
        m = MarketCfg(**{k: v for k, v in merged.items() if k in known})
        m.extra.update(extra)
        cfg.markets[name] = m

    # Секреты из окружения имеют приоритет
    env = os.environ
    cfg.api_id = int(env.get("TG_API_ID", cfg.api_id) or 0)
    cfg.api_hash = env.get("TG_API_HASH", cfg.api_hash)
    cfg.bot_token = env.get("BOT_TOKEN", cfg.bot_token)
    cfg.proxy = env.get("GIFTSNIPER_PROXY", cfg.proxy) or None
    for name, m in cfg.markets.items():
        v = env.get(f"{name.upper()}_INIT_DATA")
        if v:
            m.init_data = v
    return cfg

"""Настройки, которые меняются прямо из бота (/set ключ значение) и хранятся в базе."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable

from .config import Config
from .storage import Store

PREFIX = "set:"


def _bool(v: str) -> bool:
    v = v.strip().lower()
    if v in ("on", "1", "true", "yes", "да", "вкл"):
        return True
    if v in ("off", "0", "false", "no", "нет", "выкл"):
        return False
    raise ValueError("нужно on или off")


def _pct(v: str) -> float:
    return float(v.replace("%", "").replace(",", ".")) / 100


def _num(v: str) -> float:
    return float(v.replace(",", "."))


def _level(v: str) -> str:
    v = v.strip().lower()
    if v not in ("model", "collection"):
        raise ValueError("model или collection")
    return v


def _chat(v: str) -> str | int | None:
    v = v.strip()
    if v.lower() in ("off", "none", "-"):
        return None
    return int(v) if v.lstrip("-").isdigit() else v


@dataclass
class Setting:
    key: str
    help: str
    parse: Callable[[str], Any]
    get: Callable[[Config], Any]
    put: Callable[[Config, Any], None]
    show: Callable[[Any], str] = str


def _attr(path: str):
    obj, _, name = path.rpartition(".")

    def target(cfg):
        return getattr(cfg, obj) if obj else cfg
    return (lambda cfg: getattr(target(cfg), name),
            lambda cfg, v: setattr(target(cfg), name, v))


def _s(key, help, parse, path, show=str) -> Setting:
    g, p = _attr(path)
    return Setting(key, help, parse, g, p, show)


def _pshow(v) -> str:
    return f"{v * 100:g}%"


def _onoff(v) -> str:
    return "on" if v else "off"


SETTINGS: list[Setting] = [
    _s("min_profit", "мин. профит для алерта, TON", _num, "sniper.min_profit_ton"),
    _s("min_roi", "мин. доходность для алерта, %", _pct, "sniper.min_roi", _pshow),
    _s("max_price", "не смотреть лоты дороже, TON", _num, "sniper.max_price_ton"),
    _s("interval", "пауза между опросами, сек", _num, "sniper.poll_interval"),
    _s("undercut", "насколько ниже флора выставлять, %", _pct, "sniper.undercut", _pshow),
    _s("level", "сравнивать с флором: model / collection", _level, "sniper.match_level"),
    _s("dry_run", "on = покупки понарошку", _bool, "autobuy.dry_run", _onoff),
    _s("ab_max", "автобай: макс. цена лота, TON", _num, "autobuy.max_price_ton"),
    _s("ab_budget", "автобай: бюджет в сутки, TON", _num, "autobuy.daily_budget_ton"),
    _s("ab_profit", "автобай: мин. профит, TON", _num, "autobuy.min_profit_ton"),
    _s("ab_roi", "автобай: мин. доходность, %", _pct, "autobuy.min_roi", _pshow),
    _s("ab_per_hour", "автобай: покупок в час", lambda v: int(v), "autobuy.max_buys_per_hour"),
    _s("channel", "канал для дайджеста (@name / id / off)", _chat, "channel_id"),
    _s("digest", "дайджест в канал каждые N минут (0 = выкл)", lambda v: int(v),
       "digest_interval_min"),
    _s("star_buy", "почём покупаете Stars, $", _num, "rates.star_buy_usd"),
    _s("star_sell", "почём выводите Stars, $", _num, "rates.star_sell_usd"),
]

# Настройки отдельных маркетов: fee_portals 5, ab_tonnel on, on_mrkt off
_MARKET_KEYS = {
    "fee": ("комиссия продавца, %", _pct, "sell_fee", _pshow),
    "ab": ("разрешить автобай на маркете", _bool, "autobuy", _onoff),
    "on": ("маркет включён", _bool, "enabled", _onoff),
}


def find(cfg: Config, key: str) -> Setting | None:
    key = key.strip().lower()
    for s in SETTINGS:
        if s.key == key:
            return s
    kind, _, market = key.partition("_")
    if kind in _MARKET_KEYS and market in cfg.markets:
        help, parse, field, show = _MARKET_KEYS[kind]
        return Setting(key, f"{market}: {help}", parse,
                       lambda c: getattr(c.markets[market], field),
                       lambda c, v: setattr(c.markets[market], field, v), show)
    return None


def apply(cfg: Config, store: Store, key: str, raw: str) -> str:
    """Меняет настройку и сохраняет её. Возвращает новое значение для показа."""
    s = find(cfg, key)
    if not s:
        raise KeyError(key)
    v = s.parse(raw)
    s.put(cfg, v)
    store.set(PREFIX + s.key, raw)
    return s.show(v)


def load_saved(cfg: Config, store: Store) -> None:
    for key, raw in store.items(PREFIX).items():
        s = find(cfg, key)
        if s:
            try:
                s.put(cfg, s.parse(raw))
            except Exception:
                pass


def describe(cfg: Config) -> str:
    lines = [f"<code>{s.key}</code> = <b>{s.show(s.get(cfg))}</b> — {s.help}" for s in SETTINGS]
    for name, m in cfg.markets.items():
        lines.append(f"<code>fee_{name}</code> = <b>{_pshow(m.sell_fee)}</b> · "
                     f"<code>ab_{name}</code> = <b>{_onoff(m.autobuy)}</b> · "
                     f"<code>on_{name}</code> = <b>{_onoff(m.enabled)}</b>")
    return "\n".join(lines)

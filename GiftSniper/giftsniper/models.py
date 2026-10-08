"""Общие модели данных: лот, флор, найденная возможность."""
from __future__ import annotations

import re
import time
from dataclasses import dataclass, field
from typing import Any

_RARITY_SUFFIX = re.compile(r"\s*\([\d.,]+\s*%\)\s*$")


def norm(name: str | None) -> str:
    """Ключ для сравнения названий между маркетами.

    "Plush Pepe", "plushpepe", "Plush-Pepe" -> "plushpepe".
    Tonnel хранит модели как "Gold (1.5%)" — процент редкости отрезаем.
    """
    if not name:
        return ""
    name = _RARITY_SUFFIX.sub("", name)
    return re.sub(r"[^0-9a-zа-яё]", "", name.lower())


def strip_rarity(name: str | None) -> str | None:
    return _RARITY_SUFFIX.sub("", name).strip() if name else name


def nft_link(collection: str, num: int | None) -> str | None:
    """Универсальная ссылка на подарок: https://t.me/nft/PlushPepe-123."""
    if not collection or not num:
        return None
    slug = re.sub(r"[^0-9A-Za-z]", "", collection)
    return f"https://t.me/nft/{slug}-{num}"


@dataclass
class Listing:
    market: str                 # portals | mrkt | tonnel | telegram
    listing_id: str             # id лота на маркете (для покупки)
    collection: str             # "Plush Pepe"
    price_ton: float            # цена, которую платит покупатель, в TON-эквиваленте
    num: int | None = None
    model: str | None = None
    backdrop: str | None = None
    symbol: str | None = None
    currency: str = "TON"       # TON | STARS
    price_native: float | None = None  # цена в исходной валюте (TON или Stars)
    url: str | None = None
    seen_at: float = field(default_factory=time.time)
    raw: dict[str, Any] = field(default_factory=dict, repr=False)

    @property
    def key(self) -> tuple[str, str]:
        return (self.market, self.listing_id)

    @property
    def title(self) -> str:
        s = self.collection + (f" #{self.num}" if self.num else "")
        if self.model:
            s += f" · {self.model}"
        return s

    @property
    def link(self) -> str | None:
        return self.url or nft_link(self.collection, self.num)


@dataclass
class Floor:
    market: str
    collection: str
    price_ton: float
    model: str | None = None
    listing_id: str | None = None   # самый дешёвый лот, если известен
    count: int | None = None


@dataclass
class Exit:
    """Вариант продажи: где и за сколько можно продать и сколько останется на руках."""
    market: str
    floor_ton: float        # текущий флор на маркете продажи (без учёта нашего лота)
    sell_price_ton: float   # цена, по которой выставим (флор минус undercut)
    net_ton: float          # получим на руки после комиссии продавца
    profit_ton: float       # чистая прибыль с учётом цены покупки и переводов
    roi: float              # profit / затраты


@dataclass
class Opportunity:
    listing: Listing
    cost_ton: float                 # полная стоимость покупки (цена + комиссия покупателя)
    best_exit: Exit
    exits: list[Exit]
    level: str                      # model | collection — по какому флору считали

    @property
    def profit_ton(self) -> float:
        return self.best_exit.profit_ton

    @property
    def roi(self) -> float:
        return self.best_exit.roi

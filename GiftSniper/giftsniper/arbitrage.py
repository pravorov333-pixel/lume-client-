"""Чистая математика: сколько заработаем, купив лот здесь и продав там.

Все цены — в TON-эквиваленте «цены для покупателя» на соответствующем маркете.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping, Protocol, Sequence

from .models import Exit, Floor, Listing, Opportunity


class Econ(Protocol):
    """То, что нужно знать о маркете для расчёта (реализуется Market)."""
    name: str

    def net_share(self, currency: str | None = None) -> float: ...

    @property
    def deposit_cost(self) -> float: ...

    @property
    def withdraw_cost(self) -> float: ...


def transfer_cost(buy: Econ, sell: Econ) -> float:
    if buy.name == sell.name:
        return 0.0
    return buy.withdraw_cost + sell.deposit_cost


def make_exit(cost: float, buy: Econ, sell: Econ, floor_ton: float, undercut: float) -> Exit:
    sell_price = floor_ton * (1 - undercut)
    net = sell_price * sell.net_share()
    spent = cost + transfer_cost(buy, sell)
    profit = net - spent
    return Exit(sell.name, floor_ton, sell_price, net, profit, profit / spent if spent else 0.0)


def evaluate_listing(listing: Listing, econ: Mapping[str, Econ],
                     book: Mapping[str, Sequence[Listing]], *, undercut: float,
                     min_depth: int = 1, level: str = "model") -> Opportunity | None:
    """book: market -> самые дешёвые лоты того же подарка (по возрастанию цены).

    Сам оцениваемый лот из стакана исключается — флором считается следующий за ним.
    """
    buy = econ[listing.market]
    cost = listing.price_ton
    exits: list[Exit] = []
    for mname, items in book.items():
        if mname not in econ:
            continue
        others = [x for x in items if x.key != listing.key]
        if len(others) < min_depth:
            continue
        exits.append(make_exit(cost, buy, econ[mname], others[0].price_ton, undercut))
    if not exits:
        return None
    exits.sort(key=lambda e: e.profit_ton, reverse=True)
    return Opportunity(listing, cost, exits[0], exits, level)


@dataclass
class Spread:
    collection: str
    buy: Floor
    sell: Floor
    exit: Exit

    @property
    def profit_ton(self) -> float:
        return self.exit.profit_ton


def floor_spreads(floors: Mapping[str, Mapping[str, Floor]], econ: Mapping[str, Econ], *,
                  undercut: float, min_profit: float = 0.0) -> list[Spread]:
    """floors: norm(collection) -> {market: Floor}. Ищем: купить по флору A, продать по флору B."""
    out: list[Spread] = []
    for per_market in floors.values():
        avail = {m: f for m, f in per_market.items() if m in econ and f.price_ton > 0}
        if len(avail) < 2:
            continue
        # Portals отдаёт «короткие» имена (plushpepe) — для показа берём вариант с пробелами
        name = max((f.collection for f in avail.values()), key=lambda s: s.count(" "))
        best: Spread | None = None
        for bm, bf in avail.items():
            for sm, sf in avail.items():
                if bm == sm:
                    continue
                ex = make_exit(bf.price_ton, econ[bm], econ[sm], sf.price_ton, undercut)
                if best is None or ex.profit_ton > best.profit_ton:
                    best = Spread(name, bf, sf, ex)
        if best and best.profit_ton >= min_profit:
            out.append(best)
    out.sort(key=lambda s: s.profit_ton, reverse=True)
    return out

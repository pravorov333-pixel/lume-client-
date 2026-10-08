"""Движок: опрос маркетов, оценка новых лотов, алерты и (опционально) автопокупка."""
from __future__ import annotations

import asyncio
import logging
import time
from typing import Awaitable, Callable

from .arbitrage import Spread, evaluate_listing, floor_spreads
from .config import Config
from .markets import Market
from .models import Floor, Listing, Opportunity, norm
from .rates import Rates
from .storage import Store

log = logging.getLogger("engine")


async def _noop(*_a) -> None:
    pass

class Engine:
    def __init__(self, cfg: Config, markets: dict[str, Market], store: Store, rates: Rates):
        self.cfg = cfg
        self.markets = markets
        self.store = store
        self.rates = rates
        # Колбэки уведомлений — их подставляет бот (или CLI)
        self.on_opportunity: Callable[[Opportunity], Awaitable[None]] = _noop
        self.on_message: Callable[[str], Awaitable[None]] = _noop
        self.snipe_on = True
        self.autobuy_on = cfg.autobuy.enabled
        self.opps: dict[str, Opportunity] = {}     # для кнопок «Купить» в алертах
        self.stats: dict[str, dict] = {m: {"polls": 0, "errors": 0, "new": 0, "last_error": None}
                                       for m in markets}
        self._books: dict[tuple, tuple[float, dict[str, list[Listing]]]] = {}
        self._floors: tuple[float, dict[str, dict[str, Floor]]] = (0.0, {})
        self._sem = asyncio.Semaphore(4)
        self._buy_lock = asyncio.Lock()
        self._warm: set[str] = set()
        self._bought: set[tuple[str, str]] = set()
        self._tasks: set[asyncio.Task] = set()
        self._white = {norm(x) for x in cfg.sniper.collections_whitelist}
        self._black = {norm(x) for x in cfg.sniper.collections_blacklist}

    @property
    def live(self) -> dict[str, Market]:
        return {n: m for n, m in self.markets.items() if m.ready and m.cfg.enabled}

    # ---- стаканы и флоры ------------------------------------------------
    async def book(self, collection: str, model: str | None) -> dict[str, list[Listing]]:
        key = (norm(collection), norm(model))
        hit = self._books.get(key)
        if hit and time.time() - hit[0] < self.cfg.sniper.floor_ttl:
            return hit[1]
        names = list(self.live)
        res = await asyncio.gather(*(self.live[n].cheapest(collection, model, limit=5) for n in names),
                                   return_exceptions=True)
        book: dict[str, list[Listing]] = {}
        for n, r in zip(names, res):
            if isinstance(r, Exception):
                log.debug("%s cheapest(%s/%s): %s", n, collection, model, r)
            elif r:
                book[n] = sorted(r, key=lambda x: x.price_ton)
        self._books[key] = (time.time(), book)
        return book

    async def all_floors(self, force: bool = False) -> dict[str, dict[str, Floor]]:
        ts, cached = self._floors
        if cached and not force and time.time() - ts < self.cfg.sniper.floor_ttl:
            return cached
        names = list(self.live)
        res = await asyncio.gather(*(self.live[n].floors() for n in names), return_exceptions=True)
        out: dict[str, dict[str, Floor]] = {}
        for n, r in zip(names, res):
            if isinstance(r, Exception):
                log.warning("%s floors: %s", n, r)
                continue
            for k, f in r.items():
                out.setdefault(k, {})[n] = f
        self._floors = (time.time(), out)
        return out

    async def spreads(self, min_profit: float = 0.0) -> list[Spread]:
        return floor_spreads(await self.all_floors(), self.live,
                             undercut=self.cfg.sniper.undercut, min_profit=min_profit)

    # ---- оценка лота -----------------------------------------------------
    def allowed(self, l: Listing) -> bool:
        k = norm(l.collection)
        if self._white and k not in self._white:
            return False
        if k in self._black:
            return False
        return 0 < l.price_ton <= self.cfg.sniper.max_price_ton

    async def evaluate(self, l: Listing) -> Opportunity | None:
        s = self.cfg.sniper
        opp = None
        if s.match_level == "model" and l.model:
            opp = evaluate_listing(l, self.live, await self.book(l.collection, l.model),
                                   undercut=s.undercut, min_depth=s.min_floor_depth, level="model")
        if opp is None:
            opp = evaluate_listing(l, self.live, await self.book(l.collection, None),
                                   undercut=s.undercut, min_depth=s.min_floor_depth,
                                   level="collection")
        return opp

    async def handle(self, l: Listing) -> None:
        async with self._sem:
            try:
                opp = await self.evaluate(l)
            except Exception as e:
                log.warning("Оценка %s %s: %s", l.market, l.title, e)
                return
        s = self.cfg.sniper
        if not opp or opp.profit_ton < s.min_profit_ton or opp.roi < s.min_roi:
            return
        self.store.log_find(l.market, l.listing_id, l.title, l.price_ton,
                            opp.best_exit.market, opp.profit_ton, opp.roi)
        oid = f"{l.market}:{l.listing_id}"[:60]
        self.opps[oid] = opp
        if len(self.opps) > 500:
            self.opps.pop(next(iter(self.opps)))
        log.info("НАХОДКА %s %s %.2f TON -> %s +%.2f", l.market, l.title, l.price_ton,
                 opp.best_exit.market, opp.profit_ton)
        await self.on_opportunity(opp)
        if self.autobuy_on:
            await self.autobuy(opp)

    # ---- покупка ----------------------------------------------------------
    def autobuy_block_reason(self, opp: Opportunity) -> str | None:
        a = self.cfg.autobuy
        m = self.markets[opp.listing.market]
        if not m.cfg.autobuy:
            return f"автобай выключен для {m.name}"
        if opp.cost_ton > a.max_price_ton:
            return f"цена {opp.cost_ton:.2f} > лимита {a.max_price_ton}"
        if opp.profit_ton < a.min_profit_ton or opp.roi < a.min_roi:
            return "профит ниже порога автобая"
        spent, _ = self.store.spent_since(86400)
        if spent + opp.cost_ton > a.daily_budget_ton:
            return f"дневной бюджет: потрачено {spent:.2f} из {a.daily_budget_ton}"
        _, n = self.store.spent_since(3600)
        if n >= a.max_buys_per_hour:
            return "лимит покупок в час"
        return None

    async def autobuy(self, opp: Opportunity) -> None:
        await self.buy(opp, manual=False)

    async def buy(self, opp: Opportunity, manual: bool = True) -> str | None:
        """Покупка лота. Для автобая лимиты проверяются под замком, чтобы параллельные находки
        не превысили бюджет. dry_run действует и на ручные покупки по кнопке."""
        l = opp.listing
        async with self._buy_lock:
            if l.key in self._bought:
                return None
            if not manual:
                reason = self.autobuy_block_reason(opp)
                if reason:
                    log.info("Автобай пропущен (%s): %s", l.title, reason)
                    return None
            self._bought.add(l.key)
            if self.cfg.autobuy.dry_run:
                self.store.log_purchase(l.market, l.listing_id, l.title, opp.cost_ton, "dry")
                msg = f"🧪 DRY-RUN: купил бы {l.title} на {l.market} за {opp.cost_ton:.2f} TON"
            else:
                try:
                    info = await self.markets[l.market].buy(l)
                    self.store.log_purchase(l.market, l.listing_id, l.title, opp.cost_ton, "ok", str(info))
                    msg = f"✅ Куплено: {l.title} на {l.market} за {opp.cost_ton:.2f} TON\n{info}"
                except Exception as e:
                    self._bought.discard(l.key)
                    self.store.log_purchase(l.market, l.listing_id, l.title, opp.cost_ton, "fail", str(e))
                    msg = f"❌ Не удалось купить {l.title} на {l.market}: {e}"
        log.info(msg)
        await self.on_message(msg)
        return msg

    # ---- циклы -----------------------------------------------------------
    async def poll(self, m: Market) -> None:
        st = self.stats[m.name]
        fails = 0
        while True:
            if self.snipe_on and m.ready and m.cfg.enabled:
                try:
                    listings = await m.latest()
                    st["polls"] += 1
                    fresh = [l for l in listings
                             if self.store.is_new(l.market, l.listing_id, l.price_ton)]
                    if m.name not in self._warm:
                        # первый проход — просто запоминаем витрину, не спамим
                        self._warm.add(m.name)
                    else:
                        st["new"] += len(fresh)
                        for l in fresh:
                            if self.allowed(l):
                                t = asyncio.create_task(self.handle(l))
                                self._tasks.add(t)
                                t.add_done_callback(self._tasks.discard)
                    fails = 0
                except Exception as e:
                    fails += 1
                    st["errors"] += 1
                    st["last_error"] = f"{type(e).__name__}: {e}"[:200]
                    log.warning("%s poll: %s", m.name, st["last_error"])
                    await asyncio.sleep(min(60, 5 * fails))
            await asyncio.sleep(self.cfg.sniper.poll_interval)

    async def start_markets(self) -> None:
        async def _start(m: Market):
            try:
                await m.start()
                m.last_error = None
                log.info("Маркет %s: готов", m.name)
            except Exception as e:
                m.last_error = f"{type(e).__name__}: {e}"
                log.error("Маркет %s не запустился: %s", m.name, m.last_error)
        await asyncio.gather(*(_start(m) for m in self.markets.values()
                               if m.cfg.enabled and not m.ready))

    async def run(self) -> None:
        await self.start_markets()
        tasks = [asyncio.create_task(self.poll(m)) for m in self.markets.values()]
        tasks.append(asyncio.create_task(self._housekeeping()))
        await asyncio.gather(*tasks)

    async def _housekeeping(self) -> None:
        last_reauth = last_retry = time.time()
        while True:
            await self.rates.refresh()
            await asyncio.sleep(60)
            # маркеты, которые не стартовали (нет входа в аккаунт, сбой сети), пробуем снова
            if time.time() - last_retry > 300:
                last_retry = time.time()
                await self.start_markets()
            if time.time() - last_reauth < 3600:
                continue
            last_reauth = time.time()
            self.store.prune()
            # переавторизация раз в час: initData живёт ограниченное время
            for m in self.live.values():
                try:
                    await m.authorize()
                except Exception as e:
                    log.warning("%s reauth: %s", m.name, e)

"""Официальный маркет подарков Telegram (через MTProto/Telethon от имени вашего аккаунта).

- каталог и флоры: payments.getStarGifts (поле resell_min_stars)
- лоты: payments.getResaleStarGifts
- покупка: payments.getPaymentForm(InputInvoiceStarGiftResale) + payments.sendStarsForm
Цены бывают в Stars и в TON. Stars пересчитываем в TON по курсу из config.rates.
"""
from __future__ import annotations

import time
from typing import Any

from telethon import functions, types
from telethon.errors import FloodWaitError

from ..models import Floor, Listing, norm
from ..tgauth import get_app_config
from .base import Market

NANO = 1_000_000_000


def _attrs(g: Any) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for a in getattr(g, "attributes", None) or []:
        if isinstance(a, types.StarGiftAttributeModel):
            out["model"] = a.name
            out["model_doc"] = a.document.id
        elif isinstance(a, types.StarGiftAttributeBackdrop):
            out["backdrop"] = a.name
        elif isinstance(a, types.StarGiftAttributePattern):
            out["symbol"] = a.name
    return out


def _share(value: Any, default: float) -> float:
    """В app config может лежать как доля продавца (800 = 80%), так и комиссия (50 = 5%)."""
    try:
        v = float(value)
    except (TypeError, ValueError):
        return default
    return v / 1000 if v > 500 else 1 - v / 1000


class TelegramMarket(Market):
    name = "telegram"

    def __init__(self, cfg, ctx):
        super().__init__(cfg, ctx)
        self.catalog: dict[str, Any] = {}          # norm(title) -> StarGift
        self.models: dict[int, dict[str, int]] = {}  # gift_id -> {norm(model): document_id}
        self._rr = 0
        self._catalog_at = 0.0
        self.stars_receive = float(cfg.extra.get("stars_receive_share", 0.80))
        self.ton_receive = float(cfg.extra.get("ton_receive_share", 1 - cfg.sell_fee))
        self.sell_currency = str(cfg.extra.get("sell_currency", "STARS")).upper()
        self.watch = [norm(x) for x in cfg.extra.get("watch", [])]
        self.per_cycle = int(cfg.extra.get("collections_per_cycle", 4))

    @property
    def tg(self):
        if not self.ctx.tg:
            raise RuntimeError("telegram: нужна Telethon-сессия (python -m giftsniper login)")
        return self.ctx.tg

    async def authorize(self) -> None:
        await self._load_catalog(force=True)
        try:
            app = await get_app_config(self.tg)
            self.stars_receive = _share(app.get("stars_stargift_resale_commission_permille"),
                                        self.stars_receive)
            self.ton_receive = _share(app.get("ton_stargift_resale_commission_permille"),
                                      self.ton_receive)
        except Exception as e:
            self.log.info("app config недоступен: %s", e)
        self.log.info("Доля продавца: Stars %.0f%%, TON %.0f%%",
                      self.stars_receive * 100, self.ton_receive * 100)

    async def _load_catalog(self, force: bool = False) -> None:
        if not force and time.time() - self._catalog_at < 600:
            return
        res = await self.tg(functions.payments.GetStarGiftsRequest(hash=0))
        cat = {}
        for g in getattr(res, "gifts", []) or []:
            title = getattr(g, "title", None)
            if title and (g.availability_resale or g.resell_min_stars):
                cat[norm(title)] = g
        if cat:
            self.catalog = cat
            self._catalog_at = time.time()

    # ---- разбор лотов ---------------------------------------------------
    def _parse(self, g: Any) -> Listing | None:
        if not isinstance(g, types.StarGiftUnique) or not g.resell_amount:
            return None
        stars = ton = None
        for amt in g.resell_amount:
            if isinstance(amt, types.StarsTonAmount):
                ton = amt.amount / NANO
            elif isinstance(amt, types.StarsAmount):
                stars = amt.amount
        rates = self.ctx.rates
        options = []
        if ton:
            options.append(("TON", ton, ton))
        if stars and not g.resale_ton_only:
            options.append(("STARS", stars, rates.stars_cost_ton(stars)))
        if not options:
            return None
        currency, native, price_ton = min(options, key=lambda o: o[2])
        a = _attrs(g)
        return Listing(
            market=self.name, listing_id=g.slug, collection=g.title, num=g.num,
            model=a.get("model"), backdrop=a.get("backdrop"), symbol=a.get("symbol"),
            currency=currency, price_native=native, price_ton=price_ton,
            url=f"https://t.me/nft/{g.slug}",
            raw={"gift_id": g.gift_id, "stars": stars, "ton": ton, **a},
        )

    async def _resale(self, gift_id: int, *, limit: int, by_price: bool = False,
                      model_doc: int | None = None, attributes_hash: int | None = None):
        attrs = [types.StarGiftAttributeIdModel(document_id=model_doc)] if model_doc else None
        return await self.tg(functions.payments.GetResaleStarGiftsRequest(
            gift_id=gift_id, offset="", limit=limit, sort_by_price=by_price or None,
            attributes=attrs, attributes_hash=attributes_hash))

    # ---- интерфейс Market ----------------------------------------------
    async def latest(self) -> list[Listing]:
        await self._load_catalog()
        keys = [k for k in (self.watch or list(self.catalog)) if k in self.catalog]
        if not keys:
            return []
        out: list[Listing] = []
        for _ in range(min(self.per_cycle, len(keys))):
            g = self.catalog[keys[self._rr % len(keys)]]
            self._rr += 1
            try:
                res = await self._resale(g.id, limit=self.cfg.poll_limit)
            except FloodWaitError as e:
                self.log.warning("FloodWait %s c — пропускаю цикл", e.seconds)
                break
            out += [x for x in (self._parse(u) for u in res.gifts) if x]
        return out

    async def _model_doc(self, gift_id: int, model: str) -> int | None:
        if gift_id not in self.models:
            res = await self._resale(gift_id, limit=1, attributes_hash=0)
            self.models[gift_id] = {
                norm(a.name): a.document.id for a in (res.attributes or [])
                if isinstance(a, types.StarGiftAttributeModel)}
        return self.models[gift_id].get(norm(model))

    async def cheapest(self, collection, model=None, limit=5):
        await self._load_catalog()
        g = self.catalog.get(norm(collection))
        if not g:
            return []
        doc = await self._model_doc(g.id, model) if model else None
        if model and not doc:
            return []
        res = await self._resale(g.id, limit=max(limit, 10), by_price=True, model_doc=doc)
        items = [x for x in (self._parse(u) for u in res.gifts) if x]
        return sorted(items, key=lambda x: x.price_ton)[:limit]

    async def floors(self) -> dict[str, Floor]:
        await self._load_catalog(force=True)
        out = {}
        for k, g in self.catalog.items():
            if g.resell_min_stars:
                out[k] = Floor(self.name, g.title, self.ctx.rates.stars_cost_ton(g.resell_min_stars),
                               count=g.availability_resale)
        return out

    def net_share(self, currency: str | None = None) -> float:
        cur = (currency or self.sell_currency).upper()
        if cur == "TON":
            return self.ton_receive
        r = self.ctx.rates.cfg
        # Продаём за Stars: покупатель «платит» по курсу покупки Stars, мы выводим по курсу продажи
        return self.stars_receive * r.star_sell_usd / r.star_buy_usd

    async def buy(self, listing: Listing) -> dict[str, Any]:
        pay_ton = listing.currency == "TON"
        invoice = types.InputInvoiceStarGiftResale(
            slug=listing.listing_id, to_id=types.InputPeerSelf(), ton=pay_ton or None)
        form = await self.tg(functions.payments.GetPaymentFormRequest(invoice=invoice))
        total = sum(p.amount for p in form.invoice.prices)
        expected = (listing.raw.get("ton") or 0) * NANO if pay_ton else (listing.raw.get("stars") or 0)
        if expected and total > expected * 1.001:
            raise RuntimeError(f"Цена изменилась: {total} > {expected} ({form.invoice.currency})")
        res = await self.tg(functions.payments.SendStarsFormRequest(
            form_id=form.form_id, invoice=invoice))
        return {"paid": total, "currency": form.invoice.currency, "result": type(res).__name__}

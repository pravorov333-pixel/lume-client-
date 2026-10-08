"""Portals (@portals). API: https://portals-market.com/api, авторизация `Authorization: tma <initData>`."""
from __future__ import annotations

import re
from typing import Any
from urllib.parse import quote_plus

from ..http import Http
from ..models import Floor, Listing, norm, strip_rarity
from .base import Market

API = "https://portals-market.com/api/"


def _cap(text: str) -> str:
    """Portals ждёт названия с заглавной буквы в каждом слове: "plush pepe" -> "Plush Pepe"."""
    return re.sub(r"\w+(?:'\w+)?", lambda m: m.group(0)[:1].upper() + m.group(0)[1:], text)


def _attr(item: dict[str, Any], kind: str) -> str | None:
    for a in item.get("attributes") or []:
        if a.get("type") == kind:
            return a.get("value")
    return None


class Portals(Market):
    name = "portals"

    def __init__(self, cfg, ctx):
        super().__init__(cfg, ctx)
        self.http = Http(impersonate=cfg.impersonate, proxy=ctx.proxy, headers={
            "Accept": "application/json, text/plain, */*",
            "Origin": "https://portals-market.com",
            "Referer": "https://portals-market.com/",
        })

    async def authorize(self) -> None:
        self.http.headers["Authorization"] = f"tma {await self.fetch_init_data()}"

    async def close(self) -> None:
        await self.http.close()

    def _parse(self, it: dict[str, Any]) -> Listing | None:
        try:
            price = float(it.get("price") or 0)
        except (TypeError, ValueError):
            return None
        if price <= 0:
            return None
        num = it.get("external_collection_number")
        return Listing(
            market=self.name,
            listing_id=str(it["id"]),
            collection=it.get("name") or "",
            num=int(num) if num else None,
            model=_attr(it, "model"),
            backdrop=_attr(it, "backdrop"),
            symbol=_attr(it, "symbol"),
            price_ton=price,
            price_native=price,
            raw=it,
        )

    async def _search(self, *, sort: str, limit: int, collection: str | None = None,
                      model: str | None = None) -> list[Listing]:
        url = f"{API}nfts/search?offset=0&limit={limit}&sort_by={quote_plus(sort)}&status=listed"
        if collection:
            url += f"&filter_by_collections={quote_plus(_cap(collection))}"
        if model:
            url += f"&filter_by_models={quote_plus(_cap(strip_rarity(model) or ''))}"
        data = await self.with_reauth(lambda: self.http.get(url))
        items = data.get("results") if isinstance(data, dict) else data
        return [x for x in (self._parse(i) for i in items or []) if x]

    async def latest(self) -> list[Listing]:
        return await self._search(sort="listed_at desc", limit=min(self.cfg.poll_limit, 20))

    async def cheapest(self, collection, model=None, limit=5):
        return await self._search(sort="price asc", limit=limit, collection=collection, model=model)

    async def floors(self) -> dict[str, Floor]:
        # {"floorPrices": {"plushpepe": "1234.5", ...}} — ключи уже "короткие имена"
        data = await self.with_reauth(lambda: self.http.get(f"{API}collections/floors"))
        prices = (data or {}).get("floorPrices") or {}
        out: dict[str, Floor] = {}
        for short, fp in prices.items():
            try:
                if fp:
                    out[norm(short)] = Floor(self.name, short, float(fp))
            except (TypeError, ValueError):
                continue
        return out

    async def buy(self, listing: Listing) -> dict[str, Any]:
        detail: dict[str, Any] = {"id": listing.listing_id, "price": str(listing.price_native)}
        owner = listing.raw.get("owner_id")
        if owner:
            detail["owner_id"] = owner
        return await self.with_reauth(
            lambda: self.http.post(f"{API}nfts", json={"nft_details": [detail]})) or {"ok": True}

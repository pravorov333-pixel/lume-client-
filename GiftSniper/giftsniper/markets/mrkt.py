"""MRKT (@mrkt). API: https://api.tgmrkt.io/api/v1, токен из POST /auth {data: initData}. Цены в nanoTON."""
from __future__ import annotations

from typing import Any

from ..http import Http
from ..models import Floor, Listing, norm, strip_rarity
from .base import Market

API = "https://api.tgmrkt.io/api/v1"
NANO = 1_000_000_000


def _search_body(**kw: Any) -> dict[str, Any]:
    body = {
        "collectionNames": [], "modelNames": [], "backdropNames": [], "symbolNames": [],
        "ordering": "Price", "lowToHigh": True, "maxPrice": None, "minPrice": None,
        "mintable": None, "number": None, "count": 20, "cursor": "", "query": None,
        "promotedFirst": False,
    }
    body.update(kw)
    return body


class Mrkt(Market):
    name = "mrkt"

    def __init__(self, cfg, ctx):
        super().__init__(cfg, ctx)
        self.http = Http(impersonate=cfg.impersonate, proxy=ctx.proxy, headers={
            "Accept": "application/json, text/plain, */*",
            "Origin": "https://cdn.tgmrkt.io",
            "Referer": "https://cdn.tgmrkt.io/",
        })

    async def authorize(self) -> None:
        init = await self.fetch_init_data()
        self.http.headers.pop("Authorization", None)
        data = await self.http.post(f"{API}/auth", json={"data": init})
        token = (data or {}).get("token")
        if not token:
            raise RuntimeError(f"mrkt: нет token в ответе /auth: {data}")
        self.http.headers["Authorization"] = token
        self.http.headers["Cookie"] = f"access_token={token}"

    async def close(self) -> None:
        await self.http.close()

    def _parse(self, g: dict[str, Any]) -> Listing | None:
        nano = g.get("salePrice") or 0
        if not nano or not g.get("id"):
            return None
        price = nano / NANO
        return Listing(
            market=self.name,
            listing_id=str(g["id"]),
            collection=g.get("collectionTitle") or g.get("collectionName") or "",
            num=g.get("number"),
            model=g.get("modelTitle") or g.get("modelName"),
            backdrop=g.get("backdropName"),
            symbol=g.get("symbolName"),
            price_ton=price,
            price_native=price,
            raw=g,
        )

    async def _saling(self, **kw: Any) -> list[Listing]:
        data = await self.with_reauth(lambda: self.http.post(f"{API}/gifts/saling",
                                                             json=_search_body(**kw)))
        items = (data or {}).get("gifts") or []
        return [x for x in (self._parse(g) for g in items) if x]

    async def latest(self) -> list[Listing]:
        # Лента событий: новые выставления и смены цены
        try:
            data = await self.with_reauth(lambda: self.http.post(f"{API}/feed", json={
                "count": self.cfg.poll_limit, "cursor": "", "collectionNames": [],
                "modelNames": [], "backdropNames": [], "type": ["listing", "change_price"],
                "ordering": "Latest", "lowToHigh": False,
            }))
            items = (data or {}).get("items") or []
            out = []
            for it in items:
                g = it.get("gift") if isinstance(it, dict) else None
                if g and g.get("isOnSale", True):
                    lst = self._parse(g)
                    if lst:
                        out.append(lst)
            if out:
                return out
        except Exception as e:
            self.log.debug("feed недоступен (%s), беру saling по дате", e)
        return await self._saling(ordering="Date", lowToHigh=False, count=self.cfg.poll_limit)

    async def cheapest(self, collection, model=None, limit=5):
        # MRKT фильтрует по collectionName (без пробелов) — пробуем оба варианта
        names = list({collection, collection.replace(" ", "")})
        return await self._saling(collectionNames=names,
                                  modelNames=[strip_rarity(model)] if model else [],
                                  count=limit)

    async def floors(self) -> dict[str, Floor]:
        data = await self.with_reauth(lambda: self.http.get(f"{API}/gifts/collections"))
        out: dict[str, Floor] = {}
        for c in data or []:
            fp = c.get("floorPriceNanoTons")
            title = c.get("title") or c.get("name") or ""
            if fp and title and not c.get("isHidden"):
                out[norm(title)] = Floor(self.name, title, fp / NANO)
        return out

    async def buy(self, listing: Listing) -> dict[str, Any]:
        res = await self.with_reauth(lambda: self.http.post(
            f"{API}/gifts/buy", json={"ids": [listing.listing_id]}))
        return {"result": res}

"""Tonnel (@Tonnel_Network_bot). API: https://gifts2.tonnel.network/api.

Важно: Tonnel отдаёт «сырую» цену продавца, покупатель платит сверху price_markup (≈10%).
В Listing.price_ton кладём цену для покупателя.
"""
from __future__ import annotations

import base64
import hashlib
import json
import time
from typing import Any

from ..http import Http
from ..models import Floor, Listing, norm, strip_rarity
from .base import Market

API = "https://gifts2.tonnel.network/api"
BUY_API = "https://gifts.coffin.meme/api"
ORIGIN = "https://market.tonnel.network"

SORTS = {
    "price_asc": '{"price":1,"gift_id":-1}',
    "latest": '{"message_post_time":-1,"gift_id":-1}',
}

# Веб-клиент Tonnel шифрует timestamp (CryptoJS AES, passphrase-режим) и шлёт его в поле "wtf".
_WTF_PASS = b"yowtfisthispieceofshitiiit"


def _title(text: str) -> str:
    return " ".join(w[:1].upper() + w[1:] for w in text.split())


def _wtf() -> tuple[str, str]:
    from Crypto.Cipher import AES
    from Crypto.Random import get_random_bytes
    from Crypto.Util.Padding import pad

    ts = str(int(time.time()))
    salt = get_random_bytes(8)
    d = prev = b""
    while len(d) < 48:  # EVP_BytesToKey(md5)
        prev = hashlib.md5(prev + _WTF_PASS + salt).digest()
        d += prev
    ct = AES.new(d[:32], AES.MODE_CBC, d[32:48]).encrypt(pad(ts.encode(), 16))
    return ts, base64.b64encode(b"Salted__" + salt + ct).decode()


class Tonnel(Market):
    name = "tonnel"

    def __init__(self, cfg, ctx):
        super().__init__(cfg, ctx)
        self.auth = ""
        self.http = Http(impersonate=cfg.impersonate, proxy=ctx.proxy, headers={
            "Accept": "*/*",
            "Content-Type": "application/json",
            "Origin": ORIGIN,
            "Referer": ORIGIN + "/",
        })

    async def authorize(self) -> None:
        self.auth = await self.fetch_init_data()

    async def close(self) -> None:
        await self.http.close()

    @property
    def k(self) -> float:
        return 1.0 + self.cfg.price_markup

    def _parse(self, it: dict[str, Any]) -> Listing | None:
        try:
            raw_price = float(it.get("price") or 0)
        except (TypeError, ValueError):
            return None
        if raw_price <= 0 or not it.get("gift_id"):
            return None
        return Listing(
            market=self.name,
            listing_id=str(it["gift_id"]),
            collection=it.get("name") or it.get("gift_name") or "",
            num=it.get("gift_num"),
            model=strip_rarity(it.get("model")),
            backdrop=strip_rarity(it.get("backdrop")),
            symbol=strip_rarity(it.get("symbol")),
            price_ton=raw_price * self.k,
            price_native=raw_price,
            raw=it,
        )

    async def _page(self, *, sort: str, limit: int, collection: str | None = None,
                    model: str | None = None) -> list[Listing]:
        flt: dict[str, Any] = {
            "price": {"$exists": True}, "buyer": {"$exists": False}, "asset": "TON",
            "refunded": {"$ne": True}, "export_at": {"$exists": True},
        }
        if collection:
            flt["gift_name"] = _title(collection.strip())
        if model:
            flt["model"] = {"$regex": f"^{_title(strip_rarity(model) or '')} \\("}
        body = {"page": 1, "limit": min(limit, 30), "sort": SORTS[sort],
                "filter": json.dumps(flt), "ref": 0, "price_range": None,
                "user_auth": self.auth}
        data = await self.with_reauth(lambda: self.http.post(f"{API}/pageGifts", json=body))
        items = data if isinstance(data, list) else (data or {}).get("gifts") or []
        return [x for x in (self._parse(i) for i in items if isinstance(i, dict)) if x]

    async def latest(self) -> list[Listing]:
        return await self._page(sort="latest", limit=self.cfg.poll_limit)

    async def cheapest(self, collection, model=None, limit=5):
        return await self._page(sort="price_asc", limit=limit, collection=collection, model=model)

    async def floors(self) -> dict[str, Floor]:
        data = await self.with_reauth(lambda: self.http.post(
            f"{API}/filterStats", json={"authData": self.auth}))
        stats = (data or {}).get("data", data) if isinstance(data, dict) else {}
        out: dict[str, Floor] = {}
        # Формат: {"Toy Bear_Wizard (1.5%)": {"floorPrice": 12.3, "howMany": 4}, ...}
        for key, v in (stats or {}).items():
            if not isinstance(v, dict):
                continue
            fp = v.get("floorPrice") or v.get("floor")
            if not fp:
                continue
            col = key.split("_", 1)[0]
            f = out.setdefault(norm(col), Floor(self.name, col, float("inf"), count=0))
            f.price_ton = min(f.price_ton, float(fp) * self.k)
            f.count = (f.count or 0) + int(v.get("howMany") or 0)
        return out

    async def buy(self, listing: Listing) -> dict[str, Any]:
        ts, wtf = _wtf()
        body = {"asset": "TON", "price": float(listing.price_native or 0),
                "timestamp": ts, "wtf": wtf}
        return await self.with_reauth(lambda: self.http.post(
            f"{BUY_API}/buyGift/{listing.listing_id}", json={**body, "authData": self.auth}))

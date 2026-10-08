"""HTTP-клиент на curl_cffi: маркеты сидят за Cloudflare и проверяют TLS-отпечаток браузера."""
from __future__ import annotations

import asyncio
import logging
from typing import Any

from curl_cffi.requests import AsyncSession

log = logging.getLogger(__name__)


class HttpError(Exception):
    def __init__(self, status: int, body: str, url: str):
        super().__init__(f"HTTP {status} {url}: {body[:300]}")
        self.status = status
        self.body = body


class Http:
    def __init__(self, *, impersonate: str = "chrome", headers: dict[str, str] | None = None,
                 proxy: str | None = None, timeout: float = 15.0, retries: int = 2):
        self._session = AsyncSession(impersonate=impersonate, timeout=timeout, proxy=proxy)
        self.headers = dict(headers or {})
        self.retries = retries

    async def request(self, method: str, url: str, *, json: Any = None,
                      params: dict[str, Any] | None = None,
                      headers: dict[str, str] | None = None) -> Any:
        hdrs = {**self.headers, **(headers or {})}
        last: Exception | None = None
        for attempt in range(self.retries + 1):
            try:
                r = await self._session.request(method, url, json=json, params=params, headers=hdrs)
            except Exception as e:  # сетевые ошибки
                last = e
            else:
                if r.status_code in (200, 201):
                    try:
                        return r.json()
                    except Exception:
                        return r.text
                if r.status_code == 204:
                    return None
                last = HttpError(r.status_code, r.text, url)
                # 4xx (кроме 429) повторять бессмысленно
                if r.status_code < 500 and r.status_code != 429:
                    raise last
            await asyncio.sleep(0.5 * 2 ** attempt)
        assert last is not None
        raise last

    async def get(self, url: str, **kw: Any) -> Any:
        return await self.request("GET", url, **kw)

    async def post(self, url: str, **kw: Any) -> Any:
        return await self.request("POST", url, **kw)

    async def close(self) -> None:
        await self._session.close()

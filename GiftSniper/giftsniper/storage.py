"""SQLite: какие лоты уже видели, журнал находок и покупок (для лимитов автобая)."""
from __future__ import annotations

import sqlite3
import time


class Store:
    def __init__(self, path: str):
        self.db = sqlite3.connect(path)
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS seen (
                market TEXT, listing_id TEXT, price REAL, ts REAL,
                PRIMARY KEY (market, listing_id, price));
            CREATE TABLE IF NOT EXISTS finds (
                ts REAL, market TEXT, listing_id TEXT, title TEXT,
                price_ton REAL, exit_market TEXT, profit_ton REAL, roi REAL);
            CREATE TABLE IF NOT EXISTS purchases (
                ts REAL, market TEXT, listing_id TEXT, title TEXT,
                price_ton REAL, status TEXT, info TEXT);
        """)

    def is_new(self, market: str, listing_id: str, price: float) -> bool:
        """True, если такой лот с такой ценой ещё не встречался (и запоминает его)."""
        cur = self.db.execute("INSERT OR IGNORE INTO seen VALUES (?,?,?,?)",
                              (market, listing_id, round(price, 6), time.time()))
        self.db.commit()
        return cur.rowcount == 1

    def prune(self, older_than_sec: float = 3 * 86400) -> None:
        self.db.execute("DELETE FROM seen WHERE ts < ?", (time.time() - older_than_sec,))
        self.db.commit()

    def log_find(self, market, listing_id, title, price, exit_market, profit, roi) -> None:
        self.db.execute("INSERT INTO finds VALUES (?,?,?,?,?,?,?,?)",
                        (time.time(), market, listing_id, title, price, exit_market, profit, roi))
        self.db.commit()

    def log_purchase(self, market, listing_id, title, price, status, info="") -> None:
        self.db.execute("INSERT INTO purchases VALUES (?,?,?,?,?,?,?)",
                        (time.time(), market, listing_id, title, price, status, info))
        self.db.commit()

    def spent_since(self, seconds: float) -> tuple[float, int]:
        row = self.db.execute(
            "SELECT COALESCE(SUM(price_ton),0), COUNT(*) FROM purchases "
            "WHERE status IN ('ok','dry') AND ts > ?", (time.time() - seconds,)).fetchone()
        return float(row[0]), int(row[1])

    def recent_finds(self, limit: int = 10) -> list[tuple]:
        return self.db.execute(
            "SELECT ts, market, title, price_ton, exit_market, profit_ton, roi FROM finds "
            "ORDER BY ts DESC LIMIT ?", (limit,)).fetchall()

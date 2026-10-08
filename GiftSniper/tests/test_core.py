import asyncio
import base64
import hashlib

import pytest

from giftsniper.arbitrage import evaluate_listing, floor_spreads, make_exit
from giftsniper.config import MarketCfg, load_config
from giftsniper.engine import Engine
from giftsniper.format import fmt_compare, fmt_opp, fmt_spreads
from giftsniper.markets.base import Ctx, Market
from giftsniper.models import Floor, Listing, nft_link, norm
from giftsniper.rates import Rates
from giftsniper.config import RatesCfg
from giftsniper.storage import Store


class FakeMarket(Market):
    def __init__(self, name, sell_fee=0.0, markup=0.0, deposit=0.0, withdraw=0.0, book=None,
                 latest=None, floors=None):
        self.name = name
        cfg = MarketCfg(name=name, sell_fee=sell_fee, price_markup=markup,
                        deposit_cost_ton=deposit, withdraw_cost_ton=withdraw, autobuy=True)
        super().__init__(cfg, Ctx(tg=None, rates=Rates(RatesCfg())))
        self._book = book or {}
        self._latest = latest or []
        self._floors = floors or {}
        self.bought = []

    async def latest(self):
        return list(self._latest)

    async def floors(self):
        return self._floors

    async def cheapest(self, collection, model=None, limit=5):
        return self._book.get((norm(collection), norm(model)), [])[:limit]

    async def buy(self, listing):
        self.bought.append(listing)
        return {"ok": True}


def L(market, lid, price, col="Plush Pepe", model="Gold", num=1):
    return Listing(market=market, listing_id=lid, collection=col, price_ton=price,
                   price_native=price, model=model, num=num)


def test_norm_and_link():
    assert norm("Plush Pepe") == norm("plushpepe") == norm("Plush-Pepe")
    assert norm("Gold (1.5%)") == norm("Gold")
    assert nft_link("Jack-in-the-Box", 42) == "https://t.me/nft/JackintheBox-42"


def test_make_exit_fees_and_transfer():
    a = FakeMarket("a", withdraw=0.1)
    b = FakeMarket("b", sell_fee=0.05, deposit=0.2)
    e = make_exit(10.0, a, b, floor_ton=12.0, undercut=0.0)
    assert e.net_ton == pytest.approx(11.4)
    assert e.profit_ton == pytest.approx(11.4 - 10.0 - 0.3)
    # продажа на том же маркете — без перевода
    same = make_exit(10.0, b, b, 12.0, 0.0)
    assert same.profit_ton == pytest.approx(1.4)


def test_tonnel_style_markup_net_share():
    t = FakeMarket("tonnel", markup=0.10)
    # Покупатель видит 11 TON, продавец получает 10
    assert t.net_share() * 11.0 == pytest.approx(10.0)


def test_evaluate_excludes_own_listing():
    a, b = FakeMarket("a"), FakeMarket("b", sell_fee=0.05)
    me = L("a", "1", 5.0)
    book = {"a": [me, L("a", "2", 9.0), L("a", "3", 9.5)],
            "b": [L("b", "x", 10.0), L("b", "y", 11.0)]}
    opp = evaluate_listing(me, {"a": a, "b": b}, book, undercut=0.0, min_depth=1)
    by_market = {e.market: e for e in opp.exits}
    # флор на "a" — следующий лот (9.0), а не наш собственный за 5.0
    assert by_market["a"].floor_ton == 9.0 and by_market["a"].profit_ton == pytest.approx(4.0)
    assert opp.best_exit.market == "b" and opp.profit_ton == pytest.approx(4.5)


def test_evaluate_best_exit_and_depth():
    a, b = FakeMarket("a"), FakeMarket("b", sell_fee=0.05)
    me = L("a", "1", 5.0)
    book = {"a": [me, L("a", "2", 9.0)], "b": [L("b", "x", 10.0), L("b", "y", 11.0)]}
    opp = evaluate_listing(me, {"a": a, "b": b}, book, undercut=0.0, min_depth=1)
    assert opp.best_exit.market == "b"
    assert opp.profit_ton == pytest.approx(4.5)
    # с min_depth=2 у маркета "a" остаётся один чужой лот — он отбрасывается
    opp2 = evaluate_listing(me, {"a": a, "b": b}, book, undercut=0.0, min_depth=2)
    assert [e.market for e in opp2.exits] == ["b"]


def test_floor_spreads():
    a, b, c = FakeMarket("a"), FakeMarket("b", sell_fee=0.05), FakeMarket("c")
    floors = {
        "plushpepe": {"a": Floor("a", "plushpepe", 100), "b": Floor("b", "Plush Pepe", 120)},
        "toybear": {"a": Floor("a", "Toy Bear", 10), "c": Floor("c", "Toy Bear", 10.1)},
        "solo": {"a": Floor("a", "Solo", 1)},
    }
    sp = floor_spreads(floors, {"a": a, "b": b, "c": c}, undercut=0.0, min_profit=1.0)
    assert len(sp) == 1
    s = sp[0]
    assert s.collection == "Plush Pepe"
    assert (s.buy.market, s.sell.market) == ("a", "b")
    assert s.profit_ton == pytest.approx(120 * 0.95 - 100)
    assert "Plush Pepe" in fmt_spreads(sp)


def test_format_compare_and_opp():
    a, b = FakeMarket("portals"), FakeMarket("tonnel", markup=0.1)
    book = {"portals": [L("portals", "1", 5.0)], "tonnel": [L("tonnel", "2", 7.7)]}
    txt = fmt_compare("Plush Pepe", "Gold", book, {"portals": a, "tonnel": b}, 0.0)
    assert "Дешевле всего купить: <b>Portals</b>" in txt and "в окуп" in txt
    opp = evaluate_listing(book["portals"][0], {"portals": a, "tonnel": b}, book, undercut=0.0)
    assert "+2.00 TON" in fmt_opp(opp)  # 7.7/1.1 = 7.0 на руки, минус 5


def test_store(tmp_path):
    s = Store(str(tmp_path / "t.db"))
    assert s.is_new("a", "1", 5.0)
    assert not s.is_new("a", "1", 5.0)
    assert s.is_new("a", "1", 4.0)  # смена цены — новое событие
    s.log_purchase("a", "1", "x", 3.0, "ok")
    s.log_purchase("a", "2", "x", 2.0, "fail")
    assert s.spent_since(3600) == (3.0, 1)


def test_engine_snipe_and_autobuy(tmp_path):
    cfg = load_config(tmp_path / "missing.yaml")
    cfg.sniper.min_profit_ton = 0.5
    cfg.sniper.min_roi = 0.05
    cfg.sniper.min_floor_depth = 1
    cfg.autobuy.enabled = True
    cfg.autobuy.dry_run = False
    cfg.autobuy.max_price_ton = 10
    cfg.autobuy.min_profit_ton = 1
    cfg.autobuy.min_roi = 0.1

    cheap = L("a", "1", 5.0)
    k = (norm("Plush Pepe"), norm("Gold"))
    a = FakeMarket("a", book={k: [cheap, L("a", "2", 8.0)]}, latest=[L("a", "0", 9.0)])
    b = FakeMarket("b", book={k: [L("b", "x", 8.5), L("b", "y", 9.0)]})
    for m in (a, b):
        m.ready = True
    eng = Engine(cfg, {"a": a, "b": b}, Store(str(tmp_path / "e.db")), Rates(cfg.rates))
    got, msgs = [], []

    async def on_opp(o):
        got.append(o)

    async def on_msg(t):
        msgs.append(t)
    eng.on_opportunity, eng.on_message = on_opp, on_msg

    async def scenario():
        # первые опросы только «прогревают» витрину — без алертов
        poll = asyncio.create_task(eng.poll(a))
        await asyncio.sleep(0.05)
        assert not got
        a._latest.append(cheap)       # появился дешёвый лот
        await asyncio.sleep(0.2)
        poll.cancel()

    cfg.sniper.poll_interval = 0.01
    asyncio.run(scenario())
    assert len(got) == 1 and got[0].listing.listing_id == "1"
    assert got[0].best_exit.market == "b"
    assert a.bought == [cheap]
    assert msgs and msgs[0].startswith("✅")


def test_autobuy_limits(tmp_path):
    cfg = load_config(tmp_path / "missing.yaml")
    cfg.autobuy.max_price_ton = 3
    a = FakeMarket("a")
    eng = Engine(cfg, {"a": a}, Store(str(tmp_path / "e.db")), Rates(cfg.rates))
    opp = evaluate_listing(L("a", "1", 5.0), {"a": a}, {"a": [L("a", "2", 9.0)]}, undercut=0)
    assert "лимита" in eng.autobuy_block_reason(opp)
    a.cfg.autobuy = False
    assert "выключен" in eng.autobuy_block_reason(opp)


def test_tonnel_wtf_decrypts_to_timestamp():
    from Crypto.Cipher import AES
    from Crypto.Util.Padding import unpad

    from giftsniper.markets.tonnel import _WTF_PASS, _wtf
    ts, wtf = _wtf()
    raw = base64.b64decode(wtf)
    assert raw[:8] == b"Salted__"
    salt, ct = raw[8:16], raw[16:]
    d = prev = b""
    while len(d) < 48:
        prev = hashlib.md5(prev + _WTF_PASS + salt).digest()
        d += prev
    assert unpad(AES.new(d[:32], AES.MODE_CBC, d[32:48]).decrypt(ct), 16).decode() == ts


def test_telegram_parse_prefers_cheaper_currency():
    from telethon import types

    from giftsniper.markets.telegram import TelegramMarket, _share
    rates = Rates(RatesCfg(ton_usd=3.0, star_buy_usd=0.015, star_sell_usd=0.013))
    m = TelegramMarket(MarketCfg(name="telegram"), Ctx(tg=None, rates=rates))
    doc = types.DocumentEmpty(id=77)
    rarity = types.StarGiftAttributeRarity(permille=15)
    g = types.StarGiftUnique(
        id=1, gift_id=555, title="Plush Pepe", slug="PlushPepe-10", num=10,
        attributes=[types.StarGiftAttributeModel(name="Gold", document=doc, rarity=rarity),
                    types.StarGiftAttributeBackdrop(name="Black", backdrop_id=1, center_color=0,
                                                    edge_color=0, pattern_color=0, text_color=0,
                                                    rarity=rarity)],
        availability_issued=1, availability_total=1,
        resell_amount=[types.StarsAmount(amount=1000, nanos=0), types.StarsTonAmount(amount=6 * 10**9)])
    l = m._parse(g)
    # 1000 ⭐ * 0.015$ / 3$ = 5 TON < 6 TON
    assert l.currency == "STARS" and l.price_ton == pytest.approx(5.0)
    assert l.model == "Gold" and l.backdrop == "Black" and l.url == "https://t.me/nft/PlushPepe-10"
    g.resale_ton_only = True
    assert m._parse(g).currency == "TON"
    assert _share(800, 0.5) == pytest.approx(0.8)
    assert _share(50, 0.5) == pytest.approx(0.95)
    assert _share(None, 0.7) == 0.7


def test_config_defaults_and_env(tmp_path, monkeypatch):
    p = tmp_path / "c.yaml"
    p.write_text("markets:\n  portals:\n    sell_fee: 0.07\n    foo: 1\n  mrkt:\n    enabled: false\n",
                 encoding="utf-8")
    monkeypatch.setenv("TONNEL_INIT_DATA", "abc")
    cfg = load_config(p)
    assert cfg.markets["portals"].sell_fee == 0.07
    assert cfg.markets["portals"].extra == {"foo": 1}
    assert cfg.markets["mrkt"].enabled is False
    assert cfg.markets["tonnel"].price_markup == 0.10
    assert cfg.markets["tonnel"].init_data == "abc"


def test_autobuy_budget_is_atomic(tmp_path):
    cfg = load_config(tmp_path / "missing.yaml")
    cfg.autobuy.dry_run = False
    cfg.autobuy.daily_budget_ton = 6
    cfg.autobuy.min_profit_ton = 0
    cfg.autobuy.min_roi = 0
    a = FakeMarket("a")
    eng = Engine(cfg, {"a": a}, Store(str(tmp_path / "e.db")), Rates(cfg.rates))
    opps = [evaluate_listing(L("a", str(i), 5.0), {"a": a}, {"a": [L("a", "z", 9.0)]}, undercut=0)
            for i in range(3)]

    async def run():
        await asyncio.gather(*(eng.autobuy(o) for o in opps))
        await eng.buy(opps[0], manual=True)   # повторная покупка того же лота игнорируется
    asyncio.run(run())
    assert len(a.bought) == 1

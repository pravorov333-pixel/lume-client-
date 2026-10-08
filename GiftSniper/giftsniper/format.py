"""Тексты сообщений (HTML для Telegram)."""
from __future__ import annotations

import time
from html import escape
from typing import Mapping, Sequence

from .arbitrage import Econ, Spread, make_exit
from .models import Listing, Opportunity

TITLES = {"portals": "Portals", "mrkt": "MRKT", "tonnel": "Tonnel", "telegram": "Telegram"}


def mt(name: str) -> str:
    return TITLES.get(name, name)


def ton(x: float) -> str:
    return f"{x:,.2f}".replace(",", " ")


def signed(x: float) -> str:
    return ("+" if x >= 0 else "−") + ton(abs(x))


def price_str(l: Listing) -> str:
    if l.currency == "STARS" and l.price_native:
        return f"{int(l.price_native):,} ⭐ (≈{ton(l.price_ton)} TON)".replace(",", " ")
    return f"{ton(l.price_ton)} TON"


def fmt_opp(o: Opportunity) -> str:
    l, e = o.listing, o.best_exit
    attrs = " · ".join(escape(x) for x in (l.model, l.backdrop, l.symbol) if x)
    lines = [
        f"🎯 <b>Снайп</b> · {mt(l.market)}  <i>(флор по {'модели' if o.level == 'model' else 'коллекции'})</i>",
        f"<b>{escape(l.collection)}{f' #{l.num}' if l.num else ''}</b>",
    ]
    if attrs:
        lines.append(attrs)
    lines += [
        f"💰 Купить: <b>{price_str(l)}</b>",
        f"📤 Продать на {mt(e.market)}: выставить ~{ton(e.sell_price_ton)} (флор {ton(e.floor_ton)})"
        f" → на руки {ton(e.net_ton)}",
        f"📈 Профит: <b>{signed(e.profit_ton)} TON</b> ({e.roi * 100:.0f}%)",
    ]
    others = [f"{mt(x.market)} {signed(x.profit_ton)}" for x in o.exits[1:4]]
    if others:
        lines.append("Другие выходы: " + " · ".join(others))
    if l.link:
        lines.append(f'🔗 <a href="{l.link}">{escape(l.link)}</a>')
    return "\n".join(lines)


def fmt_spreads(spreads: Sequence[Spread], top: int = 10, title: str = "Арбитраж по флорам") -> str:
    if not spreads:
        return "Сейчас выгодных расхождений по флорам нет."
    lines = [f"📊 <b>{title}</b> (топ {min(top, len(spreads))})", ""]
    for i, s in enumerate(spreads[:top], 1):
        lines.append(
            f"{i}. <b>{escape(s.collection)}</b>\n"
            f"   купить {mt(s.buy.market)} {ton(s.buy.price_ton)} → продать {mt(s.sell.market)} "
            f"{ton(s.sell.price_ton)}\n"
            f"   чистыми {signed(s.profit_ton)} TON ({s.exit.roi * 100:.0f}%)")
    lines.append("\n<i>Учтены комиссии маркетов и стоимость перевода. Флор ≠ гарантия продажи.</i>")
    return "\n".join(lines)


def fmt_compare(collection: str, model: str | None, book: Mapping[str, Sequence[Listing]],
                econ: Mapping[str, Econ], undercut: float) -> str:
    head = escape(collection) + (f" · {escape(model)}" if model else "")
    if not book:
        return f"По «{head}» лотов не найдено ни на одном маркете."
    rows = sorted(((m, items[0]) for m, items in book.items() if items), key=lambda r: r[1].price_ton)
    lines = [f"🔎 <b>{head}</b> — где купить и где продать", ""]
    for m, l in rows:
        net = l.price_ton * (1 - undercut) * econ[m].net_share()
        lines.append(f"• {mt(m)}: флор <b>{price_str(l)}</b>, лотов≥{len(book[m])}, "
                     f"продать → на руки ~{ton(net)}")
    buy_m, buy_l = rows[0]
    exits = [make_exit(buy_l.price_ton, econ[buy_m], econ[m], l.price_ton, undercut)
             for m, l in rows if m != buy_m]
    lines.append("")
    lines.append(f"🟢 Дешевле всего купить: <b>{mt(buy_m)}</b> за {price_str(buy_l)}")
    if exits:
        best = max(exits, key=lambda e: e.profit_ton)
        lines.append(f"🔴 Выгоднее всего продать: <b>{mt(best.market)}</b> "
                     f"(на руки {ton(best.net_ton)})")
        lines.append(f"Итог сделки: <b>{signed(best.profit_ton)} TON</b> ({best.roi * 100:.0f}%)"
                     + ("  ✅ в окуп" if best.profit_ton > 0 else "  ⛔ не в окуп"))
    if buy_l.link:
        lines.append(f'🔗 <a href="{buy_l.link}">самый дешёвый лот</a>')
    return "\n".join(lines)


def fmt_status(engine) -> str:
    lines = ["⚙️ <b>Статус</b>",
             f"Снайпер: {'🟢 вкл' if engine.snipe_on else '🔴 выкл'} · "
             f"Автобай: {'🟢 вкл' if engine.autobuy_on else '🔴 выкл'}"
             f"{' (dry-run)' if engine.cfg.autobuy.dry_run else ''}",
             f"Курс TON: ${engine.rates.ton_usd:.2f}", ""]
    for name, m in engine.markets.items():
        st = engine.stats[name]
        state = "🟢" if m.ready else "🔴"
        err = st["last_error"] or m.last_error
        lines.append(f"{state} {mt(name)}: опросов {st['polls']}, новых {st['new']}, ошибок {st['errors']}"
                     + (f"\n   <code>{escape(err[:120])}</code>" if err else ""))
    spent, n = engine.store.spent_since(86400)
    lines.append(f"\nПокупок за сутки: {n} на {ton(spent)} TON "
                 f"(бюджет {ton(engine.cfg.autobuy.daily_budget_ton)})")
    return "\n".join(lines)


def fmt_finds(rows) -> str:
    if not rows:
        return "Находок пока нет."
    out = ["🗂 <b>Последние находки</b>"]
    for ts, market, title, price, exit_m, profit, roi in rows:
        t = time.strftime("%H:%M", time.localtime(ts))
        out.append(f"{t} {mt(market)} {escape(title)} — {ton(price)} → {mt(exit_m)} "
                   f"{signed(profit)} ({roi * 100:.0f}%)")
    return "\n".join(out)

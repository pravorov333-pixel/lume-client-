"""Диалог с ботом офлайн: подменяем сетевой слой aiogram и аккаунт Telegram."""
import asyncio
import datetime

from aiogram.client.session.base import BaseSession
from aiogram.types import Chat, Contact, Message, Update, User

from giftsniper.bot import build
from giftsniper.config import load_config
from giftsniper.engine import Engine
from giftsniper.markets.base import Ctx
from giftsniper.rates import Rates
from giftsniper.storage import Store
from giftsniper.tgauth import Account


class FakeSession(BaseSession):
    def __init__(self, sent):
        super().__init__()
        self.sent = sent

    async def make_request(self, bot, method, timeout=None):
        text = getattr(method, "text", None)
        if type(method).__name__ in ("SendMessage", "EditMessageText"):
            self.sent.append(text)
        if type(method).__name__ == "SendMessage":
            return Message(message_id=len(self.sent), date=datetime.datetime.now(),
                           chat=Chat(id=method.chat_id, type="private"), text=text)
        return True

    async def close(self):
        pass

    async def stream_content(self, *a, **k):
        yield b""


class FakeAccount(Account):
    async def connect(self):
        return self.ctx.tg is not None

    async def send_code(self, phone):
        self.phone = phone

    async def sign_in_code(self, code):
        self.code = code
        return False            # просим пароль 2FA

    async def sign_in_password(self, pw):
        self.ctx.tg = object()
        self.pw = pw

    async def me(self):
        return "Тест" if self.ctx.tg else None


def test_owner_login_and_settings(tmp_path):
    cfg = load_config(tmp_path / "none.yaml")
    cfg.bot_token = "123456:" + "A" * 35
    store = Store(str(tmp_path / "b.db"))
    rates = Rates(cfg.rates)
    eng = Engine(cfg, {}, store, rates)
    acc = FakeAccount(cfg, store, Ctx(None, rates))
    sent: list[str] = []
    bot, dp, bg = build(eng, acc)
    for c in bg:
        c.close()
    bot.session = FakeSession(sent)
    counter = iter(range(1, 1000))

    async def say(uid, text=None, contact=None) -> str:
        sent.clear()
        n = next(counter)
        await dp.feed_update(bot, Update(update_id=n, message=Message(
            message_id=n, date=datetime.datetime.now(), chat=Chat(id=uid, type="private"),
            from_user=User(id=uid, is_bot=False, first_name="u"), text=text, contact=contact)))
        return "\n".join(sent)

    async def scenario():
        assert "владелец" in await say(1, "/start")
        assert "приватный" in await say(2, "/start")
        assert await say(2, "/status") == ""                 # чужим бот молчит
        assert "сохранены" in await say(1, "/api 12345 abcdef")
        assert store.get("api_hash") == "abcdef"
        assert "номер" in await say(1, "/login")
        assert "через пробелы" in await say(1, contact=Contact(phone_number="79991234567",
                                                               first_name="u", user_id=1))
        assert "пароль" in await say(1, "1 2 3 4 5")
        assert "Вошли как Тест" in await say(1, "secret")
        assert (acc.phone, acc.code, acc.pw) == ("79991234567", "1 2 3 4 5", "secret")
        assert "min_profit = <b>2.0</b>" in await say(1, "/set min_profit 2")
        assert cfg.sniper.min_profit_ton == 2.0
        assert "Канал не задан" in await say(1, "/digest")
    asyncio.run(scenario())

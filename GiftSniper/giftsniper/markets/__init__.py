from .base import Ctx, Market
from .mrkt import Mrkt
from .portals import Portals
from .telegram import TelegramMarket
from .tonnel import Tonnel

REGISTRY: dict[str, type[Market]] = {
    "portals": Portals,
    "mrkt": Mrkt,
    "tonnel": Tonnel,
    "telegram": TelegramMarket,
}

__all__ = ["Ctx", "Market", "REGISTRY"]

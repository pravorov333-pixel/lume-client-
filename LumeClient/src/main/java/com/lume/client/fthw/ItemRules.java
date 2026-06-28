package com.lume.client.fthw;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Known FunTime anarchy custom items (UniqueItems plugin): Пласт/Трапка,
 * Дезориентация, Хроносфера, Огненный смерч, Снежок, Паучье чутьё, Бэкер.
 * Radii are estimates to tune. The helper recognises the item you're HOLDING by
 * its display name and shows its name/radius/cooldown (legit — your own item).
 */
public final class ItemRules {

    public static final List<ItemRule> rules = new ArrayList<>();

    static {
        rules.add(new ItemRule("дезориент", 5.0, 0xFFB76FE0, "АОЕ дезориентация"));
        rules.add(new ItemRule("хроносфер", 6.0, 0xFF6FB8E0, "замедление в зоне"));
        rules.add(new ItemRule("смерч", 4.0, 0xFFE0915A, "огненный АОЕ"));
        rules.add(new ItemRule("снеж", 3.0, 0xFF9FD8E0, "отбрасывание"));
        rules.add(new ItemRule("трапк", 0.0, 0xFFE05656, "блоки исчезают ~30с"));
        rules.add(new ItemRule("пласт", 0.0, 0xFFE05656, "временные блоки"));
        rules.add(new ItemRule("паучье", 0.0, 0xFF8FE08F, "детект игроков"));
        rules.add(new ItemRule("бэкер", 0.0, 0xFFB7AAD9, "возврат назад"));
    }

    private ItemRules() {}

    /** Find the rule matching a held item's display name, or null. */
    public static ItemRule find(String displayName) {
        if (displayName == null) return null;
        String d = displayName.toLowerCase(Locale.ROOT);
        for (ItemRule r : rules) if (d.contains(r.match)) return r;
        return null;
    }
}

package com.lume.client.fthw;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * FunTime custom items (researched from the community — UniqueItems / ExecutableItems
 * + spheres/talismans). Numbers (radius/cooldown/stats) are STARTING ESTIMATES — the
 * user verifies & tunes each one in-game via the Server tab (per-item "present" flag +
 * editable radius/cooldown), so the data stays correct as the server changes.
 *
 * <p>Three groups: ACTIVE throwable/utility items (cooldown + AoE), passive SPHERE and
 * TALISMAN gear (stat bonuses, shown in the encyclopedia).
 */
public final class ItemRules {

    public static final List<ItemRule> rules = new ArrayList<>();

    static {
        // --- Active items (cooldown + area effects) ---------------------------
        rules.add(new ItemRule("Дезориентация", "дезориент", ItemRule.Cat.ACTIVE, 5.0, 60, 0xFFB76FE0, "АОЕ: тошнота/слепота врагам"));
        rules.add(new ItemRule("Хроносфера", "хроносфер", ItemRule.Cat.ACTIVE, 6.0, 90, 0xFF6FB8E0, "зона замедления"));
        rules.add(new ItemRule("Огненный смерч", "смерч", ItemRule.Cat.ACTIVE, 4.0, 60, 0xFFE0915A, "огненный АОЕ урон"));
        rules.add(new ItemRule("Снежок", "снеж", ItemRule.Cat.ACTIVE, 0.0, 10, 0xFF9FD8E0, "сильный отброс"));
        rules.add(new ItemRule("Трапка", "трапк", ItemRule.Cat.ACTIVE, 0.0, 30, 0xFFE05656, "блоки исчезают ~30с"));
        rules.add(new ItemRule("Пласт", "пласт", ItemRule.Cat.ACTIVE, 0.0, 30, 0xFFE0A05A, "временные блоки"));
        rules.add(new ItemRule("Бэкер", "бэкер", ItemRule.Cat.ACTIVE, 0.0, 30, 0xFFB7AAD9, "возврат на прошлую позицию"));
        rules.add(new ItemRule("Паучье чутьё", "паучье", ItemRule.Cat.ACTIVE, 0.0, 0, 0xFF8FE08F, "детект игроков рядом"));
        rules.add(new ItemRule("Явная пыль", "явная пыль", ItemRule.Cat.ACTIVE, 0.0, 30, 0xFFE0D45A, "выявление скрытых игроков"));

        // --- Passive spheres (stat gear) -------------------------------------
        rules.add(new ItemRule("Сфера магмы", "магм", ItemRule.Cat.SPHERE, 0.0, 0, 0xFFE0734A, "+5 броня, +5 прочность брони"));
        rules.add(new ItemRule("Сфера незера", "незер", ItemRule.Cat.SPHERE, 0.0, 0, 0xFFC0504A, "+25% скорость, +20% атака, −1 броня"));

        // --- Passive talismans -----------------------------------------------
        rules.add(new ItemRule("Талисман крушителя", "крушител", ItemRule.Cat.TALISMAN, 0.0, 0, 0xFF9C7BD0, "+3 урон, +3 броня, +4 прочн., +2 хп"));
    }

    private ItemRules() {}

    /** Find the rule matching a held item's display name, or null (ignores {@code present}). */
    public static ItemRule find(String displayName) {
        if (displayName == null) return null;
        String d = displayName.toLowerCase(Locale.ROOT);
        for (ItemRule r : rules) if (d.contains(r.match)) return r;
        return null;
    }

    /** Lookup by exact display name (for config persistence). */
    public static ItemRule byName(String name) {
        for (ItemRule r : rules) if (r.name.equals(name)) return r;
        return null;
    }

    public static List<ItemRule> byCat(ItemRule.Cat cat) {
        List<ItemRule> out = new ArrayList<>();
        for (ItemRule r : rules) if (r.cat == cat) out.add(r);
        return out;
    }
}

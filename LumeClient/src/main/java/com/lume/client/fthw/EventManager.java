package com.lume.client.fthw;

import com.lume.client.LumeClient;
import com.lume.client.gui.Notifications;
import com.lume.client.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects server events from chat (by {@link EventRule}), tracks which are
 * active with a countdown, and fires HUD notifications. Rules are
 * placeholders for now — to be filled with real FunTime/HolyWorld chat texts.
 */
public final class EventManager {

    public static final class Active {
        public final EventRule rule;
        public final long start;
        Active(EventRule rule, long start) { this.rule = rule; this.start = start; }
        public int secondsLeft() {
            return (int) Math.max(0, rule.durationSec - (System.currentTimeMillis() - start) / 1000);
        }
    }

    /** All known event rules (placeholders — refine with real data). */
    public static final List<EventRule> rules = new ArrayList<>();
    /** Currently running events. */
    public static final List<Active> active = new ArrayList<>();

    static {
        // FunTime anarchy events. Patterns are loose (match name + a start word) and
        // durations are estimates — tune once exact chat lines are confirmed in-game.
        rules.add(new EventRule(ServerType.FUNTIME, "Вулкан", "вулкан", 300));
        rules.add(new EventRule(ServerType.FUNTIME, "Сундук смерти", "сундук\\s*смерт", 300));
        rules.add(new EventRule(ServerType.FUNTIME, "Чёрная буря", "ч[её]рн.{0,3}бур", 300));
        rules.add(new EventRule(ServerType.FUNTIME, "Воздушный шар", "(воздушн.{0,3}шар|шар)", 240));
        rules.add(new EventRule(ServerType.FUNTIME, "Сфера", "сфер", 240));
        // generic catch-all so a started event still alerts even if unnamed
        rules.add(new EventRule(ServerType.FUNTIME, "Ивент", "ивент.{0,30}(начал|старт|появил|спавн)", 300));
    }

    private EventManager() {}

    private static boolean enabled() {
        Module m = LumeClient.MODULES.getByName("Server Helper");
        return m != null && m.isEnabled();
    }

    /** Observe an incoming chat line (called read-only from the chat hook). */
    public static void onChat(String msg) {
        if (!enabled() || msg == null) return;
        ServerType st = ServerType.current();
        for (EventRule r : rules) {
            if (r.server != st && st != ServerType.UNKNOWN) continue;
            if (r.startPattern.matcher(msg).find()) {
                long now = System.currentTimeMillis();
                // learn the interval from the gap since we last saw it (ignore <30s repeats)
                if (r.lastSeen > 0) {
                    long gap = (now - r.lastSeen) / 1000;
                    if (gap > 30) r.intervalSec = r.intervalSec == 0 ? gap : Math.round(r.intervalSec * 0.6 + gap * 0.4);
                }
                if (r.lastSeen == 0 || (now - r.lastSeen) / 1000 > 30) {
                    r.lastSeen = now;
                    active.add(new Active(r, now));
                    Notifications.push("Ивент: " + r.name, 0xFFB7AAD9, 4000);
                    com.lume.client.Config.save();
                }
            }
        }
    }

    /** Expire finished events (call each tick). */
    public static void tick() {
        active.removeIf(a -> a.secondsLeft() <= 0);
    }
}

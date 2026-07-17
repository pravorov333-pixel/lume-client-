package com.lume.client.fthw;

import com.lume.client.gui.Notifications;
import com.lume.client.gui.Theme;

import java.util.HashSet;
import java.util.Set;

/**
 * Watches {@link TelegramEvents} for newly-active events (any anarchy) and
 * pops a toast for the ones NOT on the anarchy we're currently on — events on
 * OUR anarchy are shown persistently instead (see HudRenderer.renderServerHelper),
 * so they'd be redundant as a toast too. Call {@link #tick()} once a frame;
 * it's cheap (just a set diff) when nothing changed.
 */
public final class TelegramEventNotifier {
    private TelegramEventNotifier() {}

    private static Set<String> lastActive = new HashSet<>();

    public static void tick() {
        if (!TelegramEvents.available()) return;
        String current = CurrentAnarchy.get();
        Set<String> nowActive = new HashSet<>();
        for (TelegramEvents.Ev e : TelegramEvents.events()) {
            if (e.phase.isEmpty() || e.phase.toLowerCase().contains("ожидан")) continue; // only fire for actually-active events
            String key = e.anarchy + "|" + e.name;
            nowActive.add(key);
            if (!lastActive.contains(key) && !e.anarchy.equals(current)) {
                Notifications.push("/an" + e.anarchy + ": " + e.name
                        + (e.rarity.isEmpty() ? "" : " (" + e.rarity + ")"), Theme.accentRgb(), 5000);
            }
        }
        lastActive = nowActive;
    }
}

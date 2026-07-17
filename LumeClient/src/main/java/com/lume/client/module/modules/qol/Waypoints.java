package com.lume.client.module.modules.qol;

import com.lume.client.fthw.CurrentAnarchy;
import com.lume.client.fthw.TelegramEvents;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Waypoints — saved location markers, drawn as 2D HUD markers (name + distance,
 * plus an edge arrow when off-screen). Add via the "Add Waypoint" key (B) or the
 * {@code .wp} chat commands; a "Death" marker is saved automatically on death.
 * Persisted in the config.
 *
 * <p>{@link #list} holds EVERY waypoint ever saved, across every server/world —
 * each one is stamped with the server it was added on (and, if it was FunTime,
 * the specific anarchy number) so waypoints from one server/anarchy don't leak
 * into another (e.g. a singleplayer waypoint must never show up on FunTime).
 * Anything that shows or edits waypoints (HUD, the GUI manager, the {@code .wp}
 * command) should go through {@link #visible()}, never {@link #list} directly,
 * so it only ever sees the current server/anarchy's own set. Matching is
 * strict — {@link Config} defaults any waypoint saved before this scoping
 * existed to "singleplayer" on load rather than leaving it unscoped, so it
 * can't silently leak onto every server either.
 *
 * <p>Rendering lives in {@code HudRenderer.renderWaypoints}.
 */
public class Waypoints extends Module {

    public static final class WP {
        public String name;
        public final double x, y, z;
        public int color;
        /** Server address (lowercased) this was saved on, or "singleplayer". Never null — see {@link Config}. */
        public final String server;
        /** FunTime anarchy number this was saved on, or null (not on a specific anarchy). */
        public final String anarchy;
        /** True for waypoints auto-placed on a Telegram event (see EventLocator) — pruned once that event's gone. */
        public boolean eventLinked;

        public WP(String name, double x, double y, double z, int color, String server, String anarchy) {
            this(name, x, y, z, color, server, anarchy, false);
        }

        public WP(String name, double x, double y, double z, int color, String server, String anarchy, boolean eventLinked) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.color = color;
            this.server = server; this.anarchy = anarchy; this.eventLinked = eventLinked;
        }
    }

    /** Every waypoint, every server/anarchy. Use {@link #visible()} for display/editing. */
    public static final List<WP> list = new ArrayList<>();

    /** Palette cycled through for auto-coloured waypoints. */
    private static final int[] PALETTE = {
            0xFFB7AAD9, 0xFF6FCF7F, 0xFFE8C15A, 0xFF6F9CE0, 0xFFE0789C, 0xFF63D6C4, 0xFFE0915A
    };
    private static int paletteIdx = 0;

    public final BoolSetting deathPoint = add(new BoolSetting("Death point", true));
    public final BoolSetting arrows = add(new BoolSetting("Edge arrows", true));
    public final ColorSetting color = add(new ColorSetting("Arrow Color", true, 183, 170, 217));
    public final SliderSetting size = add(new SliderSetting("Size", 1.0, 0.5, 2.0, false));

    public Waypoints() {
        super("Waypoints", "Saved location markers", Category.CHAT, -1);
    }

    public static int nextColor() {
        return PALETTE[paletteIdx++ % PALETTE.length];
    }

    /** Server address (lowercased) of whatever we're connected to right now, or "singleplayer". */
    public static String currentServerKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo s = mc.getCurrentServerEntry();
        return s != null && s.address != null ? s.address.toLowerCase(Locale.ROOT) : "singleplayer";
    }

    /** Stamps the new waypoint with the server/anarchy you're on right now. */
    public static void add(String name, double x, double y, double z, int color) {
        list.add(new WP(name, x, y, z, color, currentServerKey(), CurrentAnarchy.get()));
    }

    /** Same as {@link #add}, but flagged so {@link #pruneExpiredEvents()} removes it once the event's gone. */
    public static void addEventLinked(String name, double x, double y, double z, int color) {
        list.add(new WP(name, x, y, z, color, currentServerKey(), CurrentAnarchy.get(), true));
    }

    /** Removes any existing same-name waypoint in the current scope, then adds the new one in its place. */
    public static void replace(String name, double x, double y, double z, int color) {
        for (WP w : visible()) {
            if (w.name.equalsIgnoreCase(name)) { list.remove(w); break; }
        }
        add(name, x, y, z, color);
    }

    private static long lastPrune = 0;

    /**
     * Drops any event-linked waypoint whose event is no longer in the Telegram
     * feed for its anarchy (event ended/expired) — only runs while the feed
     * actually has data, so a temporary fetch hiccup can't wipe everything.
     * Self-throttled (call freely, e.g. every tick).
     */
    public static void pruneExpiredEvents() {
        long now = System.currentTimeMillis();
        if (now - lastPrune < 3000 || !TelegramEvents.available()) return;
        lastPrune = now;
        list.removeIf(w -> w.eventLinked && !hasLiveEvent(w));
    }

    private static boolean hasLiveEvent(WP w) {
        for (TelegramEvents.Ev e : TelegramEvents.events()) {
            if (Objects.equals(w.anarchy, e.anarchy) && e.name.equalsIgnoreCase(w.name)) return true;
        }
        return false;
    }

    /**
     * Waypoints that belong here: same server, and the same anarchy number
     * (including "neither is on one", e.g. both saved in the hub/lobby). A
     * waypoint saved on anarchy 5 stays hidden on anarchy 7 of the same
     * server, even though the server matches — and a singleplayer waypoint
     * never matches any multiplayer server. Matching is strict, no wildcard.
     */
    public static List<WP> visible() {
        String server = currentServerKey();
        String anarchy = CurrentAnarchy.get();
        List<WP> out = new ArrayList<>();
        for (WP w : list) {
            if (!Objects.equals(w.server, server)) continue;
            if (!Objects.equals(w.anarchy, anarchy)) continue;
            out.add(w);
        }
        return out;
    }
}

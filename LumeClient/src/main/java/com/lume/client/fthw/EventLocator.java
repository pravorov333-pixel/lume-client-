package com.lume.client.fthw;

import com.lume.client.gui.Notifications;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.qol.Waypoints;
import net.minecraft.client.MinecraftClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-places a waypoint on a FunTime event the moment we can learn its
 * coordinates, from two sources:
 *
 * <p><b>1) The server's own spawn broadcast — the real, reliable path,
 * confirmed from an actual log (2026-07):</b>
 * <pre>
 *  ╔═════« Загадочный маяк »═════╗
 *  ║ Призван игроком balbesikdebilik
 *  ║ на координатах [-1058 124 2331]
 *  ╚══════════════════════╝
 * </pre>
 * Every player on the anarchy gets this in normal chat the instant ANY event
 * spawns (found this parsing a real log — the "/event delay" pipeline below
 * had never actually fired, this broadcast is what was in the log instead).
 * No command needed, no timing window — {@link #tryBroadcast} runs on EVERY
 * chat line, always. The name is inside {@code «...»}, coordinates after
 * "координатах". Text may contain literal two-character {@code \n} sequences
 * from the server's own template rather than real newlines (seen in the log),
 * so this is matched as one flat string, not per-line.
 *
 * <p><b>2) The reply to a "/event ..." command</b> (e.g. {@code /event delay},
 * sent from Quick Commands — see {@link QuickCommands#send}), as a manual
 * fallback/refresh — kept in case a server or event type doesn't broadcast.
 * Confirmed real reply shape:
 * <pre>
 * [Ивенты]
 * [1] До следующего ивента: 1 мин 36 сек
 * [2] Аир-дроп:
 * ‖ Статус: » Лутание (75 сек)
 * ‖ Координаты: [-2213 100 -1165]
 * </pre>
 * Only scanned for a short window right after such a command is sent (see
 * {@link #arm()}), instead of watching all chat forever for it — keeps this
 * half from ever mis-firing on unrelated numbers in normal conversation
 * (the broadcast half above is specific enough to always be on).
 */
public final class EventLocator {
    private EventLocator() {}

    /**
     * "« Загадочный маяк »" — event name inside guillemets, anywhere in the
     * line. This is NOT restricted to specific event names — Маяк, Аирдроп,
     * Алтарь нежити, Вулкан etc. all use the same "server broadcasts spawn"
     * template, so whatever name appears here just gets used as-is.
     */
    private static final Pattern BROADCAST_NAME = Pattern.compile("«\\s*(.+?)\\s*»");
    /** "координатах [-1058 124 2331]" — "координат\w*" so it also matches "координаты"/"координата" etc, in case a different event's grammar differs. */
    private static final Pattern BROADCAST_COORDS = Pattern.compile(
            "координат\\w*\\s*\\[\\s*(-?\\d+)[,;\\s]+(-?\\d+)[,;\\s]+(-?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);

    /** Always-on: the server telling everyone an event just spawned, name + exact coords included. */
    private static boolean tryBroadcast(String line) {
        Matcher cm = BROADCAST_COORDS.matcher(line);
        if (!cm.find()) return false;
        Matcher nm = BROADCAST_NAME.matcher(line);
        String name = nm.find() ? nm.group(1).trim() : currentEventName();
        int x = Integer.parseInt(cm.group(1)), y = Integer.parseInt(cm.group(2)), z = Integer.parseInt(cm.group(3));
        System.out.println("[Lume][EventLocator] broadcast caught: \"" + name + "\" at " + x + " " + y + " " + z);
        placeOrUpdate(name, x, y, z);
        return true;
    }

    private static long armedUntil = 0;
    private static final long WINDOW_MS = 6000;
    private static String pendingName = null;

    private static String lastAnarchy = null;
    private static int pendingTicks = -1;
    private static final int JOIN_DELAY_TICKS = 40;   // ~2s after landing on a (new) anarchy, so chat/world are settled

    /**
     * Call once per client tick. Auto-fires the same lookup as the "Координаты
     * ивента" quick command the moment you're detected on a NEW anarchy — no
     * button press needed. Re-arms whenever the anarchy number changes (including
     * leaving and coming back), so it also covers "appeared in the world" via
     * the first tick {@link CurrentAnarchy} can actually read a number.
     */
    public static void tick() {
        String cur = CurrentAnarchy.get();
        if (cur != null && !cur.equals(lastAnarchy)) {
            lastAnarchy = cur;
            pendingTicks = JOIN_DELAY_TICKS;
        } else if (cur == null) {
            lastAnarchy = null;
            pendingTicks = -1;
        }
        if (pendingTicks > 0 && --pendingTicks == 0) sendLookup();
    }

    private static void sendLookup() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        System.out.println("[Lume][EventLocator] auto-sending /event delay on anarchy " + lastAnarchy);
        mc.getNetworkHandler().sendChatCommand("event delay");
        arm();
    }

    /**
     * "[2] Аир-дроп:" — a numbered line ending in ": <event name>:". MULTILINE
     * so this still matches if the whole reply arrives as ONE chat message with
     * embedded newlines rather than 5 separate messages (untested which — this
     * makes {@code $} match end-of-LINE either way, not just end-of-string).
     */
    private static final Pattern NAME_LINE = Pattern.compile("\\[\\d+]\\s*(.+?):\\s*$", Pattern.MULTILINE);
    /** Labelled "X: 123, Y: 64, Z: -456" style (any of X/Х, case-insensitive, comma or space separated). */
    private static final Pattern LABELLED = Pattern.compile(
            "[xх]\\s*[:=]\\s*(-?\\d+)[,;\\s]+[yу]\\s*[:=]\\s*(-?\\d+)[,;\\s]+[zз]\\s*[:=]\\s*(-?\\d+)",
            Pattern.CASE_INSENSITIVE);
    /** "[-2213 100 -1165]" (real FunTime format) — also matches comma-separated or parenthesised variants. */
    private static final Pattern BRACKETED = Pattern.compile("[\\[(]\\s*(-?\\d+)[,;\\s]+(-?\\d+)[,;\\s]+(-?\\d+)\\s*[\\])]");
    /** Bare "123 64 -456" — only trusted while armed, right after we sent the command ourselves. */
    private static final Pattern BARE = Pattern.compile("(?<!\\d)(-?\\d{1,6})[,;\\s]+(-?\\d{1,6})[,;\\s]+(-?\\d{1,6})(?!\\d)");

    /** Call when a "/event ..." command is sent — opens the capture window. */
    public static void arm() {
        armedUntil = System.currentTimeMillis() + WINDOW_MS;
        pendingName = null;
        System.out.println("[Lume][EventLocator] armed, listening to chat for " + WINDOW_MS + "ms");
    }

    /** Feed every chat/overlay line through here (both channels — the reply's channel isn't known either). */
    public static void onChat(String line) {
        if (line == null || line.isEmpty()) return;
        if (tryBroadcast(line)) return;   // always-on, doesn't need arm()

        if (System.currentTimeMillis() > armedUntil) return;
        System.out.println("[Lume][EventLocator] armed, scanning line: " + line);

        Matcher nm = NAME_LINE.matcher(line.trim());
        if (nm.find()) {
            pendingName = nm.group(1).trim();   // e.g. "[2] Аир-дроп:" — remember, coords follow on a later line
            System.out.println("[Lume][EventLocator] captured pending name: " + pendingName);
            return;
        }

        int[] xyz = extract(line);
        if (xyz == null) return;
        armedUntil = 0;   // one shot per arm — stop scanning once we've caught a candidate line

        String name = pendingName != null ? pendingName : currentEventName();
        System.out.println("[Lume][EventLocator] captured coords " + xyz[0] + " " + xyz[1] + " " + xyz[2] + " for \"" + name + "\"");
        placeOrUpdate(name, xyz[0], xyz[1], xyz[2]);
    }

    private static int[] extract(String line) {
        Matcher m = LABELLED.matcher(line);
        if (m.find()) return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) };
        m = BRACKETED.matcher(line);
        if (m.find()) return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) };
        m = BARE.matcher(line);
        if (m.find()) return new int[]{ Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) };
        return null;
    }

    /** Fallback when no "[N] Name:" line was seen: the soonest Telegram event on the anarchy you're on, or a generic name. */
    private static String currentEventName() {
        String current = CurrentAnarchy.get();
        if (current != null && TelegramEvents.available()) {
            for (TelegramEvents.Ev e : TelegramEvents.events()) if (e.anarchy.equals(current)) return e.name;
        }
        return "Event";
    }

    /**
     * Re-uses an existing same-name waypoint in this scope (moves it) instead
     * of piling up duplicates on repeat sightings. Flagged event-linked so
     * {@link Waypoints#pruneExpiredEvents()} removes it once the event itself
     * vanishes from the Telegram feed.
     */
    private static void placeOrUpdate(String name, double x, double y, double z) {
        for (Waypoints.WP w : Waypoints.visible()) {
            if (w.name.equalsIgnoreCase(name)) {
                Waypoints.list.remove(w);
                break;
            }
        }
        Waypoints.addEventLinked(name, x, y, z, Theme.accent());
        com.lume.client.Config.save();
        Notifications.push("Точка ивента: " + name, Theme.accent(), 2500);
    }
}

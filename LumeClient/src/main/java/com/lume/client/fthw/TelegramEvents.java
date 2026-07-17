package com.lume.client.fthw;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the all-anarchy FunTime event feed from the Lume events backend over HTTP.
 * The backend (see /tg-server) reads @FunTimeEventRobot through ONE Telegram account
 * (the owner's) and serves the events as JSON, so the client never logs into Telegram
 * — it just fetches {@link #EVENTS_URL}. Fetched off-thread, throttled.
 */
public final class TelegramEvents {

    /** Owner's events server (see /tg-server), deployed on Railway. */
    private static final String EVENTS_URL = "https://lume-tg-events-production.up.railway.app/events";

    private static final Pattern TIME_PATTERN = Pattern.compile("(?:(\\d+)\\s*м)?\\s*(?:(\\d+)\\s*с)?");

    public static final class Ev {
        public final String anarchy, name, time, phase, rarity;
        /** Parsed from `time` ("24м 25с" -> 1465) at the moment the backend scraped it, or -1 if unparseable. */
        private final int totalSeconds;

        public Ev(String a, String n, String t, String p, String r) {
            anarchy = a; name = n; time = t; phase = p; rarity = r;
            totalSeconds = parseSeconds(t);
        }

        private static int parseSeconds(String t) {
            if (t == null || t.isEmpty()) return -1;
            Matcher m = TIME_PATTERN.matcher(t);
            if (!m.find() || (m.group(1) == null && m.group(2) == null)) return -1;
            int min = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
            int sec = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            return min * 60 + sec;
        }

        /** True if this event is currently running (not waiting to spawn/open/activate). */
        public boolean isActive() {
            String p = phase.toLowerCase();
            return !p.isEmpty() && !p.contains("ожидан") && !p.contains("голосован") && !p.contains("активир");
        }

        public boolean isVoting() {
            return phase.toLowerCase().contains("голосован");
        }

        /** True while waiting to spawn/open/activate (the opposite of {@link #isActive()}). */
        public boolean isWaiting() {
            String p = phase.toLowerCase();
            return p.contains("ожидан") || p.contains("активир");
        }

        /** True when the waiting phase text specifically talks about opening/activating (vs. spawning). */
        public boolean isOpening() {
            String p = phase.toLowerCase();
            return p.contains("открыт") || p.contains("активир");   // "откроется"/"активируется через" = same "opens in" meaning
        }

        /** Volcano gets its own verb ("извержение", not "откроется") — see {@link #statusText()}. */
        public boolean isVolcano() {
            return name.toLowerCase().contains("вулкан");
        }

        /** True once our live "opens in" countdown has run out — a client-side heuristic for
         *  "it should be open/erupting right now", ahead of the ~45s backend poll that would
         *  otherwise be needed to confirm the phase actually flipped. See {@link #statusText()}. */
        public boolean justOpened() {
            return isOpening() && totalSeconds >= 0 && liveSecondsLeft() <= 0;
        }

        /** Ticks down continuously client-side instead of jumping once per ~45s backend refresh. */
        public int liveSecondsLeft() {
            if (totalSeconds < 0) return -1;
            long elapsed = (System.currentTimeMillis() - updated) / 1000;
            return (int) Math.max(0, totalSeconds - elapsed);
        }

        /** "24:05" style, or the original raw string if it couldn't be parsed as a duration. */
        public String liveTimeText() {
            int s = liveSecondsLeft();
            if (s < 0) return time;
            return (s / 60) + "м " + (s % 60) + "с";
        }

        /**
         * Phase-aware Russian status line, replacing the old bare "Активно"/generic
         * label that gave no sense of "will it appear soon, or open soon, or is it
         * already running". {@link #liveSecondsLeft()} is a client-side countdown
         * clamped at 0 between backend polls (every ~45s) — once it hits 0 it just
         * means "our local estimate ran out", not necessarily that the server-side
         * phase already flipped, so that edge is worded as "already about to
         * happen" rather than a flat "0с" that reads as a hard fact.
         */
        public String statusText() {
            int s = liveSecondsLeft();
            if (isVoting()) return s > 0 ? "Голосование · " + liveTimeText() : "Голосование";
            if (isActive()) {
                String base = phase.isEmpty() ? "Активно" : phase;
                return s > 0 ? base + " · осталось " + liveTimeText() : base;
            }
            // waiting: distinguish "will appear" (spawn) from "will open"/"activate" once spawned —
            // always this fixed wording, never the bot's raw phase text (e.g. "активируется через").
            // Volcano is thematic: it "erupts", it doesn't "open".
            if (justOpened()) return isVolcano() ? "Извергается" : "Открыт";
            String verb = isOpening() ? (isVolcano() ? "Извержение" : "Откроется") : "Появится";
            if (s > 0) return verb + " через " + liveTimeText();
            if (totalSeconds >= 0) return "Появляется…";
            return verb;
        }
    }

    private static volatile List<Ev> list = Collections.emptyList();
    private static volatile long updated = 0;
    private static volatile boolean fetching = false;
    private static long lastFetch = 0;

    private TelegramEvents() {}

    public static List<Ev> events() { return list; }
    public static boolean available() { return !list.isEmpty(); }
    public static long ageSec() { return updated == 0 ? -1 : (System.currentTimeMillis() - updated) / 1000; }

    /** Kick an off-thread fetch (throttled to ~5s). Safe to call every frame. */
    public static void load() {
        long now = System.currentTimeMillis();
        if (fetching || now - lastFetch < 5000) return;
        lastFetch = now;
        fetching = true;
        Thread t = new Thread(() -> {
            try {
                HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(EVENTS_URL)).timeout(Duration.ofSeconds(4)).GET().build();
                HttpResponse<String> resp = c.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) parse(resp.body());
            } catch (Exception ignored) {
                // server offline / unreachable → keep last data
            } finally {
                fetching = false;
            }
        }, "Lume-TGEvents");
        t.setDaemon(true);
        t.start();
    }

    private static void parse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            long upd = root.has("updated") ? root.get("updated").getAsLong() : System.currentTimeMillis();
            List<Ev> fresh = new ArrayList<>();
            if (root.has("events")) {
                JsonArray arr = root.getAsJsonArray("events");
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    fresh.add(new Ev(s(o, "anarchy"), s(o, "name"), s(o, "time"), s(o, "phase"), s(o, "rarity")));
                }
            }
            list = fresh;       // atomic reference swap (never mutate the live list)
            updated = upd;
        } catch (Exception ignored) {}
    }

    private static String s(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsString() : ""; } catch (Exception e) { return ""; }
    }
}

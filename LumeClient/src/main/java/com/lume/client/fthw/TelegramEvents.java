package com.lume.client.fthw;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the FunTime events that the launcher scraped from the @FunTimeEventRobot
 * Telegram Mini App into {@code %APPDATA%/.lumeclient/events.json}. This gives the
 * Events tab data for ALL anarchies at once (not just the one you're on), since the
 * Telegram bot aggregates every anarchy. Re-read on a short throttle.
 */
public final class TelegramEvents {

    public static final class Ev {
        public final String anarchy, name, time, phase, rarity;
        public Ev(String a, String n, String t, String p, String r) { anarchy = a; name = n; time = t; phase = p; rarity = r; }
    }

    public static final List<Ev> list = new ArrayList<>();
    public static long updated = 0;
    private static long lastLoad = 0;

    private TelegramEvents() {}

    public static boolean available() { return !list.isEmpty(); }

    /** Re-read events.json (throttled to ~3s). Safe to call every frame. */
    public static void load() {
        long now = System.currentTimeMillis();
        if (now - lastLoad < 3000) return;
        lastLoad = now;
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null) return;
            Path f = Path.of(appData, ".lumeclient", "events.json");
            if (!Files.exists(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            updated = root.has("updated") ? root.get("updated").getAsLong() : 0;
            List<Ev> fresh = new ArrayList<>();
            if (root.has("events")) {
                JsonArray arr = root.getAsJsonArray("events");
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    fresh.add(new Ev(s(o, "anarchy"), s(o, "name"), s(o, "time"), s(o, "phase"), s(o, "rarity")));
                }
            }
            list.clear();
            list.addAll(fresh);
        } catch (Exception ignored) {}
    }

    /** Seconds since the launcher last refreshed the feed, or -1. */
    public static long ageSec() {
        return updated == 0 ? -1 : (System.currentTimeMillis() - updated) / 1000;
    }

    private static String s(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsString() : ""; } catch (Exception e) { return ""; }
    }
}

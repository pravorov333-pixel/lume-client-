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

/**
 * Pulls the all-anarchy FunTime event feed from the Lume events backend over HTTP.
 * The backend (see /tg-server) reads @FunTimeEventRobot through ONE Telegram account
 * (the owner's) and serves the events as JSON, so the client never logs into Telegram
 * — it just fetches {@link #EVENTS_URL}. Fetched off-thread, throttled.
 */
public final class TelegramEvents {

    /** Owner's events server. Point this at the hosted backend (default = local server). */
    private static final String EVENTS_URL = "http://localhost:8077/events";

    public static final class Ev {
        public final String anarchy, name, time, phase, rarity;
        public Ev(String a, String n, String t, String p, String r) { anarchy = a; name = n; time = t; phase = p; rarity = r; }
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

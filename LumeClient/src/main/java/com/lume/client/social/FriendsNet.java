package com.lume.client.social;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * Talks to the Lume Friends backend (see /LumeFriendsServer) — presence
 * heartbeat, friend requests, and shared cross-server points. Fire-and-forget,
 * off-thread (mirrors {@code fthw.TelegramEvents}'s HttpClient pattern); every
 * call is wrapped so a offline/unreachable server just silently no-ops.
 */
public final class FriendsNet {
    private FriendsNet() {}

    /** Owner's friends/presence backend, hosted on Railway (see /LumeFriendsServer/README.md). */
    public static final String BASE_URL = "https://friends-lume-server-production.up.railway.app";

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final Gson GSON = new Gson();

    private static void async(Runnable r) {
        Thread t = new Thread(r, "Lume-Friends");
        t.setDaemon(true);
        t.start();
    }

    private static JsonObject postJson(String path, JsonObject body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static JsonElement getJson(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + path)).timeout(Duration.ofSeconds(4)).GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(resp.body());
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public static void heartbeat(String username, String deviceId, String server, String dim, double x, double y, double z) {
        async(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("username", username);
                o.addProperty("deviceId", deviceId);
                o.addProperty("server", server);
                o.addProperty("dimension", dim);
                o.addProperty("x", x); o.addProperty("y", y); o.addProperty("z", z);
                postJson("/api/heartbeat", o);
            } catch (Exception ignored) { /* backend offline — try again next tick */ }
        });
    }

    public static void refreshFriendList(String username, Consumer<JsonObject> onDone) {
        async(() -> {
            try { onDone.accept(getJson("/api/friend/list?username=" + enc(username)).getAsJsonObject()); }
            catch (Exception ignored) {}
        });
    }

    public static void refreshStatus(List<String> names, Consumer<JsonObject> onDone) {
        if (names.isEmpty()) return;
        async(() -> {
            try { onDone.accept(getJson("/api/status?names=" + enc(String.join(",", names))).getAsJsonObject()); }
            catch (Exception ignored) {}
        });
    }

    public static void refreshPoints(String username, Consumer<JsonArray> onDone) {
        async(() -> {
            try { onDone.accept(getJson("/api/point/list?username=" + enc(username)).getAsJsonArray()); }
            catch (Exception ignored) {}
        });
    }

    public static void sendRequest(String from, String to) {
        async(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("from", from); o.addProperty("to", to);
                postJson("/api/friend/request", o);
            } catch (Exception ignored) {}
        });
    }

    public static void accept(String username, String from) {
        async(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("username", username); o.addProperty("from", from);
                postJson("/api/friend/accept", o);
            } catch (Exception ignored) {}
        });
    }

    public static void decline(String username, String from) {
        async(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("username", username); o.addProperty("from", from);
                postJson("/api/friend/decline", o);
            } catch (Exception ignored) {}
        });
    }

    public static void remove(String username, String friend) {
        async(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("username", username); o.addProperty("friend", friend);
                postJson("/api/friend/remove", o);
            } catch (Exception ignored) {}
        });
    }

    public static void sharePoint(String from, String deviceId, String to, String name,
                                   double x, double y, double z, String server, String dim) {
        async(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("from", from); o.addProperty("deviceId", deviceId); o.addProperty("to", to);
                o.addProperty("name", name); o.addProperty("x", x); o.addProperty("y", y); o.addProperty("z", z);
                o.addProperty("server", server); o.addProperty("dimension", dim);
                postJson("/api/point/share", o);
            } catch (Exception ignored) {}
        });
    }

    public static void deletePoint(long id, String username, String deviceId) {
        async(() -> {
            try {
                JsonObject o = new JsonObject();
                o.addProperty("id", id); o.addProperty("username", username); o.addProperty("deviceId", deviceId);
                postJson("/api/point/delete", o);
            } catch (Exception ignored) {}
        });
    }
}

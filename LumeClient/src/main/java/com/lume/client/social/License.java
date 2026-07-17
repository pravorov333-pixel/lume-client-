package com.lume.client.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lume.client.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Subscription status for the account widget. The mod never asks for or stores a
 * license key itself — the LAUNCHER writes {@code {key, hwid}} into this profile's
 * {@code config/lume.json} under "license" at Play time (see LumeLauncher's
 * writeLicense), and this class just reads that and checks it against LumeKeyServer.
 * Mirrors {@link FriendsNet}'s fire-and-forget off-thread HTTP pattern.
 */
public final class License {
    private License() {}

    private static final String BASE_URL = "https://lume-key-server-production.up.railway.app";
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final long RECHECK_MS = 5 * 60_000L;

    private static long lastCheck = 0;
    private static volatile String plan = null;
    private static volatile Long expiresAt = null;   // epoch millis; null = no expiry (lifetime) or unknown
    /** "no_key" | "checking" | "valid" | "invalid" | "unreachable". */
    private static volatile String status = "no_key";

    public static String status() { return status; }
    public static String plan() { return plan; }

    /** Days remaining, or -1 if unknown/lifetime/unreachable/no key. */
    public static long daysRemaining() {
        if (expiresAt == null) return -1;
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 86_400_000L);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < RECHECK_MS) return;
        lastCheck = now;

        JsonObject lic = Config.getLicenseRaw();
        String key = lic != null && lic.has("key") && !lic.get("key").isJsonNull() ? lic.get("key").getAsString() : null;
        String hwid = lic != null && lic.has("hwid") && !lic.get("hwid").isJsonNull() ? lic.get("hwid").getAsString() : null;
        if (key == null || key.isBlank() || hwid == null || hwid.isBlank()) { status = "no_key"; return; }

        status = "checking";
        Thread t = new Thread(() -> checkNow(key, hwid), "Lume-License");
        t.setDaemon(true);
        t.start();
    }

    private static void checkNow(String key, String hwid) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("key", key);
            body.addProperty("hwid", hwid);
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + "/api/keys/validate"))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (o.has("valid") && o.get("valid").getAsBoolean()) {
                plan = o.has("plan") && !o.get("plan").isJsonNull() ? o.get("plan").getAsString() : null;
                expiresAt = o.has("expiresAt") && !o.get("expiresAt").isJsonNull() ? o.get("expiresAt").getAsLong() : null;
                status = "valid";
            } else {
                plan = null; expiresAt = null;
                status = "invalid";
            }
        } catch (Exception e) {
            status = "unreachable";
        }
    }
}

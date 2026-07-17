package com.lume.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/** Looks up a Mojang account's real skin by username, for the Fake Player module. Public Mojang APIs only. */
public final class SkinFetcher {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private record Fetched(NativeImage image, boolean slim) {}

    /** Fetches off-thread, then hands {@code (textureIdentifier, slim)} back on the client thread once registered. */
    public static void fetch(String username, BiConsumer<Identifier, Boolean> onDone) {
        CompletableFuture.supplyAsync(() -> fetchBlocking(username), Executors.newVirtualThreadPerTaskExecutor())
                .thenAccept(fetched -> {
                    if (fetched == null) return;
                    MinecraftClient.getInstance().execute(() -> {
                        NativeImageBackedTexture tex = new NativeImageBackedTexture(fetched.image());
                        Identifier id = Identifier.of("lume", "fake_player_skin/" + System.nanoTime());
                        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
                        onDone.accept(id, fetched.slim());
                    });
                });
    }

    private static Fetched fetchBlocking(String username) {
        try {
            HttpRequest lookupReq = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username)).build();
            HttpResponse<String> lookupRes = HTTP.send(lookupReq, HttpResponse.BodyHandlers.ofString());
            if (lookupRes.statusCode() != 200) return null;
            String uuid = JsonParser.parseString(lookupRes.body()).getAsJsonObject().get("id").getAsString();

            HttpRequest sessionReq = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)).build();
            HttpResponse<String> sessionRes = HTTP.send(sessionReq, HttpResponse.BodyHandlers.ofString());
            if (sessionRes.statusCode() != 200) return null;
            JsonObject session = JsonParser.parseString(sessionRes.body()).getAsJsonObject();
            String texturesB64 = session.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(texturesB64))).getAsJsonObject();
            JsonObject skinObj = textures.getAsJsonObject("textures").getAsJsonObject("SKIN");
            if (skinObj == null) return null;
            String url = skinObj.get("url").getAsString();
            boolean slim = skinObj.has("metadata") && "slim".equals(skinObj.getAsJsonObject("metadata").get("model").getAsString());

            HttpRequest imgReq = HttpRequest.newBuilder(URI.create(url)).build();
            HttpResponse<InputStream> imgRes = HTTP.send(imgReq, HttpResponse.BodyHandlers.ofInputStream());
            NativeImage img = NativeImage.read(imgRes.body());
            return new Fetched(img, slim);
        } catch (Exception e) {
            System.out.println("[Lume] Fake Player skin fetch failed for '" + username.toLowerCase(Locale.ROOT) + "': " + e);
            return null;
        }
    }

    private SkinFetcher() {}
}

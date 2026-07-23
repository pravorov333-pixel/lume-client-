package com.lume.client.util;

import com.lume.client.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Instant (no-restart) offline-nickname switch — swaps {@code MinecraftClient}'s own {@code
 *  Session} object in place via {@link MinecraftClientAccessor}. Takes effect for whatever
 *  connects NEXT (a new world, a new server) — it can't rename the player entity in a world
 *  that's already loaded/connected under the old identity, same as any offline-mode client. */
public final class AltService {
    private AltService() {}

    public static void changeName(String newUsername) {
        if (newUsername == null || newUsername.isBlank()) return;
        String name = newUsername.trim();
        // Same offline-UUID algorithm the launcher's own offline auth uses (OfflinePlayer:name).
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        Session newSession = new Session(name, offlineUuid, "", Optional.empty(), Optional.empty(), Session.AccountType.MOJANG);
        ((MinecraftClientAccessor) MinecraftClient.getInstance()).lume$setSession(newSession);
    }
}

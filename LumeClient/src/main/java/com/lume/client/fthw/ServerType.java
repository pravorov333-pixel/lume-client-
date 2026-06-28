package com.lume.client.fthw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.util.Locale;

/**
 * Which Russian anarchy server we're on — basis of the FT/HW helper. Detected
 * from the server address. Rules (item radii, event chat patterns) will be keyed
 * by this once the helper is fleshed out.
 */
public enum ServerType {
    FUNTIME, HOLYWORLD, UNKNOWN;

    public static ServerType current() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo s = mc.getCurrentServerEntry();
        if (s == null || s.address == null) return UNKNOWN;
        String a = s.address.toLowerCase(Locale.ROOT);
        if (a.contains("funtime")) return FUNTIME;
        if (a.contains("holyworld") || a.contains("holy")) return HOLYWORLD;
        return UNKNOWN;
    }

    public String display() {
        return switch (this) {
            case FUNTIME -> "FunTime";
            case HOLYWORLD -> "HolyWorld";
            default -> "Unknown";
        };
    }
}

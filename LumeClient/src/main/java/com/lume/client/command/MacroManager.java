package com.lume.client.command;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Key → chat-command/message macros. Bound keys are checked by KeyboardMixin;
 * a macro whose text starts with "/" is sent as a command, otherwise as chat.
 * Persisted in the config.
 */
public final class MacroManager {

    public static final class Macro {
        public final int key;       // GLFW key code
        public final String text;
        public Macro(int key, String text) { this.key = key; this.text = text; }
    }

    public static final List<Macro> macros = new ArrayList<>();

    private MacroManager() {}

    public static void add(int key, String text) {
        macros.removeIf(m -> m.key == key);   // one macro per key
        macros.add(new Macro(key, text));
    }

    public static boolean remove(int key) {
        return macros.removeIf(m -> m.key == key);
    }

    /** Called on key press (no screen open) — fires any macro bound to this key. */
    public static void onKey(int key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        for (Macro m : macros) {
            if (m.key != key) continue;
            if (m.text.startsWith("/")) mc.getNetworkHandler().sendChatCommand(m.text.substring(1));
            else mc.getNetworkHandler().sendChatMessage(m.text);
        }
    }
}

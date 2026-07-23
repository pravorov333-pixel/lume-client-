package com.lume.client.command;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Key → chat-command macros. COMMAND-ONLY — there is no "send as a plain chat message" option
 * (that used to be the no-leading-"/" branch; {@link #add} now normalises every macro to start
 * with "/" at creation time, so a raw message can never actually be bound, from any caller).
 * Bound keys are checked by KeyboardMixin. Persisted in the config.
 */
public final class MacroManager {

    public static final class Macro {
        public final int key;       // GLFW key code
        public final String text;   // always starts with "/" — see add()
        public Macro(int key, String text) { this.key = key; this.text = text; }
    }

    public static final List<Macro> macros = new ArrayList<>();

    private MacroManager() {}

    public static void add(int key, String text) {
        String cmd = text.startsWith("/") ? text : "/" + text;
        macros.removeIf(m -> m.key == key);   // one macro per key
        macros.add(new Macro(key, cmd));
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
            mc.getNetworkHandler().sendChatCommand(m.text.substring(1));
        }
    }
}

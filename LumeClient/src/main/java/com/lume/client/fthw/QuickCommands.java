package com.lume.client.fthw;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick FunTime/HolyWorld commands — one-tap (keybind) chat commands like /spawn,
 * /tpaccept, /ah, /sell. Each entry can carry a keybind (set in the Server tab);
 * pressing it sends the command. Also shown as a HUD bar. Keys persisted in config.
 */
public final class QuickCommands {

    public static final class Cmd {
        public final String label;
        public final String command;   // includes leading "/" for a command
        public int key = -1;
        public Cmd(String label, String command) { this.label = label; this.command = command; }
    }

    public static final List<Cmd> list = new ArrayList<>();

    static {
        list.add(new Cmd("Спавн", "/spawn"));
        list.add(new Cmd("Принять ТП", "/tpaccept"));
        list.add(new Cmd("Назад", "/back"));
        list.add(new Cmd("Аукцион", "/ah"));
        list.add(new Cmd("Продать", "/sell hand"));
        list.add(new Cmd("Дом", "/home"));
    }

    private QuickCommands() {}

    public static Cmd byLabel(String label) {
        for (Cmd c : list) if (c.label.equals(label)) return c;
        return null;
    }

    /** Fire any command bound to {@code key}. */
    public static void onKey(int key) {
        if (key < 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        for (Cmd c : list) {
            if (c.key != key) continue;
            send(c);
        }
    }

    public static void send(Cmd c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (c.command.startsWith("/")) mc.getNetworkHandler().sendChatCommand(c.command.substring(1));
        else mc.getNetworkHandler().sendChatMessage(c.command);
    }
}

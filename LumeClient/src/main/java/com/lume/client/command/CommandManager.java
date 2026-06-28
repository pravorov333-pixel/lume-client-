package com.lume.client.command;

import com.lume.client.LumeClient;
import com.lume.client.fthw.ServerType;
import com.lume.client.module.Module;
import com.lume.client.module.modules.qol.Waypoints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Tiny chat-command system. Messages starting with "." are handled locally
 * (and not sent to the server). Commands: .wp, .macro, .bind, .help.
 */
public final class CommandManager {

    public static final String PREFIX = ".";

    private CommandManager() {}

    /** @return true if the message was a Lume command (and should be cancelled). */
    public static boolean handle(String msg) {
        if (msg == null || !msg.startsWith(PREFIX)) return false;
        String[] a = msg.substring(PREFIX.length()).trim().split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) return true;
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "wp", "waypoint" -> wp(a);
                case "macro" -> macro(a, msg);
                case "bind" -> bind(a);
                case "ft", "hw" -> ft();
                case "help", "lume" -> help();
                default -> msg("Unknown command §7" + a[0] + "§r — try §d.help");
            }
        } catch (Exception e) {
            msg("§cError: " + e.getMessage());
        }
        return true;
    }

    // --- .wp -----------------------------------------------------------------

    private static void wp(String[] a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "here";
        switch (sub) {
            case "here" -> {
                if (mc.player == null) { msg("Not in world."); return; }
                String name = a.length > 2 ? join(a, 2) : "WP" + (Waypoints.list.size() + 1);
                Waypoints.add(name, mc.player.getX(), mc.player.getY(), mc.player.getZ(), Waypoints.nextColor());
                msg("Added §d" + name + "§r at your position.");
                save();
            }
            case "add" -> {
                if (a.length < 5) { msg("Usage: §d.wp add <x> <y> <z> [name] [hexColor]"); return; }
                double x = Double.parseDouble(a[2]), y = Double.parseDouble(a[3]), z = Double.parseDouble(a[4]);
                String name = a.length > 5 ? a[5] : "WP" + (Waypoints.list.size() + 1);
                int color = a.length > 6 ? parseColor(a[6]) : Waypoints.nextColor();
                Waypoints.add(name, x, y, z, color);
                msg("Added §d" + name + "§r at " + (int) x + " " + (int) y + " " + (int) z + ".");
                save();
            }
            case "color" -> {
                if (a.length < 4) { msg("Usage: §d.wp color <index> <hex/name>"); return; }
                int i = Integer.parseInt(a[2]);
                if (i < 0 || i >= Waypoints.list.size()) { msg("Bad index."); return; }
                Waypoints.list.get(i).color = parseColor(a[3]);
                msg("Recoloured §d" + Waypoints.list.get(i).name + "§r.");
                save();
            }
            case "del", "remove" -> {
                int i = Integer.parseInt(a[2]);
                if (i < 0 || i >= Waypoints.list.size()) { msg("Bad index."); return; }
                msg("Removed §d" + Waypoints.list.remove(i).name + "§r.");
                save();
            }
            case "clear" -> { Waypoints.list.clear(); msg("Cleared all waypoints."); save(); }
            case "list" -> {
                if (Waypoints.list.isEmpty()) { msg("No waypoints."); return; }
                for (int i = 0; i < Waypoints.list.size(); i++) {
                    Waypoints.WP w = Waypoints.list.get(i);
                    msg("§7[" + i + "]§r §d" + w.name + "§r  " + (int) w.x + " " + (int) w.y + " " + (int) w.z);
                }
            }
            default -> msg("§d.wp§r here|add|color|del|clear|list");
        }
    }

    // --- .macro --------------------------------------------------------------

    private static void macro(String[] a, String full) {
        if (a.length < 2) { msg("§d.macro§r add <key> <text…> | del <key> | list"); return; }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (a.length < 4) { msg("Usage: §d.macro add <key> <text/command>"); return; }
                int key = keyFromName(a[2]);
                if (key < 0) { msg("Unknown key §7" + a[2]); return; }
                // text = everything after the key token (preserve spaces)
                String text = full.substring(full.indexOf(a[2]) + a[2].length()).trim();
                MacroManager.add(key, text);
                msg("Macro on §d" + keyName(key) + "§r → " + text);
                save();
            }
            case "del", "remove" -> {
                int key = keyFromName(a[2]);
                msg(MacroManager.remove(key) ? "Removed macro on §d" + keyName(key) : "No macro on that key.");
                save();
            }
            case "list" -> {
                if (MacroManager.macros.isEmpty()) { msg("No macros."); return; }
                for (MacroManager.Macro m : MacroManager.macros) msg("§d" + keyName(m.key) + "§r → " + m.text);
            }
            default -> msg("§d.macro§r add|del|list");
        }
    }

    // --- .bind ---------------------------------------------------------------

    private static void bind(String[] a) {
        if (a.length < 3) { msg("Usage: §d.bind <module> <key|none>"); return; }
        String keyTok = a[a.length - 1];
        String name = join(a, 1).substring(0, join(a, 1).length() - keyTok.length()).trim();
        Module m = LumeClient.MODULES.getByName(name);
        if (m == null) { msg("No module §7" + name); return; }
        if (keyTok.equalsIgnoreCase("none")) { m.setKey(-1); msg("Unbound §d" + m.getName()); save(); return; }
        int key = keyFromName(keyTok);
        if (key < 0) { msg("Unknown key §7" + keyTok); return; }
        m.setKey(key);
        msg("Bound §d" + m.getName() + "§r to §d" + keyName(key));
        save();
    }

    private static void ft() {
        msg("Detected server: §d" + ServerType.current().display());
        msg("FT/HW helper is a scaffold — event timers & effect radii coming.");
    }

    private static void help() {
        msg("§dLume commands:");
        msg("§d.wp§r here|add <x y z>|color|del|clear|list");
        msg("§d.macro§r add <key> <text>|del|list");
        msg("§d.bind§r <module> <key|none>");
        msg("§d.ft§r — server helper status");
    }

    // --- helpers -------------------------------------------------------------

    private static String join(String[] a, int from) {
        StringBuilder s = new StringBuilder();
        for (int i = from; i < a.length; i++) { if (i > from) s.append(' '); s.append(a[i]); }
        return s.toString();
    }

    private static int parseColor(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "red": return 0xFFE05656;
            case "green": return 0xFF6FCF7F;
            case "blue": return 0xFF6F9CE0;
            case "yellow": return 0xFFE8C15A;
            case "lavender", "accent": return 0xFFB7AAD9;
            case "pink": return 0xFFE0789C;
            case "cyan": return 0xFF63D6C4;
            case "white": return 0xFFFFFFFF;
            default:
                String hex = s.startsWith("#") ? s.substring(1) : s;
                return 0xFF000000 | (int) (Long.parseLong(hex, 16) & 0xFFFFFF);
        }
    }

    public static int keyFromName(String s) {
        s = s.toUpperCase(Locale.ROOT);
        if (s.length() == 1) {
            char c = s.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }
        return switch (s) {
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "LSHIFT", "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "LCTRL", "CTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "F1" -> GLFW.GLFW_KEY_F1; case "F2" -> GLFW.GLFW_KEY_F2; case "F3" -> GLFW.GLFW_KEY_F3;
            case "F4" -> GLFW.GLFW_KEY_F4; case "F5" -> GLFW.GLFW_KEY_F5; case "F6" -> GLFW.GLFW_KEY_F6;
            case "F7" -> GLFW.GLFW_KEY_F7; case "F8" -> GLFW.GLFW_KEY_F8;
            default -> -1;
        };
    }

    public static String keyName(int code) {
        if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
        if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
        if (code == GLFW.GLFW_KEY_SPACE) return "SPACE";
        if (code == GLFW.GLFW_KEY_LEFT_SHIFT) return "SHIFT";
        if (code == GLFW.GLFW_KEY_LEFT_CONTROL) return "CTRL";
        if (code == -1) return "NONE";
        return "KEY" + code;
    }

    public static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.inGameHud.getChatHud().addMessage(
                    Text.literal("[Lume] ").formatted(Formatting.LIGHT_PURPLE).append(Text.literal(s).formatted(Formatting.GRAY)));
        }
    }

    private static void save() {
        com.lume.client.Config.save();
    }
}

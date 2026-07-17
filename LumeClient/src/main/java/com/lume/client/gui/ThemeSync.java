package com.lume.client.gui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the SAME {@code theme.json} the LumeLauncher's "Customize Colors" screen writes
 * (see LumeLauncher/src/main.js get-theme/set-theme), so the in-game ClickGUI follows
 * whatever background/accent/style was picked in the launcher — one shared file, no
 * separate in-game colour settings to keep in sync by hand.
 *
 * <p>Location: the launcher runs the game with its working directory set to
 * {@code <appdata>/.lumeclient/profiles/<version>}, i.e. two levels below the shared
 * {@code .lumeclient} root — {@code theme.json} lives right there, next to {@code profiles/}.
 */
public final class ThemeSync {

    private static long lastCheck = 0;
    private static long lastLoadedMtime = -1;

    private ThemeSync() {}

    private static Path themeFile() {
        Path gameDir = FabricLoader.getInstance().getGameDir();           // .../.lumeclient/profiles/<version>
        Path root = gameDir.getParent() != null ? gameDir.getParent().getParent() : null; // .../.lumeclient
        return root != null ? root.resolve("theme.json") : gameDir.resolve("theme.json");
    }

    /** Cheap — call every frame/tick if convenient; only actually re-reads the file at
     *  most every few seconds, and only re-parses it if its mtime actually changed. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 3000) return;
        lastCheck = now;
        load();
    }

    public static void load() {
        try {
            Path file = themeFile();
            if (!Files.exists(file)) return;
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (mtime == lastLoadedMtime) return;
            lastLoadedMtime = mtime;

            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            boolean dark = "dark".equals(str(root, "mode", "light"));
            Theme.setDark(dark);
            Theme.setGlassStyle(styleIndex(str(root, "style", "default")));
            if (root.has("glassBlur")) Theme.setGlassBlur(root.get("glassBlur").getAsFloat());
            if (root.has("glassDistort")) Theme.setGlassDistort(root.get("glassDistort").getAsFloat());

            applyColors(root, "light", false);
            applyColors(root, "dark", true);
        } catch (Exception ignored) {
            // malformed/missing theme.json -> keep whatever Theme.java already has
        }
    }

    /** Writes the CURRENT effective Theme state back out — called from ColorsScreen
     *  whenever a colour/mode/style changes in-game, same file the launcher reads/writes,
     *  same schema, so either side always sees the other's latest edit. */
    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("mode", Theme.isDark() ? "dark" : "light");
            root.addProperty("style", switch (Theme.getGlassStyle()) { case 1 -> "fullGlass"; case 2 -> "noGlass"; default -> "default"; });
            root.addProperty("glassBlur", Theme.getGlassBlur());
            root.addProperty("glassDistort", Theme.getGlassDistort());
            root.add("light", colorObj(false));
            root.add("dark", colorObj(true));

            Path file = themeFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            lastLoadedMtime = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception ignored) {
            // best-effort — a failed write just means the launcher won't see this edit
        }
    }

    private static JsonObject colorObj(boolean dark) {
        JsonObject o = new JsonObject();
        o.addProperty("bg", "#" + hexStr(Theme.getBg(dark)));
        o.addProperty("accent", "#" + hexStr(Theme.getAccent(dark)));
        o.addProperty("activeText", "#" + hexStr(Theme.getActiveText(dark)));
        return o;
    }

    private static String hexStr(int rgb) {
        return String.format("%06X", rgb & 0xFFFFFF);
    }

    private static void applyColors(JsonObject root, String key, boolean dark) {
        if (!root.has(key) || !root.get(key).isJsonObject()) return;
        JsonObject t = root.getAsJsonObject(key);
        Integer bg = hex(t, "bg");
        Integer accent = hex(t, "accent");
        Integer activeText = hex(t, "activeText");
        if (bg != null) Theme.setCustomBg(dark, bg);
        if (accent != null) Theme.setCustomAccent(dark, accent);
        if (activeText != null) Theme.setCustomActiveText(dark, activeText);
    }

    private static int styleIndex(String s) {
        return switch (s) {
            case "fullGlass" -> 1;
            case "noGlass" -> 2;
            default -> 0;
        };
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    /** "#rrggbb" -> 0xRRGGBB, or null if missing/malformed. */
    private static Integer hex(JsonObject o, String key) {
        if (!o.has(key)) return null;
        try {
            String s = o.get(key).getAsString();
            if (s.startsWith("#")) s = s.substring(1);
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return null;
        }
    }
}

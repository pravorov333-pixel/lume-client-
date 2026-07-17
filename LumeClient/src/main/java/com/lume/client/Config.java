package com.lume.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lume.client.command.MacroManager;
import com.lume.client.fthw.EventManager;
import com.lume.client.fthw.EventRule;
import com.lume.client.gui.ClickGuiScreen;
import com.lume.client.gui.HudLayout;
import com.lume.client.gui.Theme;
import com.lume.client.menu.FastConnect;
import com.lume.client.module.Module;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.Setting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.module.setting.StringSetting;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Saves/loads everything that should persist across restarts — module on/off,
 * all settings, theme, window position/scale, per-element HUD layout and saved
 * waypoints — to {@code config/lume.json}.
 */
public final class Config {

    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {key, hwid} the LAUNCHER writes at Play time — the mod only ever reads this (never
     *  invents/overwrites it), but must round-trip it through save()/load() so its own
     *  Config.save() calls don't wipe out what the launcher wrote. See social.License. */
    private static JsonObject licenseRaw = null;
    public static JsonObject getLicenseRaw() { return licenseRaw; }

    /** Saved offline nicknames (mod-side account list) + which one is "preferred" — the
     *  launcher can read this to pre-select a nickname on next login. Switching this does
     *  NOT change the identity of the already-running game session (not possible client-side). */
    public static final java.util.List<String> savedAccounts = new java.util.ArrayList<>();
    public static String preferredAccount = null;

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lume.json");
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("theme", Theme.isDark() ? "dark" : "light");
            root.addProperty("friendsDeviceId", com.lume.client.social.Friends.ensureDeviceId());

            JsonObject win = new JsonObject();
            win.addProperty("x", ClickGuiScreen.getWinOffX());
            win.addProperty("y", ClickGuiScreen.getWinOffY());
            win.addProperty("scale", ClickGuiScreen.getWinScale());
            root.add("window", win);

            JsonObject modules = new JsonObject();
            for (Module m : LumeClient.MODULES.getModules()) {
                JsonObject mo = new JsonObject();
                mo.addProperty("enabled", m.isEnabled());
                mo.addProperty("key", m.getKey());
                mo.addProperty("mode", m.getBindMode().name());
                if (m.hasSettings()) {
                    JsonObject so = new JsonObject();
                    for (Setting s : m.getSettings()) {
                        if (s instanceof BoolSetting b) so.addProperty(s.name, b.value);
                        else if (s instanceof SliderSetting sl) so.addProperty(s.name, sl.value);
                        else if (s instanceof ModeSetting md) so.addProperty(s.name, md.get());
                        else if (s instanceof StringSetting ts) so.addProperty(s.name, ts.value);
                        else if (s instanceof ColorSetting c) {
                            JsonObject co = new JsonObject();
                            co.addProperty("accent", c.accent);
                            co.addProperty("r", c.r); co.addProperty("g", c.g); co.addProperty("b", c.b);
                            so.add(s.name, co);
                        }
                    }
                    mo.add("settings", so);
                }
                modules.add(m.getName(), mo);
            }
            root.add("modules", modules);

            JsonObject hud = new JsonObject();
            java.util.Set<String> keys = new java.util.HashSet<>();
            keys.addAll(HudLayout.offsetMap().keySet());
            keys.addAll(HudLayout.scaleMap().keySet());
            keys.addAll(HudLayout.sizeMap().keySet());
            for (String k : keys) {
                int[] off = HudLayout.offsetMap().getOrDefault(k, new int[2]);
                JsonObject e = new JsonObject();
                e.addProperty("x", off[0]); e.addProperty("y", off[1]);
                e.addProperty("scale", HudLayout.getScale(k));
                int[] size = HudLayout.getSize(k);
                if (size != null) { e.addProperty("w", size[0]); e.addProperty("h", size[1]); }
                hud.add(k, e);
            }
            root.add("hud", hud);

            JsonArray wps = new JsonArray();
            for (Waypoints.WP w : Waypoints.list) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name);
                o.addProperty("x", w.x); o.addProperty("y", w.y); o.addProperty("z", w.z);
                o.addProperty("color", w.color);
                if (w.server != null) o.addProperty("server", w.server);
                if (w.anarchy != null) o.addProperty("anarchy", w.anarchy);
                if (w.eventLinked) o.addProperty("eventLinked", true);
                wps.add(o);
            }
            root.add("waypoints", wps);

            JsonArray macros = new JsonArray();
            for (MacroManager.Macro mac : MacroManager.macros) {
                JsonObject o = new JsonObject();
                o.addProperty("key", mac.key);
                o.addProperty("text", mac.text);
                macros.add(o);
            }
            root.add("macros", macros);

            JsonArray filters = new JsonArray();
            for (String w : com.lume.client.module.modules.qol.ChatFilters.keywords) filters.add(w);
            root.add("chatFilters", filters);

            JsonObject events = new JsonObject();
            for (EventRule r : EventManager.rules) {
                JsonObject o = new JsonObject();
                o.addProperty("seen", r.lastSeen);
                o.addProperty("interval", r.intervalSec);
                events.add(r.name, o);
            }
            root.add("events", events);

            // FT/HW items — user-verified "present" flag + tuned radius/cooldown
            JsonObject items = new JsonObject();
            for (com.lume.client.fthw.ItemRule it : com.lume.client.fthw.ItemRules.rules) {
                JsonObject o = new JsonObject();
                o.addProperty("present", it.present);
                o.addProperty("radius", it.radius);
                o.addProperty("cooldown", it.cooldownSec);
                items.add(it.name, o);
            }
            root.add("ftItems", items);

            // FT/HW helper sub-function keybinds
            JsonObject hbinds = new JsonObject();
            for (BoolSetting b : com.lume.client.fthw.HelperBinds.bound) hbinds.addProperty(b.name, b.key);
            root.add("helperBinds", hbinds);

            // FT/HW quick-command keybinds
            JsonObject qcmds = new JsonObject();
            for (com.lume.client.fthw.QuickCommands.Cmd c : com.lume.client.fthw.QuickCommands.list) qcmds.addProperty(c.label, c.key);
            root.add("quickCmds", qcmds);

            JsonArray fastConnect = new JsonArray();
            for (FastConnect.Entry e : FastConnect.list) {
                JsonObject o = new JsonObject();
                o.addProperty("name", e.name);
                o.addProperty("address", e.address);
                fastConnect.add(o);
            }
            root.add("fastConnect", fastConnect);

            if (licenseRaw != null) root.add("license", licenseRaw);

            JsonArray accounts = new JsonArray();
            for (String a : savedAccounts) accounts.add(a);
            root.add("savedAccounts", accounts);
            if (preferredAccount != null) root.addProperty("preferredAccount", preferredAccount);

            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root));
        } catch (Exception e) {
            System.out.println("[Lume] config save failed: " + e);
        }
    }

    /** Serialize current module enabled states + settings to a JSON string (for profile saving). */
    public static String toJson() {
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        for (Module m : LumeClient.MODULES.getModules()) {
            JsonObject mo = new JsonObject();
            mo.addProperty("enabled", m.isEnabled());
            if (m.hasSettings()) {
                JsonObject so = new JsonObject();
                for (Setting s : m.getSettings()) {
                    if (s instanceof BoolSetting b) so.addProperty(s.name, b.value);
                    else if (s instanceof SliderSetting sl) so.addProperty(s.name, sl.value);
                    else if (s instanceof ModeSetting md) so.addProperty(s.name, md.get());
                    else if (s instanceof StringSetting ts) so.addProperty(s.name, ts.value);
                    else if (s instanceof ColorSetting c) {
                        JsonObject co = new JsonObject();
                        co.addProperty("accent", c.accent);
                        co.addProperty("r", c.r); co.addProperty("g", c.g); co.addProperty("b", c.b);
                        so.add(s.name, co);
                    }
                }
                mo.add("settings", so);
            }
            modules.add(m.getName(), mo);
        }
        root.add("modules", modules);
        return GSON.toJson(root);
    }

    /** Apply a JSON string produced by {@link #toJson()} to the current module states. */
    public static void fromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("modules")) return;
            JsonObject mods = root.getAsJsonObject("modules");
            for (Module m : LumeClient.MODULES.getModules()) {
                if (!mods.has(m.getName())) continue;
                JsonObject mo = mods.getAsJsonObject(m.getName());
                if (mo.has("enabled")) m.setEnabled(mo.get("enabled").getAsBoolean());
                if (mo.has("settings")) {
                    JsonObject so = mo.getAsJsonObject("settings");
                    for (Setting s : m.getSettings()) {
                        if (!so.has(s.name)) continue;
                        if (s instanceof BoolSetting b) b.value = so.get(s.name).getAsBoolean();
                        else if (s instanceof SliderSetting sl) sl.value = so.get(s.name).getAsDouble();
                        else if (s instanceof ModeSetting md) md.setByName(so.get(s.name).getAsString());
                        else if (s instanceof StringSetting ts) ts.value = so.get(s.name).getAsString();
                        else if (s instanceof ColorSetting c) {
                            JsonObject co = so.getAsJsonObject(s.name);
                            c.accent = co.get("accent").getAsBoolean();
                            c.r = co.get("r").getAsInt(); c.g = co.get("g").getAsInt(); c.b = co.get("b").getAsInt();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[Lume] profile fromJson failed: " + e);
        }
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();

            if (root.has("theme")) Theme.setDark("dark".equals(root.get("theme").getAsString()));
            if (root.has("friendsDeviceId")) com.lume.client.social.Friends.deviceId = root.get("friendsDeviceId").getAsString();
            if (root.has("license")) licenseRaw = root.getAsJsonObject("license");
            if (root.has("savedAccounts")) {
                savedAccounts.clear();
                for (JsonElement el : root.getAsJsonArray("savedAccounts")) savedAccounts.add(el.getAsString());
            }
            if (root.has("preferredAccount")) preferredAccount = root.get("preferredAccount").getAsString();

            if (root.has("window")) {
                JsonObject w = root.getAsJsonObject("window");
                ClickGuiScreen.setWindow(w.get("x").getAsInt(), w.get("y").getAsInt(), w.get("scale").getAsFloat());
            }

            if (root.has("modules")) {
                JsonObject mods = root.getAsJsonObject("modules");
                for (Module m : LumeClient.MODULES.getModules()) {
                    if (!mods.has(m.getName())) continue;
                    JsonObject mo = mods.getAsJsonObject(m.getName());
                    if (mo.has("enabled")) m.setEnabled(mo.get("enabled").getAsBoolean());
                    if (mo.has("key")) m.setKey(mo.get("key").getAsInt());
                    if (mo.has("mode")) try { m.setBindMode(Module.BindMode.valueOf(mo.get("mode").getAsString())); } catch (Exception ignored) {}
                    if (mo.has("settings")) {
                        JsonObject so = mo.getAsJsonObject("settings");
                        for (Setting s : m.getSettings()) {
                            if (!so.has(s.name)) continue;
                            if (s instanceof BoolSetting b) b.value = so.get(s.name).getAsBoolean();
                            else if (s instanceof SliderSetting sl) sl.value = so.get(s.name).getAsDouble();
                            else if (s instanceof ModeSetting md) md.setByName(so.get(s.name).getAsString());
                            else if (s instanceof ColorSetting c) {
                                JsonObject co = so.getAsJsonObject(s.name);
                                c.accent = co.get("accent").getAsBoolean();
                                c.r = co.get("r").getAsInt(); c.g = co.get("g").getAsInt(); c.b = co.get("b").getAsInt();
                            }
                        }
                    }
                }
            }

            if (root.has("hud")) {
                JsonObject hud = root.getAsJsonObject("hud");
                for (Map.Entry<String, JsonElement> en : hud.entrySet()) {
                    JsonObject e = en.getValue().getAsJsonObject();
                    HudLayout.set(en.getKey(), e.get("x").getAsInt(), e.get("y").getAsInt());
                    HudLayout.setScale(en.getKey(), e.get("scale").getAsFloat());
                    if (e.has("w") && e.has("h")) HudLayout.setSize(en.getKey(), e.get("w").getAsInt(), e.get("h").getAsInt());
                }
            }

            if (root.has("waypoints")) {
                Waypoints.list.clear();
                for (JsonElement el : root.getAsJsonArray("waypoints")) {
                    JsonObject o = el.getAsJsonObject();
                    // Built directly (not via Waypoints.add) so we restore the server/anarchy
                    // this waypoint was actually saved under, instead of stamping it with
                    // whatever server we happen to be loading on (there usually isn't one yet).
                    // Entries saved before per-server scoping existed have no "server" field —
                    // default those to "singleplayer" (not null/wildcard) so they can't leak
                    // onto every multiplayer server; singleplayer is where they were almost
                    // certainly placed during testing anyway.
                    Waypoints.list.add(new Waypoints.WP(o.get("name").getAsString(), o.get("x").getAsDouble(),
                            o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("color").getAsInt(),
                            o.has("server") ? o.get("server").getAsString() : "singleplayer",
                            o.has("anarchy") ? o.get("anarchy").getAsString() : null,
                            o.has("eventLinked") && o.get("eventLinked").getAsBoolean()));
                }
            }

            if (root.has("macros")) {
                MacroManager.macros.clear();
                for (JsonElement el : root.getAsJsonArray("macros")) {
                    JsonObject o = el.getAsJsonObject();
                    MacroManager.add(o.get("key").getAsInt(), o.get("text").getAsString());
                }
            }

            if (root.has("chatFilters")) {
                com.lume.client.module.modules.qol.ChatFilters.keywords.clear();
                for (JsonElement el : root.getAsJsonArray("chatFilters")) com.lume.client.module.modules.qol.ChatFilters.add(el.getAsString());
            }

            if (root.has("events")) {
                JsonObject evs = root.getAsJsonObject("events");
                for (EventRule r : EventManager.rules) {
                    if (!evs.has(r.name)) continue;
                    JsonObject o = evs.getAsJsonObject(r.name);
                    r.lastSeen = o.get("seen").getAsLong();
                    r.intervalSec = o.get("interval").getAsLong();
                }
            }

            if (root.has("ftItems")) {
                JsonObject items = root.getAsJsonObject("ftItems");
                for (com.lume.client.fthw.ItemRule it : com.lume.client.fthw.ItemRules.rules) {
                    if (!items.has(it.name)) continue;
                    JsonObject o = items.getAsJsonObject(it.name);
                    if (o.has("present")) it.present = o.get("present").getAsBoolean();
                    if (o.has("radius")) it.radius = o.get("radius").getAsDouble();
                    if (o.has("cooldown")) it.cooldownSec = o.get("cooldown").getAsInt();
                }
            }

            if (root.has("helperBinds")) {
                JsonObject hb = root.getAsJsonObject("helperBinds");
                for (BoolSetting b : com.lume.client.fthw.HelperBinds.bound)
                    if (hb.has(b.name)) b.key = hb.get(b.name).getAsInt();
            }

            if (root.has("quickCmds")) {
                JsonObject qc = root.getAsJsonObject("quickCmds");
                for (com.lume.client.fthw.QuickCommands.Cmd c : com.lume.client.fthw.QuickCommands.list)
                    if (qc.has(c.label)) c.key = qc.get(c.label).getAsInt();
            }

            if (root.has("fastConnect")) {
                FastConnect.list.clear();
                for (JsonElement el : root.getAsJsonArray("fastConnect")) {
                    JsonObject o = el.getAsJsonObject();
                    FastConnect.add(o.get("name").getAsString(), o.get("address").getAsString());
                }
            }
        } catch (Exception e) {
            System.out.println("[Lume] config load failed: " + e);
        }
    }
}

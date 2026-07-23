package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.module.setting.StringSetting;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Custom Menu — the small NanoVG overlay on top of the (now fully vanilla-again) title screen:
 * Account Manager / Fast Connect / Friends / Settings corner buttons (see
 * {@code com.lume.client.menu.LumeTitleMenu}). This class is just the on/off toggle + the
 * handful of settings those corners expose (wallpaper override, per-panel show/hide) — actual
 * layout/buttons/hit-testing live in {@code LumeTitleMenu}.
 *
 * <p>Everything the old elaborate custom title screen owned (Particles/Geometry animated
 * backgrounds, cursor sparks, the Lume/Vanilla toggle pill, re-implemented Singleplayer/
 * Multiplayer/Options/Language/Quit) is gone — vanilla renders its own title screen and its own
 * buttons untouched now; the only thing this module still paints is an optional wallpaper image
 * in place of the vanilla panorama, and the 4 corner overlay buttons/panels.
 */
public class CustomMenu extends Module {

    public final BoolSetting wallpaperOn = add(new BoolSetting("Custom Wallpaper", false));
    /** Filename inside {@code .lumeclient/wallpapers}; only meaningful while wallpaperOn. */
    public final StringSetting wallpaper = add(new StringSetting("Wallpaper", ""));
    public final SliderSetting wallpaperDim = add(new SliderSetting("Wallpaper Dim", 35, 0, 100, true));
    /** Dark overlay over WHATEVER background is showing — vanilla panorama or a custom
     *  wallpaper — same idea as the launcher's own background-dim setting. Independent of
     *  {@link #wallpaperDim}, which only ever applied to the wallpaper image itself. */
    public final SliderSetting bgDim = add(new SliderSetting("Background Dim", 0, 0, 100, true));
    public final BoolSetting showFastConnect = add(new BoolSetting("Show Fast Connect", true));
    public final BoolSetting showFriends = add(new BoolSetting("Show Friends", true));
    public final BoolSetting showAccount = add(new BoolSetting("Show Account", true));

    public CustomMenu() {
        super("Custom Menu", "Account Manager / Fast Connect / Friends overlay on the title screen", Category.COSMETIC, -1);
        // Creates the wallpapers/ folder + how-to README eagerly, same reasoning as before —
        // without this the folder only appears once a wallpaper is already selected.
        com.lume.client.menu.Wallpapers.ensureDir();
    }

    public static boolean active() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu && m.isEnabled();
    }

    public static void toggleActive() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m != null) m.toggle();
    }

    /** True when a custom wallpaper should be painted in place of the vanilla panorama. */
    public static boolean wallpaperActive() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return active() && m instanceof CustomMenu c && c.wallpaperOn.value && c.wallpaper.value != null && !c.wallpaper.value.isBlank();
    }

    public static void toggleWallpaperOn() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.wallpaperOn.value = !c.wallpaperOn.value;
    }

    /** Current wallpaper filename, or null if none selected / module missing. */
    public static String wallpaper() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c ? c.wallpaper.value : null;
    }

    public static double wallpaperDim() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c ? c.wallpaperDim.value / 100.0 : 0.35;
    }

    /** Cycles through image files in the wallpapers/ folder (dir = +1/-1); no-op if the
     *  folder has nothing in it yet — nothing to cycle to. */
    public static void cycleWallpaper(int dir) {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (!(m instanceof CustomMenu c)) return;
        java.util.List<String> files = com.lume.client.menu.Wallpapers.list();
        if (files.isEmpty()) return;
        int idx = Math.floorMod(files.indexOf(c.wallpaper.value) + dir, files.size());
        c.wallpaper.value = files.get(idx);
    }

    /** Current background-dim percentage (0-100), stepped by 10 via the Settings panel cycler. */
    public static int bgDim() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c ? (int) Math.round(c.bgDim.value) : 0;
    }

    public static void cycleBgDim(int dir) {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.bgDim.value = Math.max(0, Math.min(100, c.bgDim.value + dir * 10));
    }

    /** Flat dark overlay drawn on top of whatever background the title screen (or Singleplayer/
     *  Multiplayer, via the same mixin) already rendered — vanilla panorama or a custom
     *  wallpaper, doesn't matter which. No-op (and no NanoVG dependency) at 0%. */
    public static void drawDimOverlay(DrawContext ctx, int w, int h) {
        int pct = bgDim();
        if (pct <= 0) return;
        int alpha = Math.round(pct / 100f * 255);
        ctx.fill(0, 0, w, h, (alpha << 24));
    }

    public static boolean showFastConnect() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return !(m instanceof CustomMenu c) || c.showFastConnect.value;
    }

    public static void toggleShowFastConnect() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.showFastConnect.value = !c.showFastConnect.value;
    }

    public static boolean showFriends() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return !(m instanceof CustomMenu c) || c.showFriends.value;
    }

    public static void toggleShowFriends() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.showFriends.value = !c.showFriends.value;
    }

    public static boolean showAccount() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return !(m instanceof CustomMenu c) || c.showAccount.value;
    }

    public static void toggleShowAccount() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.showAccount.value = !c.showAccount.value;
    }

    /** Paints the wallpaper image (only ever called while {@link #wallpaperActive()}). Base fill
     *  first (cheap fallback if NanoVG isn't ready) then the actual image on top. */
    public static void drawBackground(DrawContext ctx, int w, int h) {
        ctx.fill(0, 0, w, h, Theme.isDark() ? 0xFF201C16 : 0xFFF3ECDD);
        // DrawContext batches its fill quads instead of submitting them immediately; without
        // forcing a flush here, that opaque fill can hit the GPU AFTER the raw-GL NanoVG frame
        // below and paint over it — see the historical note this replaced for the full story.
        ctx.draw();
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;
        float S = NanoVgRenderer.pxScale();
        NanoVgRenderer.frame(vg -> com.lume.client.menu.Wallpapers.draw(vg, w * S, h * S, (float) wallpaperDim()));
    }
}

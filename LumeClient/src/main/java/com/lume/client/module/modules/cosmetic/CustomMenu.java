package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.module.setting.StringSetting;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.gui.DrawContext;

import static com.lume.client.nanovg.NanoVgRenderer.*;

/**
 * Custom Menu — replaces the vanilla title screen with Lume's own fully
 * custom-rendered (NanoVG) glass UI: own buttons, own icons, own live-animated
 * background. Background styles to pick from (also changeable from the gear
 * icon on the title screen itself). The actual layout/buttons/hit-testing
 * live in {@code com.lume.client.menu.LumeTitleMenu}; this class is just the
 * on/off toggle + background painter.
 */
public class CustomMenu extends Module {

    public final ModeSetting background = add(new ModeSetting("Background", 0, "Sparkles", "Geometry", "Wallpaper", "Default"));
    public final BoolSetting cursorSparks = add(new BoolSetting("Cursor Sparks", true));
    public final BoolSetting cursorGlow = add(new BoolSetting("Cursor Glow", true));
    /** Filename inside {@code .lumeclient/wallpapers}; only meaningful for the Wallpaper style. */
    public final StringSetting wallpaper = add(new StringSetting("Wallpaper", ""));
    public final SliderSetting wallpaperDim = add(new SliderSetting("Wallpaper Dim", 35, 0, 100, true));
    public final BoolSetting showFastConnect = add(new BoolSetting("Show Fast Connect", true));
    public final BoolSetting showFriends = add(new BoolSetting("Show Friends", true));
    public final BoolSetting showAccount = add(new BoolSetting("Show Account", true));
    public final BoolSetting showVersion = add(new BoolSetting("Show Version", false));

    public CustomMenu() {
        super("Custom Menu", "Fully custom Lume main menu (own render, own background)", Category.COSMETIC, -1);
    }

    public static boolean active() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu && m.isEnabled();
    }

    /** True when we should actually paint OUR background/cancel vanilla's — false for the
     *  "Default" style, which means "just show the game's own normal background". */
    public static boolean customBackgroundActive() {
        return active() && bgStyle() != 2;
    }

    public static void toggleActive() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m != null) m.toggle();
    }

    public static int bgStyle() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c ? c.background.index : 0;
    }

    public static void cycleBgStyle(int dir) {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.background.cycle(dir);
    }

    public static boolean cursorGlowOn() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c && c.cursorGlow.value;
    }

    public static void toggleCursorGlow() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.cursorGlow.value = !c.cursorGlow.value;
    }

    /** Current wallpaper filename (Wallpaper style only), or null if none selected / module missing. */
    public static String wallpaper() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c ? c.wallpaper.value : null;
    }

    public static double wallpaperDim() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c ? c.wallpaperDim.value / 100.0 : 0.35;
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

    public static boolean showVersion() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return !(m instanceof CustomMenu c) || c.showVersion.value;
    }

    public static void toggleShowVersion() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        if (m instanceof CustomMenu c) c.showVersion.value = !c.showVersion.value;
    }

    /** Base fill (always, cheap fallback if NanoVG isn't ready) then the animated style on top. */
    public static void drawBackground(DrawContext ctx, int w, int h) {
        ctx.fill(0, 0, w, h, Theme.isDark() ? 0xFF201C16 : 0xFFF3ECDD);
        // DrawContext batches its fill quads instead of submitting them immediately;
        // without forcing a flush here, that opaque fill can hit the GPU AFTER the
        // raw-GL NanoVG frame below and paint over it — the background style would
        // then always look like a flat colour, no matter which one is picked. This
        // was the whole "backgrounds don't show" bug.
        ctx.draw();
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;
        float S = NanoVgRenderer.pxScale();
        int style = bgStyle();
        if (style == 2) return;   // "Default" — just the flat theme-colour fill above, no animation
        NanoVgRenderer.frame(vg -> {
            switch (style) {
                case 1 -> geometry(vg, w * S, h * S);
                default -> glow(vg, w * S, h * S);
            }
        });
    }

    /** One drifting glow blob: slow side-to-side + up-down wander, matching the
     *  launcher's key-entry screen (renderer/index.html's {@code .blob} elements —
     *  same drift idea, ported to a real NanoVG radial gradient here). Periods are
     *  short enough (a few seconds) that the motion actually reads as animated at
     *  a glance, not just "technically never stops". */
    private record BlobSpec(float baseX, float baseY, float radius, float driftX, float driftY,
                            float periodX, float periodY, float phase, boolean accent2) {}

    private static final BlobSpec[] BLOBS = {
            new BlobSpec(0.24f, 0.30f, 0.22f, 0.16f, 0.11f, 4200, 5300, 0.0f, false),
            new BlobSpec(0.74f, 0.66f, 0.26f, 0.15f, 0.13f, 4800, 3900, 2.1f, false),
            new BlobSpec(0.50f, 0.16f, 0.17f, 0.14f, 0.10f, 3700, 4700, 4.4f, true),
            new BlobSpec(0.14f, 0.78f, 0.19f, 0.13f, 0.15f, 5100, 4100, 1.3f, true),
    };

    // --- Style 0: Glow — real soft blur blobs (nvgRadialGradient), not flat circles ---------
    private static void glow(long vg, float w, float h) {
        // System.currentTimeMillis() is a huge long (~1.7e12) — casting straight to float
        // (as "long / float periodX" implicitly does) rounds to the nearest ~131072ms because
        // a float only has ~24 bits of mantissa, so consecutive frames landed on the EXACT
        // SAME rounded value for minutes at a time: the blobs looked frozen even though the
        // math "ran" every frame. Reduce mod a safe window first (same trick geometry() below
        // already uses) so the value that actually reaches float has millisecond precision.
        long t = System.currentTimeMillis() % 10_000_000L;
        int acc = Theme.accentRgb(), acc2 = Theme.accent2() & 0xFFFFFF;
        for (BlobSpec b : BLOBS) {
            float dx = (float) Math.sin(t / b.periodX() + b.phase()) * w * b.driftX();
            float dy = (float) Math.cos(t / b.periodY() + b.phase() * 1.7f) * h * b.driftY();
            float cx = w * b.baseX() + dx, cy = h * b.baseY() + dy;
            float outer = Math.max(w, h) * b.radius();
            NanoVgRenderer.radialGlow(vg, cx, cy, outer * 0.05f, outer, b.accent2() ? acc2 : acc, 150);
        }
    }

    // --- Style 1: Geometry — one continuous drift, no pulsing/transitions, but fast enough
    // to actually read as moving within a few seconds of watching. -------------------------
    private static final long GEOMETRY_PERIOD_MS = 16_000L;   // one full cell traversal (seamless loop)

    private static void geometry(long vg, float w, float h) {
        long t = System.currentTimeMillis();
        int rgb = Theme.accentRgb() & 0xFFFFFF;
        float cell = Math.max(40f, Math.min(w, h) / 10f);
        float drift = (float) (t % GEOMETRY_PERIOD_MS) / GEOMETRY_PERIOD_MS * cell;
        for (float x = -cell; x < w + cell; x += cell) {
            roundedRect(vg, x + drift, 0, 2f, h, 0, (60 << 24) | rgb);
        }
        for (float y = -cell; y < h + cell; y += cell) {
            roundedRect(vg, 0, y + drift * 0.6f, w, 2f, 0, (46 << 24) | rgb);
        }
    }
}

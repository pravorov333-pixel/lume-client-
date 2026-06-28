package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.client.gui.DrawContext;

/**
 * Custom Menu — replaces the vanilla title-screen panorama with Lume's own
 * cream/lavender animated background (drifting glow blobs), so the main menu
 * matches the launcher. The Menu Logo module draws the wordmark on top.
 *
 * <p>The background is painted from {@code TitleScreenMixin}.
 */
public class CustomMenu extends Module {

    public CustomMenu() {
        super("Custom Menu", "Themed Lume main-menu background", Category.COSMETIC, -1);
    }

    public static boolean active() {
        Module m = LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu && m.isEnabled();
    }

    /** Paints the themed background across the whole screen. */
    public static void drawBackground(DrawContext ctx, int w, int h) {
        ctx.fill(0, 0, w, h, Theme.isDark() ? 0xFF201C16 : 0xFFF3ECDD);
        long t = System.currentTimeMillis();
        blob(ctx, (int) (w * 0.26 + Math.sin(t / 3200.0) * w * 0.08), (int) (h * 0.34), (int) (w * 0.20), Theme.accentRgb());
        blob(ctx, (int) (w * 0.72 + Math.sin(t / 3900.0 + 1.0) * w * 0.07), (int) (h * 0.62), (int) (w * 0.24), Theme.accentRgb());
        blob(ctx, (int) (w * 0.50 + Math.cos(t / 4500.0) * w * 0.06), (int) (h * 0.18), (int) (w * 0.16), Theme.accent2() & 0xFFFFFF);
        // gentle darken at the bottom so the buttons stay readable
        for (int i = 0; i < 40; i++) {
            int a = (int) (i * 1.4);
            ctx.fill(0, h - 1 - i, w, h - i, (Math.min(80, a) << 24));
        }
    }

    private static void blob(DrawContext ctx, int cx, int cy, int radius, int rgb) {
        int layers = 11;
        for (int i = layers; i >= 1; i--) {
            int rr = radius * i / layers;
            int a = Math.max(2, 12 - i);
            RenderUtil.roundedRect(ctx, cx - rr, cy - rr, rr * 2, rr * 2, rr, (a << 24) | (rgb & 0xFFFFFF));
        }
    }
}

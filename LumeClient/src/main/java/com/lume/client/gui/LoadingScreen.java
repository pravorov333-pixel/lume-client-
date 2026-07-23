package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Shared "our own loading screen" look — background + logo + wordmark + a
 * progress bar — reused by every vanilla loading screen we paint over
 * (world generation, entering a world, connecting to a server, and the very
 * first resource-load splash). Painted on TOP of vanilla at the TAIL of each
 * screen's own render, rather than cancelling it — vanilla keeps handling
 * its own close/progress logic untouched, we just cover what it draws.
 */
public final class LoadingScreen {
    private LoadingScreen() {}

    /** @param progress 0..1 for a real percentage, or negative for an indeterminate sliding bar. */
    public static void draw(DrawContext ctx, int width, int height, float progress) {
        draw(ctx, width, height, progress, null);
    }

    /**
     * @param progress 0..1 for a real percentage, or negative for an indeterminate sliding bar.
     * @param status   what's happening right now ("Подключение к серверу…" etc), or null to omit —
     *                 shown just above the bar (Cyrillic renders fine, NanoVG falls back to the
     *                 bundled Cyrillic font for glyphs Poppins doesn't have).
     */
    public static void draw(DrawContext ctx, int width, int height, float progress, String status) {
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;   // leave vanilla's own render showing rather than a blank frame
        MinecraftClient mc = MinecraftClient.getInstance();
        int S = (int) Math.max(1, mc.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        float p = progress;

        ctx.draw();   // flush vanilla's own queued geometry first
        NanoVgRenderer.frame(vg -> {
            NanoVgRenderer.roundedRect(vg, 0, 0, sw, sh, 0, Theme.isDark() ? 0xFF201C16 : 0xFFF3ECDD);

            float cx = sw / 2f, cy = sh / 2f;
            float logoS = 56 * S;
            NanoVgRenderer.logoMark(vg, cx - logoS / 2f, cy - logoS / 2f - 34 * S, logoS);

            Wordmark.drawVividCentered(vg, cx, cy + 24 * S, 15 * S, 255);

            float barW = Math.min(240 * S, sw * 0.6f), barH = 5 * S;
            float bx = cx - barW / 2f, by = cy + 48 * S;

            if (status != null && !status.isBlank()) {
                float statusSize = NanoVgRenderer.fitSize(vg, 9.5f * S, status, barW + 40 * S, 7 * S);
                NanoVgRenderer.text(vg, cx, by - 10 * S, statusSize, Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, status);
            }

            NanoVgRenderer.roundedRect(vg, bx, by, barW, barH, barH / 2f, Theme.glassRow());
            if (p >= 0f) {
                float fw = barW * Math.max(0f, Math.min(1f, p));
                if (fw > 0) NanoVgRenderer.gradientRoundedRect(vg, bx, by, fw, barH, barH / 2f, Theme.accent(), Theme.accent2());
            } else {
                long t = System.currentTimeMillis();
                float segW = barW * 0.3f;
                float phase = (t % 1300L) / 1300f;
                float sx = bx - segW + (barW + segW) * phase;
                float cx1 = Math.max(bx, sx), cx2 = Math.min(bx + barW, sx + segW);
                if (cx2 > cx1) NanoVgRenderer.gradientRoundedRect(vg, cx1, by, cx2 - cx1, barH, barH / 2f, Theme.accent(), Theme.accent2());
            }
        });
    }
}

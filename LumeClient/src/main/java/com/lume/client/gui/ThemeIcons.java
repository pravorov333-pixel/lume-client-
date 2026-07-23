package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;

/**
 * Draws the theme toggle (sun/moon) + "Customize Colors" (4-dot scatter) icon
 * buttons — pixel-shape matches to the LumeLauncher's own corner SVG icons
 * (24x24 viewBox: sun = stroked r4.5 ring + 8 radiating stroked rays; colors =
 * 4 stroked dots at (13.5,6.5) (19,13) (6,12) (10,19)) so theme switching looks
 * identical everywhere it appears (main ClickGUI header, sub-screens, and the
 * title-screen menu cluster). Single source of truth so the call sites can't
 * drift apart.
 *
 * <p>Callers pass coordinates already in whatever pixel space their own nvg
 * frame uses (framebuffer-px for ClickGuiScreen/LumeSubScreen, which bake the
 * display-scale factor into every coordinate; logical-px pre-multiplied by S
 * for LumeTitleMenu) — this class only draws, it doesn't own hit-testing, so
 * each caller keeps computing its own rects the way it already does.
 */
public final class ThemeIcons {
    private ThemeIcons() {}

    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    /** Theme toggle button, top-left corner at (tbx, tby), size 22*S square. */
    public static void drawTheme(long vg, float tbx, float tby, float S, float themeHover) {
        float tbw = 22f * S, tbh = 22f * S;
        int themeBg = Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), themeHover);
        NanoVgRenderer.roundedRect(vg, tbx, tby, tbw, tbh, 8f * S, themeBg);
        float icx = tbx + tbw / 2f, icy = tby + tbh / 2f;
        // 13-unit glyph box maps the launcher's 24x24 viewBox 1:1 (icoS/24 = unit).
        float icoS = 13f * S;
        if (Theme.isDark()) {
            moonIcon(vg, icx, icy, icoS, Theme.txt(), themeBg);
        } else {
            sunIcon(vg, icx, icy, icoS, Theme.txt());
        }
    }

    /** Customize Colors button (4-dot scatter), top-left corner at (cbx, cby), size 22*S square. */
    public static void drawColors(long vg, float cbx, float cby, float S, float colorsHover) {
        float cbw = 22f * S, cbh = 22f * S;
        NanoVgRenderer.roundedRect(vg, cbx, cby, cbw, cbh, 8f * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), colorsHover));
        float pcx = cbx + cbw / 2f, pcy = cby + cbh / 2f;
        float icoS = 13f * S;
        colorsIcon(vg, pcx, pcy, icoS, Theme.txt());
    }

    // ---------------------------------------------------------------------
    // Icon glyphs — coordinates below mirror the launcher's SVG paths 1:1,
    // remapped from a 24x24 viewBox onto a (icoS x icoS) box centred at (cx,cy).

    /** Stroked ring + 8 radiating rays, exactly the launcher's sun glyph. */
    private static void sunIcon(long vg, float cx, float cy, float icoS, int col) {
        float u = icoS / 24f;
        float strokeW = Math.max(1f, 2f * u);
        NanoVgRenderer.strokeEllipse(vg, cx, cy, 4.5f * u, 4.5f * u, strokeW, col);
        float innerR = 7f * u, outerR = 9.5f * u, rayLen = outerR - innerR;
        for (int i = 0; i < 8; i++) {
            NanoVgRenderer.save(vg);
            NanoVgRenderer.translate(vg, cx, cy);
            NanoVgRenderer.rotate(vg, (float) (i * Math.PI / 4));
            NanoVgRenderer.roundedRect(vg, -strokeW / 2f, -outerR, strokeW, rayLen, strokeW / 2f, col);
            NanoVgRenderer.restore(vg);
        }
    }

    /** Filled crescent (body circle minus an offset bite punched in the button's own
     *  background colour), exactly the launcher's moon glyph proportions. */
    private static void moonIcon(long vg, float cx, float cy, float icoS, int col, int behindBg) {
        float u = icoS / 24f;
        float bodyR = 8.5f * u;
        NanoVgRenderer.circle(vg, cx, cy, bodyR, col);
        float biteR = 7f * u;
        NanoVgRenderer.circle(vg, cx + 3.2f * u, cy - 3.2f * u, biteR, behindBg);
    }

    /** 4 stroked dots scattered like the launcher's colours glyph. */
    private static void colorsIcon(long vg, float cx, float cy, float icoS, int col) {
        float u = icoS / 24f;
        float dotR = 2.5f * u, strokeW = Math.max(1f, 2f * u);
        float[][] pts = {{13.5f, 6.5f}, {19f, 13f}, {6f, 12f}, {10f, 19f}};
        for (float[] p : pts) {
            float px = cx + (p[0] - 12f) * u, py = cy + (p[1] - 12f) * u;
            NanoVgRenderer.strokeEllipse(vg, px, py, dotR, dotR, strokeW, col);
        }
    }

    // ---------------------------------------------------------------------
    // DrawContext (non-NanoVG) fallbacks — simplified silhouettes (a plain sun disc
    // instead of ring+rays, a plain ring instead of 4 stroked dots), used only where NanoVG
    // itself is the thing being avoided (see LumeTitleMenu's top-right cluster, which moved off
    // NanoVG entirely after it reproducibly crashed one user's Intel iGPU driver from inside
    // nvgEndFrame — same root cause as LogoDrawerMixin, see there for the full story).

    public static void drawThemeLegacy(net.minecraft.client.gui.DrawContext ctx, int x, int y, int size, int bg) {
        RenderUtil.roundedRect(ctx, x, y, size, size, size / 3, bg);
        int r = Math.round(size * 0.27f);
        int cx = x + size / 2, cy = y + size / 2;
        if (Theme.isDark()) {
            RenderUtil.roundedRect(ctx, cx - r, cy - r, 2 * r, 2 * r, r, Theme.txt());
            int bite = Math.round(r * 0.82f), off = Math.round(r * 0.4f);
            RenderUtil.roundedRect(ctx, cx - bite + off, cy - bite - off, 2 * bite, 2 * bite, bite, bg);
        } else {
            RenderUtil.roundedRect(ctx, cx - r, cy - r, 2 * r, 2 * r, r, Theme.txt());
        }
    }

    public static void drawColorsLegacy(net.minecraft.client.gui.DrawContext ctx, int x, int y, int size, int bg) {
        RenderUtil.roundedRect(ctx, x, y, size, size, size / 3, bg);
        int dotR = Math.max(1, Math.round(size * 0.1f));
        float u = size / 24f;
        float[][] pts = {{13.5f, 6.5f}, {19f, 13f}, {6f, 12f}, {10f, 19f}};
        for (float[] p : pts) {
            int px = x + Math.round((p[0] - 12f + 12f) * u) - dotR;
            int py = y + Math.round((p[1] - 12f + 12f) * u) - dotR;
            RenderUtil.roundedRect(ctx, px, py, 2 * dotR, 2 * dotR, dotR, Theme.txt());
        }
    }
}

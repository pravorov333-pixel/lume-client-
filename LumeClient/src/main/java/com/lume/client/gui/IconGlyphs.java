package com.lume.client.gui;

import com.lume.client.nanovg.SdfRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Live SDF icon glyphs (gear, globe, ×, sun, moon, colour dots) — replaces the earlier baked-PNG
 * icon sprites (see {@code tools/gen_menu.py}'s {@code ic_*} functions, which these mirror the
 * intent of) so every visible element in the Custom Menu renders through the same shader path,
 * per explicit request. Each method takes DrawContext logical px ({@code cx,cy} = icon centre,
 * {@code r} = icon radius) and converts to framebuffer px internally, same convention {@link
 * RenderUtil#premiumBg} uses.
 *
 * <p>Geometric compositions only (circles/rings/rotated bars via {@link SdfRenderer}) — the
 * shader has no path/glyph rendering, so these are simplified icon silhouettes, not pixel-faithful
 * copies of the old PIL-drawn PNGs. Un-verifiable visually before a live game launch (no in-game
 * preview available) — built from the same well-understood SDF primitives {@code RenderUtil}'s
 * button chrome already uses successfully, but the exact icon SILHOUETTES here are new.
 */
public final class IconGlyphs {
    private IconGlyphs() {}

    private static int scale() {
        return (int) Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    /** Settings gear: ring + hub + 6 teeth (short rotated bars poking out past the ring). */
    public static void gear(DrawContext ctx, int cx, int cy, float r, int col) {
        int S = scale();
        ctx.draw();
        int fcx = Math.round(cx * S), fcy = Math.round(cy * S);
        float fr = r * S;
        float ringR = fr * 0.62f, hubR = fr * 0.24f, toothLen = fr * 0.34f, toothW = fr * 0.22f;
        SdfRenderer.ring(fcx, fcy, ringR, fr * 0.16f, col);
        SdfRenderer.circle(fcx, fcy, hubR, col);
        int teeth = 6;
        for (int i = 0; i < teeth; i++) {
            float ang = (float) (i * (2 * Math.PI / teeth));
            float tx = fcx + (float) Math.cos(ang) * (ringR + toothLen * 0.4f);
            float ty = fcy + (float) Math.sin(ang) * (ringR + toothLen * 0.4f);
            SdfRenderer.boxRotated(Math.round(tx), Math.round(ty), Math.round(toothLen), Math.round(toothW),
                    toothW * 0.4f, ang, col, 0, 0f, 0, 0f);
        }
    }

    /** Language/globe: ring + a horizontal "equator" bar + a narrow vertical "meridian" bar. */
    public static void globe(DrawContext ctx, int cx, int cy, float r, int col) {
        int S = scale();
        ctx.draw();
        int fcx = Math.round(cx * S), fcy = Math.round(cy * S);
        float fr = r * S;
        SdfRenderer.ring(fcx, fcy, fr, Math.max(1f, fr * 0.16f), col);
        SdfRenderer.box(Math.round(fcx - fr), Math.round(fcy - fr * 0.08f), Math.round(fr * 2), Math.max(1, Math.round(fr * 0.16f)),
                fr * 0.08f, col, 0, 0f, 0, 0f);
        float mw = fr * 0.5f;
        SdfRenderer.box(Math.round(fcx - mw * 0.13f), Math.round(fcy - fr), Math.max(1, Math.round(mw * 0.26f)), Math.round(fr * 2),
                mw * 0.13f, col, 0, 0f, 0, 0f);
    }

    /** Quit: two rotated bars crossing at ±45°. */
    public static void x(DrawContext ctx, int cx, int cy, float r, int col) {
        int S = scale();
        ctx.draw();
        int fcx = Math.round(cx * S), fcy = Math.round(cy * S);
        float fr = r * S;
        int len = Math.round(fr * 1.5f), w = Math.max(2, Math.round(fr * 0.24f));
        SdfRenderer.boxRotated(fcx, fcy, len, w, w * 0.4f, (float) (Math.PI / 4), col, 0, 0f, 0, 0f);
        SdfRenderer.boxRotated(fcx, fcy, len, w, w * 0.4f, (float) (-Math.PI / 4), col, 0, 0f, 0, 0f);
    }

    /** Light-theme icon: filled hub + 8 short radiating rays. */
    public static void sun(DrawContext ctx, int cx, int cy, float r, int col) {
        int S = scale();
        ctx.draw();
        int fcx = Math.round(cx * S), fcy = Math.round(cy * S);
        float fr = r * S;
        float hubR = fr * 0.42f, rayLen = fr * 0.34f, rayW = fr * 0.16f;
        SdfRenderer.circle(fcx, fcy, hubR, col);
        int rays = 8;
        for (int i = 0; i < rays; i++) {
            float ang = (float) (i * (2 * Math.PI / rays));
            float rx = fcx + (float) Math.cos(ang) * (hubR + rayLen * 0.5f + fr * 0.08f);
            float ry = fcy + (float) Math.sin(ang) * (hubR + rayLen * 0.5f + fr * 0.08f);
            SdfRenderer.boxRotated(Math.round(rx), Math.round(ry), Math.round(rayLen), Math.round(rayW),
                    rayW * 0.5f, ang, col, 0, 0f, 0, 0f);
        }
    }

    /** Dark-theme icon: a crescent — filled circle, then a second filled circle in the button's
     *  own background colour offset up-right to "bite" a chunk out (no boolean-subtract op in
     *  the shader, so this fakes it the same way the old baked-PNG generator did). */
    public static void moon(DrawContext ctx, int cx, int cy, float r, int col, int bgCol) {
        int S = scale();
        ctx.draw();
        int fcx = Math.round(cx * S), fcy = Math.round(cy * S);
        float fr = r * S * 0.62f;
        SdfRenderer.circle(fcx, fcy, fr, col);
        SdfRenderer.circle(Math.round(fcx + fr * 0.55f), Math.round(fcy - fr * 0.35f), fr * 0.92f, bgCol);
    }

    /** Customize Colors icon: 4 small dots in a diamond. */
    public static void dots(DrawContext ctx, int cx, int cy, float r, int col) {
        int S = scale();
        ctx.draw();
        int fcx = Math.round(cx * S), fcy = Math.round(cy * S);
        float fr = r * S;
        float d = fr * 0.5f, dotR = fr * 0.16f;
        int[][] offs = {{1, -1}, {1, 1}, {-1, -1}, {-1, 1}};
        for (int[] o : offs) {
            SdfRenderer.circle(Math.round(fcx + o[0] * d), Math.round(fcy + o[1] * d), dotR, col);
        }
    }
}

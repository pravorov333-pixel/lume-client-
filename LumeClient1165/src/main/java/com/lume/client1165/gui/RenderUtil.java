package com.lume.client1165.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Drawing helpers for the 1.16.5 build. 1.16.5 has no {@code DrawContext}, so we
 * render through {@link MatrixStack} + {@link DrawableHelper#fill}. Provides the
 * same anti-aliased rounded-rect / glow / gradient / logo primitives as the 1.21
 * build, plus vanilla-font text helpers (no custom LumeFont yet on this build).
 *
 * <p>Extends DrawableHelper so the inherited {@code fill(MatrixStack,...)} is
 * reachable from these static helpers.
 */
public final class RenderUtil extends DrawableHelper {

    private RenderUtil() {}

    /** Raw filled rectangle (alpha-blended). */
    public static void rect(MatrixStack ms, int x1, int y1, int x2, int y2, int color) {
        fill(ms, x1, y1, x2, y2, color);
    }

    // --- text (vanilla font) ------------------------------------------------

    public static void text(MatrixStack ms, TextRenderer tr, String s, double x, double y, int color, boolean shadow) {
        if (shadow) tr.drawWithShadow(ms, s, (float) x, (float) y, color);
        else tr.draw(ms, s, (float) x, (float) y, color);
    }

    public static void textScaled(MatrixStack ms, TextRenderer tr, String s, double x, double y, int color, float scale, boolean shadow) {
        ms.push();
        ms.translate(x, y, 0);
        ms.scale(scale, scale, 1f);
        if (shadow) tr.drawWithShadow(ms, s, 0, 0, color);
        else tr.draw(ms, s, 0, 0, color);
        ms.pop();
    }

    public static int width(TextRenderer tr, String s) { return tr.getWidth(s); }

    /** Draw text vertically centred inside [boxY, boxY+boxH]. */
    public static void textVCentered(MatrixStack ms, TextRenderer tr, String s, double x, double boxY, double boxH, int color) {
        double y = boxY + boxH / 2.0 - 4.0; // vanilla glyphs ~7-8px tall
        text(ms, tr, s, x, y, color, false);
    }

    /** Draw text centred both ways inside the given box. */
    public static void textCentered(MatrixStack ms, TextRenderer tr, String s, double boxX, double boxY, double boxW, double boxH, int color) {
        int w = tr.getWidth(s);
        textVCentered(ms, tr, s, boxX + (boxW - w) / 2.0, boxY, boxH, color);
    }

    // --- shapes -------------------------------------------------------------

    private static int lerp(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = (int) (aa + (ba - aa) * t), or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t), ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    public static void roundedRect(MatrixStack ms, int x, int y, int w, int h, int r, int color) {
        roundedRectRaw(ms, x, y, w, h, r, color);
    }

    // Anti-aliased rounded rect: straight middle in ONE fill, only the 2*r corner
    // rows drawn per-line with sub-pixel edge coverage. Cheap + smooth corners.
    private static void roundedRectRaw(MatrixStack ms, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0) { rect(ms, x, y, x + w, y + h, color); return; }
        r = Math.min(r, Math.min(w, h) / 2);
        int baseA = (color >>> 24) & 0xFF, rgb = color & 0xFFFFFF;

        rect(ms, x, y + r, x + w, y + h - r, color);

        for (int j = 0; j < r; j++) {
            float dy = r - 0.5f - j;
            float insetF = r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            int solid = (int) Math.ceil(insetF);
            int x1 = x + solid, x2 = x + w - solid;
            int yt = y + j, yb = y + h - 1 - j;
            if (x1 < x2) {
                rect(ms, x1, yt, x2, yt + 1, color);
                rect(ms, x1, yb, x2, yb + 1, color);
            }
            float cov = solid - insetF;
            if (cov > 0.02f && solid >= 1) {
                int pc = (Math.round(baseA * cov) << 24) | rgb;
                rect(ms, x + solid - 1, yt, x + solid, yt + 1, pc);
                rect(ms, x + w - solid, yt, x + w - solid + 1, yt + 1, pc);
                rect(ms, x + solid - 1, yb, x + solid, yb + 1, pc);
                rect(ms, x + w - solid, yb, x + w - solid + 1, yb + 1, pc);
            }
        }
    }

    /** Anti-aliased rounded rect filled with a vertical gradient (c1 top → c2 bottom). */
    public static void gradientRoundedRect(MatrixStack ms, int x, int y, int w, int h, int r, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < h; i++) {
            int yy = y + i;
            float t = h > 1 ? (float) i / (h - 1) : 0;
            int col = lerp(c1, c2, t);
            float insetF = -1f;
            if (i < r) {
                float dy = r - 0.5f - i;
                insetF = r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            } else if (i >= h - r) {
                float dy = i - (h - r) + 0.5f;
                insetF = r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            }
            if (insetF < 0f) { rect(ms, x, yy, x + w, yy + 1, col); continue; }
            int solid = (int) Math.ceil(insetF);
            if (x + solid < x + w - solid) rect(ms, x + solid, yy, x + w - solid, yy + 1, col);
            float cov = solid - insetF;
            if (cov > 0.02f && solid >= 1) {
                int pc = (Math.round(((col >>> 24) & 0xFF) * cov) << 24) | (col & 0xFFFFFF);
                rect(ms, x + solid - 1, yy, x + solid, yy + 1, pc);
                rect(ms, x + w - solid, yy, x + w - solid + 1, yy + 1, pc);
            }
        }
    }

    /** Soft glow halo: several expanding translucent layers (capped for performance). */
    public static void glow(MatrixStack ms, int x, int y, int w, int h, int r, int rgb, int strength) {
        int n = Math.min(strength, 5);
        if (n < 1) return;
        for (int k = n; k >= 1; k--) {
            int i = Math.max(1, strength * k / n);
            int a = Math.max(2, 16 - k * 3);
            roundedRectRaw(ms, x - i, y - i, w + 2 * i, h + 2 * i, r + i, (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** The Lume logo mark: lavender gradient rounded square + white "L" + spark dot. */
    public static void drawLogo(MatrixStack ms, int x, int y, int s) {
        gradientRoundedRect(ms, x, y, s, s, Math.max(4, s / 4), 0xFFB7AAD9, 0xFF8E7FC0);
        int barW = Math.max(2, s / 7);
        int lx = x + s * 3 / 10;
        int top = y + s * 28 / 100;
        int bot = y + s * 72 / 100;
        roundedRect(ms, lx, top, barW, bot - top, 1, 0xFFFFFFFF);
        roundedRect(ms, lx, bot - barW, s * 2 / 5, barW, 1, 0xFFFFFFFF);
        int dot = Math.max(2, s / 6);
        roundedRect(ms, x + s * 60 / 100, y + s * 20 / 100, dot, dot, dot / 2, 0xFFFFFFFF);
    }
}

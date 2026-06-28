package com.lume.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Small drawing helpers. Vanilla DrawContext has no rounded-rect primitive,
 * so we approximate one by filling each scan-line with a corner inset.
 * Also provides the Lume custom font (Inter) rendered with 2x supersampling:
 * the font is rasterised at high resolution (size 18) and drawn at half scale,
 * giving crisp, smooth glyphs instead of the blurry/ragged vanilla TTF scaling.
 */
public final class RenderUtil {

    /** The bundled Inter font, defined in assets/lume/font/main.json. */
    public static final Identifier FONT = Identifier.of("lume", "main");

    /** Draw scale — font json size (18) * this = on-screen text height (~9px). */
    public static final float FONT_SCALE = 0.5f;

    private RenderUtil() {}

    /** Wraps a string in the Lume font so it renders with Inter, not the vanilla font. */
    public static Text t(String s) {
        return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT));
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, double x, double y, int color, boolean shadow) {
        text(ctx, tr, s, x, y, color, shadow, FONT_SCALE);
    }

    public static int width(TextRenderer tr, String s) {
        return width(tr, s, FONT_SCALE);
    }

    /**
     * Draw text at a custom scale. Uses the custom LumeFont renderer when ready,
     * otherwise falls back to the vanilla TTF font so nothing ever breaks.
     */
    /** True if the string has any Cyrillic — those render with the vanilla font (LumeFont/Poppins has no Cyrillic). */
    public static boolean hasCyrillic(String s) {
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= 0x400 && c <= 0x4FF) return true; }
        return false;
    }

    public static void text(DrawContext ctx, TextRenderer tr, String s, double x, double y, int color, boolean shadow, float scale) {
        if (hasCyrillic(s)) { vanillaText(ctx, tr, s, x, y, color, scale * 2f); return; }   // Cyrillic → vanilla font, size-matched
        LumeFont.ensure();
        if (LumeFont.ready) {
            LumeFont.draw(ctx, s, x, y, color, scale * (18f / LumeFont.FONT_PX));
            return;
        }
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0.0);
        m.scale(scale, scale, 1.0f);
        ctx.drawText(tr, t(s), 0, 0, color, shadow);
        m.pop();
    }

    /**
     * Draw text left-aligned at {@code x}, vertically centred inside a box that
     * spans [boxY, boxY+boxH]. Uses the font's measured optical centre so the
     * glyph body — not the padded atlas cell — sits in the middle.
     */
    public static void textVCentered(DrawContext ctx, TextRenderer tr, String s, double x, double boxY, double boxH, int color, float scale) {
        LumeFont.ensure();
        double y;
        if (LumeFont.ready) {
            double ds = scale * (18f / LumeFont.FONT_PX);
            y = boxY + boxH / 2.0 - LumeFont.opticalCenterPx() * ds;
        } else {
            y = boxY + boxH / 2.0 - 3.5 * scale; // vanilla glyphs are ~7px tall
        }
        text(ctx, tr, s, x, y, color, false, scale);
    }

    /** Draw text centred both horizontally and vertically inside the given box. */
    public static void textCentered(DrawContext ctx, TextRenderer tr, String s, double boxX, double boxY, double boxW, double boxH, int color, float scale) {
        int w = width(tr, s, scale);
        textVCentered(ctx, tr, s, boxX + (boxW - w) / 2.0, boxY, boxH, color, scale);
    }

    public static int width(TextRenderer tr, String s, float scale) {
        if (hasCyrillic(s)) return Math.round(tr.getWidth(s) * scale * 2f);
        LumeFont.ensure();
        if (LumeFont.ready) {
            return Math.round(LumeFont.advance(s) * scale * (18f / LumeFont.FONT_PX));
        }
        return Math.round(tr.getWidth(t(s)) * scale);
    }

    /** Draw text with the VANILLA font (has Cyrillic) at a scale — for Russian HUD content. */
    public static void vanillaText(DrawContext ctx, TextRenderer tr, String s, double x, double y, int color, float scale) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        ctx.drawText(tr, s, 0, 0, color, false);
        m.pop();
    }

    public static int vanillaWidth(TextRenderer tr, String s, float scale) {
        return Math.round(tr.getWidth(s) * scale);
    }

    private static int lerp(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = (int) (aa + (ba - aa) * t), or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t), ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** Anti-aliased rounded rect filled with a vertical gradient (c1 top → c2 bottom). */
    public static void gradientRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int c1, int c2) {
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
            if (insetF < 0f) { ctx.fill(x, yy, x + w, yy + 1, col); continue; }
            int solid = (int) Math.ceil(insetF);
            if (x + solid < x + w - solid) ctx.fill(x + solid, yy, x + w - solid, yy + 1, col);
            float cov = solid - insetF;
            if (cov > 0.02f && solid >= 1) {
                int pc = (Math.round(((col >>> 24) & 0xFF) * cov) << 24) | (col & 0xFFFFFF);
                ctx.fill(x + solid - 1, yy, x + solid, yy + 1, pc);
                ctx.fill(x + w - solid, yy, x + w - solid + 1, yy + 1, pc);
            }
            continue;
        }
    }

    /** The Lume logo mark: gradient rounded square + white "L" + spark dot. */
    public static void drawLogo(DrawContext ctx, int x, int y, int s) {
        gradientRoundedRect(ctx, x, y, s, s, Math.max(4, s / 4), 0xFFB7AAD9, 0xFF8E7FC0);
        int barW = Math.max(2, s / 7);
        int lx = x + s * 3 / 10;
        int top = y + s * 28 / 100;
        int bot = y + s * 72 / 100;
        roundedRect(ctx, lx, top, barW, bot - top, 1, 0xFFFFFFFF);
        roundedRect(ctx, lx, bot - barW, s * 2 / 5, barW, 1, 0xFFFFFFFF);
        int dot = Math.max(2, s / 6);
        roundedRect(ctx, x + s * 60 / 100, y + s * 20 / 100, dot, dot, dot / 2, 0xFFFFFFFF);
    }

    /**
     * Soft glow halo around a rounded rect: several expanding translucent layers.
     * rgb is the glow colour (low 24 bits); strength = number of layers.
     */
    public static void glow(DrawContext ctx, int x, int y, int w, int h, int r, int rgb, int strength) {
        int n = Math.min(strength, 5); // cap layers for performance
        if (n < 1) return;
        for (int k = n; k >= 1; k--) {
            int i = Math.max(1, strength * k / n); // spread layers across full glow width
            int a = Math.max(2, 16 - k * 3);
            roundedRectRaw(ctx, x - i, y - i, w + 2 * i, h + 2 * i, r + i, (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    /**
     * Strong radial glow centred at (cx,cy) but <b>contained</b> inside the clip
     * rectangle — every layer is clamped to [clipX..clipX+clipW, clipY..clipY+clipH]
     * so the light never spills outside the button. Brightest in the middle,
     * fading out; {@code intensity} (0..1) scales the whole effect for animation.
     */
    public static void containedGlow(DrawContext ctx, int clipX, int clipY, int clipW, int clipH,
                                     int cx, int cy, int radius, int rgb, float intensity) {
        if (intensity <= 0.01f || radius <= 0) return;
        int layers = 5;
        for (int i = layers; i >= 1; i--) {
            float f = i / (float) layers;                 // 1 = outer/faint, →0 = inner/bright
            int rr = Math.round(radius * f);
            if (rr < 1) continue;
            int a = Math.round(intensity * (1f - f) * 120f);
            if (a <= 2) continue;
            int x1 = Math.max(cx - rr, clipX);
            int y1 = Math.max(cy - rr, clipY);
            int x2 = Math.min(cx + rr, clipX + clipW);
            int y2 = Math.min(cy + rr, clipY + clipH);
            if (x2 <= x1 || y2 <= y1) continue;
            roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, Math.min(rr, Math.min(x2 - x1, y2 - y1) / 2), (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        roundedRectRaw(ctx, x, y, w, h, r, color);
    }

    // Anti-aliased rounded rect, optimised: the straight middle is ONE fill,
    // only the 2*r corner rows are drawn per-line (with sub-pixel AA). This cuts
    // the per-frame fill count massively versus filling every scanline.
    private static void roundedRectRaw(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, color); return; }
        r = Math.min(r, Math.min(w, h) / 2);
        int baseA = (color >>> 24) & 0xFF, rgb = color & 0xFFFFFF;

        // straight middle in a single fill
        ctx.fill(x, y + r, x + w, y + h - r, color);

        // corner rows (top + mirrored bottom share the same inset)
        for (int j = 0; j < r; j++) {
            float dy = r - 0.5f - j;
            float insetF = r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            int solid = (int) Math.ceil(insetF);
            int x1 = x + solid, x2 = x + w - solid;
            int yt = y + j, yb = y + h - 1 - j;
            if (x1 < x2) {
                ctx.fill(x1, yt, x2, yt + 1, color);
                ctx.fill(x1, yb, x2, yb + 1, color);
            }
            float cov = solid - insetF;
            if (cov > 0.02f && solid >= 1) {
                int pc = (Math.round(baseA * cov) << 24) | rgb;
                ctx.fill(x + solid - 1, yt, x + solid, yt + 1, pc);
                ctx.fill(x + w - solid, yt, x + w - solid + 1, yt + 1, pc);
                ctx.fill(x + solid - 1, yb, x + solid, yb + 1, pc);
                ctx.fill(x + w - solid, yb, x + w - solid + 1, yb + 1, pc);
            }
        }
    }
}

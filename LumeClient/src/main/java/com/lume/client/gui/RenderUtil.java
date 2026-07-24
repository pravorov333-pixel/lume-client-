package com.lume.client.gui;

import com.lume.client.Config;
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
        text(ctx, tr, s, x, vCenterY(s, boxY, boxH, scale, false), color, false, scale);
    }

    /** Draw text centred both horizontally and vertically inside the given box. */
    public static void textCentered(DrawContext ctx, TextRenderer tr, String s, double boxX, double boxY, double boxW, double boxH, int color, float scale) {
        int w = width(tr, s, scale);
        textVCentered(ctx, tr, s, boxX + (boxW - w) / 2.0, boxY, boxH, color, scale);
    }

    /**
     * Vertical-centring offset shared by {@code textVCentered}/{@code textBoldCentered} — split
     * out because it MUST branch on {@link #hasCyrillic} the same way {@link #text}/{@link
     * #textBold} do: a Cyrillic string doesn't actually render through LumeFont at all (it
     * routes to the vanilla font at {@code scale*2f}, see {@link #text}), so centring it with
     * LumeFont's own optical-centre metric put it visibly off-centre — the fix for the "text
     * is buggy in the new catalogs" report, which is Cyrillic-heavy (server/event/profile text)
     * where the old fallback path barely had any Cyrillic before.
     */
    private static double vCenterY(String s, double boxY, double boxH, float scale, boolean bold) {
        if (hasCyrillic(s)) {
            // vanilla font at scale*2f (see text()/textBold()'s Cyrillic branch) — half its
            // ~7px cap height at that doubled scale, i.e. 2x the plain-vanilla-fallback constant
            // below (bold uses MC's own synthetic-bold vanilla style, same metrics as regular).
            return boxY + boxH / 2.0 - 7.0 * scale;
        }
        if (bold) {
            LumeFontBold.ensure();
            if (LumeFontBold.ready) {
                double ds = scale * (18f / LumeFontBold.FONT_PX);
                return boxY + boxH / 2.0 - LumeFontBold.opticalCenterPx() * ds;
            }
        }
        LumeFont.ensure();
        if (LumeFont.ready) {
            double ds = scale * (18f / LumeFont.FONT_PX);
            return boxY + boxH / 2.0 - LumeFont.opticalCenterPx() * ds;
        }
        return boxY + boxH / 2.0 - 3.5 * scale; // vanilla glyphs are ~7px tall
    }

    /** Draw text with the VANILLA font's own synthetic bold style (has Cyrillic, and MC's
     *  built-in bold is a real per-glyph technique — not a redraw-offset hack). */
    public static void vanillaTextBold(DrawContext ctx, TextRenderer tr, String s, double x, double y, int color, float scale) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0);
        m.scale(scale, scale, 1f);
        ctx.drawText(tr, Text.literal(s).setStyle(Style.EMPTY.withBold(true)), 0, 0, color, false);
        m.pop();
    }

    /** Bold text — draws through a real bold glyph atlas ({@link LumeFontBold}, rasterised from
     *  the bundled {@code montserrat-bold.ttf}) or the vanilla font's own synthetic-bold style
     *  for Cyrillic, matching {@link #text}'s routing exactly. Falls back to the old sub-pixel
     *  triple-draw trick only if the bold atlas failed to load on this GPU/JVM. */
    public static void textBold(DrawContext ctx, TextRenderer tr, String s, double x, double y, int color, float scale) {
        if (hasCyrillic(s)) { vanillaTextBold(ctx, tr, s, x, y, color, scale * 2f); return; }
        LumeFontBold.ensure();
        if (LumeFontBold.ready) {
            LumeFontBold.draw(ctx, s, x, y, color, scale * (18f / LumeFontBold.FONT_PX));
            return;
        }
        text(ctx, tr, s, x, y, color, false, scale);
        text(ctx, tr, s, x + 0.4, y, color, false, scale);
        text(ctx, tr, s, x, y + 0.35, color, false, scale);
    }

    /** Bold + centred both horizontally and vertically inside the given box. */
    public static void textBoldCentered(DrawContext ctx, TextRenderer tr, String s, double boxX, double boxY, double boxW, double boxH, int color, float scale) {
        int w = widthBold(tr, s, scale);
        double x = boxX + (boxW - w) / 2.0;
        double y = vCenterY(s, boxY, boxH, scale, true);
        textBold(ctx, tr, s, x, y, color, scale);
    }

    // ---- SDF text path -----------------------------------------------------------------
    // Routes through com.lume.client.nanovg.SdfTextRenderer — a real per-glyph Signed Distance
    // Field font atlas (genuinely crisp at any scale, not a plain alpha-blended texture atlas
    // like LumeFont/LumeFontBold above), built for ClickGuiScreen's own pan/zoom window space.
    // Its drawWindowLocal formula collapses to a straight logical->framebuffer conversion when
    // given an IDENTITY window-local transform (offX=0,offY=0,cx=0,cy=0,total=1), which is what
    // lets a non-windowed overlay like LumeTitleMenu reuse it correctly without touching that
    // file. Falls back to textBold/text (LumeFontBold/LumeFont) if the SDF text shader failed to
    // init on this GPU — same defensive fallback chain every raw-GL helper here has.

    public static void sdfText(DrawContext ctx, TextRenderer tr, String s, double x, double y, int color, float scale, boolean bold) {
        if (com.lume.client.nanovg.SdfTextRenderer.ensureInit()) {
            int S = (int) Math.max(1, net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor());
            ctx.draw();
            float drawScale = scale * (18f / com.lume.client.nanovg.SdfTextRenderer.FONT_PX) * S;
            com.lume.client.nanovg.SdfTextRenderer.drawWindowLocal(0, 0, 0, 0, 1f, x * S, y * S, drawScale, s, color, bold);
        } else if (bold) {
            textBold(ctx, tr, s, x, y, color, scale);
        } else {
            text(ctx, tr, s, x, y, color, false, scale);
        }
    }

    public static int sdfWidth(TextRenderer tr, String s, float scale, boolean bold) {
        if (com.lume.client.nanovg.SdfTextRenderer.ensureInit()) {
            float adv = com.lume.client.nanovg.SdfTextRenderer.advance(s, bold);
            return Math.round(adv * scale * (18f / com.lume.client.nanovg.SdfTextRenderer.FONT_PX));
        }
        return bold ? widthBold(tr, s, scale) : width(tr, s, scale);
    }

    private static double sdfVCenterY(String s, double boxY, double boxH, float scale, boolean bold) {
        if (com.lume.client.nanovg.SdfTextRenderer.ensureInit()) {
            double ds = scale * (18f / com.lume.client.nanovg.SdfTextRenderer.FONT_PX);
            return boxY + boxH / 2.0 - com.lume.client.nanovg.SdfTextRenderer.opticalCenterPx(bold) * ds;
        }
        return vCenterY(s, boxY, boxH, scale, bold);
    }

    public static void sdfTextVCentered(DrawContext ctx, TextRenderer tr, String s, double x, double boxY, double boxH, int color, float scale, boolean bold) {
        sdfText(ctx, tr, s, x, sdfVCenterY(s, boxY, boxH, scale, bold), color, scale, bold);
    }

    public static void sdfTextCentered(DrawContext ctx, TextRenderer tr, String s, double boxX, double boxY, double boxW, double boxH, int color, float scale, boolean bold) {
        int w = sdfWidth(tr, s, scale, bold);
        sdfTextVCentered(ctx, tr, s, boxX + (boxW - w) / 2.0, boxY, boxH, color, scale, bold);
    }

    public static int width(TextRenderer tr, String s, float scale) {
        if (hasCyrillic(s)) return Math.round(tr.getWidth(s) * scale * 2f);
        LumeFont.ensure();
        if (LumeFont.ready) {
            return Math.round(LumeFont.advance(s) * scale * (18f / LumeFont.FONT_PX));
        }
        return Math.round(tr.getWidth(t(s)) * scale);
    }

    /** {@link #width}, but measuring the bold face (LumeFontBold's advances differ slightly
     *  from Medium's) — needed for accurate horizontal centring in {@link #textBoldCentered}. */
    public static int widthBold(TextRenderer tr, String s, float scale) {
        if (hasCyrillic(s)) {
            return Math.round(tr.getWidth(Text.literal(s).setStyle(Style.EMPTY.withBold(true))) * scale * 2f);
        }
        LumeFontBold.ensure();
        if (LumeFontBold.ready) {
            return Math.round(LumeFontBold.advance(s) * scale * (18f / LumeFontBold.FONT_PX));
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

    /** Plain (non-rounded) horizontal gradient fill, left→right — for overlaying on top of an
     *  already-rounded base rect (the color-picker SV square's white→transparent sweep), where a
     *  handful of unrounded corner pixels at the very edge are imperceptible at this size. Vanilla
     *  {@code DrawContext.fillGradient} only interpolates vertically, hence this column-by-column
     *  version instead of reusing it. */
    public static void hGradientRect(DrawContext ctx, int x, int y, int w, int h, int argbLeft, int argbRight) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < w; i++) {
            float t = w > 1 ? (float) i / (w - 1) : 0;
            int col = lerp(argbLeft, argbRight, t);
            ctx.fill(x + i, y, x + i + 1, y + h, col);
        }
    }

    /** Anti-aliased rounded rect filled with a vertical gradient (c1 top → c2 bottom). */
    public static void gradientRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        // Ultra Performance: one flat fill (top colour) instead of an h-row per-scanline lerp —
        // same panel, same spot, no gradient/rounding cost. See Config#ultra().
        if (Config.ultra()) { ctx.fill(x, y, x + w, y + h, c1); return; }
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

    /**
     * The Lume "Glass Star" logo mark — rebranded to a single 4-point sparkle with a soft glow
     * halo behind it (was: a big sparkle + a smaller secondary sparkle pinned to its tip — that
     * secondary sparkle is gone, per the new Figma reference). Same shape/coordinates as
     * {@link com.lume.client.nanovg.NanoVgRenderer#logoMark} and the launcher's SVG mark, rebuilt
     * here via scanline polygon fill since DrawContext has no curved-path API — each quadratic-
     * bezier "petal" is densely sampled into straight segments first, which is invisible at this
     * icon's size. Used by the "Menu Logo" HUD watermark and the (effectively unreachable)
     * pre-NanoVG ClickGUI fallback.
     */
    /** Live SDF version of the glass-star mark (see {@code SdfRenderer.star}) — genuinely
     *  GPU-rasterised at any scale, not a CPU scanline polygon fill like {@link #drawLogo}. Flat
     *  accent fill + a thin light outline (approximates the vector version's white stroke; the
     *  gradient nuance isn't reproduced). Falls back to {@link #drawLogo} if the shader failed to
     *  init on this GPU — same defensive pattern every raw-GL helper here uses.
     *
     * @param cx,cy  DrawContext logical px, CENTRE (not top-left — {@link #drawLogo} is top-left,
     *               this one isn't, since {@code SdfRenderer.star} is centre-based like every
     *               other icon primitive)
     * @param r      tip-to-centre radius, logical px
     */
    public static void drawLogoSdf(DrawContext ctx, int cx, int cy, float r) {
        if (com.lume.client.nanovg.SdfRenderer.ensureInit()) {
            int S = (int) Math.max(1, net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaleFactor());
            ctx.draw();
            com.lume.client.nanovg.SdfRenderer.star(Math.round(cx * S), Math.round(cy * S), r * S,
                    Theme.accent(), 0xB0FFFFFF, 1.4f * S, 0, 0f);
        } else {
            drawLogo(ctx, Math.round(cx - r), Math.round(cy - r), Math.round(r * 2f));
        }
    }

    public static void drawLogo(DrawContext ctx, int x, int y, int s) {
        float u = s / 100f;
        // Follows the current accent (see NanoVgRenderer.logoMark, same relationship) instead
        // of a fixed lavender, so Customize Colors re-tints this fallback too.
        int bigC1 = Theme.accent(), bigC2 = Theme.accent2();
        // Soft glass glow: the SAME sparkle outline, scaled up ~18% around its own centre (42,46
        // in the shared 0..100 mark space) and filled at low alpha — a cheap stand-in for a real
        // blur (DrawContext has none), same layered-translucent-copy trick used everywhere else
        // in this codebase for glow (see RenderUtil.glow).
        float gu = u * 1.18f;
        float gx = x + 42 * u - 42 * gu, gy = y + 46 * u - 46 * gu;
        float[][] glow = sparkleOutline(gx, gy, gu, 42, 16, 48.4f, 39.6f, 72, 46, 48.4f, 52.4f, 42, 76, 35.6f, 52.4f, 12, 46, 35.6f, 39.6f);
        int glowCol = 0x40000000 | (Theme.accentRgb() & 0xFFFFFF);
        fillPolygon(ctx, glow[0], glow[1], glowCol);
        float[][] big = sparkleOutline(x, y, u, 42, 16, 48.4f, 39.6f, 72, 46, 48.4f, 52.4f, 42, 76, 35.6f, 52.4f, 12, 46, 35.6f, 39.6f);
        fillPolygonGradient(ctx, big[0], big[1], bigC1, bigC2);
    }

    /** Densely samples the sparkle's 4 quadratic-bezier "petals" (tip → control → tip, ×4)
     *  into a closed straight-edge polygon, in the shared 0..100 mark space scaled by u and
     *  offset by (x,y) — same coordinate convention as NanoVgRenderer's beginSparklePath. */
    private static float[][] sparkleOutline(float x, float y, float u,
                                             float t1x, float t1y, float c1x, float c1y,
                                             float t2x, float t2y, float c2x, float c2y,
                                             float t3x, float t3y, float c3x, float c3y,
                                             float t4x, float t4y, float c4x, float c4y) {
        java.util.List<float[]> pts = new java.util.ArrayList<>();
        int steps = 8;
        sampleQuad(pts, x + t1x * u, y + t1y * u, x + c1x * u, y + c1y * u, x + t2x * u, y + t2y * u, steps);
        sampleQuad(pts, x + t2x * u, y + t2y * u, x + c2x * u, y + c2y * u, x + t3x * u, y + t3y * u, steps);
        sampleQuad(pts, x + t3x * u, y + t3y * u, x + c3x * u, y + c3y * u, x + t4x * u, y + t4y * u, steps);
        sampleQuad(pts, x + t4x * u, y + t4y * u, x + c4x * u, y + c4y * u, x + t1x * u, y + t1y * u, steps);
        float[] xs = new float[pts.size()], ys = new float[pts.size()];
        for (int i = 0; i < pts.size(); i++) { xs[i] = pts.get(i)[0]; ys[i] = pts.get(i)[1]; }
        return new float[][]{ xs, ys };
    }

    private static void sampleQuad(java.util.List<float[]> out, float p0x, float p0y, float cx, float cy, float p1x, float p1y, int steps) {
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps, mt = 1 - t;
            float x = mt * mt * p0x + 2 * mt * t * cx + t * t * p1x;
            float y = mt * mt * p0y + 2 * mt * t * cy + t * t * p1y;
            out.add(new float[]{ x, y });
        }
    }

    /** Fills an arbitrary closed polygon (even-odd rule) via horizontal scanlines — DrawContext's
     *  only real primitive is the axis-aligned rect fill, so this is how anything curved/irregular
     *  (the sparkle logo) has to be drawn. */
    private static void fillPolygon(DrawContext ctx, float[] xs, float[] ys, int color) {
        fillPolygonGradient(ctx, xs, ys, color, color);
    }

    /** Same as {@link #fillPolygon} but lerps top→bottom between two colours per scanline row —
     *  an approximation of the sparkle's true diagonal gradient (this file's rect gradient helper
     *  above is vertical-only too; close enough at icon size that the difference isn't visible). */
    private static void fillPolygonGradient(DrawContext ctx, float[] xs, float[] ys, int colorTop, int colorBot) {
        int n = xs.length;
        if (n < 3) return;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float v : ys) { if (v < minY) minY = v; if (v > maxY) maxY = v; }
        int y0 = (int) Math.floor(minY), y1 = (int) Math.ceil(maxY);
        float[] xCross = new float[n];
        for (int y = y0; y < y1; y++) {
            float sy = y + 0.5f;
            int cnt = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float ya = ys[i], yb = ys[j];
                if ((ya <= sy && yb > sy) || (yb <= sy && ya > sy)) {
                    float t = (sy - ya) / (yb - ya);
                    xCross[cnt++] = xs[i] + t * (xs[j] - xs[i]);
                }
            }
            java.util.Arrays.sort(xCross, 0, cnt);
            float rowT = maxY > minY ? (sy - minY) / (maxY - minY) : 0f;
            int color = lerp(colorTop, colorBot, rowT);
            for (int i = 0; i + 1 < cnt; i += 2) {
                int xa = Math.round(xCross[i]), xb = Math.round(xCross[i + 1]);
                if (xb > xa) ctx.fill(xa, y, xb, y + 1, color);
            }
        }
    }

    /**
     * Soft glow halo around a rounded rect: several expanding translucent layers.
     * rgb is the glow colour (low 24 bits); strength = number of layers.
     */
    public static void glow(DrawContext ctx, int x, int y, int w, int h, int r, int rgb, int strength) {
        if (Config.ultra()) return;   // purely decorative halo — skip entirely, see Config#ultra()
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
        if (Config.ultra()) return;   // decorative cursor-follow light — skip entirely, see Config#ultra()
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

    /** Minimalist "face" glyph — black rounded-square background, two white dot eyes, one flat
     *  white mouth line. Used in place of a real skin-head render anywhere an account avatar is
     *  shown (Account Manager cards, the main-menu Account chip). Shared here so both callers
     *  stay pixel-identical instead of drifting into two hand-tuned copies. */
    public static void drawFace(DrawContext ctx, int x, int y, int size) {
        roundedRect(ctx, x, y, size, size, Math.round(size * 0.22f), 0xFF000000);
        int eyeR = Math.max(1, Math.round(size * 0.09f));
        int eyeY = y + Math.round(size * 0.4f);
        int eyeDX = Math.round(size * 0.24f);
        roundedRect(ctx, x + size / 2 - eyeDX - eyeR, eyeY - eyeR, eyeR * 2, eyeR * 2, eyeR, 0xFFFFFFFF);
        roundedRect(ctx, x + size / 2 + eyeDX - eyeR, eyeY - eyeR, eyeR * 2, eyeR * 2, eyeR, 0xFFFFFFFF);
        int mouthW = Math.round(size * 0.4f), mouthH = Math.max(1, Math.round(size * 0.07f));
        int mouthY = y + Math.round(size * 0.66f);
        roundedRect(ctx, x + size / 2 - mouthW / 2, mouthY, mouthW, mouthH, mouthH / 2, 0xFFFFFFFF);
    }

    /**
     * Premium interactive background: pixel-perfect SDF fill (see {@link
     * com.lume.client.nanovg.SdfRenderer}) with a soft glow that hugs the button's actual rounded
     * CONTOUR (not a rectangular halo) on hover — replaces the older baked-PNG glass look for
     * main-menu buttons. No lift any more (removed per explicit request — highlight only, still
     * contour-shaped). Falls back to a flat {@link #roundedRect} (no contour-glow, since {@link
     * #glow} is rectangular) if the shader failed to init on this GPU — same defensive fallback
     * every raw-GL helper here uses.
     *
     * @param x,y,w,h    DrawContext logical px (top-left), NOT framebuffer px — converted
     *                   internally via the window scale factor, same convention every other
     *                   caller in this file already uses.
     * @param hoverAmt   0..1 eased hover amount — drives the glow alpha/spread.
     */
    public static void premiumBg(DrawContext ctx, int x, int y, int w, int h, int radius,
                                  float hoverAmt, int fillArgb, int outlineArgb, int glowRgb) {
        if (com.lume.client.nanovg.SdfRenderer.ensureInit()) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            int S = (int) Math.max(1, mc.getWindow().getScaleFactor());
            int glowA = Math.round(hoverAmt * 200f);
            int glow = (glowA << 24) | (glowRgb & 0xFFFFFF);
            ctx.draw(); // flush queued DrawContext content before this raw-GL write — see ClickGuiScreen's own SdfRenderer calls
            com.lume.client.nanovg.SdfRenderer.box(x * S, y * S, w * S, h * S, radius * S,
                    fillArgb, outlineArgb, 1f * S, glow, (4f + hoverAmt * 8f) * S);
        } else {
            roundedRect(ctx, x, y, w, h, radius, fillArgb);
            if (outlineArgb != 0) strokeRoundedRect(ctx, x, y, w, h, radius, 1, outlineArgb);
            if (hoverAmt > 0.02f) glow(ctx, x, y, w, h, radius, glowRgb, Math.max(1, Math.round(hoverAmt * 5)));
        }
    }

    /** No-op now (lift removed from {@link #premiumBg} per explicit request — highlight only) —
     *  kept so every existing "content drawn on top of a premiumBg" call site doesn't need to
     *  change, it just always offsets by 0. */
    public static int premiumLift(float hoverAmt) {
        return 0;
    }

    /** Static panel background — SDF fill + outline (see {@link #premiumBg}), no hover/glow
     *  layer (panels aren't interactive buttons). The point of switching this off the old CPU
     *  {@link #roundedRect}/{@link #strokeRoundedRect} pair: the SDF outline is mathematically
     *  the SAME thickness all the way around, including the corners, whereas the CPU per-scanline
     *  coverage approximation those two used could read as uneven right at the curve. Falls back
     *  to the old CPU pair if the shader failed to init on this GPU. */
    public static void panelBg(DrawContext ctx, int x, int y, int w, int h, int radius, int fillArgb, int outlineArgb) {
        if (com.lume.client.nanovg.SdfRenderer.ensureInit()) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            int S = (int) Math.max(1, mc.getWindow().getScaleFactor());
            ctx.draw();
            com.lume.client.nanovg.SdfRenderer.box(x * S, y * S, w * S, h * S, radius * S,
                    fillArgb, outlineArgb, 1f * S, 0, 0f);
        } else {
            roundedRect(ctx, x, y, w, h, radius, fillArgb);
            if (outlineArgb != 0) strokeRoundedRect(ctx, x, y, w, h, radius, 1, outlineArgb);
        }
    }

    /** Anti-aliased rounded-rect OUTLINE of a given thickness — the NanoVG-removal equivalent of
     *  NanoVgRenderer#strokeRoundedRect (the bright glass rim). Drawn as an annulus: same per-row
     *  sub-pixel coverage math as {@link #roundedRectRaw}, but each row/column keeps only the band
     *  between the outer and inner rounded-rect edges instead of a solid fill. */
    public static void strokeRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int thickness, int color) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        int t = Math.min(thickness, Math.min(w, h) / 2);
        r = Math.min(r, Math.min(w, h) / 2);
        int innerR = Math.max(0, r - t);
        int baseA = (color >>> 24) & 0xFF, rgb = color & 0xFFFFFF;

        // straight edges (top/bottom/left/right bands, corners handled separately below)
        ctx.fill(x + r, y, x + w - r, y + t, color);                         // top
        ctx.fill(x + r, y + h - t, x + w - r, y + h, color);                 // bottom
        ctx.fill(x, y + r, x + t, y + h - r, color);                         // left
        ctx.fill(x + w - t, y + r, x + w, y + h - r, color);                 // right

        for (int j = 0; j < r; j++) {
            float dy = r - 0.5f - j;
            float outF = r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            float inF;
            if (j < innerR) {
                float idy = innerR - 0.5f - j;
                inF = innerR - (float) Math.sqrt(Math.max(0f, innerR * innerR - idy * idy));
            } else {
                inF = innerR;  // below the inner circle's row span — band runs to the inner box edge
            }
            int outSolid = (int) Math.ceil(outF);
            int inSolid = (int) Math.ceil(inF);
            int yt = y + j, yb = y + h - 1 - j;
            if (outSolid < inSolid) {
                ctx.fill(x + outSolid, yt, x + inSolid, yt + 1, color);
                ctx.fill(x + w - inSolid, yt, x + w - outSolid, yt + 1, color);
                ctx.fill(x + outSolid, yb, x + inSolid, yb + 1, color);
                ctx.fill(x + w - inSolid, yb, x + w - outSolid, yb + 1, color);
            }
            float cov = outSolid - outF;
            if (cov > 0.02f && outSolid >= 1) {
                int pc = (Math.round(baseA * cov) << 24) | rgb;
                ctx.fill(x + outSolid - 1, yt, x + outSolid, yt + 1, pc);
                ctx.fill(x + w - outSolid, yt, x + w - outSolid + 1, yt + 1, pc);
                ctx.fill(x + outSolid - 1, yb, x + outSolid, yb + 1, pc);
                ctx.fill(x + w - outSolid, yb, x + w - outSolid + 1, yb + 1, pc);
            }
        }
    }

    /** Plain sharp-cornered outline, exactly on {@code x,y,w,h} — four thin edge fills, no rounding/fill. */
    public static void strokeRect(DrawContext ctx, int x, int y, int w, int h, int thickness, int color) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        ctx.fill(x, y, x + w, y + thickness, color);                     // top
        ctx.fill(x, y + h - thickness, x + w, y + h, color);              // bottom
        ctx.fill(x, y + thickness, x + thickness, y + h - thickness, color);             // left
        ctx.fill(x + w - thickness, y + thickness, x + w, y + h - thickness, color);     // right
    }

    // Anti-aliased rounded rect, optimised: the straight middle is ONE fill,
    // only the 2*r corner rows are drawn per-line (with sub-pixel AA). This cuts
    // the per-frame fill count massively versus filling every scanline.
    private static void roundedRectRaw(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        // Ultra Performance: sharp rect, one fill — no per-corner AA loop. Same panel, flat
        // corners instead of rounded/glass. See Config#ultra().
        if (r <= 0 || Config.ultra()) { ctx.fill(x, y, x + w, y + h, color); return; }
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

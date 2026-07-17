package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;

/**
 * The one and only "LUME VISUALS" wordmark.
 *
 * <p>Every screen used to hand-roll its own copy ("lume" + "visuals" in two text calls with
 * slightly different sizes, casing and spacing), so the brand drifted between the loading
 * screen, the title screen, the ClickGUI and the HUD. All of them now route through here —
 * change the look once, it changes everywhere.
 *
 * <p>Style: uppercase Montserrat ExtraBold. LUME is flat text colour; VISUALS is filled with a
 * gradient between the two theme accents that slides back and forth, so the word shimmers
 * through whatever colours the user picked in Customize Colors.
 *
 * <p>The logo mark ({@link NanoVgRenderer#logoMark}) is deliberately NOT drawn here — some
 * callers want it, some don't, and it sits in a different place on each.
 */
public final class Wordmark {

    private Wordmark() {}

    public static final String W1 = "LUME";
    public static final String W2 = "VISUALS";
    private static final String SEP = " ";

    /** Full ping-pong period of the shimmer, ms. */
    private static final float SHIMMER_MS = 3200f;

    /** Total width of the whole wordmark at {@code size} (px, in the same space as size). */
    public static float width(long vg, float size) {
        return NanoVgRenderer.textWidth(vg, size, W1 + SEP, true)
             + NanoVgRenderer.textWidth(vg, size, W2, true);
    }

    /** Draws the wordmark with its left edge at {@code x}, vertically centred on {@code cy}. */
    public static void draw(long vg, float x, float cy, float size, int alpha) {
        float w1w = NanoVgRenderer.textWidth(vg, size, W1 + SEP, true);
        float w2w = NanoVgRenderer.textWidth(vg, size, W2, true);

        NanoVgRenderer.text(vg, x, cy, size, withAlpha(Theme.txt(), alpha),
                NanoVgRenderer.ALIGN_MIDDLE, W1, true);

        // Gradient band is 2x the word and ping-pongs across it, so the accent colours visibly
        // flow through VISUALS rather than sitting still. Ping-pong (not wrap) keeps it seamless
        // without needing a repeating paint, which NanoVG's linear gradient can't do.
        float t = (System.currentTimeMillis() % (long) SHIMMER_MS) / SHIMMER_MS;
        float ping = t < 0.5f ? t * 2f : (1f - t) * 2f;               // 0..1..0
        float band = Math.max(w2w, 1f) * 2f;
        float gx0 = x + w1w - band * 0.5f + ping * band;
        float gx1 = gx0 + band;

        NanoVgRenderer.textGradient(vg, x + w1w, cy, size, NanoVgRenderer.ALIGN_MIDDLE, W2,
                gx0, gx1, withAlpha(Theme.accentRgb(), alpha), withAlpha(Theme.accent2Rgb(), alpha), true);
    }

    /** Draws the wordmark centred horizontally on {@code cx}. */
    public static void drawCentered(long vg, float cx, float cy, float size, int alpha) {
        draw(vg, cx - width(vg, size) / 2f, cy, size, alpha);
    }

    private static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    // ---- DrawContext / LumeFont path -------------------------------------------------
    // The HUD watermark and the pre-NanoVG ClickGUI fallback header draw through RenderUtil,
    // which has no gradient paint — the shimmer is faked by tinting VISUALS one character at
    // a time along the same accent→accent2 ramp, which at this text size is indistinguishable
    // from a real gradient.

    public static int widthLegacy(net.minecraft.client.font.TextRenderer tr, float scale) {
        return RenderUtil.width(tr, W1 + SEP, scale) + RenderUtil.width(tr, W2, scale);
    }

    /** Draws the wordmark with its left edge at {@code x}, top at {@code y}. */
    public static void drawLegacy(net.minecraft.client.gui.DrawContext ctx,
                                  net.minecraft.client.font.TextRenderer tr,
                                  double x, double y, float scale) {
        RenderUtil.text(ctx, tr, W1, x, y, Theme.txt(), false, scale);

        float t = (System.currentTimeMillis() % (long) SHIMMER_MS) / SHIMMER_MS;
        float ping = t < 0.5f ? t * 2f : (1f - t) * 2f;
        double cx = x + RenderUtil.width(tr, W1 + SEP, scale);
        int n = W2.length();
        for (int i = 0; i < n; i++) {
            String ch = String.valueOf(W2.charAt(i));
            // Position along the word (0..1) shifted by the animation phase, then folded back
            // into 0..1 with a triangle wave so the ramp reverses instead of jumping at the seam.
            float f = n > 1 ? (float) i / (n - 1) : 0f;
            float g = Math.abs(((f + ping) % 2f) - 1f);
            RenderUtil.text(ctx, tr, ch, cx, y, Theme.colorLerp(Theme.accent(), Theme.accent2(), g), false, scale);
            cx += RenderUtil.width(tr, ch, scale);
        }
    }

    /** Draws the wordmark centred inside the {@code panelX..panelX+panelW} span. */
    public static void drawLegacyCentered(net.minecraft.client.gui.DrawContext ctx,
                                          net.minecraft.client.font.TextRenderer tr,
                                          int panelX, int panelW, double y, float scale) {
        drawLegacy(ctx, tr, panelX + (panelW - widthLegacy(tr, scale)) / 2.0, y, scale);
    }
}

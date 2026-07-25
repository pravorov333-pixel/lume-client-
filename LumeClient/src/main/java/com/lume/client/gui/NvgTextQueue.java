package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Text draws collected during a NanoVG frame and flushed right after it ends.
 *
 * <p>NanoVG's whole draw (ClickGuiScreen's live render path, NavBar) runs inside ONE
 * {@code nvgBeginFrame}/{@code nvgEndFrame} bracket ({@link NanoVgRenderer#frame}) — DrawContext's
 * own batched {@code TextRenderer} calls can't interleave with that (different GL state/pipeline),
 * so text can no longer be drawn immediately via {@code NanoVgRenderer.text(...)} (which still
 * renders through a hardcoded Montserrat TTF the Inter font swap never reached — see
 * {@code RenderUtil}'s class doc). Call {@link #text} with the SAME parameters/semantics as
 * {@code NanoVgRenderer.text(vg,x,y,size,argb,align,str)} instead of drawing immediately, then
 * call {@link #flush} once the enclosing {@code NanoVgRenderer.frame(...)} call has returned —
 * only {@link NanoVgRenderer#ALIGN_MIDDLE} (left, vertically centred) and {@link
 * NanoVgRenderer#ALIGN_CENTER_MIDDLE} (both centred) are supported, the only two this codebase's
 * NanoVG text calls actually use.
 */
public final class NvgTextQueue {
    private NvgTextQueue() {}

    private record Entry(float x, float y, float size, int color, int align, String s) {}
    private static final List<Entry> queue = new ArrayList<>();

    // The same window-local -> real-framebuffer pan/zoom transform every raw-GL "WindowLocal"
    // helper in this codebase already uses (see SdfRenderer.boxWindowLocal / GlassRenderer's
    // panelWindowLocal): real = winOffXS/winOffYS + cx/cy + total*(local - cx/cy). Text queued via
    // {@link #text} is in the SAME window-local coordinate space every other NanoVG draw call in
    // that frame used (nvgTranslate/nvgScale applied this transform for them automatically via the
    // CTM) — flush() has to redo it by hand since DrawContext has no equivalent CTM to inherit it
    // from.
    private static double winOffXS, winOffYS, cx, cy;
    private static float total = 1f;

    /** Call once, before the {@code NanoVgRenderer.frame(...)} call whose lambda will queue text —
     *  same 5 values that lambda passes to its own {@code NanoVgRenderer.translate/scale} setup
     *  (or {@code applyTransform}'s, for LumeSubScreen callers). */
    public static void begin(double winOffXS, double winOffYS, double cx, double cy, float total) {
        NvgTextQueue.winOffXS = winOffXS; NvgTextQueue.winOffYS = winOffYS;
        NvgTextQueue.cx = cx; NvgTextQueue.cy = cy; NvgTextQueue.total = total;
        queue.clear();
    }

    public static void clear() { queue.clear(); }

    /** Queue a draw — same signature/units/window-local coordinate space as {@code
     *  NanoVgRenderer.text} (framebuffer px * GUI scale, pre pan/zoom — see {@link #begin}). */
    public static void text(float x, float y, float size, int argb, int align, String str) {
        queue.add(new Entry(x, y, size, argb, align, str));
    }

    /** First-pass px-size→RenderUtil-scale calibration constant — NanoVG's font "size" and
     *  RenderUtil's TextRenderer "scale" are different units from different renderers, this maps
     *  common nvg sizes (7-16) onto the 0.6-1.3 scale range that read right in the LumeTitleMenu
     *  Inter re-calibration. Single named constant so a live "too big/small" report is a one-line
     *  retune, not a re-derivation. */
    private static final float SIZE_TO_SCALE = 1f / 12f;

    /** Draw and clear every queued entry through {@link RenderUtil}'s vanilla Inter-font path.
     *  Call once, right after the {@code NanoVgRenderer.frame(...)} that queued these has
     *  returned (GL state is fully restored by then, DrawContext calls are safe again) — applies
     *  the SAME pan/zoom transform {@link #begin} was given, then converts framebuffer px down to
     *  DrawContext logical px (÷ GUI scale, ctx's own matrix already accounts for that). */
    public static void flush(DrawContext ctx) {
        if (queue.isEmpty()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int S = (int) Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
        for (Entry e : queue) {
            double gx = winOffXS + cx + total * (e.x - cx);
            double gy = winOffYS + cy + total * (e.y - cy);
            double x = gx / S, y = gy / S;
            float scale = (e.size / S) * total * SIZE_TO_SCALE;
            if (e.align == NanoVgRenderer.ALIGN_CENTER_MIDDLE) {
                RenderUtil.textCentered(ctx, tr, e.s, x, y, 0, 0, e.color, scale);
            } else {
                RenderUtil.textVCentered(ctx, tr, e.s, x, y, 0, e.color, scale);
            }
        }
        queue.clear();
    }
}

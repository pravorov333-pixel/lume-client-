package com.lume.client.nanovg;

import com.lume.client.gui.Theme;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

/**
 * NanoVG toolkit for smooth, anti-aliased vector UI rendered directly through
 * OpenGL at full framebuffer resolution — crisp curves/corners/text regardless of
 * Minecraft's GUI scale. Confirmed to load & render over the game (incl. Sodium).
 *
 * <p>Two fonts are loaded: <b>Poppins</b> (primary, the Lume look) with
 * <b>NotoSans</b> as a fallback so Cyrillic still renders (Poppins has none).
 *
 * <p>GL interop: NanoVG's GL3 backend binds its own shader/VAO/buffers, so
 * {@link #frame} saves the GL state MC relies on before {@code nvgBeginFrame} and
 * restores it after {@code nvgEndFrame}. Draw it LAST in the frame.
 *
 * <p>Everything is guarded — on any failure we log and {@link #ready()} stays
 * false so callers fall back to the old DrawContext renderer.
 */
public final class NanoVgRenderer {

    public static final int ALIGN_LEFT   = NVG_ALIGN_LEFT | NVG_ALIGN_TOP;
    public static final int ALIGN_CENTER = NVG_ALIGN_CENTER | NVG_ALIGN_TOP;
    public static final int ALIGN_MIDDLE = NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE;
    public static final int ALIGN_CENTER_MIDDLE = NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE;

    private static long vg = 0L;
    private static boolean failed = false;
    private static int fontMain = -1, fontCyr = -1;
    private static final List<ByteBuffer> fontBuffers = new ArrayList<>();  // keep alive for NanoVG

    private NanoVgRenderer() {}

    public static boolean ready() { return vg != 0L; }

    /** Initialise NanoVG if not yet done (so {@link #ready()} is accurate before a frame). */
    public static void ensureInit() { ensure(); }

    private static void ensure() {
        if (vg != 0L || failed) return;
        try {
            vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (vg == 0L) { failed = true; System.out.println("[Lume] NanoVG create FAILED (vg=0)"); return; }
            fontMain = loadFont("lume", "/assets/lume/font/lume.ttf");          // Poppins
            fontCyr  = loadFont("lume-cyr", "/assets/lume/font/notosans.ttf");  // Cyrillic fallback
            if (fontMain != -1 && fontCyr != -1) nvgAddFallbackFontId(vg, fontMain, fontCyr);
            System.out.println("[Lume] NanoVG READY (vg=" + vg + ", font=" + fontMain + "/" + fontCyr + ")");
        } catch (Throwable t) {
            failed = true;
            System.out.println("[Lume] NanoVG init error: " + t);
        }
    }

    private static int loadFont(String name, String path) {
        try (InputStream in = NanoVgRenderer.class.getResourceAsStream(path)) {
            if (in == null) { System.out.println("[Lume] NVG font missing: " + path); return -1; }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
            buf.put(bytes).flip();
            fontBuffers.add(buf);   // NanoVG keeps a pointer to this; must stay alive
            int id = nvgCreateFontMem(vg, name, buf, false);
            if (id == -1) System.out.println("[Lume] NVG font create failed: " + name);
            return id;
        } catch (Exception e) {
            System.out.println("[Lume] NVG font error " + path + ": " + e);
            return -1;
        }
    }

    public interface Draw { void run(long vg); }

    /** Render one NanoVG frame in framebuffer-pixel coords, saving/restoring GL state. */
    public static void frame(Draw draw) {
        ensure();
        if (vg == 0L) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();

        int prog   = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vao    = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int abuf   = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int tex    = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blend   = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth   = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull    = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean depthMaskOn = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        try {
            if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            nvgBeginFrame(vg, fbW, fbH, 1f);
            draw.run(vg);
            nvgEndFrame(vg);
        } catch (Throwable t) {
            System.out.println("[Lume] NanoVG frame error: " + t);
        } finally {
            GL13.glActiveTexture(active);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, abuf);
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(prog);
            if (blend)   GL11.glEnable(GL11.GL_BLEND);      else GL11.glDisable(GL11.GL_BLEND);
            if (depth)   GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (cull)    GL11.glEnable(GL11.GL_CULL_FACE);  else GL11.glDisable(GL11.GL_CULL_FACE);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            // NanoVG's stencil-buffer stroke technique (NVG_STENCIL_STROKES) leaves the stencil
            // test/func/mask in whatever state its last stroke pass used — MC never touches the
            // stencil buffer itself, so an un-restored stencil test here silently discards terrain
            // fragments on the next frame that doesn't call us (black ground right after a pin/stroke
            // was drawn). Always put it back to MC's own baseline instead of just the enable bit.
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            GL11.glDepthMask(depthMaskOn);
        }
    }

    /** Framebuffer-px / GUI-logical-px ratio (so callers can convert MC coords). */
    public static float pxScale() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return (float) mc.getWindow().getScaleFactor();
    }

    // ---- transform / clip (state inside a frame) --------------------------
    public static void save(long vg)      { nvgSave(vg); }
    public static void restore(long vg)   { nvgRestore(vg); }
    public static void translate(long vg, float x, float y) { nvgTranslate(vg, x, y); }
    public static void scale(long vg, float sx, float sy)   { nvgScale(vg, sx, sy); }
    public static void scissor(long vg, float x, float y, float w, float h) { nvgScissor(vg, x, y, w, h); }
    public static void intersectScissor(long vg, float x, float y, float w, float h) { nvgIntersectScissor(vg, x, y, w, h); }
    public static void resetScissor(long vg) { nvgResetScissor(vg); }
    public static void rotate(long vg, float radians) { nvgRotate(vg, radians); }

    /** Multiplies all fill/stroke alpha until the next {@link #restore} — wrap a whole panel's
     *  draw calls in {@code save()}/{@code restore()} around this for a simple panel fade. */
    public static void globalAlpha(long vg, float alpha) { nvgGlobalAlpha(vg, alpha); }

    /** Filled triangle (perfect AA edges). */
    public static void triangle(long vg, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgBeginPath(vg);
            nvgMoveTo(vg, x1, y1);
            nvgLineTo(vg, x2, y2);
            nvgLineTo(vg, x3, y3);
            nvgClosePath(vg);
            nvgFillColor(vg, col);
            nvgFill(vg);
        }
    }

    // ---- shapes -----------------------------------------------------------

    /** Anti-aliased filled rounded rectangle. Colour is ARGB. */
    public static void roundedRect(long vg, float x, float y, float w, float h, float r, int argb) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, r);
            nvgFillColor(vg, col);
            nvgFill(vg);
        }
    }

    /** Rounded rect with a vertical gradient (top → bottom). */
    public static void gradientRoundedRect(long vg, float x, float y, float w, float h, float r, int argbTop, int argbBot) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor c1 = NVGColor.malloc(s), c2 = NVGColor.malloc(s);
            color(argbTop, c1); color(argbBot, c2);
            NVGPaint p = NVGPaint.malloc(s);
            nvgLinearGradient(vg, x, y, x, y + h, c1, c2, p);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, r);
            nvgFillPaint(vg, p);
            nvgFill(vg);
        }
    }

    /** Fill a rounded rect with an arbitrary linear gradient (endpoints in px). */
    public static void fillLinearGradient(long vg, float x, float y, float w, float h, float r,
                                          float gx0, float gy0, float gx1, float gy1, int argb0, int argb1) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor c0 = NVGColor.malloc(s), c1 = NVGColor.malloc(s);
            color(argb0, c0); color(argb1, c1);
            NVGPaint p = NVGPaint.malloc(s);
            nvgLinearGradient(vg, gx0, gy0, gx1, gy1, c0, c1, p);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, r);
            nvgFillPaint(vg, p);
            nvgFill(vg);
        }
    }

    /** Stroked (outlined) rounded rect — used for the bright glass rim. */
    public static void strokeRoundedRect(long vg, float x, float y, float w, float h, float r, float width, int argb) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, r);
            nvgStrokeColor(vg, col);
            nvgStrokeWidth(vg, width);
            nvgStroke(vg);
        }
    }

    /** Strokes a connected polyline through the given points (e.g. a jagged lightning bolt) —
     *  stroking rather than filling means this is safe even for self-crossing/zigzag shapes,
     *  since (unlike a concave fill) it never needs NanoVG's stencil-buffer trick at all. */
    public static void strokePolyline(long vg, float[] xs, float[] ys, int argb, float width) {
        if (xs.length < 2) return;
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgBeginPath(vg);
            nvgMoveTo(vg, xs[0], ys[0]);
            for (int i = 1; i < xs.length; i++) nvgLineTo(vg, xs[i], ys[i]);
            nvgStrokeColor(vg, col);
            nvgStrokeWidth(vg, width);
            nvgStroke(vg);
        }
    }

    /** Soft drop shadow / outer glow around a rounded rect (feathered box gradient). */
    public static void shadow(long vg, float x, float y, float w, float h, float r, float spread, int argb) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor c1 = NVGColor.malloc(s), c0 = NVGColor.malloc(s);
            color(argb, c1);
            nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 0, c0);
            NVGPaint p = NVGPaint.malloc(s);
            nvgBoxGradient(vg, x, y + 2, w, h, r * 1.5f, spread, c1, c0, p);
            nvgBeginPath(vg);
            nvgRect(vg, x - spread, y - spread, w + spread * 2, h + spread * 2 + 2);
            nvgRoundedRect(vg, x, y, w, h, r);
            nvgPathWinding(vg, NVG_HOLE);
            nvgFillPaint(vg, p);
            nvgFill(vg);
        }
    }

    /**
     * Coloured neon bloom halo around a rounded rect (feathered box gradient,
     * no hole → also bleeds under translucent elements). Draw BEFORE the element.
     * Stack a few with growing {@code spread} + falling alpha for a stronger bloom.
     */
    public static void neonGlow(long vg, float x, float y, float w, float h, float r, float spread, int argb) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor c1 = NVGColor.malloc(s), c0 = NVGColor.malloc(s);
            color(argb, c1);
            nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 0, c0);
            NVGPaint p = NVGPaint.malloc(s);
            nvgBoxGradient(vg, x, y, w, h, r + spread * 0.4f, spread, c1, c0, p);
            nvgBeginPath(vg);
            nvgRect(vg, x - spread, y - spread, w + spread * 2, h + spread * 2);
            nvgFillPaint(vg, p);
            nvgFill(vg);
        }
    }

    /** Multi-layer neon bloom: several stacked halos for a soft, bright glow. */
    public static void bloom(long vg, float x, float y, float w, float h, float r, float spread, int rgb, int peakAlpha) {
        for (int i = 3; i >= 1; i--) {
            int a = (peakAlpha * i) / 6;             // fades outward
            neonGlow(vg, x, y, w, h, r, spread * i / 3f, (a << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** True circular soft glow via {@code nvgRadialGradient} — one smooth alpha falloff from
     *  {@code innerR} to {@code outerR}, unlike stacking flat circles (which reads as rings, not
     *  a blur). This is what actually matches a CSS {@code filter: blur(...)} glow blob look. */
    public static void radialGlow(long vg, float cx, float cy, float innerR, float outerR, int rgb, int peakAlpha) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor c1 = NVGColor.malloc(s), c0 = NVGColor.malloc(s);
            color(((peakAlpha & 0xFF) << 24) | (rgb & 0xFFFFFF), c1);
            nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 0, c0);
            NVGPaint p = NVGPaint.malloc(s);
            nvgRadialGradient(vg, cx, cy, innerR, outerR, c1, c0, p);
            nvgBeginPath(vg);
            nvgRect(vg, cx - outerR, cy - outerR, outerR * 2, outerR * 2);
            nvgFillPaint(vg, p);
            nvgFill(vg);
        }
    }

    /** Lume "Sparkle" mark — big 4-point sparkle (bright→mid lavender gradient) + a smaller
     *  sparkle (light lavender), the small one's bottom tip sitting directly above the big
     *  one's right tip (x=72 for both). Same 0..100 coordinate space and exact path numbers
     *  as the website/launcher's own SVG version (see their inline &lt;svg class="mark"&gt;
     *  markup) — keep both in sync if this ever changes.
     *
     *  <p>Drawn as a triangle FAN from the shape's centre rather than one single concave
     *  {@code nvgFill()} of the whole moveTo/quadTo outline — a sparkle's deep inward "waist"
     *  between points is exactly the kind of sharp concave path NanoVG's stencil-based concave
     *  fill can render as a solid bounding box instead of the actual silhouette on some
     *  drivers/framebuffer setups (this is what showed up in-game as two plain squares).
     *  Every wedge here is a plain triangle (always convex), so it can't hit that path at all. */
    public static void logoMark(long vg, float x, float y, float s) {
        float u = s / 100f;
        // Follows the current accent (customisable via Customize Colors) instead of a fixed
        // lavender — accent()/accent2() are the SAME two-stop gradient every other accent-filled
        // pill/button in the UI uses; the small sparkle gets a lighter tint of accent(), same
        // relationship the original fixed palette had (C9BEE0 is a lightened B7AAD9).
        int bigC1 = Theme.accent(), bigC2 = Theme.accent2();
        int smallC = 0xFF000000 | (Theme.colorLerp(Theme.accentRgb(), 0xFFFFFF, 0.25f) & 0xFFFFFF);
        try (MemoryStack ms = MemoryStack.stackPush()) {
            NVGColor c1 = NVGColor.malloc(ms), c2 = NVGColor.malloc(ms);
            color(bigC1, c1); color(bigC2, c2);
            NVGPaint p = NVGPaint.malloc(ms);
            nvgLinearGradient(vg, x + 12 * u, y + 16 * u, x + 72 * u, y + 76 * u, c1, c2, p);
            nvgFillPaint(vg, p);
            fillSparkleFan(vg, x, y, u, 42, 46, 42, 16, 48.4f, 39.6f, 72, 46, 48.4f, 52.4f, 42, 76, 35.6f, 52.4f, 12, 46, 35.6f, 39.6f);
        }
        try (MemoryStack ms = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(ms);
            color(smallC, col);
            nvgFillColor(vg, col);
            fillSparkleFan(vg, x, y, u, 72, 24, 72, 11, 74.8f, 21.2f, 85, 24, 74.8f, 26.8f, 72, 37, 69.2f, 26.8f, 59, 24, 69.2f, 21.2f);
        }
    }

    /** Samples the sparkle's 4 quadratic-bezier "petals" (tip → control → tip, ×4) into a
     *  dense perimeter point list, then fills consecutive (centre, point[i], point[i+1])
     *  triangles — see {@link #logoMark}'s doc for why. Caller sets the fill colour/paint
     *  before calling this (it only issues nvgBeginPath/Fill pairs, one per triangle). */
    private static void fillSparkleFan(long vg, float x, float y, float u, float cxRaw, float cyRaw,
                                        float t1x, float t1y, float c1x, float c1y,
                                        float t2x, float t2y, float c2x, float c2y,
                                        float t3x, float t3y, float c3x, float c3y,
                                        float t4x, float t4y, float c4x, float c4y) {
        List<float[]> pts = new ArrayList<>();
        int steps = 8;
        sampleQuad(pts, x, y, u, t1x, t1y, c1x, c1y, t2x, t2y, steps);
        sampleQuad(pts, x, y, u, t2x, t2y, c2x, c2y, t3x, t3y, steps);
        sampleQuad(pts, x, y, u, t3x, t3y, c3x, c3y, t4x, t4y, steps);
        sampleQuad(pts, x, y, u, t4x, t4y, c4x, c4y, t1x, t1y, steps);
        float cx = x + cxRaw * u, cy = y + cyRaw * u;
        for (int i = 0; i < pts.size() - 1; i++) {
            float[] a = pts.get(i), b = pts.get(i + 1);
            // Each petal's own t=0/t=1 endpoints are shared with its neighbour (same tip
            // coordinate sampled twice, once per adjacent sampleQuad call) — at those 3 seams
            // a==b exactly, producing a zero-area (centre, P, P) triangle. NanoVG's AA fill
            // computes a per-edge normal for the fringe by dividing by edge length; a
            // zero-length edge there is a divide-by-zero -> NaN vertex, which crashed the
            // Intel driver outright (access violation in igxelpicd64.dll) instead of just
            // rendering wrong. Skipping degenerate triangles costs nothing visually.
            if (Math.abs(a[0] - b[0]) < 1e-4f && Math.abs(a[1] - b[1]) < 1e-4f) continue;
            nvgBeginPath(vg);
            nvgMoveTo(vg, cx, cy);
            nvgLineTo(vg, a[0], a[1]);
            nvgLineTo(vg, b[0], b[1]);
            nvgClosePath(vg);
            nvgFill(vg);
        }
    }

    private static void sampleQuad(List<float[]> out, float x, float y, float u,
                                    float p0x, float p0y, float cpx, float cpy, float p1x, float p1y, int steps) {
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps, mt = 1 - t;
            float bx = mt * mt * p0x + 2 * mt * t * cpx + t * t * p1x;
            float by = mt * mt * p0y + 2 * mt * t * cpy + t * t * p1y;
            out.add(new float[]{ x + bx * u, y + by * u });
        }
    }

    /** Stroked (outlined) ellipse — used for the Custom Hand rotation-gizmo rings. */
    public static void strokeEllipse(long vg, float cx, float cy, float rx, float ry, float width, int argb) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgBeginPath(vg);
            nvgEllipse(vg, cx, cy, rx, ry);
            nvgStrokeColor(vg, col);
            nvgStrokeWidth(vg, width);
            nvgStroke(vg);
        }
    }

    /** Filled circle (perfect AA curve). */
    public static void circle(long vg, float cx, float cy, float r, int argb) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgBeginPath(vg);
            nvgCircle(vg, cx, cy, r);
            nvgFillColor(vg, col);
            nvgFill(vg);
        }
    }

    // ---- text -------------------------------------------------------------

    public static void text(long vg, float x, float y, float size, int argb, int align, String str) {
        if (fontMain == -1 || str == null) return;
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgFontFaceId(vg, fontMain);
            nvgFontSize(vg, size);
            nvgFillColor(vg, col);
            nvgTextAlign(vg, align);
            nvgText(vg, x, y, str);
        }
    }

    /** Shrinks the font just enough for {@code text} to fit {@code maxW}, never below {@code minSize}. */
    public static float fitSize(long vg, float baseSize, String text, float maxW, float minSize) {
        float w = textWidth(vg, baseSize, text);
        if (w <= maxW || w <= 0) return baseSize;
        return Math.max(minSize, baseSize * maxW / w);
    }

    public static float textWidth(long vg, float size, String str) {
        if (fontMain == -1 || str == null) return 0;
        nvgFontFaceId(vg, fontMain);
        nvgFontSize(vg, size);
        return nvgTextBounds(vg, 0, 0, str, (float[]) null);
    }

    // ---- helpers ----------------------------------------------------------

    private static void color(int argb, NVGColor out) {
        nvgRGBA((byte) ((argb >> 16) & 0xFF), (byte) ((argb >> 8) & 0xFF),
                (byte) (argb & 0xFF), (byte) ((argb >>> 24) & 0xFF), out);
    }
}

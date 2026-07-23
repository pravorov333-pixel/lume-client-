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
// Tried NanoVGGL2 (simpler pipeline, no VAOs) while chasing a GL3 driver crash on one Intel
// iGPU — nvgCreate just returned 0 (failed to even create a context) instead of crashing, so
// GL2 doesn't work AT ALL on that hardware (likely because MC's context is core-profile and
// GL2's backend needs compatibility-profile features). Back to GL3, the one that actually works
// everywhere else; the crash-avoidance angle now being tried is the LWJGL system allocator
// JVM flag (launcher.js) instead of the NanoVG backend.
import static org.lwjgl.nanovg.NanoVGGL3.*;

/**
 * NanoVG toolkit for smooth, anti-aliased vector UI rendered directly through
 * OpenGL at full framebuffer resolution — crisp curves/corners/text regardless of
 * Minecraft's GUI scale. Confirmed to load & render over the game (incl. Sodium).
 *
 * <p>Two fonts are loaded: <b>Montserrat Medium</b> (primary, the Lume look) and
 * <b>Montserrat ExtraBold</b>, used only by the LUME VISUALS wordmark. Montserrat
 * ships full Cyrillic, so unlike the old Poppins primary this needs no fallback
 * font for Russian.
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
    private static int fontMain = -1, fontBold = -1;
    private static final List<ByteBuffer> fontBuffers = new ArrayList<>();  // keep alive for NanoVG

    private NanoVgRenderer() {}

    public static boolean ready() { return vg != 0L; }

    /** Initialise NanoVG if not yet done (so {@link #ready()} is accurate before a frame). */
    public static void ensureInit() { ensure(); }

    /** Escape hatch for GPUs/drivers where NanoVG's GL3 backend is simply broken (confirmed on
     *  one Intel iGPU: a reproducible EXCEPTION_ACCESS_VIOLATION inside the driver itself,
     *  igxelpicd64.dll, from plain {@code nvgEndFrame} — not tied to any specific NanoVG feature
     *  flag, survived removing NVG_STENCIL_STROKES too). Every call site already guards on
     *  {@link #ready()} and falls back to the plain DrawContext renderer (see class doc), so
     *  simply never initialising NanoVG degrades the UI instead of crashing the game. Drop a
     *  file named {@code lume_disable_nanovg} in the game directory (next to options.txt) to
     *  enable — off by default, doesn't affect anyone who isn't hitting this. */
    private static boolean nanoVgDisabledByMarker() {
        try {
            return java.nio.file.Files.exists(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("lume_disable_nanovg"));
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensure() {
        if (vg != 0L || failed) return;
        if (nanoVgDisabledByMarker()) {
            failed = true;
            System.out.println("[Lume] NanoVG disabled via lume_disable_nanovg marker — using plain DrawContext rendering everywhere.");
            return;
        }
        try {
            // NVG_STENCIL_STROKES deliberately dropped: it makes NanoVG anti-alias strokes via a
            // stencil-buffer pass, and stencil-buffer ops are a classic crash trigger on flaky/older
            // Intel iGPU drivers — this client hit a real EXCEPTION_ACCESS_VIOLATION inside the Intel
            // driver itself (igxelpicd64.dll) from nvgEndFrame, on hardware whose driver only reports
            // OpenGL 3.2 despite the chip supporting far more. Plain NVG_ANTIALIAS still anti-aliases
            // fills/text/simple strokes (everything this client actually draws — rounded rects, glows,
            // text); it only loses the stencil trick for self-intersecting/overlapping stroke paths,
            // which nothing here uses.
            vg = nvgCreate(NVG_ANTIALIAS);
            if (vg == 0L) { failed = true; System.out.println("[Lume] NanoVG create FAILED (vg=0)"); return; }
            // Montserrat covers Latin AND Cyrillic, so there's deliberately no fallback
            // font registered any more (Poppins needed NotoSans bolted on for Russian).
            fontMain = loadFont("lume", "/assets/lume/font/montserrat.ttf");            // Montserrat Medium
            fontBold = loadFont("lume-bold", "/assets/lume/font/montserrat-bold.ttf");  // Montserrat ExtraBold (wordmark)
            System.out.println("[Lume] NanoVG READY (vg=" + vg + ", font=" + fontMain + "/" + fontBold + ")");
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

    // ---- images -----------------------------------------------------------
    // Used by the custom menu wallpaper. NanoVG decodes (stb_image: png/jpg/bmp/gif) and
    // uploads to a GL texture inside nvgCreateImageMem, so — unlike fonts, whose buffers must
    // stay alive for NanoVG to keep reading them — the source bytes can be freed right after.

    /** @return image handle, or -1 if the bytes aren't a decodable image. */
    public static int createImage(long vg, byte[] bytes) {
        ByteBuffer buf = MemoryUtil.memAlloc(bytes.length);
        try {
            buf.put(bytes).flip();
            return nvgCreateImageMem(vg, 0, buf);
        } catch (Throwable t) {
            System.out.println("[Lume] NVG image decode failed: " + t);
            return -1;
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    public static void deleteImage(long vg, int img) {
        if (img != -1) nvgDeleteImage(vg, img);
    }

    /**
     * Draws {@code img} filling the rect, cropping the overflow to preserve aspect ratio —
     * i.e. CSS {@code background-size: cover}, so a wallpaper of any shape fills the screen
     * without ever stretching.
     */
    public static void imageCover(long vg, float x, float y, float w, float h, int img, float alpha) {
        if (img == -1) return;
        try (MemoryStack s = MemoryStack.stackPush()) {
            int[] iw = new int[1], ih = new int[1];
            nvgImageSize(vg, img, iw, ih);
            if (iw[0] <= 0 || ih[0] <= 0) return;
            float scale = Math.max(w / iw[0], h / ih[0]);
            float dw = iw[0] * scale, dh = ih[0] * scale;
            NVGPaint p = NVGPaint.malloc(s);
            nvgImagePattern(vg, x + (w - dw) / 2f, y + (h - dh) / 2f, dw, dh, 0, img, alpha, p);
            nvgBeginPath(vg);
            nvgRect(vg, x, y, w, h);
            nvgFillPaint(vg, p);
            nvgFill(vg);
        }
    }

    /**
     * Small solid 4-point sparkle (tips N/E/S/W at {@code r}, waists pinched to {@code waist}).
     *
     * <p>Like {@link #logoMark} this is a triangle FAN, not one concave path: a sparkle's inward
     * waist is exactly the silhouette NanoVG's stencil fill can turn into a solid bounding box on
     * some drivers. Unlike logoMark it samples no curves — 8 flat triangles, cheap enough to run
     * on every particle of the menu sparkle field each frame.
     */
    public static void sparkle4(long vg, float cx, float cy, float r, float waist, int argb) {
        float[] pts = {
                cx, cy - r,          cx + waist, cy - waist,
                cx + r, cy,          cx + waist, cy + waist,
                cx, cy + r,          cx - waist, cy + waist,
                cx - r, cy,          cx - waist, cy - waist,
        };
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgFillColor(vg, col);
            for (int i = 0; i < 8; i++) {
                int j = (i + 1) % 8;
                nvgBeginPath(vg);
                nvgMoveTo(vg, cx, cy);
                nvgLineTo(vg, pts[i * 2], pts[i * 2 + 1]);
                nvgLineTo(vg, pts[j * 2], pts[j * 2 + 1]);
                nvgClosePath(vg);
                nvgFill(vg);
            }
        }
    }

    /** Lume "Glass Star" mark — rebranded (per the new Figma reference) to a single 4-point
     *  sparkle (bright→mid lavender/accent gradient) with a soft glow halo behind it, replacing
     *  the old two-sparkle composition (a small secondary sparkle used to sit pinned to the big
     *  one's right tip — removed, the new reference shows one star only). Same 0..100 coordinate
     *  space and exact path numbers as {@link com.lume.client.gui.RenderUtil#drawLogo} and the
     *  launcher's own SVG mark (see their inline &lt;svg class="mark"&gt; markup) — keep all
     *  three in sync if this ever changes.
     *
     *  <p>Drawn as a triangle FAN from the shape's centre rather than one single concave
     *  {@code nvgFill()} of the whole moveTo/quadTo outline — a sparkle's deep inward "waist"
     *  between points is exactly the kind of sharp concave path NanoVG's stencil-based concave
     *  fill can render as a solid bounding box instead of the actual silhouette on some
     *  drivers/framebuffer setups (this is what showed up in-game as a plain square).
     *  Every wedge here is a plain triangle (always convex), so it can't hit that path at all. */
    public static void logoMark(long vg, float x, float y, float s) {
        float u = s / 100f;
        // Follows the current accent (customisable via Customize Colors) instead of a fixed
        // lavender — accent()/accent2() are the SAME two-stop gradient every other accent-filled
        // pill/button in the UI uses.
        int bigC1 = Theme.accent(), bigC2 = Theme.accent2();
        // Soft glass glow halo: the SAME sparkle fan, scaled up ~18% around its own centre
        // (42,46) and filled at low flat alpha, drawn first (behind the crisp star) — a real
        // blur isn't cheaply available here, so a larger translucent copy stands in for one,
        // same trick RenderUtil.glow/containedGlow already use for rounded rects.
        float gu = u * 1.18f;
        float gx = x + 42 * u - 42 * gu, gy = y + 46 * u - 46 * gu;
        try (MemoryStack ms = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(ms);
            color(0x40000000 | (Theme.accentRgb() & 0xFFFFFF), col);
            nvgFillColor(vg, col);
            fillSparkleFan(vg, gx, gy, gu, 42, 46, 42, 16, 48.4f, 39.6f, 72, 46, 48.4f, 52.4f, 42, 76, 35.6f, 52.4f, 12, 46, 35.6f, 39.6f);
        }
        try (MemoryStack ms = MemoryStack.stackPush()) {
            NVGColor c1 = NVGColor.malloc(ms), c2 = NVGColor.malloc(ms);
            color(bigC1, c1); color(bigC2, c2);
            NVGPaint p = NVGPaint.malloc(ms);
            nvgLinearGradient(vg, x + 12 * u, y + 16 * u, x + 72 * u, y + 76 * u, c1, c2, p);
            nvgFillPaint(vg, p);
            fillSparkleFan(vg, x, y, u, 42, 46, 42, 16, 48.4f, 39.6f, 72, 46, 48.4f, 52.4f, 42, 76, 35.6f, 52.4f, 12, 46, 35.6f, 39.6f);
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
        text(vg, x, y, size, argb, align, str, false);
    }

    /** {@code bold=true} draws in Montserrat ExtraBold — reserved for the LUME VISUALS wordmark. */
    public static void text(long vg, float x, float y, float size, int argb, int align, String str, boolean bold) {
        int face = bold && fontBold != -1 ? fontBold : fontMain;
        if (face == -1 || str == null) return;
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor col = NVGColor.malloc(s);
            color(argb, col);
            nvgFontFaceId(vg, face);
            nvgFontSize(vg, size);
            nvgFillColor(vg, col);
            nvgTextAlign(vg, align);
            nvgText(vg, x, y, str);
        }
    }

    /** Applies the wordmark's ExtraBold face + size to {@code vg} without drawing — for
     *  callers that paint text themselves (e.g. a gradient fill via nvgFillPaint). */
    public static void useBoldFace(long vg, float size) {
        if (fontBold != -1) nvgFontFaceId(vg, fontBold);
        nvgFontSize(vg, size);
    }

    /**
     * Text filled with a horizontal linear gradient instead of a flat colour — NanoVG applies
     * the current fill paint to glyphs the same way it does to shapes, so this is just a normal
     * text draw with nvgFillPaint. Used by the shimmering VISUALS half of the wordmark.
     * Gradient endpoints are absolute px, so callers can slide them past the text bounds to
     * animate the colours flowing through the letters.
     */
    public static void textGradient(long vg, float x, float y, float size, int align, String str,
                                    float gx0, float gx1, int argb0, int argb1, boolean bold) {
        int face = bold && fontBold != -1 ? fontBold : fontMain;
        if (face == -1 || str == null) return;
        try (MemoryStack s = MemoryStack.stackPush()) {
            NVGColor c0 = NVGColor.malloc(s), c1 = NVGColor.malloc(s);
            color(argb0, c0); color(argb1, c1);
            NVGPaint p = NVGPaint.malloc(s);
            nvgLinearGradient(vg, gx0, y, gx1, y, c0, c1, p);
            nvgFontFaceId(vg, face);
            nvgFontSize(vg, size);
            nvgFillPaint(vg, p);
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
        return textWidth(vg, size, str, false);
    }

    public static float textWidth(long vg, float size, String str, boolean bold) {
        int face = bold && fontBold != -1 ? fontBold : fontMain;
        if (face == -1 || str == null) return 0;
        nvgFontFaceId(vg, face);
        nvgFontSize(vg, size);
        return nvgTextBounds(vg, 0, 0, str, (float[]) null);
    }

    // ---- helpers ----------------------------------------------------------

    private static void color(int argb, NVGColor out) {
        nvgRGBA((byte) ((argb >> 16) & 0xFF), (byte) ((argb >> 8) & 0xFF),
                (byte) (argb & 0xFF), (byte) ((argb >>> 24) & 0xFF), out);
    }
}

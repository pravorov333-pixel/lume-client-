package com.lume.client.nanovg;

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
    public static void resetScissor(long vg) { nvgResetScissor(vg); }

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

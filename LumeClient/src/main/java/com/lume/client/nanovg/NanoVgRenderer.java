package com.lume.client.nanovg;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

/**
 * Thin wrapper around NanoVG (LWJGL) for smooth, anti-aliased vector UI rendered
 * directly through OpenGL — independent of Minecraft's GUI-scale grid, so corners
 * and curves are crisp at full framebuffer resolution.
 *
 * <p>EXPERIMENT (branch nanovg-ui): validates that NanoVG initialises and draws
 * over the game without breaking MC/Sodium. Everything is guarded — on any failure
 * we log and {@link #ready()} stays false so callers fall back to the old renderer.
 *
 * <p>GL interop: NanoVG's GL3 backend binds its own shader/VAO/buffers, so we save
 * the GL state MC relies on before {@code nvgBeginFrame} and restore it after
 * {@code nvgEndFrame}. Draw it LAST in the frame to minimise state desync.
 */
public final class NanoVgRenderer {

    private static long vg = 0L;
    private static boolean failed = false;

    private NanoVgRenderer() {}

    public static boolean ready() { return vg != 0L; }

    private static void ensure() {
        if (vg != 0L || failed) return;
        try {
            vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (vg == 0L) { failed = true; System.out.println("[Lume] NanoVG create FAILED (vg=0)"); }
            else System.out.println("[Lume] NanoVG READY (vg=" + vg + ")");
        } catch (Throwable t) {
            failed = true;
            System.out.println("[Lume] NanoVG init error: " + t);
        }
    }

    public interface Draw { void run(long vg); }

    /**
     * Render one NanoVG frame in framebuffer-pixel coordinates. Saves/restores the
     * GL state MC needs. No-op (safe) if NanoVG isn't available.
     */
    public static void frame(Draw draw) {
        ensure();
        if (vg == 0L) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();

        // --- save the GL state NanoVG clobbers ---
        int prog   = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vao    = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int abuf   = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int tex    = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull  = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        try {
            if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            nvgBeginFrame(vg, fbW, fbH, 1f);
            draw.run(vg);
            nvgEndFrame(vg);
        } catch (Throwable t) {
            System.out.println("[Lume] NanoVG frame error: " + t);
        } finally {
            // --- restore MC's GL state ---
            GL13.glActiveTexture(active);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, abuf);
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(prog);
            if (blend)   GL11.glEnable(GL11.GL_BLEND);        else GL11.glDisable(GL11.GL_BLEND);
            if (depth)   GL11.glEnable(GL11.GL_DEPTH_TEST);   else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (cull)    GL11.glEnable(GL11.GL_CULL_FACE);    else GL11.glDisable(GL11.GL_CULL_FACE);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    // ---- drawing helpers (call inside frame(...)) -------------------------

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

    private static void color(int argb, NVGColor out) {
        nvgRGBA((byte) ((argb >> 16) & 0xFF), (byte) ((argb >> 8) & 0xFF),
                (byte) (argb & 0xFF), (byte) ((argb >>> 24) & 0xFF), out);
    }
}

package com.lume.client.nanovg;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Real "premium glass" backdrop for Full Glass style: captures whatever's already been
 * drawn behind a panel (world/HUD/previous UI — everything rendered so far this frame),
 * blurs it (separable Gaussian, downsampled for both cost and a softer look), and samples
 * it back with a refraction offset near the rounded-rect edges (Apple "Liquid Glass"-style
 * bend, via an SDF computed in the composite shader) — then leaves the result sitting in
 * the framebuffer for the caller's own NanoVG panel draw (rim/gradient tint/text/icons) to
 * layer on top exactly as before. Call {@link #panel} BEFORE the NanoVG frame that draws
 * the rest of that panel, using the SAME framebuffer-px rect.
 *
 * <p>Entirely raw GL (own shaders/FBOs/VAO) — deliberately NOT routed through NanoVG, since
 * NanoVG's paint system has no hook for a custom per-fragment fragment shader. Saves/restores
 * every bit of GL state it touches, the same defensive pattern {@link NanoVgRenderer#frame}
 * already uses, so a failure here can't corrupt whatever draws next.
 *
 * <p>Every GL call is guarded — first failure permanently disables this renderer (mirrors
 * {@code NanoVgRenderer}'s own {@code failed} flag) so a driver/shader issue degrades to
 * "no backdrop blur" instead of a broken or crashing GUI. This class was written without the
 * ability to run Minecraft and visually verify it — if it looks wrong or logs errors, that's
 * expected until confirmed working live.
 */
public final class GlassRenderer {
    private GlassRenderer() {}

    private static boolean disabled = false;
    private static boolean initialized = false;

    private static int blurProgram, blurTexelLoc, blurDirLoc, blurRadiusLoc;
    private static int compositeProgram, compSizeLoc, compRadiusLoc, compDistortLoc;
    private static int quadVao, quadVbo;

    // FBO sets cached per capture size — the ClickGUI panel, the Colors window, and the
    // transition overlays can all run in the same frame at DIFFERENT sizes, so a single
    // last-size set would reallocate GPU textures on every call, every frame. Small cap,
    // cleared wholesale when exceeded (new sizes only appear on window resize or when the
    // blur slider changes the downsample factor).
    private static final java.util.HashMap<Long, int[]> fboCache = new java.util.HashMap<>();
    private static int captureFbo, captureTex;
    private static int pingFboA, pingTexA, pingFboB, pingTexB;

    /**
     * Draws the blurred+refracted backdrop into [x,y,w,h] (framebuffer px, top-left origin —
     * same convention every screen already uses for NanoVG). No-op (leaves the framebuffer
     * untouched) if glass rendering is disabled, blurAmount01 is 0, or anything fails.
     *
     * @param blurAmount01    0 = no blur (skips straight to just refraction/passthrough), 1 = max
     * @param distortAmount01 0 = no refraction bend, 1 = max
     */
    public static void panel(int x, int y, int w, int h, float cornerR, float blurAmount01, float distortAmount01) {
        if (disabled || w <= 0 || h <= 0) return;
        try {
            if (!initialized && !init()) { disabled = true; return; }

            // blur=0 + distort=1.0 (an untested extreme — never exercised until a real user
            // dragged both sliders to their literal endpoints) crashed the Intel driver at the
            // exact same fault offset as the earlier sparkle-fan NaN crash. Couldn't pin the
            // precise shader-side cause without live GPU debugging, so clamping both inputs
            // away from their literal boundary values and never allowing a FULL native-
            // resolution (undownsampled) capture texture — both cheap, safe, non-visible
            // mitigations for the exact combination that crashed.
            blurAmount01 = clamp01(blurAmount01);
            distortAmount01 = Math.min(0.97f, clamp01(distortAmount01));

            MinecraftClient mc = MinecraftClient.getInstance();
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            if (x + w <= 0 || y + h <= 0 || x >= fbW || y >= fbH) return;   // fully off-screen

            // Downsample more aggressively at higher blur amounts — cheaper AND visually
            // softer/bigger blur for the same kernel size (standard real-time blur trick).
            // Minimum 2 even at blur=0 — never a full native-resolution capture texture.
            int downsample = 2 + Math.round(blurAmount01 * 2f);   // 2..4
            int cw = Math.max(1, w / downsample), ch = Math.max(1, h / downsample);

            // MC (1.17+) renders the whole game into its OWN fbo, not the default framebuffer
            // 0 — the first version of this class read from / composited into 0, so the glass
            // sampled stale garbage and drew somewhere never shown (looked like the blur and
            // distortion sliders "did nothing"). Everything below reads from and writes to
            // MC's real framebuffer instead.
            int mcFbo = mc.getFramebuffer().fbo;

            int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevAbuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int[] prevViewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
            boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

            try {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                // Allocate/look up FBOs strictly AFTER the prev-state save above — attachTex
                // binds GL_FRAMEBUFFER, so doing this earlier (as the first version did)
                // "saved" an already-clobbered binding and the finally-block then handed MC a
                // wrong framebuffer for the rest of the frame: every later draw (e.g. the
                // Colors window opened on top of the ClickGUI) went into an invisible
                // offscreen texture — the "Colors won't open in-game" bug.
                ensureFbos(cw, ch);

                // 1) Capture what's already drawn behind this panel. GL framebuffers are
                // bottom-left origin; our (x,y) is top-left/screen-space, so the source Y
                // range is flipped — captureTex ends up "GL-orientation" (row 0 = panel's
                // bottom edge), which the composite pass's texcoords account for below.
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mcFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureFbo);
                GL30.glBlitFramebuffer(x, fbH - (y + h), x + w, fbH - y, 0, 0, cw, ch,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

                // 2) Separable Gaussian blur, ping-ponging between two small FBOs. Skipped
                // entirely at blurAmount01 == 0 (Default/No Glass never call this at all, but
                // Full Glass with the slider dragged to 0 should still just show a crisp — not
                // blurred — refracted backdrop).
                int sourceTex = captureTex;
                if (blurAmount01 > 0.01f) {
                    float radius = 1f + blurAmount01 * 3f;
                    GL20.glUseProgram(blurProgram);
                    GL30.glBindVertexArray(quadVao);
                    GL20.glUniform1f(blurRadiusLoc, radius);
                    // Blur passes need a FULLSCREEN quad (they're covering the whole cw×ch
                    // render target) — quadVbo otherwise still holds whatever the LAST
                    // composite draw (a small per-panel rect) set it to, so every call after
                    // the very first blurred only a tiny sliver and left the rest of pingFboA/B
                    // full of stale/garbage texture data from a previous, differently-sized
                    // use of that cached FBO. This was a real bug, not just "off by a bit".
                    setQuad(-1f, -1f, 1f, 1f);

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingFboA);
                    GL11.glViewport(0, 0, cw, ch);
                    bindTex(captureTex);
                    GL20.glUniform2f(blurTexelLoc, 1f / cw, 1f / ch);
                    GL20.glUniform2f(blurDirLoc, 1f, 0f);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingFboB);
                    bindTex(pingTexA);
                    GL20.glUniform2f(blurDirLoc, 0f, 1f);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
                    sourceTex = pingTexB;
                }

                // 3) Composite: refraction offset + rounded-rect clip, written straight back
                // into MC's framebuffer at the panel's real screen position/size. The
                // caller's own NanoVG draw (gradient tint, rim, text) layers on top right after.
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFbo);
                GL11.glViewport(0, 0, fbW, fbH);
                GL20.glUseProgram(compositeProgram);
                GL30.glBindVertexArray(quadVao);
                bindTex(sourceTex);
                GL20.glUniform2f(compSizeLoc, w, h);
                GL20.glUniform1f(compRadiusLoc, cornerR);
                GL20.glUniform1f(compDistortLoc, distortAmount01);

                // NDC quad exactly covering [x,y,w,h] in framebuffer px (Y flipped: NDC +1 is
                // screen top, our y is measured from the top).
                float ndcX0 = (2f * x / fbW) - 1f, ndcX1 = (2f * (x + w) / fbW) - 1f;
                float ndcY0 = 1f - (2f * (y + h) / fbH), ndcY1 = 1f - (2f * y / fbH);
                setQuad(ndcX0, ndcY0, ndcX1, ndcY1);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
                GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
                GL13.glActiveTexture(prevActiveTex);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevAbuf);
                GL30.glBindVertexArray(prevVao);
                GL20.glUseProgram(prevProgram);
                if (prevBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
                if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
                if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        } catch (Throwable t) {
            disabled = true;
            System.out.println("[Lume] GlassRenderer disabled after error: " + t);
        }
    }

    /**
     * Lighter-weight than {@link #panel}: captures + blurs [x,y,w,h] and blends it back over
     * the SAME region at the given alpha (no refraction, no rounded-rect clip) — a brief
     * "blur dissolving into focus" wash, used for the catalog-switch open transition instead
     * of the old scale-up-from-96% pop (which visibly changed the window's size, not wanted).
     * Call this AFTER the panel's own NanoVG draw for this frame, so it captures the freshly
     * drawn (sharp) content and blurs THAT — alpha should ramp 1→0 as the transition finishes.
     */
    public static void transitionOverlay(int x, int y, int w, int h, float blurAmount01, float alpha) {
        if (disabled || w <= 0 || h <= 0 || alpha <= 0.01f || blurAmount01 <= 0.01f) return;
        try {
            if (!initialized && !init()) { disabled = true; return; }
            MinecraftClient mc = MinecraftClient.getInstance();
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            if (x + w <= 0 || y + h <= 0 || x >= fbW || y >= fbH) return;

            int downsample = 2 + Math.round(blurAmount01 * 2f);   // 2..4 — see panel()'s comment
            int cw = Math.max(1, w / downsample), ch = Math.max(1, h / downsample);
            int mcFbo = mc.getFramebuffer().fbo;   // MC's real render target, NOT fbo 0 (see panel())

            int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevAbuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int[] prevViewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
            boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            // glBlendFunc on restore would collapse MC's separate RGB/alpha blend funcs into
            // one pair — query and restore all four via glBlendFuncSeparate instead.
            int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

            try {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                ensureFbos(cw, ch);   // AFTER the state save — see panel() for why

                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mcFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureFbo);
                GL30.glBlitFramebuffer(x, fbH - (y + h), x + w, fbH - y, 0, 0, cw, ch,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

                float radius = 1f + blurAmount01 * 3f;
                GL20.glUseProgram(blurProgram);
                GL30.glBindVertexArray(quadVao);
                GL20.glUniform1f(blurRadiusLoc, radius);
                setQuad(-1f, -1f, 1f, 1f);   // fullscreen for the blur passes — see panel()

                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingFboA);
                GL11.glViewport(0, 0, cw, ch);
                bindTex(captureTex);
                GL20.glUniform2f(blurTexelLoc, 1f / cw, 1f / ch);
                GL20.glUniform2f(blurDirLoc, 1f, 0f);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingFboB);
                bindTex(pingTexA);
                GL20.glUniform2f(blurDirLoc, 0f, 1f);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

                // Plain alpha-blended blit back into MC's framebuffer (own composite shader,
                // but distort=0 and r=0 so the SDF clip is a plain box over exactly [x,y,w,h]).
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFbo);
                GL11.glViewport(0, 0, fbW, fbH);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL14.GL_CONSTANT_ALPHA, GL14.GL_ONE_MINUS_CONSTANT_ALPHA);
                setBlendColorAlpha(alpha);
                GL20.glUseProgram(compositeProgram);
                GL30.glBindVertexArray(quadVao);
                bindTex(pingTexB);
                GL20.glUniform2f(compSizeLoc, w, h);
                // r=0 -> plain box SDF, clips only at the exact [x,y,w,h] edge (sharp corners,
                // fine here since this overlay exactly covers what was already drawn there).
                GL20.glUniform1f(compRadiusLoc, 0f);
                GL20.glUniform1f(compDistortLoc, 0f);

                float ndcX0 = (2f * x / fbW) - 1f, ndcX1 = (2f * (x + w) / fbW) - 1f;
                float ndcY0 = 1f - (2f * (y + h) / fbH), ndcY1 = 1f - (2f * y / fbH);
                setQuad(ndcX0, ndcY0, ndcX1, ndcY1);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
                GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
                GL13.glActiveTexture(prevActiveTex);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevAbuf);
                GL30.glBindVertexArray(prevVao);
                GL20.glUseProgram(prevProgram);
                GL14.glBlendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);
                if (prevBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
                if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
                if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        } catch (Throwable t) {
            disabled = true;
            System.out.println("[Lume] GlassRenderer disabled after error: " + t);
        }
    }

    private static void setBlendColorAlpha(float alpha) {
        GL14.glBlendColor(0f, 0f, 0f, alpha);
    }

    /**
     * Convenience for callers using ClickGuiScreen/LumeSubScreen's window-local coordinate
     * convention (pan by an offset, then scale around the screen centre for open/drag anim)
     * — converts [x,y,w,h,r] in that space into real framebuffer coords before calling
     * {@link #panel}. offX/offY must already be in framebuffer px (i.e. winOffX*S, not
     * raw winOffX) to match cx/cy/x/y/w/h, which are all framebuffer-px too.
     */
    public static void panelWindowLocal(int offX, int offY, double cx, double cy, float total,
                                         int x, int y, int w, int h, float r,
                                         float blurAmount01, float distortAmount01) {
        double sx0 = offX + cx + total * (x - cx);
        double sy0 = offY + cy + total * (y - cy);
        double sx1 = offX + cx + total * (x + w - cx);
        double sy1 = offY + cy + total * (y + h - cy);
        panel((int) Math.round(sx0), (int) Math.round(sy0),
                (int) Math.round(sx1 - sx0), (int) Math.round(sy1 - sy0),
                r * total, blurAmount01, distortAmount01);
    }

    private static void bindTex(int tex) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    // ---------------------------------------------------------------------
    // Setup

    private static boolean init() {
        // init() runs BEFORE panel()/transitionOverlay() save the caller's GL state, so it
        // must clean up after itself — it binds a program/VAO/buffer while setting up, and
        // leaving those bound (as the first version did) handed MC a clobbered binding for
        // the rest of that first frame.
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevAbuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            blurProgram = link(VERT_SRC, BLUR_FRAG_SRC);
            compositeProgram = link(VERT_SRC, COMPOSITE_FRAG_SRC);
            if (blurProgram == 0 || compositeProgram == 0) return false;
            blurTexelLoc = GL20.glGetUniformLocation(blurProgram, "uTexel");
            blurDirLoc = GL20.glGetUniformLocation(blurProgram, "uDirection");
            blurRadiusLoc = GL20.glGetUniformLocation(blurProgram, "uRadius");
            compSizeLoc = GL20.glGetUniformLocation(compositeProgram, "uSize");
            compRadiusLoc = GL20.glGetUniformLocation(compositeProgram, "uRadius");
            compDistortLoc = GL20.glGetUniformLocation(compositeProgram, "uDistort");

            quadVao = GL30.glGenVertexArrays();
            quadVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
            setQuad(-1f, -1f, 1f, 1f);   // default: fullscreen, used by the blur passes

            initialized = true;
            System.out.println("[Lume] GlassRenderer READY");
            return true;
        } catch (Throwable t) {
            System.out.println("[Lume] GlassRenderer init failed: " + t);
            return false;
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevAbuf);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);
        }
    }

    /** Uploads a quad [x0,y0]..[x1,y1] in NDC with matching 0..1 UVs (V flipped per-corner so
     *  sampling lines up with captureTex's GL-native bottom-left-origin orientation). */
    private static void setQuad(float x0, float y0, float x1, float y1) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            FloatBuffer buf = s.mallocFloat(24);
            // pos.xy, uv.xy — two triangles
            put(buf, x0, y0, 0f, 0f);
            put(buf, x1, y0, 1f, 0f);
            put(buf, x1, y1, 1f, 1f);
            put(buf, x0, y0, 0f, 0f);
            put(buf, x1, y1, 1f, 1f);
            put(buf, x0, y1, 0f, 1f);
            buf.flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buf);
        }
    }

    private static void put(FloatBuffer b, float x, float y, float u, float v) { b.put(x).put(y).put(u).put(v); }

    private static void ensureFbos(int cw, int ch) {
        long key = ((long) cw << 32) | (ch & 0xFFFFFFFFL);
        int[] set = fboCache.get(key);
        if (set == null) {
            if (fboCache.size() >= 8) {
                for (int[] s : fboCache.values()) deleteSet(s);
                fboCache.clear();
            }
            set = new int[6];
            set[0] = makeFbo(); set[1] = attachTex(set[0], cw, ch);
            set[2] = makeFbo(); set[3] = attachTex(set[2], cw, ch);
            set[4] = makeFbo(); set[5] = attachTex(set[4], cw, ch);
            fboCache.put(key, set);
        }
        captureFbo = set[0]; captureTex = set[1];
        pingFboA = set[2]; pingTexA = set[3];
        pingFboB = set[4]; pingTexB = set[5];
    }

    private static void deleteSet(int[] s) {
        GL30.glDeleteFramebuffers(s[0]); GL11.glDeleteTextures(s[1]);
        GL30.glDeleteFramebuffers(s[2]); GL11.glDeleteTextures(s[3]);
        GL30.glDeleteFramebuffers(s[4]); GL11.glDeleteTextures(s[5]);
    }

    private static int makeFbo() { return GL30.glGenFramebuffers(); }

    private static int attachTex(int fbo, int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete FBO: 0x" + Integer.toHexString(status));
        return tex;
    }

    private static int link(String vertSrc, String fragSrc) {
        int vert = compile(GL20.GL_VERTEX_SHADER, vertSrc);
        int frag = compile(GL20.GL_FRAGMENT_SHADER, fragSrc);
        if (vert == 0 || frag == 0) return 0;
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vert);
        GL20.glAttachShader(prog, frag);
        GL20.glBindAttribLocation(prog, 0, "aPos");
        GL20.glBindAttribLocation(prog, 1, "aUv");
        GL20.glLinkProgram(prog);
        try (MemoryStack s = MemoryStack.stackPush()) {
            IntBuffer status = s.mallocInt(1);
            GL20.glGetProgramiv(prog, GL20.GL_LINK_STATUS, status);
            if (status.get(0) == GL11.GL_FALSE) {
                System.out.println("[Lume] Glass shader link failed: " + GL20.glGetProgramInfoLog(prog));
                return 0;
            }
        }
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        return prog;
    }

    private static int compile(int type, String src) {
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        try (MemoryStack s = MemoryStack.stackPush()) {
            IntBuffer status = s.mallocInt(1);
            GL20.glGetShaderiv(sh, GL20.GL_COMPILE_STATUS, status);
            if (status.get(0) == GL11.GL_FALSE) {
                System.out.println("[Lume] Glass shader compile failed: " + GL20.glGetShaderInfoLog(sh));
                return 0;
            }
        }
        return sh;
    }

    // ---------------------------------------------------------------------
    // Shader sources

    private static final String VERT_SRC = """
            #version 150
            in vec2 aPos;
            in vec2 aUv;
            out vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """;

    private static final String BLUR_FRAG_SRC = """
            #version 150
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uTex;
            uniform vec2 uTexel;
            uniform vec2 uDirection;
            uniform float uRadius;
            void main() {
                vec2 o1 = uDirection * uTexel * uRadius;
                vec2 o2 = o1 * 2.0;
                vec2 o3 = o1 * 3.0;
                vec2 o4 = o1 * 4.0;
                vec4 c = texture(uTex, vUv) * 0.227027;
                c += texture(uTex, vUv + o1) * 0.1945946;
                c += texture(uTex, vUv - o1) * 0.1945946;
                c += texture(uTex, vUv + o2) * 0.1216216;
                c += texture(uTex, vUv - o2) * 0.1216216;
                c += texture(uTex, vUv + o3) * 0.054054;
                c += texture(uTex, vUv - o3) * 0.054054;
                c += texture(uTex, vUv + o4) * 0.016216;
                c += texture(uTex, vUv - o4) * 0.016216;
                fragColor = c;
            }
            """;

    /** vUv here is the panel-local 0..1 coordinate (V=0 at panel TOP, matching the quad set up
     *  in {@code panel()}); the quad's own v=0/v=1 already line up with the capture texture's
     *  bottom/top rows (both were derived from the same screen-bottom/screen-top mapping), so
     *  sampling needs NO extra flip here — an earlier version added one anyway, which just
     *  re-flipped an already-correct UV and rendered everything upside down. */
    private static final String COMPOSITE_FRAG_SRC = """
            #version 150
            in vec2 vUv;
            out vec4 fragColor;
            uniform sampler2D uTex;
            uniform vec2 uSize;
            uniform float uRadius;
            uniform float uDistort;

            float sdRoundRect(vec2 p, vec2 halfSize, float r) {
                vec2 q = abs(p) - halfSize + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            void main() {
                vec2 halfSize = uSize * 0.5;
                vec2 p = (vUv - 0.5) * uSize;
                float d = sdRoundRect(p, halfSize, uRadius);
                if (d > 0.0) discard;

                float edge = smoothstep(-28.0, 0.0, d);
                vec2 toCenter = -p / max(length(p), 0.0001);
                vec2 offsetPx = toCenter * edge * uDistort * 14.0;
                vec2 uv = clamp((p + offsetPx) / uSize + 0.5, 0.0, 1.0);

                fragColor = vec4(texture(uTex, uv).rgb, 1.0);
            }
            """;
}

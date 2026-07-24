package com.lume.client.nanovg;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Pixel-perfect rounded-rect fill + outline + glow, computed per-fragment on the GPU via a
 * Signed Distance Field (SDF) instead of {@code RenderUtil}'s CPU per-scanline coverage
 * approximation — genuinely sub-pixel smooth at any GUI scale, not just anti-aliased-looking.
 * Also supports ROTATION ({@link #boxRotated}) for icon glyphs built out of rotated bars
 * (gear teeth, an X, sun rays) — the quad drawn on screen is padded to the rotated shape's
 * bounding box, then the fragment shader rotates the SAMPLE point back into the box's own
 * unrotated local space before running the same SDF test {@link #box} uses.
 *
 * <p>Entirely raw GL (own shader/VAO/VBO), same defensive pattern as {@link GlassRenderer}
 * (first failure disables it permanently, saves/restores every bit of GL state it touches) —
 * deliberately NOT routed through DrawContext, since a custom per-fragment shader has no vanilla
 * hook. Coordinates are real FRAMEBUFFER px, top-left origin — same convention {@link
 * GlassRenderer#panel} uses, NOT the GUI-logical px {@code DrawContext}/{@code RenderUtil} take.
 * Call {@link #box} directly if you already have framebuffer coords, or go through a caller's
 * own window-local -> framebuffer conversion first (same idea as {@link
 * GlassRenderer#panelWindowLocal}) if drawing inside a pannable/zoomable window.
 */
public final class SdfRenderer {
    private SdfRenderer() {}

    private static boolean disabled = false;
    private static boolean initialized = false;

    private static int program;
    private static int quadSizeLoc, sizeLoc, angleLoc, radiusLoc, fillLoc, outlineLoc, outlineWidthLoc, glowLoc, glowSpreadLoc;
    private static int quadVao, quadVbo;

    /** Forces {@link #init()} to run now (if it hasn't already) and reports whether the shader
     *  is usable — lets a caller decide up front whether to draw the SDF background or fall back
     *  to a plain {@code RenderUtil} rect, instead of finding out only after {@link #box} silently
     *  no-op'd. Safe to call every frame; init only actually runs once. */
    public static boolean ensureInit() {
        if (!initialized && !disabled) {
            try { if (!init()) disabled = true; } catch (Throwable t) { disabled = true; }
        }
        return initialized && !disabled;
    }

    /**
     * Draws one rounded rect: solid {@code fillArgb} inside, an optional {@code outlineArgb}
     * ring of {@code outlineWidthPx} at the edge, and an optional soft {@code glowArgb} falloff
     * of {@code glowSpreadPx} beyond the outline — any of the three layers is skipped by passing
     * alpha 0 (fill) / width 0 (outline) / spread 0 (glow). All three are masked so they never
     * double-blend where they overlap (fill wins over outline wins over glow).
     *
     * @param x,y,w,h    framebuffer px, top-left origin
     * @param radiusPx   corner radius, framebuffer px
     */
    public static void box(int x, int y, int w, int h, float radiusPx,
                            int fillArgb, int outlineArgb, float outlineWidthPx,
                            int glowArgb, float glowSpreadPx) {
        draw(x, y, w, h, w, h, 0f, radiusPx, fillArgb, outlineArgb, outlineWidthPx, glowArgb, glowSpreadPx);
    }

    /**
     * Rotated rounded rect, centred at {@code (cx,cy)} — for icon glyphs (gear teeth, an X, sun
     * rays) built out of rotated bars. The quad actually drawn on screen is padded to the
     * rotated shape's own bounding box (computed here); the shader rotates the sample point back
     * into the box's unrotated local frame before the same SDF test {@link #box} uses, so the
     * box itself reads as genuinely rotated, not just its content.
     *
     * @param cx,cy      framebuffer px, box CENTRE (not top-left — rotation needs a centre)
     * @param w,h        the box's own (unrotated) size, framebuffer px
     * @param angleRad   rotation, radians
     */
    public static void boxRotated(int cx, int cy, int w, int h, float radiusPx, float angleRad,
                                   int fillArgb, int outlineArgb, float outlineWidthPx,
                                   int glowArgb, float glowSpreadPx) {
        float pad = Math.max(outlineWidthPx * 0.5f, 0f) + Math.max(glowSpreadPx, 0f) + 2f;
        float hw = w * 0.5f + pad, hh = h * 0.5f + pad;
        float ca = Math.abs((float) Math.cos(angleRad)), sa = Math.abs((float) Math.sin(angleRad));
        // Half-extents of the rotated box's own axis-aligned bounding box, so the quad we
        // actually draw always fully covers the rotated shape regardless of angle.
        float bx = hw * ca + hh * sa, by = hw * sa + hh * ca;
        int qx = Math.round(cx - bx), qy = Math.round(cy - by);
        int qw = Math.round(bx * 2f), qh = Math.round(by * 2f);
        draw(qx, qy, qw, qh, w, h, angleRad, radiusPx, fillArgb, outlineArgb, outlineWidthPx, glowArgb, glowSpreadPx);
    }

    /** Filled rounded rect only — shorthand for {@link #box} with no outline/glow. */
    public static void fill(int x, int y, int w, int h, float radiusPx, int fillArgb) {
        box(x, y, w, h, radiusPx, fillArgb, 0, 0f, 0, 0f);
    }

    /** Filled CIRCLE — shorthand for {@link #boxRotated} with a square box and radius covering
     *  the whole thing; angle is irrelevant for a circle, kept 0. Centre-based, like every icon
     *  primitive here (icons compose around a shared centre point). */
    public static void circle(int cx, int cy, float r, int fillArgb) {
        int d = Math.round(r * 2f);
        draw(cx - d / 2, cy - d / 2, d, d, d, d, 0f, r, fillArgb, 0, 0f, 0, 0f);
    }

    /** Ring (circle outline only) — for gear/globe icon bodies. */
    public static void ring(int cx, int cy, float r, float thicknessPx, int outlineArgb) {
        int d = Math.round(r * 2f);
        draw(cx - d / 2, cy - d / 2, d, d, d, d, 0f, r, 0, outlineArgb, thicknessPx, 0, 0f);
    }

    /** Outline (+ optional glow), no fill — the "invisible button, visible only by its glowing
     *  border" look. Pass {@code glowArgb=0} or {@code glowSpreadPx=0} for a plain crisp outline. */
    public static void outline(int x, int y, int w, int h, float radiusPx,
                                int outlineArgb, float outlineWidthPx, int glowArgb, float glowSpreadPx) {
        box(x, y, w, h, radiusPx, 0, outlineArgb, outlineWidthPx, glowArgb, glowSpreadPx);
    }

    /** Window-local [x,y,w,h,r] (the same pan/zoom-transformed coordinate convention {@code
     *  ClickGuiScreen}/{@code LumeSubScreen} use internally) -> real framebuffer px, then {@link
     *  #box}. Mirrors {@link GlassRenderer#panelWindowLocal} exactly. */
    public static void boxWindowLocal(int offX, int offY, double cx, double cy, float total,
                                       int x, int y, int w, int h, float r,
                                       int fillArgb, int outlineArgb, float outlineWidthPx,
                                       int glowArgb, float glowSpreadPx) {
        double sx0 = offX + cx + total * (x - cx);
        double sy0 = offY + cy + total * (y - cy);
        double sx1 = offX + cx + total * (x + w - cx);
        double sy1 = offY + cy + total * (y + h - cy);
        box((int) Math.round(sx0), (int) Math.round(sy0),
                (int) Math.round(sx1 - sx0), (int) Math.round(sy1 - sy0),
                r * total, fillArgb, outlineArgb, outlineWidthPx * total, glowArgb, glowSpreadPx * total);
    }

    /** Shared draw core — {@code qx,qy,qw,qh} is the actual quad drawn on screen (top-left,
     *  framebuffer px); {@code sizeW,sizeH} is the logical box size the SDF test runs against
     *  (equal to qw,qh for {@link #box}, smaller than the quad for {@link #boxRotated} since
     *  that quad is padded to the rotated bounding box). */
    private static void draw(int qx, int qy, int qw, int qh, int sizeW, int sizeH, float angleRad, float radiusPx,
                              int fillArgb, int outlineArgb, float outlineWidthPx,
                              int glowArgb, float glowSpreadPx) {
        if (disabled || qw <= 0 || qh <= 0) return;
        try {
            if (!initialized && !init()) { disabled = true; return; }
            MinecraftClient mc = MinecraftClient.getInstance();
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            if (qx + qw <= 0 || qy + qh <= 0 || qx >= fbW || qy >= fbH) return;   // fully off-screen

            int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int prevAbuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

            try {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

                GL20.glUseProgram(program);
                GL30.glBindVertexArray(quadVao);
                GL20.glUniform2f(quadSizeLoc, qw, qh);
                GL20.glUniform2f(sizeLoc, sizeW, sizeH);
                GL20.glUniform1f(angleLoc, angleRad);
                GL20.glUniform1f(radiusLoc, Math.min(radiusPx, Math.min(sizeW, sizeH) * 0.5f));
                setColor(fillLoc, fillArgb);
                setColor(outlineLoc, outlineArgb);
                GL20.glUniform1f(outlineWidthLoc, Math.max(0f, outlineWidthPx));
                setColor(glowLoc, glowArgb);
                GL20.glUniform1f(glowSpreadLoc, Math.max(0f, glowSpreadPx));

                float ndcX0 = (2f * qx / fbW) - 1f, ndcX1 = (2f * (qx + qw) / fbW) - 1f;
                float ndcY0 = 1f - (2f * (qy + qh) / fbH), ndcY1 = 1f - (2f * qy / fbH);
                setQuad(ndcX0, ndcY0, ndcX1, ndcY1);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            } finally {
                GL14.glBlendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);
                if (prevBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
                if (prevDepth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevAbuf);
                GL30.glBindVertexArray(prevVao);
                GL20.glUseProgram(prevProgram);
            }
        } catch (Throwable t) {
            disabled = true;
            System.out.println("[Lume] SdfRenderer disabled after error: " + t);
        }
    }

    private static void setColor(int loc, int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        GL20.glUniform4f(loc, r, g, b, a);
    }

    // ---------------------------------------------------------------------
    // Setup — same link()/compile()/setQuad() pattern as GlassRenderer, standalone copy since
    // there's no shared base class for these two small raw-GL utilities.

    private static boolean init() {
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevAbuf = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            program = link(VERT_SRC, FRAG_SRC);
            if (program == 0) return false;
            quadSizeLoc = GL20.glGetUniformLocation(program, "uQuadSize");
            sizeLoc = GL20.glGetUniformLocation(program, "uSize");
            angleLoc = GL20.glGetUniformLocation(program, "uAngle");
            radiusLoc = GL20.glGetUniformLocation(program, "uRadius");
            fillLoc = GL20.glGetUniformLocation(program, "uFill");
            outlineLoc = GL20.glGetUniformLocation(program, "uOutline");
            outlineWidthLoc = GL20.glGetUniformLocation(program, "uOutlineWidth");
            glowLoc = GL20.glGetUniformLocation(program, "uGlow");
            glowSpreadLoc = GL20.glGetUniformLocation(program, "uGlowSpread");

            quadVao = GL30.glGenVertexArrays();
            quadVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);

            initialized = true;
            System.out.println("[Lume] SdfRenderer READY");
            return true;
        } catch (Throwable t) {
            System.out.println("[Lume] SdfRenderer init failed: " + t);
            return false;
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevAbuf);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProgram);
        }
    }

    private static void setQuad(float x0, float y0, float x1, float y1) {
        try (MemoryStack s = MemoryStack.stackPush()) {
            FloatBuffer buf = s.mallocFloat(24);
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
                System.out.println("[Lume] SDF shader link failed: " + GL20.glGetProgramInfoLog(prog));
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
                System.out.println("[Lume] SDF shader compile failed: " + GL20.glGetShaderInfoLog(sh));
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

    /** Same sdRoundRect distance function GlassRenderer's composite shader already uses (proven
     *  to compile/link/render correctly on this project's actual GL setup) — layers fill (d<=0),
     *  outline band (0..outlineWidth), then glow falloff (outlineWidth..+glowSpread), each masked
     *  by (1-prevLayerAlpha) so overlapping bands don't double-blend. {@code uQuadSize} is the
     *  ACTUAL drawn quad's px size (may exceed {@code uSize} when rotated/padded, see {@link
     *  #boxRotated}); the sample point is measured in that quad space, then rotated by
     *  {@code -uAngle} into the box's own unrotated local frame before the SDF test runs against
     *  {@code uSize} — so {@code uAngle=0} (every {@link #box} call) is identical to the
     *  original unrotated shader, just with an extra no-op rotation. */
    private static final String FRAG_SRC = """
            #version 150
            in vec2 vUv;
            out vec4 fragColor;
            uniform vec2 uQuadSize;
            uniform vec2 uSize;
            uniform float uAngle;
            uniform float uRadius;
            uniform vec4 uFill;
            uniform vec4 uOutline;
            uniform float uOutlineWidth;
            uniform vec4 uGlow;
            uniform float uGlowSpread;

            float sdRoundRect(vec2 p, vec2 halfSize, float r) {
                vec2 q = abs(p) - halfSize + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            void main() {
                vec2 halfSize = uSize * 0.5;
                vec2 p = (vUv - 0.5) * uQuadSize;
                float ca = cos(uAngle), sa = sin(uAngle);
                vec2 pl = vec2(ca * p.x + sa * p.y, -sa * p.x + ca * p.y);
                float d = sdRoundRect(pl, halfSize, uRadius);

                // Outline is CENTERED on the d=0 boundary (straddles [-halfOutline, +halfOutline]),
                // the conventional stroke convention (matches SVG/Skia) — keeps the visible bounding
                // box close to the nominal uSize instead of ballooning outward by the full outline
                // width, which otherwise reads as the border not lining up with content drawn
                // elsewhere against the same nominal rect.
                float halfOutline = uOutlineWidth * 0.5;
                float fillEdge = -halfOutline;
                float fillA = (1.0 - smoothstep(fillEdge - 1.0, fillEdge + 1.0, d)) * uFill.a;

                float outEdge = halfOutline;
                float outA = (1.0 - smoothstep(outEdge - 1.0, outEdge + 1.0, d))
                            * smoothstep(fillEdge - 1.0, fillEdge + 1.0, d) * uOutline.a;

                float glowA = 0.0;
                if (uGlowSpread > 0.001) {
                    glowA = (1.0 - smoothstep(outEdge, outEdge + uGlowSpread, d))
                          * smoothstep(outEdge - 1.0, outEdge + 1.0, d) * uGlow.a;
                }

                vec3 rgb = uFill.rgb * fillA
                         + uOutline.rgb * outA * (1.0 - fillA)
                         + uGlow.rgb * glowA * (1.0 - fillA) * (1.0 - outA);
                float a = fillA + outA * (1.0 - fillA) + glowA * (1.0 - fillA) * (1.0 - outA);
                if (a < 0.003) discard;
                fragColor = vec4(rgb, a);
            }
            """;
}

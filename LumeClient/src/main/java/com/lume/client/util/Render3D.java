package com.lume.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Small camera-relative 3D line primitives for world-space overlays. */
public final class Render3D {

    private Render3D() {}

    // corner index bits: x<<2 | y<<1 | z ; faces reference these 4-corner loops (proven in BlockOutline)
    private static final int[][] FILL_FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };

    /**
     * Solid-filled box (all 6 faces), camera-relative coords, single flat colour.
     * Uses {@code getDebugQuads()} (QUADS mode, culling disabled — verified from
     * bytecode) so every face is fully visible from either side, no depth test.
     */
    public static void fillBox(VertexConsumerProvider vcp, Matrix4f mat, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float[][] p = {
                {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z0}, {x0, y1, z1},
                {x1, y0, z0}, {x1, y0, z1}, {x1, y1, z0}, {x1, y1, z1}
        };
        RenderLayer layer = RenderLayer.getDebugQuads();
        VertexConsumer vc = vcp.getBuffer(layer);
        for (int[] f : FILL_FACES)
            for (int idx : f)
                vc.vertex(mat, p[idx][0], p[idx][1], p[idx][2]).color(argb);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    /**
     * Filled box (arbitrary per-corner colours, arbitrary face winding via {@code faces}),
     * camera-relative coords, raw immediate draw with depth testing off — same technique as
     * {@link #boxThroughWalls}, so the fill stays visible through other blocks instead of
     * being occluded by the batched, depth-tested {@code getDebugQuads()} layer.
     */
    public static void fillBoxThroughWalls(MatrixStack.Entry e, float[][] corners, int[] cornerColors, int[][] faces) {
        net.minecraft.client.gl.ShaderProgram prevShader = RenderSystem.getShader();
        // Whatever drew just before this callback (Sodium's terrain pass, in particular)
        // may leave GL_CULL_FACE enabled with its own front-face winding convention still
        // bound — our face winding is outward/CCW-consistent, but relying on inherited state
        // instead of owning it here is exactly the kind of assumption that produced the
        // GlassRenderer FBO bugs earlier; disable culling explicitly and restore afterward.
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        Matrix4f mat = e.getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int[] f : faces)
            for (int idx : f)
                buf.vertex(mat, corners[idx][0], corners[idx][1], corners[idx][2]).color(cornerColors[idx]);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        if (prevCull) RenderSystem.enableCull();
        RenderSystem.setShader(prevShader);   // never leave a debug shader bound — terrain/entities drawn after this assume their own
    }

    public static void line(VertexConsumer vc, MatrixStack.Entry e,
                            float ax, float ay, float az, float bx, float by, float bz, int argb) {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return;
        nx /= len; ny /= len; nz /= len;
        Matrix4f p = e.getPositionMatrix();
        vc.vertex(p, ax, ay, az).color(argb).normal(e, nx, ny, nz);
        vc.vertex(p, bx, by, bz).color(argb).normal(e, nx, ny, nz);
    }

    public static void box(VertexConsumer vc, MatrixStack.Entry e,
                           float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        line(vc, e, x0, y0, z0, x1, y0, z0, c); line(vc, e, x1, y0, z0, x1, y0, z1, c);
        line(vc, e, x1, y0, z1, x0, y0, z1, c); line(vc, e, x0, y0, z1, x0, y0, z0, c);
        line(vc, e, x0, y1, z0, x1, y1, z0, c); line(vc, e, x1, y1, z0, x1, y1, z1, c);
        line(vc, e, x1, y1, z1, x0, y1, z1, c); line(vc, e, x0, y1, z1, x0, y1, z0, c);
        line(vc, e, x0, y0, z0, x0, y1, z0, c); line(vc, e, x1, y0, z0, x1, y1, z0, c);
        line(vc, e, x1, y0, z1, x1, y1, z1, c); line(vc, e, x0, y0, z1, x0, y1, z1, c);
    }

    // corner index bits: x<<2 | y<<1 | z ; each pair is one of the cube's 12 edges
    private static final int[][] EDGES = {
            {0, 4}, {4, 5}, {5, 1}, {1, 0},   // y0 face perimeter (bottom)
            {2, 6}, {6, 7}, {7, 3}, {3, 2},   // y1 face perimeter (top)
            {0, 2}, {4, 6}, {5, 7}, {1, 3},   // verticals
    };

    /**
     * Same 12-edge wireframe as {@link #box}, but through-walls: depth testing off, each edge
     * drawn as a camera-facing QUAD (not a GL line) so thickness is fully our own doing —
     * routed through {@link RenderLayer#getLines()} once (proven correct on this exact
     * GPU/driver — {@code CustomHitbox} uses it) but {@code imm.draw(layer)} internally calls
     * the layer's own {@code startDrawing()}, which re-applies ITS depth-test phase and quietly
     * undid our {@code disableDepthTest()} the moment the draw actually happened — so only the
     * near side of the box (not occluded by the wall to begin with) ever showed. A plain
     * {@code POSITION_COLOR} raw draw (same proven technique {@link #fillBoxThroughWalls}
     * already uses) has no such phase to fight with.
     */
    public static void boxThroughWalls(MatrixStack.Entry e,
                                        float x0, float y0, float z0, float x1, float y1, float z1,
                                        int argb, float lineWidth) {
        net.minecraft.client.gl.ShaderProgram prevShader = RenderSystem.getShader();
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        Matrix4f mat = e.getPositionMatrix();
        float[][] p = {
                {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z0}, {x0, y1, z1},
                {x1, y0, z0}, {x1, y0, z1}, {x1, y1, z0}, {x1, y1, z1}
        };
        float halfW = Math.max(0.003f, lineWidth * 0.0045f);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int[] edge : EDGES) {
            float[] a = p[edge[0]], b = p[edge[1]];
            edgeQuad(buf, mat, a[0], a[1], a[2], b[0], b[1], b[2], argb, halfW);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        if (prevCull) RenderSystem.enableCull();
        RenderSystem.setShader(prevShader);
    }

    /** One edge of {@link #boxThroughWalls} as a thin camera-facing quad — the perpendicular
     *  offset is cross(edgeDirection, viewDirection), and since these coordinates are already
     *  camera-relative, the camera sits at the origin, so "view direction" at the edge's
     *  midpoint is just that midpoint normalized. Degenerates safely (zero-length guards) for
     *  the vanishingly unlikely case of an edge pointing straight at the camera. */
    private static void edgeQuad(BufferBuilder buf, Matrix4f mat, float ax, float ay, float az,
                                  float bx, float by, float bz, int argb, float halfWidth) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        dx /= len; dy /= len; dz /= len;
        float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
        float vlen = (float) Math.sqrt(mx * mx + my * my + mz * mz);
        float vx, vy, vz;
        if (vlen < 1e-4f) { vx = 0; vy = 1; vz = 0; } else { vx = mx / vlen; vy = my / vlen; vz = mz / vlen; }
        float px = dy * vz - dz * vy, py = dz * vx - dx * vz, pz = dx * vy - dy * vx;
        float plen = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (plen < 1e-4f) { px = 1; py = 0; pz = 0; plen = 1; }
        px = px / plen * halfWidth; py = py / plen * halfWidth; pz = pz / plen * halfWidth;
        buf.vertex(mat, ax - px, ay - py, az - pz).color(argb);
        buf.vertex(mat, ax + px, ay + py, az + pz).color(argb);
        buf.vertex(mat, bx + px, by + py, bz + pz).color(argb);
        buf.vertex(mat, bx - px, by - py, bz - pz).color(argb);
    }

    /** Ring in the XZ plane at height y. */
    public static void ring(VertexConsumer vc, MatrixStack.Entry e,
                            float cx, float y, float cz, float radX, float radZ, int c, int seg) {
        float prevX = cx + radX, prevZ = cz;
        for (int i = 1; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            float nx = cx + radX * (float) Math.cos(a);
            float nz = cz + radZ * (float) Math.sin(a);
            line(vc, e, prevX, y, prevZ, nx, y, nz, c);
            prevX = nx; prevZ = nz;
        }
    }

    /** Ring in the XY plane at depth z (vertical circle). */
    public static void ringXY(VertexConsumer vc, MatrixStack.Entry e,
                              float cx, float cy, float z, float radX, float radY, int c, int seg) {
        float prevX = cx + radX, prevY = cy;
        for (int i = 1; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            float nx = cx + radX * (float) Math.cos(a);
            float ny = cy + radY * (float) Math.sin(a);
            line(vc, e, prevX, prevY, z, nx, ny, z, c);
            prevX = nx; prevY = ny;
        }
    }

    /** Ring in the YZ plane at x (vertical circle, other axis). */
    public static void ringYZ(VertexConsumer vc, MatrixStack.Entry e,
                              float x, float cy, float cz, float radY, float radZ, int c, int seg) {
        float prevY = cy + radY, prevZ = cz;
        for (int i = 1; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            float ny = cy + radY * (float) Math.cos(a);
            float nz = cz + radZ * (float) Math.sin(a);
            line(vc, e, x, prevY, prevZ, x, ny, nz, c);
            prevY = ny; prevZ = nz;
        }
    }
}

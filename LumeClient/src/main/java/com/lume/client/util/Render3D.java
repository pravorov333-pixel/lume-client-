package com.lume.client.util;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** Small camera-relative 3D line primitives for world-space overlays. */
public final class Render3D {

    private Render3D() {}

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

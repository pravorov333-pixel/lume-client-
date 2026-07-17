package com.lume.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight soft-glow particle engine. Particles are camera-facing additive
 * billboards (built from concentric quads → a smooth radial glow, no blocky
 * texture). Physics is frame-rate independent. Rendered via the vanilla
 * {@code getLightning} additive layer — no shaders/access-wideners required.
 * A GLSL bloom pass can later stack on top of the same emissive output.
 */
public final class ParticleEngine {

    private ParticleEngine() {}

    // Particle silhouette shapes — Circle/Square render as the original soft billboard quad
    // (batched, cheap); Star/Sparkle/Triangle/Heart/Diamond render as a real outlined
    // triangle-fan silhouette (see fanShape) since a "not-Minecraft-looking" star etc.
    // can't be faked with a plain quad.
    public static final int SHAPE_CIRCLE = 0, SHAPE_SQUARE = 1, SHAPE_STAR = 2, SHAPE_SPARKLE = 3, SHAPE_TRIANGLE = 4, SHAPE_HEART = 5, SHAPE_DIAMOND = 6;
    public static final String[] SHAPE_NAMES = { "Circle", "Square", "Star", "Sparkle", "Triangle", "Heart", "Diamond" };

    private static final List<GlowParticle> PARTICLES = new ArrayList<>();
    private static final int MAX = 4000;
    private static long lastNanos = System.nanoTime();

    public static void add(GlowParticle p) {
        if (PARTICLES.size() < MAX) PARTICLES.add(p);
    }

    public static void clear() { PARTICLES.clear(); }

    private static final java.util.Random RND = new java.util.Random();

    /** Radial burst of {@code count} particles (e.g. a hit bubble / explosion). */
    public static void burst(double x, double y, double z, int rgb, int count,
                             float speed, float size, float lifeSec) {
        burst(x, y, z, rgb, count, speed, size, lifeSec, SHAPE_CIRCLE);
    }

    public static void burst(double x, double y, double z, int rgb, int count,
                             float speed, float size, float lifeSec, int shape) {
        burst(x, y, z, rgb, count, speed, size, lifeSec, shape, null);
    }

    public static void burst(double x, double y, double z, int rgb, int count,
                             float speed, float size, float lifeSec, int shape, net.minecraft.util.Identifier tex) {
        for (int i = 0; i < count; i++) {
            GlowParticle p = new GlowParticle(x, y, z, rgb);
            double theta = RND.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2 * RND.nextDouble() - 1);
            double sp = speed * (0.6 + RND.nextDouble() * 0.4);
            p.vx = Math.sin(phi) * Math.cos(theta) * sp;
            p.vy = Math.cos(phi) * sp;
            p.vz = Math.sin(phi) * Math.sin(theta) * sp;
            p.gravity = 1.2f;
            p.drag = 0.35f;
            p.size = size; p.sizeEnd = size * 0.25f;
            p.alpha = 1f;
            p.maxLife = p.life = lifeSec;
            p.shape = shape;
            p.texture = tex;
            add(p);
        }
    }

    /** Hit-particle style dispatch (0 Burst, 1 Spark, 2 Ring, 3 Nova). */
    public static void hit(int style, double x, double y, double z, int rgb, int count, float speed, float size) {
        hit(style, x, y, z, rgb, count, speed, size, SHAPE_CIRCLE);
    }

    public static void hit(int style, double x, double y, double z, int rgb, int count, float speed, float size, int shape) {
        hit(style, x, y, z, rgb, count, speed, size, shape, null);
    }

    public static void hit(int style, double x, double y, double z, int rgb, int count, float speed, float size, int shape, net.minecraft.util.Identifier tex) {
        switch (style) {
            case 1 -> { // Spark — fast, gravity-heavy sparks with trails
                for (int i = 0; i < count; i++) {
                    GlowParticle p = new GlowParticle(x, y, z, rgb);
                    double theta = RND.nextDouble() * Math.PI * 2, phi = Math.acos(2 * RND.nextDouble() - 1);
                    double sp = speed * (1.0 + RND.nextDouble());
                    p.vx = Math.sin(phi) * Math.cos(theta) * sp; p.vy = Math.abs(Math.cos(phi)) * sp; p.vz = Math.sin(phi) * Math.sin(theta) * sp;
                    p.gravity = 6f; p.drag = 0.8f; p.size = size * 0.6f; p.sizeEnd = 0f; p.alpha = 1f; p.maxLife = p.life = 0.6;
                    p.shape = shape;
                    p.texture = tex;
                    add(p);
                }
            }
            case 2 -> { // Ring — flat expanding ring in the horizontal plane
                for (int i = 0; i < count; i++) {
                    double a = 2 * Math.PI * i / count;
                    GlowParticle p = new GlowParticle(x, y, z, rgb);
                    p.vx = Math.cos(a) * speed; p.vz = Math.sin(a) * speed; p.vy = 0;
                    p.gravity = 0f; p.drag = 0.25f; p.size = size; p.sizeEnd = size * 0.2f; p.alpha = 1f; p.maxLife = p.life = 0.5;
                    p.shape = shape;
                    p.texture = tex;
                    add(p);
                }
            }
            case 3 -> { // Nova — bright dense sphere flash: main burst + a lightened-tint inner core
                burst(x, y, z, rgb, count * 2, speed * 0.7f, size * 1.2f, 0.4f, shape, tex);
                burst(x, y, z, tint(rgb, 0.6f), count, speed * 0.4f, size * 0.8f, 0.3f, shape, tex);
            }
            default -> burst(x, y, z, rgb, count, speed, size, 0.5f, shape, tex); // Burst
        }
    }

    /** Lightens an 0xRRGGBB colour toward white by {@code amount} (0..1) — keeps the hue recognisable. */
    private static int tint(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (255 - r) * amount; g += (255 - g) * amount; b += (255 - b) * amount;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Immediate soft glow aura around an entity silhouette (Target ESP glow):
     * a vertical stack of additive billboards from feet to head, camera-facing.
     */
    public static void auraColumn(WorldRenderContext ctx, double cx, double y0, double y1, double cz,
                                  float radius, int rgb, float alpha, int layers) {
        if (ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        int rgbm = rgb & 0xFFFFFF;
        for (int i = 0; i <= layers; i++) {
            float f = i / (float) layers;
            float yy = (float) (y0 + (y1 - y0) * f - cam.y);
            float fx = (float) (cx - cam.x), fz = (float) (cz - cam.z);
            int a1 = (int) (alpha * 110), a2 = (int) (alpha * 200);
            quad(vc, mat, fx, yy, fz, right, up, radius * 1.9f, rgbm, a1);
            quad(vc, mat, fx, yy, fz, right, up, radius * 1.1f, rgbm, a2);
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.getDebugQuads());
        }
    }

    /**
     * Target ESP "Halo" — a ring of small billboards orbiting the entity at
     * mid-height, slowly rotating. Recomputed every frame (no persistent state).
     */
    public static void halo(WorldRenderContext ctx, double cx, double y0, double y1, double cz, float radius, int rgb) {
        halo(ctx, cx, y0, y1, cz, radius, rgb, SHAPE_CIRCLE);
    }

    public static void halo(WorldRenderContext ctx, double cx, double y0, double y1, double cz, float radius, int rgb, int shape) {
        if (ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Matrix4f mat = ms.peek().getPositionMatrix();
        int rgbm = rgb & 0xFFFFFF;
        double midY = (y0 + y1) / 2;
        double t = System.currentTimeMillis() / 1000.0;
        int n = 10;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());   // one quads buffer for both shapes — see fanShape()
        for (int i = 0; i < n; i++) {
            double a = t * 1.2 + 2 * Math.PI * i / n;
            double wx = cx + Math.cos(a) * radius, wz = cz + Math.sin(a) * radius;
            float fx = (float) (wx - cam.x), fy = (float) (midY - cam.y), fz = (float) (wz - cam.z);
            if (shape >= SHAPE_STAR) {
                fanShape(vc, mat, fx, fy, fz, right, up, shape, 0.14f, rgbm, 235);
            } else {
                quad(vc, mat, fx, fy, fz, right, up, 0.10f, rgbm, 220);
                quad(vc, mat, fx, fy, fz, right, up, 0.18f, rgbm, 90);
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getDebugQuads());
    }

    /** Target ESP "Sparks" — occasional rising sparks spawned from within the entity's silhouette. */
    public static void sparksAround(double cx, double y0, double y1, double cz, float radius, int rgb) {
        sparksAround(cx, y0, y1, cz, radius, rgb, SHAPE_CIRCLE);
    }

    public static void sparksAround(double cx, double y0, double y1, double cz, float radius, int rgb, int shape) {
        if (RND.nextFloat() > 0.5f) return;   // throttle spawn rate to a light trickle
        double a = RND.nextDouble() * Math.PI * 2;
        double wx = cx + Math.cos(a) * radius * RND.nextDouble();
        double wz = cz + Math.sin(a) * radius * RND.nextDouble();
        double wy = y0 + RND.nextDouble() * (y1 - y0);
        GlowParticle p = new GlowParticle(wx, wy, wz, rgb);
        p.vy = 0.6 + RND.nextDouble() * 0.4;
        p.gravity = 0.4f;
        p.drag = 0.9f;
        p.size = 0.06f; p.sizeEnd = 0f;
        p.alpha = 1f;
        p.maxLife = p.life = 0.8;
        p.shape = shape;
        add(p);
    }

    /**
     * Target ESP "Limbs" — a solid, always-visible (X-ray) glow at one exact world point,
     * redrawn fresh every frame (no fade). Used for the constant "limb highlight" look.
     */
    public static void limbGlow(WorldRenderContext ctx, double x, double y, double z, float radius, int rgb) {
        if (ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        float fx = (float) (x - cam.x), fy = (float) (y - cam.y), fz = (float) (z - cam.z);
        quad(vc, mat, fx, fy, fz, right, up, radius * 1.6f, rgb & 0xFFFFFF, 130);
        quad(vc, mat, fx, fy, fz, right, up, radius, rgb & 0xFFFFFF, 255);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getDebugQuads());
    }

    /** A single short-lived particle dropped at a limb's exact position — builds a comet trail while moving. */
    public static void trailSpark(double x, double y, double z, int rgb) {
        GlowParticle p = new GlowParticle(x, y, z, rgb);
        p.gravity = 0f; p.drag = 0.4f;
        p.size = 0.09f; p.sizeEnd = 0.02f;
        p.alpha = 0.9f;
        p.maxLife = p.life = 0.25;
        add(p);
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void render(WorldRenderContext ctx) {
        if (PARTICLES.isEmpty() || ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        // frame-rate independent physics step
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        // Compact in place instead of Iterator.remove(): ArrayList.remove() shifts every
        // trailing element (O(n) per call), so removing a whole wave of same-age particles
        // that die together (e.g. the first batch spawned right after the module is enabled)
        // costs O(n*k) in one frame — a real stutter. This does one O(n) pass + O(dead) tail-trim.
        int w = 0;
        for (int r = 0, n = PARTICLES.size(); r < n; r++) {
            GlowParticle p = PARTICLES.get(r);
            if (p.tick(dt)) PARTICLES.set(w++, p);
        }
        for (int i = PARTICLES.size() - 1; i >= w; i--) PARTICLES.remove(i);
        if (PARTICLES.isEmpty()) return;

        Vec3d cam = ctx.camera().getPos();
        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up    = new Vector3f(0, 1, 0).rotate(q);

        Matrix4f mat = ms.peek().getPositionMatrix();

        // ONE buffer, one pass, quads only. Everything (round billboards AND star/heart/etc.
        // silhouettes) goes through DrawMode.QUADS, where every 4 vertices are an independent
        // quad — so particles can never bleed into each other no matter who owns the buffer.
        // Do NOT reintroduce getDebugTriangleFan(): TRIANGLE_FAN connects every triangle back to
        // the buffer's FIRST vertex, so it only stays correct if the buffer is flushed between
        // particles, and the old `vcp instanceof Immediate` flush silently does nothing once
        // something wraps the provider (Iris' batched entity rendering does exactly that) —
        // every shaped particle then fused into one connected mess. See fanShape().
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        for (GlowParticle p : PARTICLES) {
            if (p.texture != null) continue;
            float t = p.progress();
            float a = p.alpha * (1f - t);                       // fade out over life
            if (a <= 0.01f) continue;
            float rad = p.size + (p.sizeEnd - p.size) * t;
            float cx = (float) (p.x - cam.x), cy = (float) (p.y - cam.y), cz = (float) (p.z - cam.z);
            int rgb = p.rgb & 0xFFFFFF;
            if (p.shape >= SHAPE_STAR) {
                fanShape(vc, mat, cx, cy, cz, right, up, p.shape, rad * 1.6f, rgb, (int) (a * 255));
            } else {
                // soft radial falloff: faint wide halo + bright core
                quad(vc, mat, cx, cy, cz, right, up, rad * 1.7f, rgb, (int) (a * 90));
                quad(vc, mat, cx, cy, cz, right, up, rad * 0.9f, rgb, (int) (a * 255));
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate immQuads) immQuads.draw(RenderLayer.getDebugQuads());

        // Custom drop-in PNG particles — textured billboard, one draw call per particle
        // (each may use a different texture; batching by texture isn't worth the complexity here).
        for (GlowParticle p : PARTICLES) {
            if (p.texture == null) continue;
            float t = p.progress();
            float a = p.alpha * (1f - t);
            if (a <= 0.01f) continue;
            float rad = p.size + (p.sizeEnd - p.size) * t;
            float cx = (float) (p.x - cam.x), cy = (float) (p.y - cam.y), cz = (float) (p.z - cam.z);
            texturedQuad(vcp, mat, p.texture, cx, cy, cz, right, up, rad * 1.4f, p.rgb & 0xFFFFFF, (int) (a * 255));
        }
    }

    /** One custom-texture billboard (camera-facing, additive-ish translucent), tinted by the
     *  particle's colour. Flushed immediately, same reasoning as {@link #fanShape}. */
    private static void texturedQuad(VertexConsumerProvider vcp, Matrix4f m, net.minecraft.util.Identifier tex,
                                     float cx, float cy, float cz, Vector3f right, Vector3f up, float r, int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        int argb = (alpha << 24) | rgb;
        int light = 0xF000F0;   // full bright — matches other always-lit Lume world overlays (waypoints, etc.)
        RenderLayer layer = RenderLayer.getText(tex);
        VertexConsumer vc = vcp.getBuffer(layer);
        float rx = right.x * r, ry = right.y * r, rz = right.z * r;
        float ux = up.x * r, uy = up.y * r, uz = up.z * r;
        vc.vertex(m, cx - rx - ux, cy - ry - uy, cz - rz - uz).color(argb).texture(0f, 1f).light(light);
        vc.vertex(m, cx - rx + ux, cy - ry + uy, cz - rz + uz).color(argb).texture(0f, 0f).light(light);
        vc.vertex(m, cx + rx + ux, cy + ry + uy, cz + rz + uz).color(argb).texture(1f, 0f).light(light);
        vc.vertex(m, cx + rx - ux, cy + ry - uy, cz + rz - uz).color(argb).texture(1f, 1f).light(light);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    /** Package-visible for DeathFx's bubble-ring effect. */
    static void quad(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                             Vector3f right, Vector3f up, float r, int rgb, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        int argb = (alpha << 24) | rgb;
        float rx = right.x * r, ry = right.y * r, rz = right.z * r;
        float ux = up.x * r, uy = up.y * r, uz = up.z * r;
        // four corners, CCW
        vc.vertex(m, cx - rx - ux, cy - ry - uy, cz - rz - uz).color(argb);
        vc.vertex(m, cx - rx + ux, cy - ry + uy, cz - rz + uz).color(argb);
        vc.vertex(m, cx + rx + ux, cy + ry + uy, cz + rz + uz).color(argb);
        vc.vertex(m, cx + rx - ux, cy + ry - uy, cz + rz - uz).color(argb);
    }

    // ---- shaped (non-Minecraft-looking) particle silhouettes --------------

    // shapeOutline() only ever depends on `shape`, never on per-particle state, but used to
    // rebuild (and for star/sparkle, re-run Math.cos/sin over) a fresh float[][] on EVERY
    // fanShape() call — i.e. every frame, for every star/sparkle/triangle/diamond particle
    // alive. Cached by shape id the same way heartOutline() already cached itself.
    private static final float[][][] SHAPE_OUTLINE_CACHE = new float[7][][];

    /** Outline points (normalized -1..1) for each fan-drawn shape, in order around the perimeter. */
    private static float[][] shapeOutline(int shape) {
        float[][] cached = SHAPE_OUTLINE_CACHE[shape];
        if (cached != null) return cached;
        float[][] out = switch (shape) {
            case SHAPE_SPARKLE -> starOutline(4, 0.22f);   // thin 4-point glint/sparkle
            case SHAPE_TRIANGLE -> new float[][] {
                    { 0f, -1f }, { 0.87f, 0.5f }, { -0.87f, 0.5f }
            };
            case SHAPE_DIAMOND -> new float[][] {
                    { 0f, -1f }, { 1f, 0f }, { 0f, 1f }, { -1f, 0f }
            };
            case SHAPE_HEART -> heartOutline();
            default -> starOutline(5, 0.45f);   // SHAPE_STAR
        };
        SHAPE_OUTLINE_CACHE[shape] = out;
        return out;
    }

    private static float[][] starOutline(int points, float innerRatio) {
        float[][] out = new float[points * 2][2];
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + Math.PI * i / points;
            float r = (i % 2 == 0) ? 1f : innerRatio;
            out[i][0] = (float) (Math.cos(a) * r);
            out[i][1] = (float) (Math.sin(a) * r);
        }
        return out;
    }

    private static float[][] HEART_CACHE;
    private static float[][] heartOutline() {
        if (HEART_CACHE != null) return HEART_CACHE;
        int n = 20;
        float[][] out = new float[n][2];
        float maxR = 0f;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            xs[i] = x; ys[i] = -y;   // flip so the point faces down, lobes up
            maxR = (float) Math.max(maxR, Math.max(Math.abs(x), Math.abs(y)));
        }
        for (int i = 0; i < n; i++) { out[i][0] = (float) (xs[i] / maxR); out[i][1] = (float) (ys[i] / maxR); }
        HEART_CACHE = out;
        return out;
    }

    /**
     * Draws one shaped particle (star/heart/…) into a shared DrawMode.QUADS buffer: each
     * centre→edge triangle is emitted as a quad whose last vertex is repeated (a degenerate
     * quad renders exactly as that triangle).
     *
     * <p>This deliberately does NOT use TRIANGLE_FAN. A fan chains every triangle to the
     * buffer's first vertex, so two particles in one un-flushed buffer get welded together —
     * and the only thing that used to prevent that was a per-particle
     * {@code vcp instanceof VertexConsumerProvider.Immediate} flush, which silently no-ops as
     * soon as anything wraps the provider (Iris' batched entity rendering). QUADS needs no
     * flush to stay correct: every 4 vertices stand alone.
     */
    private static void fanShape(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                 Vector3f right, Vector3f up, int shape, float r, int rgb, int alpha) {
        float[][] pts = shapeOutline(shape);
        alpha = Math.max(0, Math.min(255, alpha));
        int argb = (alpha << 24) | (rgb & 0xFFFFFF);
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            float[] a = pts[i], b = pts[(i + 1) % n];   // wraps → closes the silhouette
            float ax = a[0] * r, ay = a[1] * r;
            float bx = b[0] * r, by = b[1] * r;
            float axw = cx + right.x * ax + up.x * ay, ayw = cy + right.y * ax + up.y * ay, azw = cz + right.z * ax + up.z * ay;
            float bxw = cx + right.x * bx + up.x * by, byw = cy + right.y * bx + up.y * by, bzw = cz + right.z * bx + up.z * by;
            vc.vertex(m, cx, cy, cz).color(argb);
            vc.vertex(m, axw, ayw, azw).color(argb);
            vc.vertex(m, bxw, byw, bzw).color(argb);
            vc.vertex(m, bxw, byw, bzw).color(argb);   // repeated → degenerate quad == triangle
        }
    }
}

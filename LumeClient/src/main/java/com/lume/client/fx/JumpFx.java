package com.lume.client.fx;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * One-shot ground effects for the Jump Particles module — Circle (an expanding ring that grows
 * to swallow your hitbox, holds, then fades) and Wave (concentric rings of ground tiles lighting
 * up in sequence). Explosion style doesn't need this — it's plain {@link ParticleEngine} particles.
 */
public final class JumpFx {
    private JumpFx() {}

    private static final long GROW_MS = 220, HOLD_MS = 500;
    private static final long WAVE_TILE_LIFE_MS = 350;

    private static final class Ring {
        double x, y, z; int rgb; long start; float maxRadius;
    }

    private static final class WaveTile {
        double x, y, z; int rgb; long spawnAt;
    }

    private static final List<Ring> rings = new ArrayList<>();
    private static final List<WaveTile> waveTiles = new ArrayList<>();

    /** Circle style: grows from ~0 to {@code maxRadius} (roughly your hitbox radius), holds, fades. */
    public static void spawnRing(double x, double y, double z, int rgb, float maxRadius) {
        Ring r = new Ring();
        r.x = x; r.y = y; r.z = z; r.rgb = rgb; r.maxRadius = maxRadius;
        r.start = System.currentTimeMillis();
        rings.add(r);
    }

    /**
     * Wave style: {@code ringCount} concentric rings of actual ground BLOCKS lighting up (grid-
     * aligned, sat on the real terrain surface via the heightmap — not a floating decal), each
     * ring firing a little after the previous.
     */
    public static void spawnWave(double px, double py, double pz, int rgb, int ringCount, float spacing) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        long now = System.currentTimeMillis();
        Set<Long> seen = new HashSet<>();   // dedupe blocks two rings might both land on
        for (int ring = 0; ring < ringCount; ring++) {
            double radius = (ring + 1) * spacing;
            int count = Math.max(6, (int) (radius * 7));
            long delay = ring * 70L;
            for (int i = 0; i < count; i++) {
                double a = 2 * Math.PI * i / count;
                int bx = MathHelper.floor(px + Math.cos(a) * radius);
                int bz = MathHelper.floor(pz + Math.sin(a) * radius);
                long key = (((long) bx) << 32) ^ (bz & 0xFFFFFFFFL);
                if (!seen.add(key)) continue;
                int by = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);

                WaveTile t = new WaveTile();
                t.x = bx + 0.5; t.z = bz + 0.5; t.y = by;
                t.rgb = rgb;
                t.spawnAt = now + delay;
                waveTiles.add(t);
            }
        }
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void render(WorldRenderContext ctx) {
        if (rings.isEmpty() && waveTiles.isEmpty()) return;
        if (ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        Matrix4f mat = ms.peek().getPositionMatrix();
        long now = System.currentTimeMillis();

        RenderLayer layer = RenderLayer.getDebugQuads();
        VertexConsumer vc = vcp.getBuffer(layer);
        boolean drew = false;

        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring r = it.next();
            long age = now - r.start;
            if (age > GROW_MS + HOLD_MS) { it.remove(); continue; }
            float radius, alpha;
            if (age < GROW_MS) {
                float p = age / (float) GROW_MS;
                radius = r.maxRadius * (1f - (1f - p) * (1f - p));   // ease-out grow
                alpha = 1f;
            } else {
                radius = r.maxRadius;
                alpha = 1f - (age - GROW_MS) / (float) HOLD_MS;      // fade during hold
            }
            drawRing(vc, mat, cam, r.x, r.y, r.z, radius, r.rgb, alpha);
            drew = true;
        }

        for (Iterator<WaveTile> it = waveTiles.iterator(); it.hasNext(); ) {
            WaveTile t = it.next();
            if (now < t.spawnAt) continue;
            long age = now - t.spawnAt;
            if (age > WAVE_TILE_LIFE_MS) { it.remove(); continue; }
            float alpha = 1f - age / (float) WAVE_TILE_LIFE_MS;
            drawTile(vc, mat, cam, t.x, t.y, t.z, t.rgb, alpha);
            drew = true;
        }

        if (drew && vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    private static void drawRing(VertexConsumer vc, Matrix4f mat, Vec3d cam, double cx, double cy, double cz,
                                  float radius, int rgb, float alpha) {
        if (radius <= 0.01f) return;
        int seg = 24;
        float thickness = 0.025f;
        int argb = (Math.round(alpha * 210) << 24) | (rgb & 0xFFFFFF);
        float y = (float) (cy - cam.y) + 0.02f;
        float ccx = (float) (cx - cam.x), ccz = (float) (cz - cam.z);
        float outer = radius, inner = Math.max(0f, radius - thickness);
        float prevOX = ccx + outer, prevOZ = ccz, prevIX = ccx + inner, prevIZ = ccz;
        for (int i = 1; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            float cosA = (float) Math.cos(a), sinA = (float) Math.sin(a);
            float ox = ccx + cosA * outer, oz = ccz + sinA * outer;
            float ix = ccx + cosA * inner, iz = ccz + sinA * inner;
            vc.vertex(mat, prevOX, y, prevOZ).color(argb);
            vc.vertex(mat, prevIX, y, prevIZ).color(argb);
            vc.vertex(mat, ix, y, iz).color(argb);
            vc.vertex(mat, ox, y, oz).color(argb);
            prevOX = ox; prevOZ = oz; prevIX = ix; prevIZ = iz;
        }
    }

    private static void drawTile(VertexConsumer vc, Matrix4f mat, Vec3d cam, double x, double y, double z, int rgb, float alpha) {
        // Nearly the full block face and fairly opaque — reads as "the block itself lit up",
        // not a translucent decal floating over it.
        float half = 0.49f;
        int argb = (Math.round(alpha * 225) << 24) | (rgb & 0xFFFFFF);
        float cx = (float) (x - cam.x), cy = (float) (y - cam.y) + 0.02f, cz = (float) (z - cam.z);
        vc.vertex(mat, cx - half, cy, cz - half).color(argb);
        vc.vertex(mat, cx - half, cy, cz + half).color(argb);
        vc.vertex(mat, cx + half, cy, cz + half).color(argb);
        vc.vertex(mat, cx + half, cy, cz - half).color(argb);
    }
}

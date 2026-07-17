package com.lume.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * A connected ribbon/line trailing from the player's back — NOT individual particles (that's
 * {@link com.lume.client.module.modules.render.ParticleTrail}). Sampled once per tick at the
 * same body-anchored back point {@code Capes} uses, trimmed by cumulative distance from the
 * newest point rather than time, so it always reads as "however many blocks of trail", not
 * "however many seconds" (matters at different movement speeds).
 *
 * <p>Only grows while {@code active} (sprinting roughly forward — see {@code Trail.isActive}).
 * The instant that stops, no new points are added and the tail end "retracts" — is eaten away
 * over time from the oldest end — until it catches up to the (now stationary) head and the whole
 * trail is gone, instead of just freezing in place.
 */
public final class BackTrail {
    private BackTrail() {}

    public static final int STYLE_RECTANGLE = 0, STYLE_THIN = 1;

    /** Blocks/sec the tail eats itself once you stop running forward. */
    private static final float RETRACT_SPEED = 3.5f;

    private static final class Pt {
        float x, y, z;   // world-space, absolute (converted to camera-relative at draw time)
        float distFromNewer;   // distance to the NEXT (newer) point in the deque
    }

    private static final Deque<Pt> pts = new ArrayDeque<>();
    private static float retractBudget = 0f;
    private static long lastNanos = System.nanoTime();

    /** Call once per client tick. {@code active} = sprinting roughly forward right now. */
    public static void tick(float maxLength, boolean active) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;

        if (p == null) { pts.clear(); retractBudget = 0; return; }

        if (!active || maxLength <= 0.01f) {
            retractBudget += dt * RETRACT_SPEED;
            retractFromTail();
            return;
        }
        retractBudget = 0;

        float bodyYaw = p.bodyYaw;
        float yawRad = (float) Math.toRadians(bodyYaw);
        float fx = -MathHelper.sin(yawRad), fz = MathHelper.cos(yawRad);
        float bx = (float) (p.getX() - fx * 0.14);
        float bz = (float) (p.getZ() - fz * 0.14);
        float by = (float) (p.getY() + p.getHeight() * 0.55);

        Pt head = pts.peekLast();
        if (head != null) {
            float dx = bx - head.x, dy = by - head.y, dz = bz - head.z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.015f) { trim(maxLength); return; }   // not enough movement — avoid degenerate/duplicate points
            head.distFromNewer = d;
        }

        Pt np = new Pt();
        np.x = bx; np.y = by; np.z = bz; np.distFromNewer = 0f;
        pts.addLast(np);

        trim(maxLength);
    }

    private static void trim(float maxLength) {
        float total = 0f;
        // Walk from newest to oldest; once cumulative distance exceeds maxLength, drop everything older.
        Iterator<Pt> it = pts.descendingIterator();
        Pt prev = null;
        while (it.hasNext()) {
            Pt cur = it.next();
            if (prev != null) total += prev.distFromNewer;
            if (total > maxLength) { it.remove(); continue; }
            prev = cur;
        }
        while (pts.size() > 200) pts.removeFirst();   // hard safety cap regardless of distance math
    }

    /** Eats away at the OLDEST end by {@code retractBudget} worth of cumulative distance. */
    private static void retractFromTail() {
        while (!pts.isEmpty() && retractBudget > 0) {
            Pt oldest = pts.peekFirst();
            if (pts.size() == 1 || oldest.distFromNewer <= retractBudget) {
                retractBudget -= oldest.distFromNewer;
                pts.removeFirst();
            } else {
                break;
            }
        }
        if (pts.isEmpty()) retractBudget = 0;
    }

    public static void clear() { pts.clear(); retractBudget = 0; }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered by the Trail module, gated on itself). */
    public static void render(WorldRenderContext ctx, int rgb, int style, float width, float maxLength) {
        if (pts.size() < 2 || ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        Matrix4f mat = ms.peek().getPositionMatrix();

        RenderLayer layer = style == STYLE_THIN ? RenderLayer.getLines() : RenderLayer.getDebugQuads();
        if (style == STYLE_THIN) RenderSystem.lineWidth(2f);
        VertexConsumer vc = vcp.getBuffer(layer);

        float cum = 0f;
        Pt[] arr = pts.toArray(new Pt[0]);   // oldest..newest
        for (int i = 0; i < arr.length - 1; i++) {
            Pt a = arr[i], b = arr[i + 1];
            float aAlpha = 1f - Math.min(1f, cum / maxLength);
            cum += a.distFromNewer;
            float bAlpha = 1f - Math.min(1f, cum / maxLength);

            float ax = (float) (a.x - cam.x), ay = (float) (a.y - cam.y), az = (float) (a.z - cam.z);
            float bx = (float) (b.x - cam.x), by = (float) (b.y - cam.y), bz = (float) (b.z - cam.z);

            if (style == STYLE_THIN) {
                int aArgb = (Math.round(aAlpha * 220) << 24) | (rgb & 0xFFFFFF);
                int bArgb = (Math.round(bAlpha * 220) << 24) | (rgb & 0xFFFFFF);
                vc.vertex(mat, ax, ay, az).color(aArgb).normal(0, 1, 0);
                vc.vertex(mat, bx, by, bz).color(bArgb).normal(0, 1, 0);
            } else {
                // Stands UP (vertical fin), not flat on the ground — offset by height, not by
                // the horizontal "right" vector.
                float hw = width / 2f;
                int aArgb = (Math.round(aAlpha * 180) << 24) | (rgb & 0xFFFFFF);
                int bArgb = (Math.round(bAlpha * 180) << 24) | (rgb & 0xFFFFFF);
                vc.vertex(mat, ax, ay - hw, az).color(aArgb);
                vc.vertex(mat, ax, ay + hw, az).color(aArgb);
                vc.vertex(mat, bx, by + hw, bz).color(bArgb);
                vc.vertex(mat, bx, by - hw, bz).color(bArgb);
            }
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
        if (style == STYLE_THIN) RenderSystem.lineWidth(1f);
    }
}

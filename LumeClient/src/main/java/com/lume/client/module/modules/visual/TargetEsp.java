package com.lume.client.module.modules.visual;

import com.lume.client.LumeClient;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Target ESP — marks the entity your crosshair is aimed at (legit: crosshair
 * target only, never wall/proximity scan). "Outline" uses MC's native entity
 * outline framebuffer (see MinecraftClientMixin + EntityMixin); Circle/Square
 * are wireframe markers (see {@link Render3D}); Halo/Sparks/Limbs are particle
 * effects via {@link ParticleEngine}. The "HUD" panel (name/HP/armor, top of
 * screen) absorbs what used to be the separate "Target HUD" module — one
 * toggle governs everything about the crosshair target.
 */
public class TargetEsp extends Module {

    public final ModeSetting  filter    = add(new ModeSetting("Filter", 0, "Players", "Hostiles", "All"));
    public final ModeSetting  style     = add(new ModeSetting("Style", 0, "Outline", "Halo", "Circle", "Square", "Sparks", "Limbs"));
    public final ModeSetting  shape     = add(new ModeSetting("Shape", 0, ParticleEngine.SHAPE_NAMES));
    public final ColorSetting color     = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting thickness = add(new SliderSetting("Thickness", 2.0, 1.0, 5.0, false));
    public final SliderSetting size      = add(new SliderSetting("Size", 1.0, 0.3, 1.0, false));

    // HUD — absorbs the old standalone "Target HUD" module: name/HP/armor panel,
    // top-centre of the screen, for whatever passes the Filter above.
    public final BoolSetting  hud           = add(new BoolSetting("HUD", true));
    public final BoolSetting  hudHead       = add(new BoolSetting("3D Head", true));
    public final BoolSetting  hudHealthBar  = add(new BoolSetting("Health Bar", true));
    public final BoolSetting  hudArmor      = add(new BoolSetting("Armor", true));
    public final BoolSetting  hudAnimate    = add(new BoolSetting("Animate HP", true));

    // Show HP — independent of the HUD panel above: a small HP readout floating
    // right over whatever entity you're hitting/aiming at (screen-space NanoVG
    // pin, projected from the target's world position — see
    // HudRenderer#renderTargetHpPin). Also hides the vanilla "below name"
    // scoreboard HP some servers (e.g. FunTime) push, so only ours shows — see
    // ClientPlayNetworkHandlerMixin#lume$suppressBelowNameHp.
    public final BoolSetting  hpAboveTarget = add(new BoolSetting("Show HP", false));
    public final ModeSetting  hpStyle       = add(new ModeSetting("HP Style", 0, "Hearts", "Bar"));
    public final BoolSetting  hpText        = add(new BoolSetting("HP Text", true));

    public TargetEsp() {
        super("Target ESP", "Marks the entity your crosshair is on", Category.VISUALS, -1);
    }

    /** Shared Filter check (Players/Hostiles/All) — used by both the world ESP styles and the HUD panel. */
    public static boolean passesFilter(TargetEsp esp, LivingEntity le) {
        boolean isPlayer  = le instanceof PlayerEntity;
        boolean isHostile = le instanceof MobEntity mob && mob.getTarget() != null;
        int f = esp.filter.index;
        if (f == 0) return isPlayer;
        if (f == 1) return isHostile;
        return true;
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Target ESP");
        if (!(m instanceof TargetEsp esp) || !esp.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        Entity raw = mc.targetedEntity;
        if (raw == null && mc.crosshairTarget instanceof EntityHitResult ehr) raw = ehr.getEntity();
        if (!(raw instanceof LivingEntity le) || !le.isAlive() || le == mc.player) return;
        if (!passesFilter(esp, le)) return;
        if (context.camera() == null) return;

        // interpolate to the render pose so the effect tracks smoothly
        float td = context.tickCounter().getTickDelta(false);
        double ix = MathHelper.lerp(td, le.lastRenderX, le.getX());
        double iy = MathHelper.lerp(td, le.lastRenderY, le.getY());
        double iz = MathHelper.lerp(td, le.lastRenderZ, le.getZ());
        Box hb = le.getBoundingBox().offset(ix - le.getX(), iy - le.getY(), iz - le.getZ());

        // Show HP now renders in screen space (NanoVG) — see HudRenderer#renderTargetHpPin.

        // Outline style — native entity outline framebuffer. Nothing else to draw here.
        if (esp.style.index == 0) return;

        double s = esp.size.value;
        double cx = (hb.minX + hb.maxX) / 2, cz = (hb.minZ + hb.maxZ) / 2;
        float w = (float) (Math.max(hb.maxX - hb.minX, hb.maxZ - hb.minZ) * s);
        int rgb = esp.color.accent ? Theme.accentRgb() : esp.color.rgb();

        switch (esp.style.index) {
            case 1 -> ParticleEngine.halo(context, cx, hb.minY, hb.maxY, cz, w * 0.65f, rgb, esp.shape.index);
            case 2 -> drawCircle(context, cx, hb.minY, cz, w * 0.6, rgb);
            case 3 -> drawSpinSquare(context, le, cx, (hb.minY + hb.maxY) / 2, cz, w * 0.55, rgb);
            case 4 -> ParticleEngine.sparksAround(cx, hb.minY, hb.maxY, cz, w * 0.55f, rgb, esp.shape.index);
            case 5 -> {   // Limbs — a small swarm of lights freely orbiting the whole entity (not attached to
                          // any body part): each orbiter has its own radius/height-band/speed/spin direction
                          // and bobs up and down, so they never bunch together in one spot.
                double midY = (hb.minY + hb.maxY) / 2, bh = hb.maxY - hb.minY;
                double t = System.currentTimeMillis() / 1000.0;
                int seed = le.getId();
                int n = 6;
                for (int i = 0; i < n; i++) {
                    double dir = (i % 2 == 0) ? 1 : -1;                                  // alternating spin direction
                    double speed = 0.55 + 0.4 * (((seed * 31 + i * 17) % 7) / 6.0);
                    double phase = ((seed * 13 + i * 97) % 360) * Math.PI / 180.0;
                    double radius = w * (0.5 + 0.3 * (((seed + i * 53) % 5) / 4.0));
                    double bobAmp = bh * (0.22 + 0.16 * (((seed + i * 29) % 4) / 3.0));
                    double bobSpeed = 0.45 + 0.35 * (((seed + i * 71) % 5) / 4.0);
                    double a = t * speed * dir + phase;
                    double oy = midY + Math.sin(t * bobSpeed + phase) * bobAmp;
                    double ox = cx + Math.cos(a) * radius, oz = cz + Math.sin(a) * radius;
                    ParticleEngine.limbGlow(context, ox, oy, oz, w * 0.10f, rgb);
                }
            }
        }
    }

    /** True while our own "Show HP" is active — used by ClientPlayNetworkHandlerMixin to hide the server's own belowName HP. */
    public static boolean shouldHideServerHp() {
        Module m = LumeClient.MODULES.getByName("Target ESP");
        return m instanceof TargetEsp esp && esp.isEnabled() && esp.hpAboveTarget.value;
    }

    /** Circle — a thin wireframe ring flat on the ground at the target's feet. */
    private static void drawCircle(WorldRenderContext ctx, double cx, double y, double cz, double radius, int rgb) {
        VertexConsumerProvider vcp = ctx.consumers();
        MatrixStack ms = ctx.matrixStack();
        if (vcp == null || ms == null) return;
        Vec3d cam = ctx.camera().getPos();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);
        Render3D.ring(vc, ms.peek(), (float) (cx - cam.x), (float) (y - cam.y + 0.02), (float) (cz - cam.z),
                (float) radius, (float) radius, argb, 40);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
    }

    // Square style state — one spinning marker at a time (single target), so a
    // few static fields are enough: base slow spin, kicks faster on every hit,
    // eases back down to the baseline speed.
    private static int spinTargetId = -1;
    private static float spinAngle = 0f;
    private static float spinVel = 60f;
    private static long spinLastNanos = System.nanoTime();
    private static final float SPIN_BASE = 60f;     // deg/sec at rest
    private static final float SPIN_KICK = 420f;    // deg/sec added per hit

    /** Called from HitEffects.onAttack — speeds the Square marker's spin up on a hit. */
    public static void onHit(int entityId) {
        if (entityId == spinTargetId) spinVel += SPIN_KICK;
    }

    /** Square — a camera-facing marker centred on the target's body that spins in place, speeding up on every hit. */
    private static void drawSpinSquare(WorldRenderContext ctx, LivingEntity le, double cx, double cy, double cz, double half, int rgb) {
        VertexConsumerProvider vcp = ctx.consumers();
        MatrixStack ms = ctx.matrixStack();
        if (vcp == null || ms == null || ctx.camera() == null) return;

        long now = System.nanoTime();
        float dt = (float) Math.min(0.1, (now - spinLastNanos) / 1e9);
        spinLastNanos = now;
        if (le.getId() != spinTargetId) { spinTargetId = le.getId(); spinVel = SPIN_BASE; spinAngle = 0f; }
        spinAngle += spinVel * dt;
        spinVel += (SPIN_BASE - spinVel) * Math.min(1f, dt * 2.2f);   // ease back down to the resting speed

        Vec3d cam = ctx.camera().getPos();
        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);

        // rotate the (right, up) billboard basis itself by spinAngle — the square spins in the view plane
        double rad = Math.toRadians(spinAngle);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        Vector3f r2 = new Vector3f(right).mul(cos).add(new Vector3f(up).mul(sin));
        Vector3f u2 = new Vector3f(up).mul(cos).sub(new Vector3f(right).mul(sin));

        float ccx = (float) (cx - cam.x), ccy = (float) (cy - cam.y), ccz = (float) (cz - cam.z);
        float[][] local = { { -1, -1 }, { 1, -1 }, { 1, 1 }, { -1, 1 } };
        float[][] corners = new float[4][3];
        for (int i = 0; i < 4; i++) {
            float lx = local[i][0] * (float) half, ly = local[i][1] * (float) half;
            corners[i][0] = ccx + r2.x * lx + u2.x * ly;
            corners[i][1] = ccy + r2.y * lx + u2.y * ly;
            corners[i][2] = ccz + r2.z * lx + u2.z * ly;
        }

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);
        var entry = ms.peek();
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            Render3D.line(vc, entry, corners[i][0], corners[i][1], corners[i][2], corners[j][0], corners[j][1], corners[j][2], argb);
        }
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
    }

    /** True if {@code e} is the crosshair target and the Outline style is active (for the outline mixins). */
    public static boolean isOutlineTarget(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Target ESP");
        if (!(m instanceof TargetEsp esp) || !esp.isEnabled() || esp.style.index != 0 || mc.player == null) return false;
        Entity t = mc.targetedEntity;
        if (t == null && mc.crosshairTarget instanceof EntityHitResult ehr) t = ehr.getEntity();
        if (e != t || !(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) return false;
        return passesFilter(esp, le);
    }

    /** Outline colour as 0xRRGGBB for the outline mixins. */
    public static int outlineColorRgb() {
        Module m = LumeClient.MODULES.getByName("Target ESP");
        return (m instanceof TargetEsp esp) ? esp.color.rgb() : 0xFFFFFF;
    }
}

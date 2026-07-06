package com.lume.client.module.modules.visual;

import com.lume.client.LumeClient;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

/**
 * Target ESP — marks the entity your crosshair is aimed at (legit: crosshair
 * target only, never wall/proximity scan). "Outline" uses MC's native entity
 * outline framebuffer (see MinecraftClientMixin + EntityMixin); the other 3
 * styles are particle effects around the entity via {@link ParticleEngine}.
 */
public class TargetEsp extends Module {

    public final ModeSetting  filter    = add(new ModeSetting("Filter", 0, "Players", "Hostiles", "All"));
    public final ModeSetting  style     = add(new ModeSetting("Style", 0, "Outline", "Halo", "Pulse", "Sparks", "Limbs"));
    public final ModeSetting  shape     = add(new ModeSetting("Shape", 0, ParticleEngine.SHAPE_NAMES));
    public final ColorSetting color     = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting thickness = add(new SliderSetting("Thickness", 2.0, 1.0, 5.0, false));
    public final SliderSetting size      = add(new SliderSetting("Size", 1.0, 0.3, 1.0, false));

    public TargetEsp() {
        super("Target ESP", "Marks the entity your crosshair is on", Category.VISUALS, -1);
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

        boolean isPlayer  = le instanceof PlayerEntity;
        boolean isHostile = le instanceof MobEntity mob && mob.getTarget() != null;
        int f = esp.filter.index;
        if (f == 0 && !isPlayer)  return;
        if (f == 1 && !isHostile) return;

        // Outline style — native entity outline framebuffer. Nothing to draw here.
        if (esp.style.index == 0) return;
        if (context.camera() == null) return;

        // interpolate to the render pose so the effect tracks smoothly
        float td = context.tickCounter().getTickDelta(false);
        double ix = MathHelper.lerp(td, le.lastRenderX, le.getX());
        double iy = MathHelper.lerp(td, le.lastRenderY, le.getY());
        double iz = MathHelper.lerp(td, le.lastRenderZ, le.getZ());
        Box hb = le.getBoundingBox().offset(ix - le.getX(), iy - le.getY(), iz - le.getZ());

        double s = esp.size.value;
        double cx = (hb.minX + hb.maxX) / 2, cz = (hb.minZ + hb.maxZ) / 2;
        float w = (float) (Math.max(hb.maxX - hb.minX, hb.maxZ - hb.minZ) * s);
        int rgb = esp.color.accent ? Theme.accentRgb() : esp.color.rgb();

        switch (esp.style.index) {
            case 1 -> ParticleEngine.halo(context, cx, hb.minY, hb.maxY, cz, w * 0.65f, rgb, esp.shape.index);
            case 2 -> {
                float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 400.0);
                ParticleEngine.auraColumn(context, cx, hb.minY, hb.maxY, cz, w * 0.55f, rgb, pulse, 9);
            }
            case 3 -> ParticleEngine.sparksAround(cx, hb.minY, hb.maxY, cz, w * 0.55f, rgb, esp.shape.index);
            case 4 -> {   // Limbs — solid glow tracking arms/legs, X-ray through walls, trails when moving
                double bh = hb.maxY - hb.minY, bw = hb.maxX - hb.minX;
                double armY = hb.minY + bh * 0.62, legY = hb.minY + bh * 0.18;
                double armX = bw * 0.32, legX = bw * 0.14;
                double[][] limbs = {
                        { cx - armX, armY }, { cx + armX, armY },
                        { cx - legX, legY }, { cx + legX, legY }
                };
                boolean moving = le.getVelocity().horizontalLength() > 0.02 || Math.abs(le.getVelocity().y) > 0.02;
                for (double[] p : limbs) {
                    ParticleEngine.limbGlow(context, p[0], p[1], cz, w * 0.13f, rgb);
                    if (moving) ParticleEngine.trailSpark(p[0], p[1], cz, rgb);
                }
            }
        }
    }

    /** True if {@code e} is the crosshair target and the Outline style is active (for the outline mixins). */
    public static boolean isOutlineTarget(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Target ESP");
        if (!(m instanceof TargetEsp esp) || !esp.isEnabled() || esp.style.index != 0 || mc.player == null) return false;
        Entity t = mc.targetedEntity;
        if (t == null && mc.crosshairTarget instanceof EntityHitResult ehr) t = ehr.getEntity();
        if (e != t || !(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) return false;
        boolean isPlayer  = e instanceof PlayerEntity;
        boolean isHostile = e instanceof MobEntity mob && mob.getTarget() != null;
        if (esp.filter.index == 0 && !isPlayer)  return false;
        if (esp.filter.index == 1 && !isHostile) return false;
        return true;
    }

    /** Outline colour as 0xRRGGBB for the outline mixins. */
    public static int outlineColorRgb() {
        Module m = LumeClient.MODULES.getByName("Target ESP");
        return (m instanceof TargetEsp esp) ? esp.color.rgb() : 0xFFFFFF;
    }
}

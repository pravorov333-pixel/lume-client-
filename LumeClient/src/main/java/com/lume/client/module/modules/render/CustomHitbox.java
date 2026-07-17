package com.lume.client.module.modules.render;

import com.lume.client.LumeClient;
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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Custom Hitbox — draws every nearby living entity's actual hitbox (outline
 * and/or a solid fill), colour/opacity/style all configurable, plus an optional
 * "Look Direction" ray showing where each shown entity is currently facing.
 * Legit visual only: uses the entity's real vanilla bounding box and rotation,
 * nothing hidden (no through-wall inventory/ESP-style enemy-item data).
 */
public class CustomHitbox extends Module {

    public final ModeSetting   filter      = add(new ModeSetting("Filter", 0, "Players", "Hostiles", "All"));
    public final ModeSetting   style       = add(new ModeSetting("Style", 2, "Outline", "Fill", "Both"));
    public final ColorSetting  color       = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting fillOpacity = add(new SliderSetting("Fill Opacity", 0.22, 0.0, 0.8, false));
    public final SliderSetting range       = add(new SliderSetting("Range", 24, 8, 64, true));
    public final BoolSetting   lookDirection = add(new BoolSetting("Look Direction", false));

    public CustomHitbox() {
        super("Custom Hitbox", "Настраиваемые хитбоксы + направление взгляда", Category.RENDER, -1);
    }

    /** On AND vanilla's own F3+B hitboxes are toggled on — see EntityRenderDispatcherMixin,
     *  which uses this same check to hide vanilla's lines so only ours are visible. */
    public static boolean active() {
        Module m = LumeClient.MODULES.getByName("Custom Hitbox");
        return m instanceof CustomHitbox && m.isEnabled()
                && MinecraftClient.getInstance().getEntityRenderDispatcher().shouldRenderHitboxes();
    }

    private boolean passes(LivingEntity le) {
        boolean isPlayer  = le instanceof PlayerEntity;
        boolean isHostile = le instanceof MobEntity mob && mob.getTarget() != null;
        if (filter.index == 0) return isPlayer;
        if (filter.index == 1) return isHostile;
        return true;
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void renderWorld(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Custom Hitbox");
        if (!(m instanceof CustomHitbox hb) || !hb.isEnabled()) return;
        if (!mc.getEntityRenderDispatcher().shouldRenderHitboxes()) return;   // gated to vanilla's F3+B toggle
        if (mc.world == null || mc.player == null || ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        Vec3d cam = ctx.camera().getPos();
        double range = hb.range.value;
        int rgb = hb.color.accent ? Theme.accentRgb() : hb.color.rgb();
        int fillArgb = (Math.round(255 * (float) hb.fillOpacity.value) << 24) | (rgb & 0xFFFFFF);
        int lineArgb = 0xFF000000 | (rgb & 0xFFFFFF);
        float td = ctx.tickCounter().getTickDelta(false);
        Matrix4f mat = ms.peek().getPositionMatrix();

        boolean drawFill = hb.style.index != 0;
        boolean drawLine = hb.style.index != 1;

        // Bounded AABB query instead of world.getEntities() (every loaded entity, incl. item
        // drops/projectiles/entities far outside range) — lets the engine's own chunk-based
        // entity lookup do the culling instead of a full linear scan every frame.
        Box searchBox = new Box(cam.x - range, cam.y - range, cam.z - range, cam.x + range, cam.y + range, cam.z + range);
        for (LivingEntity le : mc.world.getEntitiesByClass(LivingEntity.class, searchBox, le2 -> le2 != mc.player && le2.isAlive())) {
            if (!hb.passes(le)) continue;
            if (mc.player.squaredDistanceTo(le) > range * range) continue;

            double ix = MathHelper.lerp(td, le.lastRenderX, le.getX());
            double iy = MathHelper.lerp(td, le.lastRenderY, le.getY());
            double iz = MathHelper.lerp(td, le.lastRenderZ, le.getZ());
            Box wb = le.getBoundingBox().offset(ix - le.getX(), iy - le.getY(), iz - le.getZ());
            float x0 = (float) (wb.minX - cam.x), y0 = (float) (wb.minY - cam.y), z0 = (float) (wb.minZ - cam.z);
            float x1 = (float) (wb.maxX - cam.x), y1 = (float) (wb.maxY - cam.y), z1 = (float) (wb.maxZ - cam.z);

            if (drawFill) Render3D.fillBox(vcp, mat, x0, y0, z0, x1, y1, z1, fillArgb);
            if (drawLine) {
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
                Render3D.box(vc, ms.peek(), x0, y0, z0, x1, y1, z1, lineArgb);
                if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
            }

            if (hb.lookDirection.value) {
                Vec3d look = le.getRotationVector();
                float ex = (float) (ix - cam.x), ey = (float) (iy + le.getStandingEyeHeight() - cam.y), ez = (float) (iz - cam.z);
                float len = 8f;
                VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());
                Render3D.line(vc, ms.peek(), ex, ey, ez,
                        ex + (float) (look.x * len), ey + (float) (look.y * len), ez + (float) (look.z * len), lineArgb);
                if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
            }
        }
    }
}

package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.SliderSetting;
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

/**
 * Capes — a simple flat cloth plane hanging from your back, swaying with
 * movement. Client-only cosmetic (like Self Name): only visible to yourself,
 * only in 3rd person, no server/skin-system involved — a stand-in for a real
 * cape system until we bundle actual cape textures. Colour instead of a
 * texture keeps this dependency-free.
 *
 * <p>Sized like a real vanilla cape (10x16px on the cape texture, i.e.
 * 0.625x1.0 blocks at Minecraft's 1px = 1/16 block model scale) rather than
 * a user-adjustable rectangle — no Width/Length sliders.
 */
public class Capes extends Module {

    private static final float CAPE_WIDTH = 10f / 16f, CAPE_LENGTH = 1f;

    public final ColorSetting  color  = add(new ColorSetting("Color", true, 183, 170, 217));
    public final SliderSetting sway   = add(new SliderSetting("Sway", 0.4, 0.0, 1.2, false));

    public Capes() {
        super("Capes", "A cloth cape that sways as you move", Category.COSMETIC, -1);
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void renderWorld(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        var m = LumeClient.MODULES.getByName("Capes");
        if (!(m instanceof Capes cape) || !cape.isEnabled()) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || ctx.camera() == null) return;
        if (mc.options.getPerspective().isFirstPerson()) return;   // matches vanilla: no cape in 1st person

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        float td = ctx.tickCounter().getTickDelta(false);
        double px = MathHelper.lerp(td, p.lastRenderX, p.getX());
        double py = MathHelper.lerp(td, p.lastRenderY, p.getY());
        double pz = MathHelper.lerp(td, p.lastRenderZ, p.getZ());
        Vec3d cam = ctx.camera().getPos();

        // Body yaw, not look/head yaw — getYaw() follows the camera and can lead the torso by
        // a lot (looking around while standing still, quick turns while walking), so the cape
        // swung independently of the model's actual back and visibly clipped through the torso.
        // Real capes/elytra are anchored to bodyYaw for exactly this reason.
        float bodyYaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
        float yawRad = (float) Math.toRadians(bodyYaw);
        float fx = -MathHelper.sin(yawRad), fz = MathHelper.cos(yawRad);   // forward
        float rx = fz, rz = -fx;                                          // right (perp to forward)

        float hw = CAPE_WIDTH / 2f;
        float len = CAPE_LENGTH;
        float topY = (float) (py + p.getHeight() * 0.82);
        float botY = topY - len;

        double vx = p.getVelocity().x, vz = p.getVelocity().z;
        float swayMag = (float) cape.sway.value;
        float swayX = (float) (-vx * swayMag * 6.0);
        float swayZ = (float) (-vz * swayMag * 6.0);

        float bx = (float) (px - fx * 0.14 - cam.x);
        float bz = (float) (pz - fz * 0.14 - cam.z);
        float ty = (float) (topY - cam.y);
        float by = (float) (botY - cam.y);

        int rgb = (cape.color.accent ? Theme.accentRgb() : cape.color.rgb()) & 0xFFFFFF;
        int argb = 0xF0000000 | rgb;

        Matrix4f mat = ms.peek().getPositionMatrix();
        RenderLayer layer = RenderLayer.getDebugQuads();
        VertexConsumer vc = vcp.getBuffer(layer);

        float tlx = bx - rx * hw, tlz = bz - rz * hw;
        float trx = bx + rx * hw, trz = bz + rz * hw;
        float blx = tlx + swayX, blz = tlz + swayZ;
        float brx = trx + swayX, brz = trz + swayZ;

        // front winding
        vc.vertex(mat, tlx, ty, tlz).color(argb);
        vc.vertex(mat, blx, by, blz).color(argb);
        vc.vertex(mat, brx, by, brz).color(argb);
        vc.vertex(mat, trx, ty, trz).color(argb);
        // back winding (same quad, reversed) — keeps the cape visible from either side
        vc.vertex(mat, trx, ty, trz).color(argb);
        vc.vertex(mat, brx, by, brz).color(argb);
        vc.vertex(mat, blx, by, blz).color(argb);
        vc.vertex(mat, tlx, ty, tlz).color(argb);

        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }
}

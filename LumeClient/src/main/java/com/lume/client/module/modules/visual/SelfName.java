package com.lume.client.module.modules.visual;

import com.lume.client.LumeClient;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Renders your own username as a floating tag above your head (visible in 3rd person). */
public class SelfName extends Module {

    public final ColorSetting color = add(new ColorSetting("Color", false, 183, 170, 217));

    public SelfName() {
        super("Self Name", "Показывает ваш ник над головой", Category.VISUALS, -1);
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void renderWorld(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Self Name");
        if (!(m instanceof SelfName sn) || !sn.isEnabled()) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || ctx.camera() == null) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;

        float td = ctx.tickCounter().getTickDelta(false);
        double px = MathHelper.lerp(td, p.lastRenderX, p.getX());
        double py = MathHelper.lerp(td, p.lastRenderY, p.getY());
        double pz = MathHelper.lerp(td, p.lastRenderZ, p.getZ());
        Vec3d cam = ctx.camera().getPos();

        String name = p.getGameProfile().getName();
        TextRenderer tr = mc.textRenderer;
        int rgb = sn.color.accent ? Theme.accentRgb() : sn.color.rgb();
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);

        ms.push();
        ms.translate(px - cam.x, py + p.getHeight() + 0.5 - cam.y, pz - cam.z);
        ms.multiply(ctx.camera().getRotation());
        ms.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f mat = ms.peek().getPositionMatrix();
        float w = -tr.getWidth(name) / 2f;
        tr.draw(name, w, 0, argb, false, mat, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0x40000000, 0xF000F0);
        ms.pop();
    }
}

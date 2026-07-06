package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.BlockOutline;
import com.lume.client.module.modules.render.WorldCustomizer;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recolours the block-selection outline (Block Outline) + World Customizer "No Clouds". */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    // 1.21.4: drawBlockOutline calls drawOutline(ms, vcp, shape, x, y, z, ARGB int color) —
    // the old float r/g/b/a overload (drawCuboidShapeOutline) no longer exists.
    @ModifyArg(
            method = "drawBlockOutline",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/shape/VoxelShape;DDDI)V"),
            index = 6,
            require = 0)
    private int lume$recolorOutline(int color) {
        int argb = BlockOutline.argbOrZero();
        if (argb == 0) return color;
        int a = Math.max((color >>> 24) & 0xFF, (int) (0.8f * 255));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noClouds(FrameGraphBuilder frameGraphBuilder, Matrix4f positionMatrix, Matrix4f projectionMatrix,
                               CloudRenderMode cloudRenderMode, Vec3d cloudsColor, float ticks, int cameraX, float cameraY,
                               CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        if (m instanceof WorldCustomizer wc && wc.isEnabled() && wc.noClouds.value) ci.cancel();
    }
}

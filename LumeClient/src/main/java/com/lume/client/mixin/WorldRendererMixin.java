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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the block-selection outline (Block Outline) + World Customizer "No Clouds". */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    /** Block Outline draws its own full-block, no-depth wireframe instead (see
     *  BlockOutline#renderOutline) — vanilla's shape-following, depth-tested one is
     *  cancelled outright rather than just recoloured. */
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$skipVanillaOutline(CallbackInfo ci) {
        if (BlockOutline.active()) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noClouds(FrameGraphBuilder frameGraphBuilder, Matrix4f positionMatrix, Matrix4f projectionMatrix,
                               CloudRenderMode cloudRenderMode, Vec3d cloudsColor, float ticks, int cameraX, float cameraY,
                               CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        if (m instanceof WorldCustomizer wc && wc.isEnabled() && wc.noClouds.value) ci.cancel();
    }
}

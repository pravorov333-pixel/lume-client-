package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CleanView;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean View "Low Fire" — dims the full-screen on-fire overlay instead of leaving it at full strength. */
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), require = 0)
    private static void lume$lowFireStart(MatrixStack matrices, VertexConsumerProvider vcp, CallbackInfo ci) {
        if (CleanView.lowFire()) RenderSystem.setShaderColor(1f, 1f, 1f, 0.35f);
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"), require = 0)
    private static void lume$lowFireEnd(MatrixStack matrices, VertexConsumerProvider vcp, CallbackInfo ci) {
        if (CleanView.lowFire()) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}

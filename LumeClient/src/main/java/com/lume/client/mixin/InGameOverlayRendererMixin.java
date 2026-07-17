package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CleanView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean View: "No Fire overlay" removes the full-screen on-fire tint; "No Underwater" removes the water screen tint. */
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lume$fireStart(MatrixStack matrices, VertexConsumerProvider vcp, CallbackInfo ci) {
        if (CleanView.noFire()) ci.cancel();
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lume$noUnderwater(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vcp, CallbackInfo ci) {
        if (CleanView.noUnderwater()) ci.cancel();
    }
}

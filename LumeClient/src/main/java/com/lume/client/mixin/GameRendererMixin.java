package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CleanView;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean View: cancels the hurt camera tilt and/or the walking view-bob. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CleanView.noHurtCam()) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CleanView.noBob()) ci.cancel();
    }
}

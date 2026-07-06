package com.lume.client.mixin;

import com.lume.client.module.modules.render.WorldCustomizer;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World Customizer "No Sun/Moon" — skips the two celestial-body draw calls entirely. */
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noSun(float alpha, VertexConsumerProvider vcp, MatrixStack ms, CallbackInfo ci) {
        if (WorldCustomizer.hideCelestial()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noMoon(int phase, float alpha, VertexConsumerProvider vcp, MatrixStack ms, CallbackInfo ci) {
        if (WorldCustomizer.hideCelestial()) ci.cancel();
    }
}

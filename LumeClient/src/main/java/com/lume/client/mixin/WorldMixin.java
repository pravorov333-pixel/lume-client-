package com.lume.client.mixin;

import com.lume.client.module.modules.render.WorldCustomizer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World Customizer (Custom Weather) — client-side visual overrides. Time Changer lives in LunarWorldViewMixin. */
@Mixin(World.class)
public class WorldMixin {

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$rain(float tickDelta, CallbackInfoReturnable<Float> cir) {
        float v = WorldCustomizer.rainOverride();
        if (v >= 0f) cir.setReturnValue(v);
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$thunder(float tickDelta, CallbackInfoReturnable<Float> cir) {
        float v = WorldCustomizer.thunderOverride();
        if (v >= 0f) cir.setReturnValue(v);
    }
}

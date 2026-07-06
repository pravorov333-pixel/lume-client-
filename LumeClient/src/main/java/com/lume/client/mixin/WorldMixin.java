package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.WorldCustomizer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World Customizer (weather) — client-side visual overrides. Time Changer lives in LunarWorldViewMixin. */
@Mixin(World.class)
public class WorldMixin {

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noRain(float tickDelta, CallbackInfoReturnable<Float> cir) {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        if (m instanceof WorldCustomizer wc && wc.isEnabled() && wc.noRain.value) cir.setReturnValue(0f);
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noThunder(float tickDelta, CallbackInfoReturnable<Float> cir) {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        if (m instanceof WorldCustomizer wc && wc.isEnabled() && wc.noThunder.value) cir.setReturnValue(0f);
    }
}

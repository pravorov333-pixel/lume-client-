package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.TimeChanger;
import net.minecraft.world.LunarWorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time Changer — overrides the visual sky angle (day/night) when enabled. */
@Mixin(LunarWorldView.class)
public interface LunarWorldViewMixin {

    @Inject(method = "getSkyAngle(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$timeChanger(float tickDelta, CallbackInfoReturnable<Float> cir) {
        Module m = LumeClient.MODULES.getByName("Time Changer");
        if (m instanceof TimeChanger tc && tc.isEnabled()) {
            cir.setReturnValue(TimeChanger.skyAngle(tc.time.getInt()));
        }
    }
}

package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.SkyColor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sky module — overrides the sky colour. */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void lume$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        Module m = LumeClient.MODULES.getByName("Sky");
        if (m instanceof SkyColor s && s.isEnabled()) {
            cir.setReturnValue(s.rgb());
        }
    }
}

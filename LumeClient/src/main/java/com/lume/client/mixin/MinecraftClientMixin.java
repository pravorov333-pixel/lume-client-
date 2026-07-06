package com.lume.client.mixin;

import com.lume.client.module.modules.visual.TargetEsp;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Target ESP "Outline" style — makes the crosshair target render a glowing outline. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true, require = 0)
    private void lume$targetOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (TargetEsp.isOutlineTarget(entity)) cir.setReturnValue(true);
    }
}

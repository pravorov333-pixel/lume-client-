package com.lume.client.mixin;

import com.lume.client.module.modules.visual.TargetEsp;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colours the Target ESP "Outline" glow to the chosen ESP colour. */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true, require = 0)
    private void lume$outlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (TargetEsp.isOutlineTarget(self)) cir.setReturnValue(TargetEsp.outlineColorRgb());
    }
}

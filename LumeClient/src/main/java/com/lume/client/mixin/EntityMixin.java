package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.ParticleTrail;
import com.lume.client.module.modules.visual.TargetEsp;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Colours the Target ESP "Outline" glow to the chosen colour. */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true, require = 0)
    private void lume$outlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (TargetEsp.isOutlineTarget(self)) cir.setReturnValue(TargetEsp.outlineColorRgb());
    }

    /**
     * Suppresses vanilla's own sprint dust while Particle Trail is on, on the LOCAL player only
     * (other entities' own sprint dust is untouched — we don't control what other players see,
     * and it's not our own particle stream cluttering the view for us).
     */
    @Inject(method = "spawnSprintingParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noVanillaSprintDust(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self != MinecraftClient.getInstance().player) return;
        Module m = LumeClient.MODULES.getByName("Particle Trail");
        if (m instanceof ParticleTrail && m.isEnabled()) ci.cancel();
    }
}

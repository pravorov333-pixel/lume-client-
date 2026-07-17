package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.DeathAnimations;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's own white "poof" cloud at the moment of death
 * ({@code addDeathParticles}, fired via {@code handleStatus(ADD_DEATH_PARTICLES)})
 * while Death Animations is on — cancelled here specifically, rather than by
 * filtering {@code ParticleTypes.POOF} globally in {@code ParticleManagerMixin},
 * because POOF is shared by lots of unrelated vanilla effects (potions,
 * teleports, saplings, ...) that must keep working normally.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(method = "addDeathParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressDeathPoof(CallbackInfo ci) {
        Module dm = LumeClient.MODULES.getByName("Death Animations");
        if (dm instanceof DeathAnimations && dm.isEnabled()) ci.cancel();
    }
}

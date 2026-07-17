package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.HitParticles;
import com.lume.client.module.modules.render.NoHitParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Last-resort, lowest-level catch: every particle spawned via an emitter
 * (which is how {@code addCritParticles}/{@code addEnchantedHitParticles}
 * work) eventually calls this exact method each tick — confirmed by tracing
 * the real call chain: {@code EmitterParticle.tick()} → {@code
 * ClientWorld.addParticle} → {@code WorldRenderer.addParticle} → {@code
 * WorldRenderer.spawnParticle} → HERE. Added as a diagnostic + belt-and-
 * suspenders backstop after {@code PlayerEntityMixin}'s higher-level
 * (ClientPlayerEntity) suppression didn't fully stop the vanilla crit stars —
 * this is the one point every path funnels through, so if particles are
 * still getting past it, we've cornered the actual source.
 *
 * <p>(The vanilla death "poof" cloud is a separate, more targeted mixin —
 * see {@link com.lume.client.mixin.LivingEntityMixin} — cancelling {@code
 * addDeathParticles()} directly, since {@code ParticleTypes.POOF} is also
 * used for lots of unrelated vanilla effects that shouldn't be touched.)
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressParticle(ParticleEffect parameters, double x, double y, double z,
                                        double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        var type = parameters.getType();

        boolean isHitType = type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT
                || type == ParticleTypes.DAMAGE_INDICATOR || type == ParticleTypes.SWEEP_ATTACK;
        if (!isHitType) return;
        boolean noHit = NoHitParticles.active();
        Module m = LumeClient.MODULES.getByName("Hit Particles");
        boolean hitParticlesOn = m instanceof HitParticles hp && hp.isEnabled();
        if (noHit || hitParticlesOn) cir.setReturnValue(null);
    }
}

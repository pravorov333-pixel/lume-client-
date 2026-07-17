package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.HitParticles;
import com.lume.client.module.modules.render.NoHitParticles;
import com.lume.client.module.modules.visual.TargetEsp;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the vanilla crit / hit particle packets while Hit Particles is enabled. */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressHitParticles(ParticleS2CPacket packet, CallbackInfo ci) {
        boolean noHit = NoHitParticles.active();
        Module m = LumeClient.MODULES.getByName("Hit Particles");
        boolean hitParticlesOn = m instanceof HitParticles hp && hp.isEnabled();
        if (!noHit && !hitParticlesOn) return;
        var type = packet.getParameters().getType();
        boolean match = type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT
                || type == ParticleTypes.DAMAGE_INDICATOR || type == ParticleTypes.SWEEP_ATTACK;
        if (match) {
            System.out.println("[Lume][crit debug] onParticle cancelled, type=" + net.minecraft.registry.Registries.PARTICLE_TYPE.getId(type));
            ci.cancel();
        }
    }

    /**
     * Refuses to apply the server's own "below name" scoreboard slot (the vanilla
     * mechanic anarchy servers like FunTime use to show a custom HP number under
     * nametags, no resource pack needed) while our own Target ESP "Show HP" is on
     * — so only our world-space HP shows, the server can't push its own over it.
     * Other slots (sidebar, list, team colours) are untouched.
     */
    @Inject(method = "onScoreboardDisplay", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressBelowNameHp(ScoreboardDisplayS2CPacket packet, CallbackInfo ci) {
        if (packet.getSlot() == ScoreboardDisplaySlot.BELOW_NAME && TargetEsp.shouldHideServerHp()) {
            ci.cancel();
        }
    }
}

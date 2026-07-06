package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.HitParticles;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the vanilla crit / hit particle packets while Hit Particles is active
 *  with a custom shape — "Default" leaves the vanilla ones alone. */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressHitParticles(ParticleS2CPacket packet, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("Hit Particles");
        if (!(m instanceof HitParticles hp) || !hp.isEnabled() || hp.isDefault()) return;
        var type = packet.getParameters().getType();
        if (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT
                || type == ParticleTypes.DAMAGE_INDICATOR) {
            ci.cancel();
        }
    }
}

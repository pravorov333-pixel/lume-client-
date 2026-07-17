package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.HitParticles;
import com.lume.client.module.modules.render.NoHitParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's OWN crit / enchanted-hit particles for the local
 * player's attacks. These are NOT sent as a {@code ParticleS2CPacket} (that
 * mixin lives in {@code ClientPlayNetworkHandlerMixin} and only ever catches
 * other broadcast particle effects) — they're spawned by a DIRECT client-side
 * call from {@code PlayerEntity#attack}.
 *
 * <p><b>The real bug (found 2026-07-09):</b> {@code PlayerEntity.addCritParticles}
 * / {@code addEnchantedHitParticles} are no-op stubs ({@code { return; }}) in the
 * base class — the actual particle-spawning code (via {@code
 * MinecraftClient.particleManager.addEmitter}) lives in {@code
 * ClientPlayerEntity}'s OWN override of both methods. A mixin targeting {@code
 * PlayerEntity} injects into a method body that virtual dispatch never
 * actually calls for the local player (a {@code ClientPlayerEntity} instance)
 * — it silently does nothing, no error, no log line, ever. Must target {@code
 * ClientPlayerEntity} instead. {@code spawnSweepAttackParticles} is fine to
 * leave on {@code PlayerEntity} — {@code ClientPlayerEntity} doesn't override
 * it, and the base impl is itself a no-op on the client (it only spawns via
 * {@code ServerWorld}).
 */
@Mixin(ClientPlayerEntity.class)
public class PlayerEntityMixin {

    @Inject(method = "addCritParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressCritParticles(Entity target, CallbackInfo ci) {
        boolean suppress = lume$shouldSuppress();
        System.out.println("[Lume][crit debug] ClientPlayerEntity.addCritParticles fired, suppress=" + suppress);
        if (suppress) ci.cancel();
    }

    @Inject(method = "addEnchantedHitParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressEnchantedHitParticles(Entity target, CallbackInfo ci) {
        boolean suppress = lume$shouldSuppress();
        System.out.println("[Lume][crit debug] ClientPlayerEntity.addEnchantedHitParticles fired, suppress=" + suppress);
        if (suppress) ci.cancel();
    }

    private boolean lume$shouldSuppress() {
        if ((Object) this != MinecraftClient.getInstance().player) return false;   // don't touch other entities
        if (NoHitParticles.active()) return true;
        Module m = LumeClient.MODULES.getByName("Hit Particles");
        return m instanceof HitParticles hp && hp.isEnabled();
    }
}

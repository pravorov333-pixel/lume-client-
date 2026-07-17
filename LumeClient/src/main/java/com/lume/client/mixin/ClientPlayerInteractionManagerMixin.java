package com.lume.client.mixin;

import com.lume.client.combat.HitEffects;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires the client-side hit feedback (HitSound / HitColor / HitBubble) when the player attacks an entity. */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void lume$onAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        System.out.println("[Lume][attack debug] attackEntity fired, target=" + (target == null ? "null" : target.getName().getString()));
        HitEffects.onAttack(target);
    }
}

package com.lume.client.combat;

import com.lume.client.LumeClient;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.HitParticles;
import com.lume.client.module.modules.render.HitSound;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;

/**
 * Combat hit feedback dispatch: HitSound (audio) and Hit Particles (via
 * {@link ParticleEngine}). HitColor is handled separately, in EntityRendererMixin
 * — it reads the entity's own vanilla "hurt" render state, not this attack hook.
 * Fed by the attack mixin. All client-side & legit.
 */
public final class HitEffects {

    private HitEffects() {}

    /** True if attacking right now would land a critical hit (vanilla conditions). */
    public static boolean isCrit(PlayerEntity p, Entity target) {
        boolean descending = p.fallDistance > 0f || p.getVelocity().y < 0.0;   // in the crit fall window
        return descending && !p.isOnGround() && !p.isClimbing()
                && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle() && !p.isSprinting()
                && p.getAttackCooldownProgress(0.5f) > 0.9f
                && target instanceof LivingEntity;
    }

    /** Called by ClientPlayerInteractionManagerMixin when the local player attacks an entity. */
    public static void onAttack(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target == null || mc.player == null) return;
        boolean crit = isCrit(mc.player, target);

        Module hs = LumeClient.MODULES.getByName("HitSound");
        if (hs instanceof HitSound s && s.isEnabled() && (!s.onlyOnCrit.value || crit)) {
            if (s.useMySound.value) {   // "My Sounds" — user-dropped .ogg, played straight via OpenAL
                java.io.File[] files = com.lume.client.audio.CustomAudioPlayer.list("hitsound");
                java.io.File custom = null;
                if (s.selectedFile != null) {
                    for (java.io.File f : files) if (f.getName().equals(s.selectedFile)) { custom = f; break; }
                }
                if (custom == null && files.length > 0) custom = files[0];
                if (custom != null) {
                    com.lume.client.audio.CustomAudioPlayer.play(custom, (float) s.volume.value, (float) s.pitch.value);
                }
            } else {
                SoundEvent ev = switch (s.sound.index) {
                    case 1 -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
                    case 2 -> SoundEvents.BLOCK_ANVIL_LAND;
                    case 3 -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
                    case 4 -> com.lume.client.Sounds.BASS_CRIT_1;   // real bass-boosted crit audio
                    case 5 -> com.lume.client.Sounds.BASS_CRIT_2;   // deeper/heavier variant
                    default -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
                };
                mc.getSoundManager().play(PositionedSoundInstance.master(ev, (float) s.pitch.value, (float) s.volume.value));
            }
        }

        Module hpM = LumeClient.MODULES.getByName("Hit Particles");
        if (hpM instanceof HitParticles hp && hp.isEnabled() && !hp.isDefault()) {
            Box b = target.getBoundingBox();
            int rgb = hp.color.accent ? Theme.accentRgb() : hp.color.rgb();
            ParticleEngine.hit(hp.engineStyle(), (b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2,
                    rgb, hp.count.getInt(), (float) hp.speed.value, (float) hp.size.value, hp.shape.index);
        }
    }
}

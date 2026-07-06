package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.HitSound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the vanilla melee attack sound while HitSound is active. */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressAttackSound(SoundInstance sound, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("HitSound");
        if (!(m instanceof HitSound hs) || !hs.isEnabled() || sound == null) return;
        Identifier id = sound.getId();
        if (id == null) return;
        String path = id.getPath();
        if (!path.startsWith("entity.player.attack.")) return;
        boolean isCrit = path.equals("entity.player.attack.crit");
        // Cancel the vanilla crit sound always (crit → custom). Cancel the normal
        // attack sounds only when we replace every hit (not only-on-crit).
        if (isCrit || !hs.onlyOnCrit.value) ci.cancel();
    }
}

package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.NoBgBlur;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No BG Blur — forces the blur intensity itself to 0 rather than chasing every
 * {@code Screen.blur()} call site (some screens, like the inventory, override
 * {@code render}/{@code init} in ways that may not even go through the base
 * class's blur call — this hooks the actual intensity value the post-process
 * pass reads, so it's zero everywhere regardless of which screen triggered it).
 */
@Mixin(GameOptions.class)
public class GameOptionsMixin {

    @Inject(method = "getMenuBackgroundBlurrinessValue", at = @At("RETURN"), cancellable = true, require = 0)
    private void lume$noBlurValue(CallbackInfoReturnable<Integer> cir) {
        if (NoBgBlur.active()) cir.setReturnValue(0);
    }
}

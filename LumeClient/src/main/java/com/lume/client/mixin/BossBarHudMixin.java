package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CleanView;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean View "No Boss Bar" — hides Wither/Ender Dragon (and any server-sent) boss bars. */
@Mixin(BossBarHud.class)
public class BossBarHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noBossBar(DrawContext ctx, CallbackInfo ci) {
        if (CleanView.noBossBar()) ci.cancel();
    }
}

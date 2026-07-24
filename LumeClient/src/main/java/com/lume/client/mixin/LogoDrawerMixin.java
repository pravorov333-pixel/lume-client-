package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla's "MINECRAFT" logo texture while Custom Menu is on — draws nothing itself.
 * {@link com.lume.client.menu.LumeTitleMenu#render} draws the "LUME VISUALS" lockup instead, as
 * part of its own unified composition (logo + Singleplayer/Multiplayer + bottom bar, all laid
 * out together in one coordinate space) rather than as an independent element positioned by this
 * class — see that method for the actual draw call.
 */
@Mixin(LogoDrawer.class)
public class LogoDrawerMixin {

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$hide(DrawContext ctx, int screenWidth, float alpha, CallbackInfo ci) {
        if (CustomMenu.active()) ci.cancel();
    }
}

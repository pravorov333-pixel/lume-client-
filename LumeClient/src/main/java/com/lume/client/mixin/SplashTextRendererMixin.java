package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla's rotating yellow splash text while Custom Menu is on — it sat right next to
 * vanilla's logo and reads oddly next to the "LUME VISUALS" wordmark that replaces it (see
 * {@link LogoDrawerMixin}).
 */
@Mixin(SplashTextRenderer.class)
public class SplashTextRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$hide(DrawContext ctx, int screenWidth, net.minecraft.client.font.TextRenderer textRenderer, int color, CallbackInfo ci) {
        if (CustomMenu.active()) ci.cancel();
    }
}

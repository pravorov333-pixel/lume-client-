package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla's rotating yellow splash text ("Also try Minecraft Dungeons!")
 * on the title screen while Custom Menu is active — it was still showing
 * next to our wordmark after {@link LogoDrawerMixin} replaced the logo texture.
 */
@Mixin(SplashTextRenderer.class)
public class SplashTextRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$hideSplash(DrawContext context, int width, TextRenderer textRenderer, int color, CallbackInfo ci) {
        if (CustomMenu.active()) ci.cancel();
    }
}

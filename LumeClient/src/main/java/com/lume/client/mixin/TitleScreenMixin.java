package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the title-screen panorama with Lume's themed background when Custom Menu is on. */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (CustomMenu.active()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            CustomMenu.drawBackground(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            ci.cancel();
        }
    }
}

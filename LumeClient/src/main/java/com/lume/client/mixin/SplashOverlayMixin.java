package com.lume.client.mixin;

import com.lume.client.gui.LoadingScreen;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Menu — paints our own logo+bar over the resource-reload splash
 * (Mojang logo screen), real progress via the eased {@code progress} field.
 * Fires for every resource reload, not just the very first one at game
 * boot — the first one specifically may render before our mod has finished
 * registering modules (CustomMenu.active() just reads false until then, no
 * crash either way), so it can fall back to vanilla on that first frame or two.
 */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {

    @Shadow private float progress;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lume$overlay(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CustomMenu.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        LoadingScreen.draw(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), progress);
    }
}

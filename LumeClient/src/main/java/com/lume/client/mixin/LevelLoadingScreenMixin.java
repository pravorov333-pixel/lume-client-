package com.lume.client.mixin;

import com.lume.client.gui.LoadingScreen;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.server.WorldGenerationProgressTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Menu — paints our own logo+bar over the world-generation loading screen (real % progress). */
@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {

    @Shadow private WorldGenerationProgressTracker progressProvider;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lume$overlay(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CustomMenu.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        float p = progressProvider != null ? progressProvider.getProgressPercentage() / 100f : -1f;
        LoadingScreen.draw(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), p, "Генерация мира…");
    }
}

package com.lume.client.mixin;

import com.lume.client.gui.LoadingScreen;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Menu — paints our own logo+bar over "Loading terrain..." (entering
 * a world, single or multiplayer). No real % here, so an indeterminate
 * sliding fill; the entry reason (nether/end portal vs. a normal join) is
 * available though, so the status line at least says which.
 */
@Mixin(DownloadingTerrainScreen.class)
public class DownloadingTerrainScreenMixin {

    @Shadow private DownloadingTerrainScreen.WorldEntryReason worldEntryReason;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lume$overlay(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CustomMenu.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        String status = switch (worldEntryReason) {
            case NETHER_PORTAL -> "Портал в Нижний мир…";
            case END_PORTAL -> "Портал в Край…";
            default -> "Загрузка мира…";
        };
        LoadingScreen.draw(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), -1f, status);
    }
}

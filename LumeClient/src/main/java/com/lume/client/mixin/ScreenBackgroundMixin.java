package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints Lume's own animated background on the Singleplayer (SelectWorldScreen)
 * and Multiplayer (MultiplayerScreen) screens while Custom Menu is on — neither
 * overrides {@code renderBackground} itself, so hooking the shared {@code
 * Screen} implementation and gating by instance type covers both without
 * touching every other screen (options, pause, etc. keep vanilla background).
 * Button layout/sizes are untouched here — see {@code PressableWidgetMixin}
 * for the paint-only button reskin.
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof SelectWorldScreen) && !(self instanceof MultiplayerScreen)) return;
        if (!CustomMenu.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        CustomMenu.drawBackground(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        ci.cancel();
    }
}

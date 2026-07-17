package com.lume.client.mixin;

import com.lume.client.gui.LoadingScreen;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Menu — paints our own logo+bar over "Connecting.../Logging in...", showing vanilla's own live status text (no real % here). */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Shadow private Text status;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lume$overlay(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CustomMenu.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        String s = status != null ? status.getString() : null;
        LoadingScreen.draw(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), -1f, s);
    }
}

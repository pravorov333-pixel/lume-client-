package com.lume.client.mixin;

import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Wordmark;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps vanilla's "MINECRAFT" logo texture for the "LUME VISUALS" wordmark + glass-star mark
 * while Custom Menu is on — per the reference, which shows the custom wordmark centred at the
 * top of the title screen. Entirely {@code RenderUtil}/DrawContext (the star mark is a live
 * scanline-polygon-fill draw, the wordmark a plain font draw with a shimmering accent gradient
 * baked per-character) — no NanoVG, so none of the earlier NanoVG-driver-crash risk this class
 * was removed over applies here.
 */
@Mixin(LogoDrawer.class)
public class LogoDrawerMixin {

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$wordmark(DrawContext ctx, int screenWidth, float alpha, CallbackInfo ci) {
        if (!CustomMenu.active()) return;
        var tr = MinecraftClient.getInstance().textRenderer;
        float scale = 0.9f;
        int starSize = 26, gap = 6;
        int textW = Wordmark.widthLegacy(tr, scale);
        int totalW = starSize + gap + textW;
        int x = screenWidth / 2 - totalW / 2;
        int y = 16;
        RenderUtil.drawLogo(ctx, x, y, starSize);
        Wordmark.drawLegacyVivid(ctx, tr, x + starSize + gap, y + (starSize - 7) / 2f, scale);
        ci.cancel();
    }
}

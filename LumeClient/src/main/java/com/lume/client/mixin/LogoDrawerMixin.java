package com.lume.client.mixin;

import com.lume.client.gui.Theme;
import com.lume.client.menu.LumeTitleMenu;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's "MINECRAFT" logo texture on the title screen with a
 * "Lume Visuals" wordmark (our diamond mark + accent-tinted text) while
 * Custom Menu is active, so the title screen reads as our brand instead of
 * Mojang's. Both {@code LogoDrawer.draw} overloads are covered since which
 * one vanilla calls isn't pinned down here — the unused one just never fires.
 */
@Mixin(LogoDrawer.class)
public class LogoDrawerMixin {

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$logo3(DrawContext context, int width, float alpha, CallbackInfo ci) {
        if (lume$drawWordmark(context, width, alpha)) ci.cancel();
    }

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$logo4(DrawContext context, int width, float alpha, int yOffset, CallbackInfo ci) {
        if (lume$drawWordmark(context, width, alpha)) ci.cancel();
    }

    /** @return true if vanilla's draw should be cancelled (Custom Menu owns the screen) */
    private static boolean lume$drawWordmark(DrawContext context, int width, float alpha) {
        if (!CustomMenu.active()) return false;
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return true;   // still cancel vanilla even if we can't draw ours
        float a = Math.max(0f, Math.min(1f, alpha));
        if (a <= 0f) return true;
        float S = NanoVgRenderer.pxScale();
        int alphaByte = (int) (a * 255f);
        // Anchored a fixed gap above the Singleplayer button (LumeTitleMenu.buttonRowY) instead
        // of vanilla's own LOGO_BASE_Y, which sits much higher up — wordmark + buttons now read
        // as one centred group instead of the wordmark floating far above them.
        int height = MinecraftClient.getInstance().getWindow().getScaledHeight();
        float cx = width / 2f, cy = LumeTitleMenu.buttonRowY(height) - 34f;

        context.draw();
        NanoVgRenderer.frame(vg -> {
            float textSize = 20f, markSize = 22f, gap = 6f;
            float twTotal = com.lume.client.gui.Wordmark.width(vg, textSize * S) / S;
            float totalW = markSize + gap + twTotal;
            float startX = cx - totalW / 2f;

            NanoVgRenderer.logoMark(vg, startX * S, (cy - markSize / 2f) * S, markSize * S);
            com.lume.client.gui.Wordmark.draw(vg, (startX + markSize + gap) * S, cy * S, textSize * S, alphaByte);
        });
        return true;
    }

    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }
}

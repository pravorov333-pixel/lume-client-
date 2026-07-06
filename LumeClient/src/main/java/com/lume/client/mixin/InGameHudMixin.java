package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomHotbar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla status-effect (potion) overlay while our Potion HUD is on,
 * so only Lume's HUD shows. require=0 → if the target name differs in some
 * version, the inject is skipped silently instead of crashing.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$hideStatusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("Potion HUD");
        if (m != null && m.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$hideCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("Custom Crosshair");
        if (m != null && m.isEnabled()) {
            ci.cancel();   // Lume draws its own crosshair in HudRenderer
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), require = 0)
    private void lume$customHotbar(DrawContext ctx, RenderTickCounter tickCounter, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("Custom Hotbar");
        if (!(m instanceof CustomHotbar h) || !h.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = sw / 2 - 91, y = sh - 22;
        int a = Math.max(0, Math.min(255, (int) (h.opacity.value * 255)));
        int rgb = h.style.index == 0 ? (Theme.winBg() & 0xFFFFFF) : (h.color.rgb() & 0xFFFFFF);
        RenderUtil.roundedRect(ctx, x - 3, y - 3, 188, 28, 6, (a << 24) | rgb);
        if (h.style.index == 0)   // glass rim
            RenderUtil.roundedRect(ctx, x - 3, y - 3, 188, 1, 1, (Math.min(255, a + 40) << 24) | 0xFFFFFF);
    }
}

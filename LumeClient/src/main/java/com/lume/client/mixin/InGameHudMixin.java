package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
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
}

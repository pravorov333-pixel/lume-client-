package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.GuiAnimations;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GUI Animations — grows vanilla screens in with an eased scale when they open. */
@Mixin(Screen.class)
public class ScreenMixin {

    @Unique private boolean lume$pushed;
    @Unique private static Screen lume$last;
    @Unique private static long lume$start;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"), require = 0)
    private void lume$animIn(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        lume$pushed = false;
        Screen self = (Screen) (Object) this;
        Module m = LumeClient.MODULES.getByName("GUI Animations");
        if (!(m instanceof GuiAnimations ga) || !ga.isEnabled()) return;
        if (self.getClass().getName().startsWith("com.lume.client")) return;   // our own menus animate themselves

        if (self != lume$last) { lume$last = self; lume$start = System.currentTimeMillis(); }
        float p = Math.min(1f, (System.currentTimeMillis() - lume$start) / ga.durationMs());
        if (p >= 1f) return;
        float e = 1f - (1f - p) * (1f - p);           // ease-out
        float scale = 0.85f + 0.15f * e;
        float cx = self.width / 2f, cy = self.height / 2f;
        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(cx, cy, 0);
        ms.scale(scale, scale, 1f);
        ms.translate(-cx, -cy, 0);
        lume$pushed = true;
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"), require = 0)
    private void lume$animOut(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (lume$pushed) { ctx.getMatrices().pop(); lume$pushed = false; }
    }
}

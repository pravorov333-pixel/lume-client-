package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.GuiAnimations;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GUI Animations — "Animate Tab". {@code render} is only ever called while
 * the player list is actually held up (vanilla only invokes it then), so a
 * gap between two calls longer than a frame means it was just (re)opened —
 * used to time the animation the same way {@code ScreenMixin} times a
 * screen's open, without needing an explicit open/close event.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Unique private boolean lume$pushed;
    @Unique private static long lume$start;
    @Unique private static long lume$lastFrame;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void lume$animIn(DrawContext ctx, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        lume$pushed = false;
        Module m = LumeClient.MODULES.getByName("GUI Animations");
        if (!(m instanceof GuiAnimations ga) || !ga.isEnabled() || !ga.tab.value) return;

        long now = System.currentTimeMillis();
        if (now - lume$lastFrame > 100) lume$start = now;   // gap since last render = just opened
        lume$lastFrame = now;

        float p = Math.min(1f, (now - lume$start) / ga.durationMs());
        if (p >= 1f) return;
        float e = 1f - (1f - p) * (1f - p);   // ease-out

        var ms = ctx.getMatrices();
        ms.push();
        if (ga.style.index == 0) {
            float scale = 0.85f + 0.15f * e;
            float cx = scaledWidth / 2f, cy = 0f;
            ms.translate(cx, cy, 0);
            ms.scale(scale, scale, 1f);
            ms.translate(-cx, -cy, 0);
        } else {
            float offset = -80f * (1f - e);   // slides down from above
            ms.translate(0, offset, 0);
        }
        lume$pushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void lume$animOut(DrawContext ctx, int scaledWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (lume$pushed) { ctx.getMatrices().pop(); lume$pushed = false; }
    }
}

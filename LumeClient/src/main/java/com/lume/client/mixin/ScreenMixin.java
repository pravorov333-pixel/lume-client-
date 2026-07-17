package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.menu.LumeTitleMenu;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.module.modules.cosmetic.GuiAnimations;
import com.lume.client.module.modules.cosmetic.NoBgBlur;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GUI Animations — grows/slides screens in when they open. Scoped to exactly
 * two screen kinds now (everything else renders instantly, no animation):
 * chat ({@link ChatScreen}, the typing prompt) and inventory/containers
 * ({@link HandledScreen}). The player list (Tab) isn't a Screen at all — see
 * PlayerListHudMixin — and the hotbar's own item-switch animation is separate
 * too (HotbarMixin), since neither opens/closes the way a screen does.
 */
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

        boolean isChat = self instanceof ChatScreen;
        boolean isInventory = self instanceof HandledScreen;
        if (isChat && !ga.chat.value) return;
        if (isInventory && !ga.inventory.value) return;
        if (!isChat && !isInventory) return;   // scoped: nothing else animates any more

        if (self != lume$last) { lume$last = self; lume$start = System.currentTimeMillis(); }
        float p = Math.min(1f, (System.currentTimeMillis() - lume$start) / ga.durationMs());
        if (p >= 1f) return;
        // Cubic ease-out — snappier off the start and gentler into the landing than the old
        // quadratic curve, which combined with pure-translate Slide read as mechanical/robotic
        // rather than smooth. Slide now also carries a touch of the same scale pop Scale style
        // gets, instead of being position-only, so both styles feel like one motion, not a
        // straight-line "выезжает" (slides out) with no give.
        float inv = 1f - p;
        float e = 1f - inv * inv * inv;

        var ms = ctx.getMatrices();
        ms.push();
        float cx = self.width / 2f, cy = self.height / 2f;
        float scale = 0.92f + 0.08f * e;
        ms.translate(cx, cy, 0);
        ms.scale(scale, scale, 1f);
        ms.translate(-cx, -cy, 0);
        if (ga.style.index == 1) {
            // slide — chat and inventory both come up from below, layered on top of the scale pop
            float offset = self.height * 0.12f * (1f - e);
            ms.translate(0, offset, 0);
        }
        lume$pushed = true;
    }

    @Inject(method = "renderWithTooltip", at = @At("RETURN"), require = 0)
    private void lume$animOut(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (lume$pushed) { ctx.getMatrices().pop(); lume$pushed = false; }
    }

    /**
     * "No BG Blur" — skips vanilla's own background-blur post-effect entirely.
     * Targets {@code applyBlur()} (the method that actually calls
     * {@code GameRenderer.renderBlur()} and runs the post-process shader) — NOT
     * {@code blur()}, which is an unrelated method that only clears GUI focus
     * navigation and has nothing to do with rendering. Confirmed via javap
     * bytecode after this fix was reported as still not working: the original
     * hook was cancelling the wrong method the whole time. {@link GameOptionsMixin}
     * already zeroes the blur-intensity value {@code renderBlur()} itself reads,
     * which is the real single source of truth — this is a belt-and-suspenders
     * second guard so blur never even attempts to trigger.
     */
    /**
     * Skips only the actual blur post-process — NOT the whole method. {@code applyBlur()}
     * also does {@code client.getFramebuffer().beginWrite(false)} right after the render
     * call, which every screen's own drawing depends on being bound; cancelling the whole
     * method (the previous approach) skipped that too, leaving the wrong framebuffer bound
     * and crashing the very next screen that tried to render (reproduced: opening any menu
     * — e.g. Right Shift — crashed outright). Redirecting only the render call keeps the
     * framebuffer rebind intact.
     */
    @Redirect(method = "applyBlur", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;renderBlur()V"), require = 0)
    private void lume$skipBlurRender(GameRenderer instance) {
        if (!NoBgBlur.active()) instance.renderBlur();
    }

    /**
     * Custom Menu — "everywhere" fix: {@link TitleScreenMixin} only replaces TitleScreen's
     * own background/panorama, so any OTHER pre-game screen (Multiplayer, Options-from-title,
     * Realms, server list, etc.) that doesn't override these methods still falls through to
     * this base {@link Screen} implementation and showed vanilla's panorama/background — the
     * bug being reported. Gated to {@code mc.world == null} so the actual in-game pause menu
     * (blurred gameplay behind it) is untouched; our own menu classes skip this entirely since
     * they paint their own background already.
     */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customMenuBackgroundEverywhere(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!CustomMenu.customBackgroundActive() || self.getClass().getName().startsWith("com.lume.client")) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) return;
        CustomMenu.drawBackground(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        ci.cancel();
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noPanoramaEverywhere(DrawContext ctx, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!CustomMenu.customBackgroundActive() || self.getClass().getName().startsWith("com.lume.client")) return;
        if (MinecraftClient.getInstance().world != null) return;
        ci.cancel();
    }

    /**
     * No BG Blur, the inventory case: {@code HandledScreen.renderBackground} overrides the base
     * class entirely and never calls {@code applyBlur()} at all (confirmed via bytecode — it
     * calls {@code renderInGameBackground} + its own {@code drawBackground} instead), so the
     * applyBlur redirect above never even fires for it. The dark hazy overlay the user actually
     * sees behind the inventory is this plain fillGradient darkening, not the blur shader.
     */
    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noInGameDarkening(DrawContext ctx, CallbackInfo ci) {
        if (NoBgBlur.active()) ci.cancel();
    }

    /**
     * Custom Menu's typed text fields (friend-add, FastConnect name/addr, account nickname) —
     * moved here from {@code TitleScreenMixin} because {@code keyPressed} is only INHERITED by
     * TitleScreen (declared on {@link Screen} itself); a @Mixin(TitleScreen.class) injection
     * targeting it never resolves (confirmed via the "Unable to determine descriptor" javac-AP
     * warning every build) and silently no-ops under require=0 — so no field ever received a
     * single keystroke. Screen genuinely declares this method, so this one actually attaches.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$titleMenuKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!(((Object) this) instanceof TitleScreen)) return;
        if (LumeTitleMenu.keyPressed(keyCode, scanCode, modifiers)) cir.setReturnValue(true);
    }
}

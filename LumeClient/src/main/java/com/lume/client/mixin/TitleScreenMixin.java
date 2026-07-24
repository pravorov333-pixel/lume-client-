package com.lume.client.mixin;

import com.lume.client.Config;
import com.lume.client.menu.FlatAccountManagerScreen;
import com.lume.client.menu.FlatFastConnectScreen;
import com.lume.client.menu.FlatFriendsScreen;
import com.lume.client.menu.LumeTitleMenu;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin wiring layer: while Custom Menu is on, every vanilla widget on the title screen (
 * Singleplayer/Multiplayer/Realms banner/Options/Language/Accessibility/whatever else vanilla
 * added) is hidden AND deactivated ({@code visible=false, active=false} on every child, set once
 * in {@code init}'s TAIL) — Lume draws and hit-tests its own complete replacement composition
 * instead (logo, Singleplayer/Multiplayer, corner buttons, bottom bar — see
 * {@link LumeTitleMenu#render}), laid out to match the reference rather than vanilla's own
 * spacing. Deactivating (not just hiding) vanilla's widgets matters: without it, an invisible
 * vanilla button could still eat a click in whatever blank space it used to occupy, since Lume's
 * own buttons live at different coordinates now. Vanilla's background/panorama is still used
 * unless a wallpaper is selected — only the WIDGETS are replaced, per this same file's background
 * injections below.
 *
 * <p>Ultra Performance draws its own 3 plain buttons (Friends/Fast Connect/Account Manager) via
 * the exact same {@code render TAIL} + {@code mouseClicked HEAD} injection pair already used for
 * {@link LumeTitleMenu} above, hand-hit-tested — deliberately NOT via a {@code @Shadow} on
 * {@code Screen#addDrawableChild}: that method is declared on the SUPERCLASS ({@code Screen}),
 * not on {@code TitleScreen} itself, and shadowing an inherited generic-bounded method through
 * Mixin's refmap resolution is a known source of "Mixin apply failed, game won't boot" crashes —
 * not worth the risk for 3 buttons when a manual fill+hit-test (proven safe by every other
 * injection in this file) does the exact same job.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void lume$hideVanillaWidgets(CallbackInfo ci) {
        if (Config.ultra() || !CustomMenu.active()) return;
        TitleScreen screen = (TitleScreen) (Object) this;
        for (var el : screen.children()) {
            if (el instanceof ClickableWidget w) { w.visible = false; w.active = false; }
        }
    }

    private static final int ULTRA_BTN_X = 4, ULTRA_BTN_W = 118, ULTRA_BTN_H = 20, ULTRA_BTN_GAP = 3;

    /** Which ultra-mode buttons are showing this frame, top to bottom — recomputed every render
     *  (cheap: 3 boolean reads) so it always matches the CustomMenu show/hide toggles live. */
    private static String[] ultraButtonLabels() {
        java.util.List<String> labels = new java.util.ArrayList<>(3);
        if (CustomMenu.showFriends()) labels.add("Friends");
        if (CustomMenu.showFastConnect()) labels.add("Fast Connect");
        if (CustomMenu.showAccount()) labels.add("Account Manager");
        return labels.toArray(new String[0]);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Config.ultra() && CustomMenu.wallpaperActive()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            CustomMenu.drawBackground(ctx, w, h);
            CustomMenu.drawDimOverlay(ctx, w, h);   // ci.cancel() below skips the TAIL injector, so draw it here too
            ci.cancel();
        }
    }

    /** Background Dim (Menu settings) over vanilla's OWN panorama — only reached when the HEAD
     *  injection above didn't already cancel the method (i.e. no custom wallpaper is active). */
    @Inject(method = "renderBackground", at = @At("TAIL"), require = 0)
    private void lume$dimOverlay(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Config.ultra()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        CustomMenu.drawDimOverlay(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }

    /**
     * The rotating 3D panorama is a SEPARATE render call from {@code renderBackground}
     * above (found the hard way — cancelling just renderBackground left our flat
     * background drawing on TOP of the still-rendering vanilla panorama, layered
     * instead of replaced). Cancelling this too means nothing vanilla renders at all.
     */
    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noPanorama(DrawContext ctx, float delta, CallbackInfo ci) {
        if (!Config.ultra() && CustomMenu.wallpaperActive()) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lume$renderExtras(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Config.ultra()) {
            TitleScreen screen = (TitleScreen) (Object) this;
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            int y = 4;
            for (String label : ultraButtonLabels()) {
                boolean hover = mouseX >= ULTRA_BTN_X && mouseX < ULTRA_BTN_X + ULTRA_BTN_W && mouseY >= y && mouseY < y + ULTRA_BTN_H;
                ctx.fill(ULTRA_BTN_X, y, ULTRA_BTN_X + ULTRA_BTN_W, y + ULTRA_BTN_H, hover ? 0xB0303030 : 0x90202020);
                ctx.drawCenteredTextWithShadow(tr, label, ULTRA_BTN_X + ULTRA_BTN_W / 2, y + (ULTRA_BTN_H - 8) / 2, 0xFFFFFFFF);
                y += ULTRA_BTN_H + ULTRA_BTN_GAP;
            }
            return;
        }
        LumeTitleMenu.render(ctx, (TitleScreen) (Object) this, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (Config.ultra()) {
            TitleScreen screen = (TitleScreen) (Object) this;
            int y = 4;
            for (String label : ultraButtonLabels()) {
                if (mouseX >= ULTRA_BTN_X && mouseX < ULTRA_BTN_X + ULTRA_BTN_W && mouseY >= y && mouseY < y + ULTRA_BTN_H) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    switch (label) {
                        case "Friends" -> mc.setScreen(new FlatFriendsScreen(screen));
                        case "Fast Connect" -> mc.setScreen(new FlatFastConnectScreen(screen));
                        case "Account Manager" -> mc.setScreen(new FlatAccountManagerScreen(screen));
                    }
                    cir.setReturnValue(true);
                    return;
                }
                y += ULTRA_BTN_H + ULTRA_BTN_GAP;
            }
            return;
        }
        if (LumeTitleMenu.mouseClicked((TitleScreen) (Object) this, mouseX, mouseY)) cir.setReturnValue(true);
    }

    /**
     * No {@code charTyped} hook here on purpose: it's only a default interface
     * method on vanilla's {@code Element}, never overridden by {@code Screen}
     * or {@code TitleScreen}, so a Mixin @Inject targeting it here would have
     * no real bytecode to attach to. Typed characters are derived from
     * {@code keyPressed} instead — but that injection lives in {@code ScreenMixin}
     * now, not here: {@code keyPressed} is only INHERITED by TitleScreen (declared
     * on {@code Screen} itself), so a @Mixin(TitleScreen.class) target for it never
     * actually resolves (see ScreenMixin's {@code lume$titleMenuKeyPressed}).
     */
}

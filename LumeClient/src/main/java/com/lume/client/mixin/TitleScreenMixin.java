package com.lume.client.mixin;

import com.lume.client.menu.LumeTitleMenu;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Thin wiring layer: replaces the vanilla panorama with Lume's themed
 * background and, while Custom Menu is on, strips vanilla widgets and hands
 * render/input entirely to {@link LumeTitleMenu} (own NanoVG glass UI, not
 * reskinned vanilla buttons). All actual layout/click logic lives there.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void lume$onInit(CallbackInfo ci) {
        LumeTitleMenu.onInit((TitleScreen) (Object) this);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (CustomMenu.customBackgroundActive()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            CustomMenu.drawBackground(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            ci.cancel();
        }
    }

    /**
     * The rotating 3D panorama is a SEPARATE render call from {@code renderBackground}
     * above (found the hard way — cancelling just renderBackground left our flat
     * background drawing on TOP of the still-rendering vanilla panorama, layered
     * instead of replaced). Cancelling this too means nothing vanilla renders at all.
     */
    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noPanorama(DrawContext ctx, float delta, CallbackInfo ci) {
        if (CustomMenu.customBackgroundActive()) ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lume$renderExtras(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        LumeTitleMenu.render(ctx, (TitleScreen) (Object) this, mouseX, mouseY);
    }

    /**
     * Menu settings → Version, done properly: this is the ONE inline
     * {@code drawTextWithShadow} call inside {@code render()} (bottom-left
     * "Minecraft x.xx.x[-release/modded]", confirmed via javap — there's no
     * separate overridable method for it). A @Redirect here truly skips the
     * draw when off, instead of painting over it after the fact.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I"),
            require = 0)
    private int lume$versionText(DrawContext instance, TextRenderer textRenderer, String text, int x, int y, int color) {
        if (!CustomMenu.showVersion()) return 0;
        return instance.drawTextWithShadow(textRenderer, text, x, y, color);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
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

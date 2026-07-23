package com.lume.client.mixin;

import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskins vanilla's Singleplayer/Multiplayer buttons on the title screen with the Lume glass
 * pill look — live SDF background (see {@link RenderUtil#premiumBg}), same premium hover
 * lift/contour-glow every other Custom Menu button uses now — while Custom Menu is on. Same
 * technique {@code PressableWidgetMixin} already uses for the Death Screen: repaint only, same
 * widget object, so click handling/position/tooltip all keep working exactly as vanilla left
 * them. Matched by translation key (locale-safe) rather than position or button order, since
 * vanilla's exact title-screen layout isn't something this mod controls and shouldn't hardcode
 * coordinates against.
 */
@Mixin(PressableWidget.class)
public class TitleButtonMixin {

    /** Per-widget eased hover amount — keyed by the actual button instance (stable across
     *  frames, only re-created on screen init) so the lift/glow animate smoothly instead of
     *  snapping. Weak-keyed: no leak once a TitleScreen (and its buttons) is discarded. */
    private static final java.util.WeakHashMap<ClickableWidget, float[]> ANIM = new java.util.WeakHashMap<>();
    private static long lastFrame = System.currentTimeMillis();

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$reskin(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof TitleScreen) || !CustomMenu.active()) return;

        ClickableWidget w = (ClickableWidget) (Object) this;
        if (!w.visible) return;
        if (!matchesKey(w.getMessage(), "menu.singleplayer") && !matchesKey(w.getMessage(), "menu.multiplayer")) return;

        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float[] st = ANIM.computeIfAbsent(w, k -> new float[1]);
        st[0] = st[0] + ((w.isHovered() ? 1f : 0f) - st[0]) * Math.min(1f, 12f * dt);

        int x = w.getX(), y = w.getY(), width = w.getWidth(), height = w.getHeight();
        RenderUtil.premiumBg(ctx, x, y, width, height, 6, st[0], Theme.winBg(), Theme.rim(), Theme.accentRgb());
        int ly = y - RenderUtil.premiumLift(st[0]);

        RenderUtil.textCentered(ctx, mc.textRenderer, w.getMessage().getString(), x, ly, width, height, Theme.txt(), 0.5f);
        ci.cancel();
    }

    /** Compares by rendered string against the SAME translation key, so it's correct under any
     *  locale (never hardcode the English label itself). */
    private static boolean matchesKey(Text msg, String key) {
        return msg != null && msg.getString().equals(Text.translatable(key).getString());
    }
}

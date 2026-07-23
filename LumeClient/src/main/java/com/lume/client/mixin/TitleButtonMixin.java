package com.lume.client.mixin;

import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.menu.MenuAssets;
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
 * pill look (see {@code tools/gen_menu.py}'s {@code singleplayer.png}/{@code multiplayer.png})
 * while Custom Menu is on — same technique {@code PressableWidgetMixin} already uses for the
 * Death Screen: repaint only, same widget object, so click handling/position/tooltip all keep
 * working exactly as vanilla left them. Matched by translation key (locale-safe) rather than
 * position or button order, since vanilla's exact title-screen layout isn't something this mod
 * controls and shouldn't hardcode coordinates against.
 */
@Mixin(PressableWidget.class)
public class TitleButtonMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$reskin(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof TitleScreen) || !CustomMenu.active()) return;

        ClickableWidget w = (ClickableWidget) (Object) this;
        if (!w.visible) return;

        String asset;
        if (matchesKey(w.getMessage(), "menu.singleplayer")) asset = MenuAssets.SINGLEPLAYER;
        else if (matchesKey(w.getMessage(), "menu.multiplayer")) asset = MenuAssets.MULTIPLAYER;
        else return;

        int x = w.getX(), y = w.getY(), width = w.getWidth(), height = w.getHeight();
        if (!MenuAssets.blit(ctx, asset, x, y, width, height)) return; // texture not ready yet — fall through to vanilla paint

        if (w.isHovered()) RenderUtil.roundedRect(ctx, x, y, width, height, 6, 0x1AFFFFFF);

        RenderUtil.textCentered(ctx, mc.textRenderer, w.getMessage().getString(), x, y, width, height, Theme.txt(), 0.5f);
        ci.cancel();
    }

    /** Compares by rendered string against the SAME translation key, so it's correct under any
     *  locale (never hardcode the English label itself). */
    private static boolean matchesKey(Text msg, String key) {
        return msg != null && msg.getString().equals(Text.translatable(key).getString());
    }
}

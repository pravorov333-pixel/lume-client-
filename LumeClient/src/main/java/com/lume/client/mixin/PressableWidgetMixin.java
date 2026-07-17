package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomDeathScreen;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reskins vanilla buttons on the Singleplayer (SelectWorldScreen) and
 * Multiplayer (MultiplayerScreen) screens with the Lume glass look while
 * Custom Menu is on — every vanilla action, hover/disabled state and tooltip
 * keeps working exactly as before, and so does every button's size/position
 * (untouched, still vanilla layout) — only {@code renderWidget}'s paint call
 * is replaced, now via NanoVG (our own font + a shrink-to-fit label so text
 * never overflows the button). Scoped tightly to these two screens so
 * nothing else (options, pause, our own ClickGUI, the Lume title screen
 * which has no vanilla widgets left anyway) is affected.
 */
@Mixin(PressableWidget.class)
public class PressableWidgetMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$reskin(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean onMenuScreen = (mc.currentScreen instanceof SelectWorldScreen || mc.currentScreen instanceof MultiplayerScreen)
                && CustomMenu.active();
        Module cdM = LumeClient.MODULES.getByName("Custom Death Screen");
        boolean onDeathScreen = mc.currentScreen instanceof DeathScreen
                && cdM instanceof CustomDeathScreen cds && cds.isEnabled();
        if (!onMenuScreen && !onDeathScreen) return;

        ClickableWidget w = (ClickableWidget) (Object) this;
        if (!w.visible) return;

        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;   // fall through to vanilla paint rather than draw nothing

        int x = w.getX(), y = w.getY(), width = w.getWidth(), height = w.getHeight();
        boolean hovered = w.isHovered();
        boolean active = w.active;
        String label = w.getMessage().getString();

        int S = (int) Math.max(1, mc.getWindow().getScaleFactor());
        int nx = x * S, ny = y * S, nw = width * S, nh = height * S;

        ctx.draw();   // flush whatever DrawContext has queued so far (background, earlier buttons) before raw-GL NanoVG draws
        NanoVgRenderer.frame(vg -> {
            int bg = !active ? Theme.glassRow() : (hovered ? Theme.glassHov() : Theme.glassRow());
            NanoVgRenderer.roundedRect(vg, nx, ny, nw, nh, 6 * S, bg);
            if (active && hovered) NanoVgRenderer.bloom(vg, nx, ny, nw, nh, 6 * S, 7 * S, Theme.accentRgb(), 0x55);
            if (active) NanoVgRenderer.roundedRect(vg, nx, ny + nh - 2 * S, nw, 2 * S, S,
                    hovered ? Theme.accent() : (Theme.accent() & 0x66FFFFFF));

            int color = !active ? Theme.txtDim() : (hovered ? Theme.accent() : Theme.txt());
            float maxW = nw - 12 * S;
            float size = NanoVgRenderer.fitSize(vg, 10 * S, label, maxW, 6 * S);
            NanoVgRenderer.text(vg, nx + nw / 2f, ny + nh / 2f, size, color, NanoVgRenderer.ALIGN_CENTER_MIDDLE, label);
        });

        ci.cancel();
    }
}

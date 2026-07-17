package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomInventory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Custom Inventory — replaces the vanilla container panel with a Lume glass reskin.
 *  drawBackground is abstract, so we redirect its call inside render() instead. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;
    @Shadow protected abstract void drawBackground(DrawContext context, float delta, int mouseX, int mouseY);

    @Redirect(method = "renderBackground",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/HandledScreen;drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V"),
            require = 0)
    private void lume$reskin(HandledScreen self, DrawContext ctx, float delta, int mouseX, int mouseY) {
        Module m = LumeClient.MODULES.getByName("Custom GUI");
        // Skip the creative inventory (its tabs/buttons are part of the texture we'd hide).
        if (!(m instanceof CustomInventory inv) || !inv.isEnabled()
                || (Object) this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) {
            drawBackground(ctx, delta, mouseX, mouseY);   // vanilla panel
            return;
        }
        int a = Math.max(0, Math.min(255, (int) (inv.opacity.value * 255)));
        int rgb = inv.style.index == 0 ? (Theme.winBg() & 0xFFFFFF)
                : ((inv.color.accent ? Theme.accentRgb() : inv.color.rgb()) & 0xFFFFFF);
        RenderUtil.roundedRect(ctx, x - 5, y - 5, backgroundWidth + 10, backgroundHeight + 10, 8, (a << 24) | rgb);
        for (Slot s : self.getScreenHandler().slots)
            RenderUtil.roundedRect(ctx, x + s.x - 1, y + s.y - 1, 18, 18, 3, 0x55000000);

        // vanilla drawBackground for InventoryScreen also renders the player model preview —
        // replacing the whole method silently dropped it, so replay that call here (same
        // coords/size as vanilla: x+26,y+8 to x+75,y+78, size 30, scale 0.0625).
        if ((Object) self instanceof InventoryScreen) {
            var mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                InventoryScreen.drawEntity(ctx, x + 26, y + 8, x + 75, y + 78, 30, 0.0625f,
                        (float) mouseX, (float) mouseY, mc.player);
            }
        }

        // PvP Helper — highlight the best food slot when it's in the player's main inventory
        // (the hotbar portion is drawn separately by HudRenderer, always visible in-world).
        if ((Object) self instanceof InventoryScreen) {
            Module pm = LumeClient.MODULES.getByName("PvP Helper");
            if (pm instanceof com.lume.client.module.modules.qol.PvpHelper ph && ph.isEnabled()) {
                int slot = ph.targetSlot();
                if (slot > 8) {
                    for (Slot s : self.getScreenHandler().slots) {
                        if (s.getIndex() == slot) {
                            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
                            int pulseA = Math.round(90 + 120 * pulse);
                            RenderUtil.roundedRect(ctx, x + s.x - 1, y + s.y - 1, 18, 18, 3, (pulseA << 24) | 0x00FF66);
                            break;
                        }
                    }
                }
            }
        }
    }
}

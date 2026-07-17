package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomHotbar;
import com.lume.client.module.modules.cosmetic.GuiAnimations;
import com.lume.client.util.SmoothSlot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * GUI Animations — "Animate Hotbar": when the selected slot changes, its
 * icon pops in with a quick scale bounce instead of snapping to the new
 * item. {@code renderHotbarItem} is called once per slot but isn't told
 * which index it's drawing — rather than guess vanilla's exact pixel layout
 * constants, this matches by identity: the caller passes each slot's own
 * {@code ItemStack} instance straight from the inventory, so comparing by
 * reference against {@code inventory.getStack(selectedSlot)} reliably picks
 * out the one call that's actually the selected slot.
 */
@Mixin(InGameHud.class)
public class HotbarMixin {

    @Unique private static int lume$lastSlot = -1;
    @Unique private static long lume$start = 0;
    @Unique private boolean lume$pushed;

    @Inject(method = "renderHotbarItem", at = @At("HEAD"), require = 0)
    private void lume$popIn(DrawContext ctx, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        lume$pushed = false;
        Module m = LumeClient.MODULES.getByName("GUI Animations");
        if (!(m instanceof GuiAnimations ga) || !ga.isEnabled() || !ga.hotbar.value) return;

        int selected = player.getInventory().selectedSlot;
        if (selected != lume$lastSlot) { lume$lastSlot = selected; lume$start = System.currentTimeMillis(); }
        if (stack != player.getInventory().getStack(selected)) return;   // not the selected slot's own render call

        long dur = 160;
        float p = Math.min(1f, (System.currentTimeMillis() - lume$start) / (float) dur);
        if (p >= 1f) return;
        float e = 1f - (1f - p) * (1f - p);   // ease-out
        float scale = 1.35f - 0.35f * e;      // pops slightly big, settles to normal size

        var ms = ctx.getMatrices();
        ms.push();
        float cx = x + 8f, cy = y + 8f;
        ms.translate(cx, cy, 0);
        ms.scale(scale, scale, 1f);
        ms.translate(-cx, -cy, 0);
        lume$pushed = true;
    }

    @Inject(method = "renderHotbarItem", at = @At("RETURN"), require = 0)
    private void lume$popOut(DrawContext ctx, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        if (lume$pushed) { ctx.getMatrices().pop(); lume$pushed = false; }
    }

    // ---- selection-box slide (vanilla's own indicator) --------------------

    @Unique private static final Identifier SELECTION_TEX = Identifier.ofVanilla("hud/hotbar_selection");
    @Unique private static final SmoothSlot lume$selSlide = new SmoothSlot();

    /**
     * Redirects EVERY {@code drawGuiTexture} call inside {@code renderHotbar} (there are several —
     * base bar, selection box, offhand slot) but only actually changes behaviour for the one
     * drawing {@code hotbar_selection}, matched by identity rather than call-order (fragile
     * ordinal indexing) — confirmed via bytecode this texture/x,y,w,h combo is unique to that draw.
     * Custom Hotbar draws its own rounded replacement (see InGameHudMixin), so this one is skipped
     * entirely when that's on; otherwise it slides smoothly between slots when GUI Animations is on.
     */
    @Redirect(method = "renderHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V"),
            require = 0)
    private void lume$selectionSlide(DrawContext ctx, Function<Identifier, RenderLayer> layerFn, Identifier tex,
                                      int x, int y, int width, int height) {
        if (!SELECTION_TEX.equals(tex)) { ctx.drawGuiTexture(layerFn, tex, x, y, width, height); return; }

        Module chM = LumeClient.MODULES.getByName("Custom Hotbar");
        if (chM instanceof CustomHotbar ch && ch.isEnabled()) return;   // ours draws instead

        Module gaM = LumeClient.MODULES.getByName("GUI Animations");
        boolean animate = gaM instanceof GuiAnimations ga && ga.isEnabled() && ga.hotbar.value;
        int ax = Math.round(lume$selSlide.update(x, animate));
        ctx.drawGuiTexture(layerFn, tex, ax, y, width, height);
    }
}

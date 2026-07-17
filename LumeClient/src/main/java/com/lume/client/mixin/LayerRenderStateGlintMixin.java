package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.EnchantGlint;
import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enchant Glint "No Animation" style: forces every item's glint layer to NONE while active, so
 *  enchanted items render like plain ones. Applies wherever items are built (hand / GUI / dropped /
 *  other players) — item glint only; armor glint uses a different path and is left alone. */
@Mixin(ItemRenderState.LayerRenderState.class)
public abstract class LayerRenderStateGlintMixin {

    @Shadow private ItemRenderState.Glint glint;

    @Inject(method = "setGlint", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$suppressGlint(ItemRenderState.Glint glint, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("Enchant Glint");
        if (m instanceof EnchantGlint eg && eg.isEnabled() && eg.style.index == EnchantGlint.STYLE_NONE) {
            this.glint = ItemRenderState.Glint.NONE;
            ci.cancel();
        }
    }
}

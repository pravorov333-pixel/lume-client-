package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CleanView;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean View "No Totem Animation" — cancels the floating-item pop shown when your own totem saves you. */
@Mixin(GameRenderer.class)
public class GameRendererTotemMixin {

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$noTotemAnim(ItemStack stack, CallbackInfo ci) {
        if (CleanView.noTotemAnim()) ci.cancel();
    }
}

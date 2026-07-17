package com.lume.client.mixin;

import com.lume.client.module.modules.qol.LockSlot;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lock Slot — cancels dropping the selected hotbar slot's item when that slot is the one locked. */
@Mixin(ClientPlayerEntity.class)
public class LockSlotMixin {

    @Inject(method = "dropSelectedItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$lockSlot(boolean entireStack, CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (LockSlot.lockedSlotSelected(self.getInventory().selectedSlot)) {
            cir.setReturnValue(false);
        }
    }
}

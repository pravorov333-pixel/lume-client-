package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.ItemPhysics;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

/** Item Physics — extra fall gravity + ground bounce for dropped items (client-side visual only). */
@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    private static final Map<ItemEntity, Float> lume$prevVy = new WeakHashMap<>();

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void lume$extraGravity(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        Module m = LumeClient.MODULES.getByName("Item Physics");
        if (!(m instanceof ItemPhysics ip) || !ip.isEnabled() || self.isOnGround()) return;
        double extraG = (ip.gravity.value - 1.0) * 0.04;   // vanilla item gravity is ~0.04 blocks/tick²
        if (extraG != 0) {
            Vec3d v = self.getVelocity();
            self.setVelocity(v.x, v.y - extraG, v.z);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void lume$bounce(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        Module m = LumeClient.MODULES.getByName("Item Physics");
        if (!(m instanceof ItemPhysics ip) || !ip.isEnabled() || ip.bounce.value <= 0.01) {
            lume$prevVy.remove(self);
            return;
        }
        double vy = self.getVelocity().y;
        Float prev = lume$prevVy.get(self);
        // was falling last tick, now resting on the ground this tick → bounce it back up
        if (prev != null && prev < -0.08f && self.isOnGround() && vy >= -0.01 && vy <= 0.01) {
            Vec3d v = self.getVelocity();
            self.setVelocity(v.x, -prev * ip.bounce.value, v.z);
        }
        lume$prevVy.put(self, (float) vy);
    }
}

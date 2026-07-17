package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.ItemPhysics;
import com.lume.client.util.ItemPhysicsEntityCarrier;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Item Physics — real-world-style drop: while airborne the item tumbles on
 * all three axes (yaw/pitch/roll, each at its own per-item random rate so a
 * pile of drops doesn't spin in lockstep) instead of vanilla's slow single-
 * axis float-spin; the INSTANT it touches the ground the tumble freezes at
 * whatever angle it happened to be at — so it comes to rest lying at a
 * natural, varied angle instead of standing perfectly upright and endlessly
 * spinning. The actual draw (skipping vanilla's own bob/spin and applying
 * this instead) happens in {@code ItemEntityRendererMixin}.
 */
@Mixin(ItemEntity.class)
public class ItemEntityMixin implements ItemPhysicsEntityCarrier {

    /** Pitch that makes an item lie flat instead of standing upright in this render path — see the
     *  landing logic below. If it lands on its edge instead of flat, this is the sign to flip. */
    private static final float LANDED_PITCH = 90f;

    @Unique private float lume$yaw, lume$pitch, lume$roll;
    @Unique private float lume$yawRate, lume$pitchRate, lume$rollRate;
    @Unique private boolean lume$landed;
    @Unique private int lume$seededFor = Integer.MIN_VALUE;

    @Override public float lume$tumbleYaw() { return lume$yaw; }
    @Override public float lume$tumblePitch() { return lume$pitch; }
    @Override public float lume$tumbleRoll() { return lume$roll; }
    @Override public boolean lume$isLanded() { return lume$landed; }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void lume$physics(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        Module m = LumeClient.MODULES.getByName("Item Physics");
        if (!(m instanceof ItemPhysics) || !m.isEnabled()) { lume$landed = false; return; }

        if (lume$seededFor != self.getId()) {
            lume$seededFor = self.getId();
            Random rnd = new Random(self.getId());
            lume$yawRate = (8f + rnd.nextFloat() * 10f) * (rnd.nextBoolean() ? 1 : -1);
            lume$pitchRate = (5f + rnd.nextFloat() * 8f) * (rnd.nextBoolean() ? 1 : -1);
            lume$rollRate = (5f + rnd.nextFloat() * 8f) * (rnd.nextBoolean() ? 1 : -1);
            lume$yaw = rnd.nextFloat() * 360f;
            lume$pitch = rnd.nextFloat() * 360f;
            lume$roll = rnd.nextFloat() * 360f;
        }

        if (self.isOnGround()) {
            // pitch=roll=0 turned out to be the model's UPRIGHT/facing-camera pose in this render
            // path, not flat-on-ground (that's what actually caused the "stands up" bug) — a 90°
            // pitch is what tips it over to lie flat instead. Ease toward that on landing; yaw
            // (spin around the vertical axis) is left alone since it never tilts it off the ground.
            if (!lume$landed) {
                lume$pitch = LANDED_PITCH + wrap(lume$pitch - LANDED_PITCH);
                lume$roll = wrap(lume$roll);
            }
            lume$landed = true;
            lume$pitch = LANDED_PITCH + (lume$pitch - LANDED_PITCH) * 0.7f;
            lume$roll *= 0.7f;
        } else {
            lume$landed = false;
            lume$yaw += lume$yawRate;
            lume$pitch += lume$pitchRate;
            lume$roll += lume$rollRate;
        }
    }

    /** Normalizes a possibly-huge accumulated tumble angle into -180..180 so the
     *  settle-to-flat ease above takes the shortest path instead of spinning down. */
    @Unique
    private static float wrap(float deg) {
        deg %= 360f;
        if (deg > 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }
}

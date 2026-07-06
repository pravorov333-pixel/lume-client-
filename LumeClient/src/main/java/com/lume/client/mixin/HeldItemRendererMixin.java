package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.Animations;
import com.lume.client.module.modules.render.CustomHand;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Hand (position/scale/rotation) + Animations (swing-driven presets). */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void lume$customHand(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                 float swingProgress, ItemStack item, float equipProgress,
                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                 CallbackInfo ci) {
        // Custom Hand — static offset / rotation / scale
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (chM instanceof CustomHand ch && ch.isEnabled()) {
            matrices.translate(ch.posX.value, ch.posY.value, ch.posZ.value);
            if (ch.rotX.value != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) ch.rotX.value));
            if (ch.rotY.value != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) ch.rotY.value));
            if (ch.rotZ.value != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) ch.rotZ.value));
            float s = (float) ch.scale.value;
            if (s != 1f) matrices.scale(s, s, s);
        }

        // Hand Animations — static rotation preset (always visible, not swing-gated)
        Module anM = LumeClient.MODULES.getByName("Hand Animations");
        if (anM instanceof Animations an && an.isEnabled()) {
            float a = (float) an.angle.value;
            switch (an.preset.index) {
                case 0 -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a));   // Toward You
                case 1 -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-a));  // Away
                case 2 -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a));   // Tilt In
                case 3 -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-a));  // Tilt Out
                case 4 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-a));  // Vertical
                case 5 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a));   // Flat
                case 6 -> matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180)); // Reverse
                case 7 -> {   // Custom — full free rotation, turn the hand to any side
                    if (an.customRotX.value != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) an.customRotX.value));
                    if (an.customRotY.value != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) an.customRotY.value));
                    if (an.customRotZ.value != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) an.customRotZ.value));
                }
                // case 8 "None" — no static rotation, dynamic effects below still apply
            }

            // --- dynamic motion: sway / idle bob / sprint bob / swing ease ---
            long now = System.nanoTime();
            float dt = (float) Math.min(0.1, (now - lastNanos) / 1e9);
            lastNanos = now;

            float yaw = player.getYaw(), pitchNow = player.getPitch();
            if (Float.isNaN(prevYaw)) { prevYaw = yaw; prevPitch = pitchNow; }
            float dYaw = MathHelper.wrapDegrees(yaw - prevYaw);
            float dPitch = pitchNow - prevPitch;
            prevYaw = yaw; prevPitch = pitchNow;

            if (an.sway.value) {
                float amt = (float) an.swayAmount.value;
                float followRate = Math.min(1f, dt * 10f);
                swayYaw += (-dYaw * 0.15f * amt - swayYaw) * followRate;
                swayPitch += (-dPitch * 0.15f * amt - swayPitch) * followRate;
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swayYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swayPitch));
            }

            float t = (System.currentTimeMillis() % 100000L) / 1000f;
            boolean moving = player.getVelocity().horizontalLength() > 0.01;
            if (an.idleBob.value && !moving && player.isOnGround()) {
                matrices.translate(0, Math.sin(t * 2.0) * 0.010, 0);
            }
            if (an.sprintBob.value && player.isSprinting()) {
                matrices.translate(0, Math.sin(t * 12.0) * 0.03, 0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(3f));
            }
            if (an.swingEase.value && swingProgress > 0f) {
                float eased = 1f - (1f - swingProgress) * (1f - swingProgress);   // ease-out quad
                matrices.translate(0, 0, -eased * 0.08);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-eased * 12f));
            }
        }
    }

    // dynamic-animation smoothing state (single local player, static is fine)
    private static float prevYaw = Float.NaN, prevPitch = Float.NaN;
    private static float swayYaw = 0f, swayPitch = 0f;
    private static long lastNanos = System.nanoTime();
}

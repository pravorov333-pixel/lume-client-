package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.CustomHand;
import com.lume.client.util.ForcedColorVertexConsumer;
import com.lume.client.util.HandGeometryPivot;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Hand — static position/scale (per hand, ALWAYS applied) + a named Style rotation
 *  for the right hand only, applied around a pivot computed straight from the held item's
 *  own baked mesh (see HandGeometryPivot) — not a manually tuned slider, not vanilla's
 *  per-item "how to hold this" hint. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow private void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress) { throw new AssertionError(); }
    @Shadow private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) { throw new AssertionError(); }

    // Sway state (see applySway) — instance fields on the mixin, which is woven into MC's
    // single HeldItemRenderer instance, so this genuinely persists frame-to-frame like any
    // other per-renderer state would.
    private float lume$swayYaw, lume$swayPitch;
    private float lume$lastYaw = Float.NaN, lume$lastPitch = Float.NaN;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), require = 0)
    private void lume$customHand(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                 float swingProgress, ItemStack item, float equipProgress,
                                 MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                 CallbackInfo ci) {
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (!(chM instanceof CustomHand ch) || !ch.isEnabled()) return;
        boolean right = hand == Hand.MAIN_HAND;

        double px = right ? ch.rPosX.value : ch.lPosX.value;
        double py = right ? ch.rPosY.value : ch.lPosY.value;
        double pz = right ? ch.rPosZ.value : ch.lPosZ.value;
        matrices.translate(px, py, pz);

        // Right hand only — Style's fixed rotation, around the held item's own mesh centre
        // (HandGeometryPivot) so it never visibly swings/drifts, just rotates in place.
        if (right) {
            Vector3f center = HandGeometryPivot.center(item);
            float[] rot = ch.styleRot();
            matrices.translate(center.x, center.y, center.z);
            if (rot[0] != 0) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rot[0]));
            if (rot[1] != 0) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rot[1]));
            if (rot[2] != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot[2]));
            matrices.translate(-center.x, -center.y, -center.z);
        }
    }

    /**
     * "No Animation" skips vanilla's own swing-bob method entirely. THIS TARGETS {@code
     * swingArm}, not {@code renderFirstPersonItem} — confirmed via bytecode that {@code
     * applySwingOffset}/{@code applyEquipOffset} are actually invoked from {@code swingArm}
     * (called earlier in the render pipeline, its transforms already baked into the shared
     * MatrixStack by the time {@code renderFirstPersonItem} runs). The previous version of this
     * redirect targeted {@code renderFirstPersonItem}, where neither call exists at all, so it
     * silently matched nothing (require=0) and vanilla's full swing kept playing regardless of
     * the Animation setting — the actual cause of every non-Default style looking broken.
     */
    @Redirect(method = "swingArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applySwingOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"),
            require = 0)
    private void lume$skipSwingOffset(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float swingProgress) {
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (chM instanceof CustomHand ch && ch.freezeSwing()) return;
        applySwingOffset(matrices, arm, swingProgress);
    }

    /** Equip (raise/lower on item switch) bob — {@code swingArm} calls it once too. */
    @Redirect(method = "swingArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"),
            require = 0)
    private void lume$skipEquipOffsetSwingArm(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float equipProgress) {
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (chM instanceof CustomHand ch && ch.freezeSwing()) return;
        applyEquipOffset(matrices, arm, equipProgress);
    }

    /** {@code applyEquipOffset} is ALSO called several times directly inside
     *  {@code renderFirstPersonItem} itself (confirmed via bytecode — unlike {@code
     *  applySwingOffset}, which ISN'T) — this one genuinely was matching something before and
     *  still needs to, so it stays targeting this method. */
    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipOffset(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/Arm;F)V"),
            require = 0)
    private void lume$skipEquipOffset(HeldItemRenderer instance, MatrixStack matrices, Arm arm, float equipProgress) {
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (chM instanceof CustomHand ch && ch.freezeSwing()) return;
        applyEquipOffset(matrices, arm, equipProgress);
    }

    /** Scale AND the swing/hit Animation both apply here, right before the item mesh
     *  actually renders — i.e. AFTER vanilla's own eat/drink/bow/spear/swing/equip
     *  transforms further up in this same method. Deliberately last: anything applied
     *  earlier (the HEAD injection above) can still get bobbed/offset by vanilla's OWN
     *  later code, which is exactly why the animation styles were invisible before —
     *  they were being applied, then vanilla's own swing offset ran afterward and moved
     *  things again on top. Being the very last transform means nothing downstream can
     *  mask it. (Scale specifically also needs to be here rather than at HEAD so it
     *  doesn't also scale vanilla's own absolute-unit offsets, e.g. bow pull distance.) */
    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),
            require = 0)
    private void lume$customHandScaleAndAnimation(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                       float swingProgress, ItemStack item, float equipProgress,
                                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                       CallbackInfo ci) {
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (!(chM instanceof CustomHand ch) || !ch.isEnabled()) return;
        boolean right = hand == Hand.MAIN_HAND;
        float s = (float) (right ? ch.rScale.value : ch.lScale.value);
        if (s != 1f) matrices.scale(s, s, s);
        applySway(matrices, ch, player, tickDelta);
        applyAnimation(matrices, ch, swingProgress, item);

        // Outline / Fill — each is a SEPARATE extra render pass through a forced-flat-colour
        // VertexConsumerProvider (see lume$renderFlat), not a RenderSystem.setShaderColor tint.
        // A multiply tint (texture * colour) can never produce a true flat white — texture *
        // 0xFFFFFF is the identity multiply, so white silently did nothing. This instead blends
        // a solid near-opaque copy of the item OVER the real one through
        // RenderLayer.getEntityTranslucentEmissiveNoOutline (a normal alpha-blended layer, used
        // vanilla-side for glowing eyes etc.) with every vertex colour forced to the target hue,
        // so white genuinely reads as white. Outline additionally enlarges that copy ~12% around
        // the item's own centre and draws it BEFORE the real (normal, untouched) render, so a
        // thin rim of it peeks out past the real silhouette.
        if (ch.outline.value || ch.fill.index != 0) {
            HeldItemRenderer self = (HeldItemRenderer) (Object) this;
            boolean mainIsRight = player.getMainArm() == Arm.RIGHT;
            boolean effectiveRight = right == mainIsRight;
            ModelTransformationMode mode = effectiveRight ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
            boolean leftHanded = !effectiveRight;

            if (ch.outline.value) {
                matrices.push();
                Vector3f c = HandGeometryPivot.center(item);
                matrices.translate(c.x, c.y, c.z);   // enlarge around the item's own centre, not the hand origin
                matrices.scale(1.12f, 1.12f, 1.12f);
                matrices.translate(-c.x, -c.y, -c.z);
                lume$renderFlat(self, player, item, mode, leftHanded, matrices, vertexConsumers, light, ch.outlineRgb());
                matrices.pop();
            }
            if (ch.fill.index != 0) {
                lume$renderFlat(self, player, item, mode, leftHanded, matrices, vertexConsumers, light, ch.fillRgb());
            }
            // Vanilla's own (real, untouched) renderItem call still runs immediately after this
            // method returns, drawing the normal textured item on top of whatever we just added.
        }
    }

    // Items share the block atlas since 1.19 (no separate items.png any more) — same texture
    // regardless of which specific item is being recoloured, so one fixed identifier works for all.
    private static final Identifier ITEM_ATLAS = Identifier.ofVanilla("textures/atlas/blocks.png");

    private static void lume$renderFlat(HeldItemRenderer self, AbstractClientPlayerEntity player, ItemStack item,
                                         ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                         VertexConsumerProvider realVcp, int light, int rgb) {
        RenderLayer layer = RenderLayer.getEntityTranslucentEmissiveNoOutline(ITEM_ATLAS);
        VertexConsumer forced = new ForcedColorVertexConsumer(realVcp.getBuffer(layer), 0xE6000000 | rgb);
        VertexConsumerProvider wrapper = l -> forced;
        self.renderItem(player, item, mode, leftHanded, matrices, wrapper, light);
        if (realVcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    /**
     * The swing/hit animation — always runs (both hands), purely additive on top of whatever
     * Style set up. Default adds nothing (vanilla's own swing/equip bob plays normally). Every
     * other choice relies on {@link CustomHand#freezeSwing()} having already frozen vanilla's own
     * bob (see the two @Redirect hooks above) — otherwise vanilla's dip-then-return was still
     * running underneath and fighting whatever got added here, which is why every non-Default
     * style used to look broken. Simple/Spin rotate around the item's own mesh pivot (same
     * {@link HandGeometryPivot} the Style rotation uses) so the item visibly stays in place and
     * rotates rather than orbiting some off-center point.
     */
    private static void applyAnimation(MatrixStack matrices, CustomHand ch, float swingProgress, ItemStack item) {
        // Same hump vanilla's own applySwingOffset uses for its dominant rotation term (confirmed
        // via bytecode: RotationAxis.POSITIVE_X.rotationDegrees(g * -80f), g = sin(sqrt(p)*pi)) —
        // reused here on purpose so "Simple"/"Spin" tilt the same way vanilla's swing does, just
        // without vanilla's other Y/Z "guard stance" terms mixed in (now that swingArm's own call
        // is frozen, see the @Redirects above).
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * 3.1415927F);
        switch (ch.animation.index) {
            case CustomHand.ANIM_DEFAULT -> {   // tilt LEFT and back, in place — the item never moves spatially
                Vector3f c = HandGeometryPivot.center(item);
                matrices.translate(c.x, c.y, c.z);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(g * 45f));
                matrices.translate(-c.x, -c.y, -c.z);
            }
            case CustomHand.ANIM_SIMPLE -> {   // forward tilt and back, in place
                Vector3f c = HandGeometryPivot.center(item);
                matrices.translate(c.x, c.y, c.z);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -80f));
                matrices.translate(-c.x, -c.y, -c.z);
            }
            case CustomHand.ANIM_SPIN -> {   // full forward cartwheel in place — same pivot, full turn
                Vector3f c = HandGeometryPivot.center(item);
                matrices.translate(c.x, c.y, c.z);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress * -360f));
                matrices.translate(-c.x, -c.y, -c.z);
            }
            case CustomHand.ANIM_USE ->      // downward nudge and back ONLY — the one animation allowed to translate
                    matrices.translate(0.0, -g * 0.16, 0.0);
            default -> { /* No Animation */ }
        }
    }

    /**
     * Continuous idle bob + sprint bob + camera-turn sway — the "smooth viewmodel sway"
     * this client always claimed to have but never actually built (confirmed nothing like
     * this existed anywhere in the codebase before this method). Purely additive, applied
     * BEFORE the swing animation so a swing's much bigger motion always reads as dominant
     * over this ambient layer rather than fighting it.
     *
     * <p>Idle/sprint bob: a small sine wave in player.age (ticks alive), so it's perfectly
     * smooth across frames regardless of framerate — faster/bigger while sprinting.
     * Camera-turn sway: an exponentially-smoothed lag opposite the frame's yaw/pitch delta,
     * decaying back to neutral — the classic "weapon lags a beat behind a fast turn" feel.
     * lastYaw/lastPitch start as NaN so the very first frame after (re)joining a world
     * contributes zero delta instead of one huge spurious jump from an uninitialised 0.
     */
    private void applySway(MatrixStack matrices, CustomHand ch, AbstractClientPlayerEntity player, float tickDelta) {
        if (!ch.sway.value) return;

        float t = (player.age + tickDelta) / 20f;   // ticks → seconds, smooth across frames
        boolean sprinting = player.isSprinting();
        float bobFreq = sprinting ? 3.2f : 1.4f;
        float bobAmp = sprinting ? 0.02f : 0.008f;
        matrices.translate(0.0, MathHelper.sin(t * bobFreq) * bobAmp, 0.0);
        if (sprinting) matrices.translate(MathHelper.sin(t * bobFreq * 0.5f) * 0.01f, 0.0, 0.0);

        float yaw = player.getYaw(tickDelta), pitch = player.getPitch(tickDelta);
        if (!Float.isNaN(lume$lastYaw)) {
            float dYaw = MathHelper.wrapDegrees(yaw - lume$lastYaw);
            float dPitch = pitch - lume$lastPitch;
            lume$swayYaw = MathHelper.clamp(lume$swayYaw * 0.85f - dYaw * 0.12f, -6f, 6f);
            lume$swayPitch = MathHelper.clamp(lume$swayPitch * 0.85f - dPitch * 0.12f, -6f, 6f);
        }
        lume$lastYaw = yaw; lume$lastPitch = pitch;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(lume$swayYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lume$swayPitch));
    }
}

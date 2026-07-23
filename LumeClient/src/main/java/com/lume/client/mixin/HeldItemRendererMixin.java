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

    /**
     * The FIRST thing {@code swingArm} does is a raw {@code translate(arm * -0.4·sin, …, …)}
     * (confirmed via bytecode) BEFORE it ever calls applySwingOffset/applyEquipOffset — that
     * {@code arm}-signed X term is the sideways lunge you see on every hit, and it is NOT part
     * of either offset method, so cancelling those two never touched it. This is exactly the
     * "меч сдвигается влево при ударе" for No Animation / Use. Skip this translate whenever the
     * module is on (all animations, Default included): it keeps every custom animation purely
     * in place, and stops Default's own swing from flinging a repositioned item off-screen.
     */
    @Redirect(method = "swingArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"),
            require = 0)
    private void lume$skipSwingLunge(MatrixStack matrices, float x, float y, float z) {
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (chM instanceof CustomHand ch && ch.isEnabled()) return;   // drop the lunge entirely
        matrices.translate(x, y, z);
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
    }

    /** Screen-space directions the Outline silhouettes are offset in (8-way ring, unit-ish). */
    private static final float[][] OUTLINE_DIRS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
            { 0.7f, 0.7f }, { -0.7f, 0.7f }, { 0.7f, -0.7f }, { -0.7f, -0.7f }
    };
    private static final float OUTLINE_WIDTH = 0.012f;   // in hand-space units; ~a thin rim at default scale

    /**
     * Outline / Fill, replacing vanilla's own renderItem call for this hand.
     *
     * <p><b>Outline</b> is a real rim, not a second sword: the item is drawn 8 times, flat-coloured
     * and each copy nudged a hair in a different SCREEN-space direction, all BEFORE the real item
     * — then the real item draws on top and covers the middle, leaving only a uniform line peeking
     * out around the silhouette. (The previous version scaled one copy up 12%, which is why it read
     * as a bigger sword behind the real one rather than an outline: a centre-scale grows the far
     * parts much more than the near ones.)
     *
     * <p><b>Fill</b> REPLACES the real render rather than drawing under it — the old version drew
     * the flat copy and then let vanilla paint the fully-textured item straight over the top, so
     * the fill was always completely hidden. That's why Fill "did nothing" on its own.
     *
     * <p>Flat colour is drawn via {@link com.lume.client.util.ItemFlatFill} — the item's REAL
     * textured render (real geometry, real texture, real alpha cutout, real {@code RenderLayer}),
     * with only each vertex's colour intercepted via {@link ForcedColorVertexConsumer}. Two
     * earlier approaches both failed for different reasons — see {@code ItemFlatFill}'s class doc
     * for the full story (a manually-picked {@code RenderLayer} skipped the model's own transform
     * entirely, and a later raw colour-only draw discarded the texture/alpha and always rendered
     * as a plain rectangle regardless of position).
     */
    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"),
            require = 0)
    private void lume$outlineAndFill(HeldItemRenderer self, LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                                     boolean leftHanded, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        Module chM = LumeClient.MODULES.getByName("Custom Hand");
        if (!(chM instanceof CustomHand ch) || !ch.isEnabled() || (!ch.outline.value && ch.fill.index == 0)) {
            self.renderItem(entity, stack, mode, leftHanded, matrices, vcp, light);   // untouched vanilla path
            return;
        }

        if (ch.outline.value) {
            int argb = 0xFF000000 | ch.outlineRgb();   // always fully opaque — Fill Opacity only applies to Fill
            for (float[] d : OUTLINE_DIRS) {
                matrices.push();
                matrices.translate(d[0] * OUTLINE_WIDTH, d[1] * OUTLINE_WIDTH, 0.0);
                com.lume.client.util.ItemFlatFill.draw(matrices, entity, stack, mode, vcp, light, (x, y, z) -> argb);
                matrices.pop();
            }
        }

        if (ch.fill.index != 0) {
            int a = Math.max(0, Math.min(255, (int) Math.round(ch.fillOpacity.value * 255)));
            if (ch.fill.index >= 2) {
                // Cosmos / Swirl / Starfield / Worms — per-vertex procedural colour instead of one
                // flat argb. Position is already fully hand-transformed (see ItemFlatFill) —
                // plenty of spatial variation for any of these to play out across.
                double t = System.currentTimeMillis() / 1000.0;
                int fillIdx = ch.fill.index;
                com.lume.client.util.ItemFlatFill.draw(matrices, entity, stack, mode, vcp, light, (x, y, z) -> switch (fillIdx) {
                    case 3 -> com.lume.client.fx.ProceduralFill.swirl(x * 4, y * 4, z * 4, t, a);
                    case 4 -> com.lume.client.fx.ProceduralFill.starfield(x * 4, y * 4, z * 4, t, a);
                    case 5 -> com.lume.client.fx.ProceduralFill.worms(x * 4, y * 4, z * 4, t, a);
                    default -> com.lume.client.fx.ProceduralFill.cosmos(x * 4, y * 4, z * 4, t, a);
                });
            } else {
                int argb = (a << 24) | ch.fillRgb();
                com.lume.client.util.ItemFlatFill.draw(matrices, entity, stack, mode, vcp, light, (x, y, z) -> argb);
            }
        } else {
            self.renderItem(entity, stack, mode, leftHanded, matrices, vcp, light);   // real item over the outline
        }
    }

    /**
     * The swing/hit animation — always runs (both hands), purely additive on top of whatever
     * Style set up.
     *
     * <p><b>Default</b> deliberately adds nothing AND doesn't freeze vanilla ({@link
     * CustomHand#freezeSwing()} returns false for it), so it's vanilla's normal animation,
     * movement included. Every other choice freezes vanilla's own bob first (see the @Redirect
     * hooks above) and then rotates the item IN PLACE around its own mesh pivot ({@link
     * HandGeometryPivot}) — so the sword pivots where it stands instead of travelling through
     * space. Use is the single exception that translates, and only straight down/back on Y.
     *
     * <p>Rotation sign is NEGATIVE on X — the same sign vanilla's own {@code applySwingOffset}
     * uses for its dominant term (confirmed via bytecode: {@code POSITIVE_X.rotationDegrees(g *
     * -80f)}), which is the actual, known-correct "swings/dips down and forward" direction
     * everyone recognises from vanilla combat. An earlier version of this flipped the sign to
     * make "the blade lead instead of the handle", but that reasoning was never actually
     * confirmed visually and produced the opposite of a downward dip — reverted.
     */
    private static void applyAnimation(MatrixStack matrices, CustomHand ch, float swingProgress, ItemStack item) {
        // Same 0→1→0 hump vanilla's own applySwingOffset uses (confirmed via bytecode:
        // g = sin(sqrt(swingProgress) * pi)), so the timing/feel of the swing matches vanilla's
        // even though the pivot here is ours (vanilla rotates from wherever the matrix stack
        // already is, which is why its own swing drags the whole item through space; pivoting on
        // the item's own mesh point is what keeps ours in place instead).
        float g = MathHelper.sin(MathHelper.sqrt(swingProgress) * 3.1415927F);
        float angle = (float) ch.swingAngle.value;
        switch (ch.animation.index) {
            case CustomHand.ANIM_SIMPLE -> {   // direct hand-space pivot (see CustomHand#swingPivotY) — NOT a
                                                // fraction of the item's own mesh bounds any more. That mesh is
                                                // tiny/degenerate for generated-model items (confirmed via debug
                                                // log), so even a full 0..1 drag barely moved anything once
                                                // downstream transforms scaled it down — a direct offset in the
                                                // same units Pos already uses has real, visible range instead.
                float py = (float) ch.swingPivotY.value;
                matrices.translate(0.0, py, 0.0);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -angle));
                matrices.translate(0.0, -py, 0.0);
            }
            case CustomHand.ANIM_TILT -> {   // whole sword rotates as one rigid piece around its OWN centre — blade end dips, grip end lifts, neither end fixed
                Vector3f c = HandGeometryPivot.center(item);
                matrices.translate(c.x, c.y, c.z);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g * -angle));
                matrices.translate(-c.x, -c.y, -c.z);
            }
            case CustomHand.ANIM_SPIN -> {   // full cartwheel, in place
                Vector3f c = HandGeometryPivot.center(item);
                matrices.translate(c.x, c.y, c.z);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress * -360f));
                matrices.translate(-c.x, -c.y, -c.z);
            }
            case CustomHand.ANIM_USE ->      // straight down and back on Y, nothing else — no tilt, no X/Z drift
                    matrices.translate(0.0, -g * 0.16, 0.0);
            default -> { /* Default (vanilla, unfrozen) / No Animation */ }
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

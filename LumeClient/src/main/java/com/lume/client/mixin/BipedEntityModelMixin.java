package com.lume.client.mixin;

import com.lume.client.module.modules.qol.FakePlayer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fake Player: forces Sitting / T-pose limb angles onto our own tracked fake entity, after
 * vanilla's own animation runs. Targets the {@code BipedEntityRenderState} overload specifically
 * — {@code PlayerEntityModel.setAngles(PlayerEntityRenderState)} calls
 * {@code super.setAngles(BipedEntityRenderState)} (confirmed via bytecode), never the generic
 * {@code EntityRenderState} bridge overload this used to target, which is why poses silently
 * never applied to players before.
 */
@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin {

    @Shadow public ModelPart rightArm;
    @Shadow public ModelPart leftArm;
    @Shadow public ModelPart rightLeg;
    @Shadow public ModelPart leftLeg;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("TAIL"), require = 0)
    private void lume$fakePlayerPose(BipedEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState prs)) return;
        int pose = FakePlayer.poseFor(prs.id);
        if (pose == 1) {                 // Sitting
            rightLeg.pitch = -1.5708f;
            leftLeg.pitch = -1.5708f;
        } else if (pose == 2) {          // T-pose
            rightArm.pitch = 0f; rightArm.yaw = 0f; rightArm.roll = 1.4f;
            leftArm.pitch = 0f; leftArm.yaw = 0f; leftArm.roll = -1.4f;
            rightLeg.pitch = 0f; leftLeg.pitch = 0f;
        }
    }
}

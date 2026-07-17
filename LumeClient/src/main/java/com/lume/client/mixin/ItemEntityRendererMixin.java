package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.ItemPhysics;
import com.lume.client.util.ItemPhysicsCarrier;
import com.lume.client.util.ItemPhysicsEntityCarrier;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Item Physics' tumble (computed by {@code ItemEntityMixin}) when a
 * dropped item is drawn, replacing vanilla's own bob/spin entirely: 1) after
 * vanilla populates the render state, copy the entity's live tumble onto it
 * (state persists per-entity across frames, so this is safe every frame);
 * 2) at render time, if Item Physics is on, cancel vanilla's own {@code
 * render} outright and draw the stack ourselves ({@code renderStack} is
 * public) with our own yaw/pitch/roll applied instead of vanilla's bob —
 * this is what actually lets it lie flat on the ground rather than keep
 * floating/spinning once landed, which a matrix rotation layered on TOP of
 * vanilla's own render (the previous approach) could never do.
 */
@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    private static final Random RANDOM = Random.create();

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void lume$copyTumble(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (!(state instanceof ItemPhysicsCarrier carrier)) return;
        Module m = LumeClient.MODULES.getByName("Item Physics");
        if (m instanceof ItemPhysics && m.isEnabled() && entity instanceof ItemPhysicsEntityCarrier ec) {
            carrier.lume$set(ec.lume$tumbleYaw(), ec.lume$tumblePitch(), ec.lume$tumbleRoll(), ec.lume$isLanded());
        } else {
            carrier.lume$set(0f, 0f, 0f, false);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customRender(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (!(state instanceof ItemPhysicsCarrier carrier)) return;
        Module m = LumeClient.MODULES.getByName("Item Physics");
        if (!(m instanceof ItemPhysics) || !m.isEnabled()) return;   // fall through to vanilla's own render

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(carrier.lume$getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(carrier.lume$getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(carrier.lume$getRoll()));
        ItemEntityRenderer.renderStack(matrices, vertexConsumers, light, state, RANDOM);
        matrices.pop();
        ci.cancel();
    }
}

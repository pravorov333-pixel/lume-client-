package com.lume.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the package-private {@code model} field — see HandGeometryPivot.
 *  Also exposes the package-private {@code render(...)} method, which is what actually applies
 *  the model's {@code Transformation} (the FIRST_PERSON_RIGHT_HAND scale/rotate/translate) and
 *  the {@code -0.5,-0.5,-0.5} centering translate before emitting quads — confirmed via javap on
 *  {@code ItemRenderState$LayerRenderState.render(...)}. {@code ItemFlatFill} used to extract raw
 *  {@code BakedModel} quads itself and skip both of those, which is exactly why the hand fill/
 *  outline rendered as a giant, tilted, mis-centered plane: the quads were correct model data,
 *  just never scaled/rotated/centered into hand space at all. Routing through the real {@code
 *  render(...)} (with a custom {@code VertexConsumerProvider} that just captures final vertex
 *  positions) gets that transform for free and can't drift from whatever vanilla does. */
@Mixin(ItemRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("model")
    BakedModel lume$getModel();

    @Invoker("render")
    void lume$render(MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay);
}

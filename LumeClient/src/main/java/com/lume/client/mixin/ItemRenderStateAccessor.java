package com.lume.client.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private {@code getFirstLayer()} — needed to reach the item's actual
 *  {@link net.minecraft.client.render.model.BakedModel} for HandGeometryPivot's own
 *  mesh bounding-box computation (see HandGeometryPivot). */
@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {
    @Invoker("getFirstLayer")
    ItemRenderState.LayerRenderState lume$getFirstLayer();
}

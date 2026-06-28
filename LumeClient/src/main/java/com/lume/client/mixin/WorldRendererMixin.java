package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.BlockOutline;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Recolours the block-selection outline (Block Outline module). */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    // drawBlockOutline calls the private drawCuboidShapeOutline(ms, vc, shape, x, y, z, r, g, b, a)
    @ModifyArgs(
            method = "drawBlockOutline",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawCuboidShapeOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/util/shape/VoxelShape;DDDFFFF)V"),
            require = 0)
    private void lume$recolorOutline(Args args) {
        int argb = BlockOutline.argbOrZero();
        if (argb == 0) return;
        args.set(6, ((argb >> 16) & 0xFF) / 255f);   // r
        args.set(7, ((argb >> 8) & 0xFF) / 255f);    // g
        args.set(8, (argb & 0xFF) / 255f);           // b
        args.set(9, Math.max((float) args.get(9), 0.8f)); // a (keep it visible)
    }
}

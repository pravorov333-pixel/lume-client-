package com.lume.client.mixin;

import com.lume.client.module.modules.render.CustomHitbox;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Custom Hitbox must fully replace vanilla's F3+B hitbox lines, not draw both
 * on top of each other — this redirects the one call site inside
 * {@code render()} that draws vanilla's hitbox to a no-op while our module is
 * active, so only Custom Hitbox's own lines (drawn separately in
 * WorldRenderEvents.AFTER_ENTITIES) end up visible.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Shadow
    private static native void renderHitbox(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, float red, float green, float blue, float alpha);

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitbox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;FFFF)V"),
            require = 0)
    private static void lume$skipVanillaHitbox(MatrixStack matrices, VertexConsumer vertexConsumer, Entity entity, float red, float green, float blue, float alpha) {
        if (!CustomHitbox.active()) {
            renderHitbox(matrices, vertexConsumer, entity, red, green, blue, alpha);
        }
    }
}

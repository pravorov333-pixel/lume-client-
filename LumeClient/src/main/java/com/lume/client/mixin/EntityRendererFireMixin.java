package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.CleanView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean View "No Fire" — suppresses the burning-model render flag, but only for yourself. */
@Mixin(EntityRenderer.class)
public class EntityRendererFireMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"), require = 0)
    private void lume$noFireSelf(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (CleanView.noFireSelf() && entity == MinecraftClient.getInstance().player) {
            state.onFire = false;
        }
    }
}

package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.DeathAnimations;
import com.lume.client.module.modules.render.HitColor;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HitColor — recolours the whole entity model like vanilla's white hurt flash,
 * but with a configurable colour, and with vanilla's own white flash suppressed
 * (so the two don't mix into a washed-out colour). Also drives Death Animations
 * (ragdoll spin + particle burst), reading the same render-state snapshot.
 *
 * <p>Two earlier attempts didn't actually recolour anything: (1) mixing into
 * {@code EntityRenderer.render} (the base class) only runs at the very END of
 * {@code LivingEntityRenderer.render}, after the model is already submitted;
 * (2) using {@code RenderSystem.setShaderColor} around the HEAD of the real
 * render method compiles and "looks right" but the entity shader in 1.21.4
 * doesn't multiply fragments by that global uniform — the model's colour comes
 * from an explicit {@code color} int argument baked per-vertex, computed by
 * {@code getMixColor(state)} and passed straight into
 * {@code EntityModel.render(matrices, vertexConsumer, light, overlay, color)}.
 * {@code @ModifyArg} on THAT argument is what actually recolours the model.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @ModifyArg(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"),
            index = 4, require = 0)
    private int lume$hitColorTint(int color, LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        if (state.hurt && hitColorOn()) {
            HitColor hc = hitColor();
            int rgb = hc.color.accent ? Theme.accentRgb() : hc.color.rgb();
            return 0xFF000000 | (rgb & 0xFFFFFF);
        }
        return color;
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void lume$deathAnimations(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        // Death Animations — extra ragdoll spin on top of vanilla's own death tilt,
        // plus a one-shot particle burst on the first tick of death.
        if (state.deathTime > 0f) {
            Module dm = LumeClient.MODULES.getByName("Death Animations");
            if (dm instanceof DeathAnimations da && da.isEnabled()) {
                if (da.ragdoll.value) {
                    float seed = (float) ((state.x * 37.1 + state.z * 13.7) % 360);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((state.deathTime * 30f + seed) % 360f));
                }
                if (da.burst.value && state.deathTime <= 1.5f) {
                    int rgb = da.color.accent ? Theme.accentRgb() : da.color.rgb();
                    ParticleEngine.burst(state.x, state.y + state.height / 2.0, state.z, rgb, da.count.getInt(), 1.2f, 0.3f, 0.6f);
                }
            }
        }
    }

    /** Suppresses vanilla's own white hurt-flash overlay while HitColor is active, so colours don't mix. */
    @Inject(method = "getOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lume$noVanillaFlash(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (state.hurt && hitColorOn()) cir.setReturnValue(OverlayTexture.DEFAULT_UV);
    }

    private static boolean hitColorOn() {
        Module m = LumeClient.MODULES.getByName("HitColor");
        return m instanceof HitColor hc && hc.isEnabled();
    }

    private static HitColor hitColor() {
        return (HitColor) LumeClient.MODULES.getByName("HitColor");
    }
}

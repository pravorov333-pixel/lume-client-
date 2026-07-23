package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.fx.DeathFx;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.DeathAnimations;
import com.lume.client.module.modules.render.HitColor;
import com.lume.client.util.ForcedColorVertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HitColor — recolours the whole entity model like vanilla's white hurt flash,
 * but with a configurable colour, and with vanilla's own white flash suppressed
 * (so the two don't mix into a washed-out colour). Also drives Death Animations:
 * once vanilla's own death tilt/render is cancelled, {@link DeathFx} takes over
 * with the chosen custom animation/particle style.
 *
 * <p><b>Why an extra render pass instead of tinting the real one:</b> the model's
 * real colour comes from an explicit per-vertex {@code color} int, multiplied
 * against the texture in the shader — so a MULTIPLY-based tint (an earlier
 * version of this mixin used {@code @ModifyArg} on that argument) can never
 * produce a true flat white: {@code texture * 0xFFFFFF} is the identity
 * multiply, i.e. picking white silently did nothing. Right after vanilla's own
 * (unmodified) model render, this draws the SAME model A SECOND TIME into
 * {@code RenderLayer.getEntityTranslucentEmissiveNoOutline(...)} through a
 * {@link ForcedColorVertexConsumer} that overrides every vertex colour to the
 * chosen hue — bound to a solid-WHITE 1x1 texture ({@link HeldItemRendererMixin}'s
 * white texture, reused here) instead of the entity's own skin. That second fix
 * matters just as much as the forced vertex colour: "emissive" only means "skip
 * world lighting", the shader still multiplies texture RGB × vertex colour, so
 * sampling the REAL skin texture reproduces the exact same white-is-identity bug
 * one layer down. A texture that's white everywhere makes texture × colour ==
 * colour exactly, for every colour, so white genuinely reads as white.
 *
 * <p><b>Death Animations, why {@code updateRenderState} is hooked too:</b>
 * {@code LivingEntityRenderState} doesn't carry the owning Entity (no id field
 * in 1.21.4's render-state split), so the death-time HEAD hook below — which
 * only sees the state — can't look up the real entity by itself. {@code
 * updateRenderState(entity, state, tickDelta)} gets BOTH every frame, so a
 * TAIL hook there just remembers the mapping in {@link DeathFx#trackState}.
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    private static LivingEntityRenderState lume$state;

    @Inject(method = "updateRenderState", at = @At("TAIL"), require = 0)
    private void lume$trackEntity(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        DeathFx.trackState(entity, state);
    }

    /** Fires right after vanilla's own model.render() call, while {@code matrices} is still in
     *  this entity's own local transformed space (its {@code push()}/{@code pop()} pair hasn't
     *  closed yet) — an @Inject at TAIL of the outer method would be too late, matrices would
     *  already be back to whatever it was before this entity's transform. */
    @Inject(method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void lume$hitColorRender(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (!state.hurt || !hitColorOn()) return;
        HitColor hc = hitColor();
        int rgb = (hc.color.accent ? Theme.accentRgb() : hc.color.rgb()) & 0xFFFFFF;
        @SuppressWarnings("rawtypes")
        LivingEntityRenderer self = (LivingEntityRenderer) (Object) this;
        @SuppressWarnings("unchecked")
        RenderLayer layer = RenderLayer.getEntityTranslucentEmissiveNoOutline(com.lume.client.util.FlatColorTexture.id());
        VertexConsumer forced = new ForcedColorVertexConsumer(vcp.getBuffer(layer), 0xFF000000 | rgb);
        @SuppressWarnings("unchecked")
        var model = self.getModel();
        model.render(matrices, forced, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$deathAnimations(LivingEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        lume$state = state;
        if (state.deathTime > 0f) {
            Module dm = LumeClient.MODULES.getByName("Death Animations");
            if (dm instanceof DeathAnimations da && da.isEnabled() && da.animation.index != 0) {
                DeathFx.onDeathFrame(state, da.animation.index);
                ci.cancel();   // vanilla tilt/render suppressed; DeathFx renders instead
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

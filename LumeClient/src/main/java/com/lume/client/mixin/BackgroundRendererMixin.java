package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.WorldCustomizer;
import net.minecraft.client.render.BackgroundRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World Customizer's Fog section — recolours the fog and customises its distance/density. */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void lume$fogColor(CallbackInfoReturnable<Vector4f> cir) {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        if (m instanceof WorldCustomizer f && f.isEnabled()) {
            int rgb = f.fogColor.accent ? com.lume.client.gui.Theme.accentRgb() : f.fogColor.rgb();
            Vector4f v = cir.getReturnValue();
            if (v != null) {
                v.x = ((rgb >> 16) & 0xFF) / 255f;
                v.y = ((rgb >> 8) & 0xFF) / 255f;
                v.z = (rgb & 0xFF) / 255f;
                cir.setReturnValue(v);
            }
        }
    }

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void lume$fogDistance(net.minecraft.client.render.Camera camera,
            net.minecraft.client.render.BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance,
            boolean thickFog, float tickDelta, CallbackInfoReturnable<net.minecraft.client.render.Fog> cir) {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        if (!(m instanceof WorldCustomizer f) || !f.isEnabled()) return;
        net.minecraft.client.render.Fog orig = cir.getReturnValue();
        if (orig == null) return;
        float mul = (float) f.fogDistance.value;
        float end = orig.end() * mul;
        // Scaling BOTH start and end by the same multiplier also shrinks the start→end
        // GRADIENT WIDTH by that same factor — at low Distance values the transition
        // band collapses to almost nothing, so fog reads as a hard-edged "wall"/circle
        // instead of a gradual fade. Real vanilla fog keeps a wide falloff band relative
        // to its distance; do the same here by deriving start as a fixed fraction of end
        // (clamped to a sane minimum band width) instead of scaling the original band.
        float band = Math.max(end * 0.55f, 4f);
        float start = Math.max(0f, end - band);
        // Density IS the fog's own alpha (how much it actually blends into the
        // view at full strength) — 1 = normal vanilla fog, 0 = doesn't obscure
        // anything at all. Squeezing the start/end range instead (the old
        // approach) barely changed how the fog LOOKED, hence "doesn't work".
        float alpha = orig.alpha() * (float) f.fogDensity.value;
        cir.setReturnValue(new net.minecraft.client.render.Fog(start, end, orig.shape(), orig.red(), orig.green(), orig.blue(), alpha));
    }
}

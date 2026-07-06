package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.Fog;
import net.minecraft.client.render.BackgroundRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fog module — recolours the fog. */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void lume$fogColor(CallbackInfoReturnable<Vector4f> cir) {
        Module m = LumeClient.MODULES.getByName("Fog");
        if (m instanceof Fog f && f.isEnabled()) {
            int rgb = f.color.rgb();
            Vector4f v = cir.getReturnValue();
            if (v != null) {
                v.x = ((rgb >> 16) & 0xFF) / 255f;
                v.y = ((rgb >> 8) & 0xFF) / 255f;
                v.z = (rgb & 0xFF) / 255f;
                cir.setReturnValue(v);
            }
        }
    }
}

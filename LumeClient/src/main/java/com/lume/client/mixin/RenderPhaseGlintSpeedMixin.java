package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.EnchantGlint;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Enchant Glint "Normal" style — animation speed. The vanilla glint UV scroll in
 * {@code RenderPhase.setupGlintTexturing(float)} is driven straight off {@link Util#getMeasuringTimeMs()};
 * scaling that time scales the scroll rate. At Speed=1 the value is untouched (identical to vanilla);
 * this only kicks in while Enchant Glint is on and set to Normal, so all other glint is unaffected.
 */
@Mixin(RenderPhase.class)
public class RenderPhaseGlintSpeedMixin {

    @Redirect(method = "setupGlintTexturing",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMeasuringTimeMs()J"),
            require = 0)
    private static long lume$scaleGlintTime() {
        long t = Util.getMeasuringTimeMs();
        Module m = LumeClient.MODULES.getByName("Enchant Glint");
        if (m instanceof EnchantGlint eg && eg.isEnabled() && eg.style.index == EnchantGlint.STYLE_NORMAL) {
            return (long) (t * eg.speed.value);
        }
        return t;
    }
}

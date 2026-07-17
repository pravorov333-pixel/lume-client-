package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomDeathScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Death Screen: dark glass gradient instead of vanilla's flat red scrim. Buttons reskinned via PressableWidgetMixin. */
@Mixin(DeathScreen.class)
public class DeathScreenMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$customBackground(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Module m = LumeClient.MODULES.getByName("Custom Death Screen");
        if (!(m instanceof CustomDeathScreen cds) || !cds.isEnabled()) return;
        Screen self = (Screen) (Object) this;
        ctx.fillGradient(0, 0, self.width, self.height, 0xE0240E0E, 0xF00A0505);
        ci.cancel();
    }
}

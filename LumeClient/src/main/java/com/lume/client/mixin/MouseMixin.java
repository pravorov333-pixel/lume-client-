package com.lume.client.mixin;

import com.lume.client.module.modules.render.FreeLook;
import com.lume.client.util.ClickTracker;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the CPS counter by recording left/right mouse-button presses. Also handles FreeLook mouse redirect. */
@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), require = 0)
    private void lume$trackClicks(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) ClickTracker.leftClick();
        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) ClickTracker.rightClick();
    }

    @Redirect(method = "updateMouse", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void lume$freeLookRedirect(ClientPlayerEntity player, double dx, double dy) {
        if (FreeLook.active) {
            FreeLook.freeYaw   += (float) dx;
            FreeLook.freePitch  = Math.max(-90f, Math.min(90f, FreeLook.freePitch + (float) dy));
        } else {
            player.changeLookDirection(dx, dy);
        }
    }
}

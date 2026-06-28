package com.lume.client.mixin;

import com.lume.client.util.ClickTracker;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the CPS counter by recording left/right mouse-button presses. */
@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), require = 0)
    private void lume$trackClicks(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) ClickTracker.leftClick();
        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) ClickTracker.rightClick();
    }
}

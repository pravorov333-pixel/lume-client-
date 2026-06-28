package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.command.MacroManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes raw key presses to module binds and macros (only in-world, no screen open). */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), require = 0)
    private void lume$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (action == GLFW.GLFW_PRESS) {
            if (mc.currentScreen != null || mc.player == null) return;
            LumeClient.MODULES.onKey(key, true);
            com.lume.client.fthw.HelperBinds.onKey(key);   // FT/HW helper sub-function binds
            MacroManager.onKey(key);
        } else if (action == GLFW.GLFW_RELEASE) {
            LumeClient.MODULES.onKey(key, false);   // always release HOLD binds
        }
    }
}

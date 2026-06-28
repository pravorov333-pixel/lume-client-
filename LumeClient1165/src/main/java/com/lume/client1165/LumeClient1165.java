package com.lume.client1165;

import com.lume.client1165.gui.ClickGuiScreen;
import com.lume.client1165.gui.HudRenderer;
import com.lume.client1165.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Lume Client — 1.16.5 build entry point. Logic + render layer (MatrixStack):
 * modules/settings, the glass ClickGUI (Right Shift) and the HUD overlay.
 */
public class LumeClient1165 implements ClientModInitializer {

    public static final ModuleManager MODULES = new ModuleManager();

    private boolean guiKeyDown = false;

    @Override
    public void onInitializeClient() {
        MODULES.init();
        HudRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MODULES.onTick();
            handleGuiKey(client);
        });

        System.out.println("[Lume] 1.16.5 build initialized, modules=" + MODULES.getModules().size());
    }

    /** Open the ClickGUI on a Right Shift press (edge-detected) when no screen is open. */
    private void handleGuiKey(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null) return;
        boolean down = InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (down && !guiKeyDown && mc.currentScreen == null) {
            mc.openScreen(new ClickGuiScreen());
        }
        guiKeyDown = down;
    }
}

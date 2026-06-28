package com.lume.client1165;

import com.lume.client1165.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Lume Client — 1.16.5 build entry point. Logic layer ported (modules/settings);
 * GUI + rendering (MatrixStack) and the rest land next.
 */
public class LumeClient1165 implements ClientModInitializer {

    public static final ModuleManager MODULES = new ModuleManager();

    @Override
    public void onInitializeClient() {
        MODULES.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> MODULES.onTick());
        System.out.println("[Lume] 1.16.5 build initialized, modules=" + MODULES.getModules().size());
    }
}

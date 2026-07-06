package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {

    private static final int DELAY_TICKS = 20;   // 1 second before respawn
    private int ticks = 0;

    public AutoRespawn() {
        super("Auto Respawn", "Respawns automatically after death", Category.CHAT, -1);
    }

    @Override
    public void onEnable() { ticks = 0; }

    @Override
    public void onTick() {
        if (mc.currentScreen instanceof DeathScreen) {
            if (++ticks >= DELAY_TICKS && mc.player != null) {
                mc.player.requestRespawn();
                ticks = 0;
            }
        } else {
            ticks = 0;
        }
    }
}

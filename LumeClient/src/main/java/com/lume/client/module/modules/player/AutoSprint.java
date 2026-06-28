package com.lume.client.module.modules.player;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Keeps the player sprinting while moving forward — a common QoL toggle.
 */
public class AutoSprint extends Module {

    public AutoSprint() {
        super("AutoSprint", "Always sprint forward", Category.CHAT, -1);
        setBindable(true);   // toggle bind by default
    }

    @Override
    public void onTick() {
        if (mc.player != null && mc.options != null && mc.options.forwardKey.isPressed()) {
            mc.player.setSprinting(true);
        }
    }
}

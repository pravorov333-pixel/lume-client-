package com.lume.client1165.module.modules.player;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;

public class AutoSprint extends Module {

    public AutoSprint() {
        super("AutoSprint", "Always sprint forward", Category.CHAT, -1);
        setBindable(true);
    }

    @Override
    public void onTick() {
        if (mc.player != null && mc.options != null && mc.options.keyForward.isPressed()) {
            mc.player.setSprinting(true);
        }
    }
}

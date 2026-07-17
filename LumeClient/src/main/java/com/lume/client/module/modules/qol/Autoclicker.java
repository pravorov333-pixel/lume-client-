package com.lume.client.module.modules.qol;

import com.lume.client.mixin.MinecraftClientAccessor;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

/** Toggle it on with its bind and it auto-fires left-click attacks at a set CPS — no need to hold the mouse. */
public class Autoclicker extends Module {

    public final SliderSetting cps = add(new SliderSetting("CPS", 10, 2, 20, true));

    private long lastClick = 0;

    public Autoclicker() {
        super("Autoclicker", "Auto-fires left click at a set CPS", Category.CHAT, -1);
        setBindable(true);
        setBindMode(BindMode.TOGGLE);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) return;
        long now = System.currentTimeMillis();
        long interval = Math.max(1, Math.round(1000.0 / cps.value));
        if (now - lastClick < interval) return;
        lastClick = now;
        ((MinecraftClientAccessor) mc).lume$doAttack();
    }
}

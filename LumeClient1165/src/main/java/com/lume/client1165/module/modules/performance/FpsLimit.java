package com.lume.client1165.module.modules.performance;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;
import com.lume.client1165.module.setting.SliderSetting;

public class FpsLimit extends Module {

    private final SliderSetting limit = add(new SliderSetting("FPS Cap", 120, 30, 260, true));
    private int previous = 120;

    public FpsLimit() {
        super("FPS Limit", "Cap framerate to reduce GPU load", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.maxFps;
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        int target = limit.getInt();
        if (mc.options.maxFps != target) mc.options.maxFps = target;
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.maxFps = previous;
    }
}

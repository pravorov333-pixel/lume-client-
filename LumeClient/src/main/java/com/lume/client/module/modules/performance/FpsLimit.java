package com.lume.client.module.modules.performance;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

public class FpsLimit extends Module {

    private final SliderSetting limit = add(new SliderSetting("FPS Cap", 120, 30, 260, true));
    private int previous = 120;

    public FpsLimit() {
        super("FPS Limit", "Cap framerate to reduce GPU load", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.getMaxFps().getValue();
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        int target = limit.getInt();
        if (mc.options.getMaxFps().getValue() != target)
            mc.options.getMaxFps().setValue(target);
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.getMaxFps().setValue(previous);
    }
}

package com.lume.client.module.modules.performance;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

public class EntityDistance extends Module {

    private final SliderSetting percent = add(new SliderSetting("Distance %", 100, 50, 500, true));
    private double previous = 1.0;

    public EntityDistance() {
        super("Entity Distance", "Reduce entity render range to save FPS", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.getEntityDistanceScaling().getValue();
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        double target = percent.getInt() / 100.0;
        if (Math.abs(mc.options.getEntityDistanceScaling().getValue() - target) > 0.01)
            mc.options.getEntityDistanceScaling().setValue(target);
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.getEntityDistanceScaling().setValue(previous);
    }
}

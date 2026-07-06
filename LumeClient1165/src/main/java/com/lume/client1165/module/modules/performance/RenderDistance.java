package com.lume.client1165.module.modules.performance;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;
import com.lume.client1165.module.setting.SliderSetting;

public class RenderDistance extends Module {

    private final SliderSetting chunks = add(new SliderSetting("Chunks", 8, 2, 16, true));
    private int previous = 8;

    public RenderDistance() {
        super("Render Distance", "Override render distance for more FPS", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.viewDistance;
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        int target = chunks.getInt();
        if (mc.options.viewDistance != target) mc.options.viewDistance = target;
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.viewDistance = previous;
    }
}

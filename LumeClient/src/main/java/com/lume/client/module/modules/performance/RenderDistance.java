package com.lume.client.module.modules.performance;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

public class RenderDistance extends Module {

    private final SliderSetting chunks = add(new SliderSetting("Chunks", 8, 2, 32, true));
    private int previous = 8;

    public RenderDistance() {
        super("Render Distance", "Override render distance for more FPS", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.getViewDistance().getValue();
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        int target = chunks.getInt();
        if (mc.options.getViewDistance().getValue() != target)
            mc.options.getViewDistance().setValue(target);
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.getViewDistance().setValue(previous);
    }
}

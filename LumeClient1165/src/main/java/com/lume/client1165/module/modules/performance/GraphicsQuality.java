package com.lume.client1165.module.modules.performance;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;
import com.lume.client1165.module.setting.ModeSetting;
import net.minecraft.client.option.GraphicsMode;

public class GraphicsQuality extends Module {

    private final ModeSetting mode = add(new ModeSetting("Mode", 0, "Fast", "Fancy"));
    private GraphicsMode previous = GraphicsMode.FANCY;

    public GraphicsQuality() {
        super("Graphics Quality", "Switch between Fast and Fancy graphics", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) previous = mc.options.graphicsMode;
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        GraphicsMode target = mode.index == 0 ? GraphicsMode.FAST : GraphicsMode.FANCY;
        if (mc.options.graphicsMode != target) mc.options.graphicsMode = target;
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.graphicsMode = previous;
    }
}

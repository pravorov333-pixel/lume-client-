package com.lume.client1165.module.modules.render;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;

public class FullBright extends Module {

    private double prevGamma = 1.0;

    public FullBright() {
        super("FullBright", "Max brightness", Category.RENDER, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) { prevGamma = mc.options.gamma; mc.options.gamma = 1.0; }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.gamma = prevGamma;
    }
}

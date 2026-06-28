package com.lume.client1165.module.modules.render;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;

public class Zoom extends Module {

    private double prevFov = 70.0;

    public Zoom() {
        super("Zoom", "Narrow FOV zoom", Category.RENDER, -1);
        setBindable(true);
        setBindMode(BindMode.HOLD);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) { prevFov = mc.options.fov; mc.options.fov = 30.0; }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.fov = prevFov;
    }
}

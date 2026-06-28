package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Narrows the field of view while enabled for a zoom effect, restoring the
 * previous FOV when disabled. No mixin needed — uses the FOV option directly.
 */
public class Zoom extends Module {

    private int previousFov = 70;

    public Zoom() {
        super("Zoom", "Narrow FOV zoom", Category.RENDER, -1);
        setBindable(true);
        setBindMode(BindMode.HOLD);   // hold to zoom by default
    }

    @Override
    public void onEnable() {
        if (mc.options != null) {
            previousFov = mc.options.getFov().getValue();
            mc.options.getFov().setValue(30);
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.getFov().setValue(previousFov);
        }
    }
}

package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Raises the brightness (gamma) to maximum while enabled, and restores the
 * previous value when disabled.
 *
 * Note: vanilla clamps gamma to 1.0. "True" fullbright (gamma far above 1.0)
 * requires a small mixin that we can add later — this is the safe, no-mixin version.
 */
public class FullBright extends Module {

    private double previousGamma = 0.5;

    public FullBright() {
        super("FullBright", "Maximises brightness", Category.RENDER, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) {
            previousGamma = mc.options.getGamma().getValue();
        }
    }

    @Override
    public void onTick() {
        if (mc.options != null) {
            mc.options.getGamma().setValue(1.0);
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.getGamma().setValue(previousGamma);
        }
    }
}

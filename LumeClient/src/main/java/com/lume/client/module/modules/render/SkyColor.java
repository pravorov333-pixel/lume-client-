package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;

/** Custom sky colour (client-side) — a picker, plus 2 curated presets. Applied in ClientWorldMixin. */
public class SkyColor extends Module {

    public final ModeSetting  style = add(new ModeSetting("Style", 0, "Custom", "Dusk", "Nebula"));
    public final ColorSetting color = add(new ColorSetting("Color", false, 140, 120, 210));

    private static final int DUSK   = (210 << 16) | (110 << 8) | 90;
    private static final int NEBULA = (90 << 16) | (40 << 8) | 140;

    public SkyColor() {
        super("Sky", "Свой цвет неба (+ 2 готовых пресета)", Category.RENDER, -1);
    }

    /** Effective sky RGB — the picker for "Custom", a fixed preset otherwise. */
    public int rgb() {
        return switch (style.index) {
            case 1 -> DUSK;
            case 2 -> NEBULA;
            default -> color.rgb();
        };
    }
}

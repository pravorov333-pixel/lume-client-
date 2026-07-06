package com.lume.client.module.modules.cosmetic;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/** Styled panel behind the hotbar (glass or custom colour) with adjustable opacity. */
public class CustomHotbar extends Module {

    public final ModeSetting   style   = add(new ModeSetting("Style", 0, "Glass", "Color"));
    public final ColorSetting  color   = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting opacity = add(new SliderSetting("Opacity", 0.55, 0.0, 1.0, false));

    public CustomHotbar() {
        super("Custom Hotbar", "Стиль фона хотбара (стекло/цвет)", Category.COSMETIC, -1);
    }
}

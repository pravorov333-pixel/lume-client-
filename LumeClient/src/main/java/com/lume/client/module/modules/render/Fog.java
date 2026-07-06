package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;

/** Custom fog colour (client-side). Applied in BackgroundRendererMixin. */
public class Fog extends Module {

    public final ColorSetting color = add(new ColorSetting("Color", false, 183, 170, 217));

    public Fog() {
        super("Fog", "Свой цвет тумана", Category.RENDER, -1);
    }
}

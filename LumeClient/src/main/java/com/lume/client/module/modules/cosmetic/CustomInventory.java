package com.lume.client.module.modules.cosmetic;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/** Full glass/colour reskin of container screens — replaces the vanilla panel
 *  texture with a Lume glass panel + slot squares (items/craft/armor untouched). */
public class CustomInventory extends Module {

    public final ModeSetting   style   = add(new ModeSetting("Style", 0, "Glass", "Color"));
    public final ColorSetting  color   = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting opacity = add(new SliderSetting("Opacity", 0.7, 0.1, 1.0, false));

    public CustomInventory() {
        super("Custom GUI", "Glass-рескин инвентаря (фон, не слоты)", Category.COSMETIC, -1);
    }
}

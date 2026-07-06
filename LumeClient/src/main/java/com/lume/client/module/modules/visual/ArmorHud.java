package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;

/** Toggle for the armor + held item overlay (durability shown). Drawn in HudRenderer. */
public class ArmorHud extends Module {

    /** Lay the armor pieces out vertically instead of horizontally. */
    public final BoolSetting flipY = add(new BoolSetting("Flip Y axis", false));
    public final ColorSetting color = add(new ColorSetting("Color", true, 183, 170, 217));

    public ArmorHud() {
        super("Armor HUD", "Armor & durability", Category.VISUALS, -1);
    }
}

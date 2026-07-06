package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;

/**
 * Toggle for the active status-effect (potion) list. Drawing lives in
 * {@link com.lume.client.gui.HudRenderer}.
 */
public class PotionHud extends Module {

    public final ColorSetting color = add(new ColorSetting("Color", true, 183, 170, 217));

    public PotionHud() {
        super("Potion HUD", "Active effects list", Category.VISUALS, -1);
    }
}

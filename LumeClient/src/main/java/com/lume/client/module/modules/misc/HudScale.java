package com.lume.client.module.modules.misc;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

/**
 * Global HUD scale — multiplies the size of every HUD element. Per-element size
 * and position are set on-screen in the ClickGUI editor; this is the one shared
 * multiplier. Lives under Settings.
 */
public class HudScale extends Module {

    public final SliderSetting scale = add(new SliderSetting("Scale", 1.0, 0.5, 2.0, false));

    public HudScale() {
        super("HUD Scale", "Global size of all HUD elements", Category.SETTINGS, -1);
    }
}

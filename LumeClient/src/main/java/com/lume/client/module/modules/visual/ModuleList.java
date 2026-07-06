package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;

/**
 * ArrayList — the classic right-side list of currently enabled modules, sorted by
 * name width into a staircase. Drawn in {@link com.lume.client.gui.HudRenderer}.
 */
public class ModuleList extends Module {

    public final ColorSetting color = add(new ColorSetting("Color", true, 183, 170, 217));

    public ModuleList() {
        super("Module List", "ArrayList of enabled modules", Category.VISUALS, -1);
    }
}

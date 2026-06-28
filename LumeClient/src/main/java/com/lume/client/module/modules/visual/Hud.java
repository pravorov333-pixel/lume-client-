package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Toggle module that controls whether the on-screen HUD (watermark, coords,
 * FPS …) is drawn. The actual drawing lives in
 * {@link com.lume.client.gui.HudRenderer}. Position &amp; size are edited on-screen
 * via the ClickGUI HUD editor (drag to move, slider to resize); global scale
 * lives in the "HUD Scale" module under Settings.
 */
public class Hud extends Module {

    public Hud() {
        super("HUD", "On-screen info and module list", Category.VISUALS, -1);
    }
}

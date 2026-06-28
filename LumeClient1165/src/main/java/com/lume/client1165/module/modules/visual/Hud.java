package com.lume.client1165.module.modules.visual;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;

/** Toggles the top-left Lume info panel (watermark + FPS). Rendered in HudRenderer. */
public class Hud extends Module {
    public Hud() {
        super("HUD", "Lume watermark + FPS panel", Category.VISUALS, -1);
        setEnabled(true);
    }
}

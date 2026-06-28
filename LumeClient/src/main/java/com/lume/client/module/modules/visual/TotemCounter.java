package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Toggle for the totem-of-undying counter. Drawn in HudRenderer. */
public class TotemCounter extends Module {
    public TotemCounter() {
        super("Totem Counter", "Totems in inventory", Category.VISUALS, -1);
    }
}

package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Shows horizontal movement speed (blocks/second) as a line in the HUD panel. */
public class Speed extends Module {
    public Speed() {
        super("Speed", "Movement speed (b/s)", Category.VISUALS, -1);
    }
}

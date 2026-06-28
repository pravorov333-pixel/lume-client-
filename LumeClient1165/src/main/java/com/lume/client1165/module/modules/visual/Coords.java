package com.lume.client1165.module.modules.visual;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;

/** Toggles the bottom-center XYZ coordinates readout. Rendered in HudRenderer. */
public class Coords extends Module {
    public Coords() {
        super("Coords", "Show XYZ coordinates", Category.VISUALS, -1);
    }
}

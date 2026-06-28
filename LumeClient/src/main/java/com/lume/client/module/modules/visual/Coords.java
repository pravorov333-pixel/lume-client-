package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Toggles the XYZ coordinates line in the HUD info panel. Read in HudRenderer. */
public class Coords extends Module {
    public Coords() {
        super("Coords", "Show XYZ position", Category.VISUALS, -1);
    }
}

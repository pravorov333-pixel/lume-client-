package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Adds an in-game day number to the info panel. Read in HudRenderer. */
public class DayCounter extends Module {
    public DayCounter() {
        super("Day Counter", "Show world day", Category.VISUALS, -1);
    }
}

package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Shows clicks-per-second (left | right) as a line in the HUD panel. */
public class Cps extends Module {
    public Cps() {
        super("CPS", "Clicks per second", Category.VISUALS, -1);
    }
}

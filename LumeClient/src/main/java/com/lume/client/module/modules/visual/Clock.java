package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Shows the real-world time (HH:mm:ss) as a line in the HUD panel. */
public class Clock extends Module {
    public Clock() {
        super("Clock", "Real-world clock", Category.VISUALS, -1);
    }
}

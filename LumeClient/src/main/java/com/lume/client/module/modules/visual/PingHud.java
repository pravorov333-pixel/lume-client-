package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Adds a Ping line to the info panel. Read in HudRenderer. */
public class PingHud extends Module {
    public PingHud() {
        super("Ping", "Show ping in ms", Category.VISUALS, -1);
    }
}

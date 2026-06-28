package com.lume.client1165.module.modules.visual;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;

/** Toggles the WASD + space key overlay. Rendered in HudRenderer. */
public class Keystrokes extends Module {
    public Keystrokes() {
        super("Keystrokes", "WASD + space key overlay", Category.VISUALS, -1);
    }
}

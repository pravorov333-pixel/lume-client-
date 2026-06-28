package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Toggle for the on-screen WASD + space key overlay. Drawing lives in
 * {@link com.lume.client.gui.HudRenderer}.
 */
public class Keystrokes extends Module {

    public Keystrokes() {
        super("Keystrokes", "Show movement keys", Category.VISUALS, -1);
    }
}

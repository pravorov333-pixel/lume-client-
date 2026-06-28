package com.lume.client.module.modules.cosmetic;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Menu Logo — paints Lume branding (logo mark + wordmark) onto the vanilla
 * title screen, so the client feels like yours from the first screen.
 *
 * <p>The drawing happens in {@link com.lume.client.LumeClient}'s title-screen
 * render hook; this class is just the on/off toggle. On by default.
 */
public class MenuLogo extends Module {

    public MenuLogo() {
        super("Menu Logo", "Show Lume branding on the title screen", Category.COSMETIC, -1);
    }
}

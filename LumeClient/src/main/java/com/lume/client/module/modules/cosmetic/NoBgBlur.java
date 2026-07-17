package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * No Background Blur — when opening the inventory, a menu, or any other
 * screen, skip both vanilla's own background-blur post-effect (see
 * ScreenMixin#lume$noBlur) AND our own dimmed backdrop behind the Lume menu
 * (see Theme#backdrop).
 */
public class NoBgBlur extends Module {

    public NoBgBlur() {
        super("No BG Blur", "Без размытия/затемнения фона за экранами", Category.COSMETIC, -1);
    }

    public static boolean active() {
        Module m = LumeClient.MODULES.getByName("No BG Blur");
        return m != null && m.isEnabled();
    }
}

package com.lume.client.module.modules.cosmetic;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Restyles the death screen: dark glass gradient background + reskinned buttons (see DeathScreenMixin / PressableWidgetMixin). */
public class CustomDeathScreen extends Module {

    public CustomDeathScreen() {
        super("Custom Death Screen", "Glass-styled death screen background & buttons", Category.COSMETIC, -1);
    }
}

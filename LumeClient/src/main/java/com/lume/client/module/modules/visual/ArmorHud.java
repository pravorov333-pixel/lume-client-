package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Toggle for the armor + held item overlay (durability shown). Drawn in HudRenderer. */
public class ArmorHud extends Module {
    public ArmorHud() {
        super("Armor HUD", "Armor & durability", Category.VISUALS, -1);
    }
}

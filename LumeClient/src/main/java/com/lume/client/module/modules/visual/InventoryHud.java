package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/** Toggles a transparent inventory overlay (items only) above the hotbar. Drawn in HudRenderer. */
public class InventoryHud extends Module {
    public InventoryHud() {
        super("Inventory HUD", "Items above hotbar", Category.VISUALS, -1);
    }
}

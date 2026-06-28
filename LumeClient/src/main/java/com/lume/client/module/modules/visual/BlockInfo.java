package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/**
 * Block Info — its own HUD (like Target HUD): a glass panel with the 3D block
 * icon + name of the block under the crosshair. "Simple info" mode instead just
 * writes the block name next to the crosshair. Drawn in HudRenderer.
 */
public class BlockInfo extends Module {

    public final BoolSetting simple = add(new BoolSetting("Simple info", false));

    public BlockInfo() {
        super("Block Info", "Name of block you look at", Category.VISUALS, -1);
    }
}

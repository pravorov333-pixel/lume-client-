package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;

/**
 * Block Outline — recolours the block-selection outline to the client accent (or
 * a custom RGB). Pure cosmetic; the recolour happens in {@code WorldRendererMixin}.
 */
public class BlockOutline extends Module {

    public final ColorSetting color = add(new ColorSetting("Color", true, 169, 155, 199));

    public BlockOutline() {
        super("Block Outline", "Coloured block selection outline", Category.COSMETIC, -1);
    }

    /** Outline colour as 0xAARRGGBB, or 0 when the module is off (= keep vanilla). */
    public static int argbOrZero() {
        Module m = LumeClient.MODULES.getByName("Block Outline");
        if (!(m instanceof BlockOutline b) || !b.isEnabled()) return 0;
        int rgb = b.color.accent ? Theme.accentRgb() : b.color.rgb();
        return 0xFF000000 | rgb;
    }
}

package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;

/** Restyled nametags above OTHER players (yours is Self Name) — see {@code PlayerEntityRendererMixin}. */
public class CustomNametags extends Module {

    public final ColorSetting color = add(new ColorSetting("Color", true, 183, 170, 217));

    public CustomNametags() {
        super("Custom Nametags", "Restyled nametags above other players", Category.VISUALS, -1);
    }
}

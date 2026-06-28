package com.lume.client.module.modules.cosmetic;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Custom Crosshair — replaces the chunky vanilla crosshair with a clean Lume
 * one. Fully customisable (size / thickness / gap / colour / outline / dot).
 *
 * <p>The vanilla crosshair is cancelled in {@code InGameHudMixin}; the Lume one
 * is drawn in {@code HudRenderer.renderCrosshair} using these settings.
 */
public class CustomCrosshair extends Module {

    public final SliderSetting size = add(new SliderSetting("Size", 5, 1, 12, true));
    public final SliderSetting thickness = add(new SliderSetting("Thickness", 1, 1, 4, true));
    public final SliderSetting gap = add(new SliderSetting("Gap", 3, 0, 10, true));
    public final ColorSetting color = add(new ColorSetting("Color", true, 169, 155, 199));
    public final BoolSetting outline = add(new BoolSetting("Outline", true));
    public final BoolSetting dot = add(new BoolSetting("Center dot", true));

    public CustomCrosshair() {
        super("Custom Crosshair", "Clean customisable crosshair", Category.COSMETIC, -1);
    }
}

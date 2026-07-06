package com.lume.client.module.modules.render;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/** Client-side world/weather visual tweaks (no rain, no storm darkening, no sun/moon). */
public class WorldCustomizer extends Module {

    public final BoolSetting noRain      = add(new BoolSetting("No Rain", true));
    public final BoolSetting noThunder   = add(new BoolSetting("No Storm", true));
    public final BoolSetting noCelestial = add(new BoolSetting("No Sun/Moon", false));
    public final BoolSetting noClouds    = add(new BoolSetting("No Clouds", false));

    public WorldCustomizer() {
        super("World Customizer", "Убирает дождь, грозу, солнце, луну и облака (визуально)", Category.RENDER, -1);
    }

    /** True if the sun/moon should be skipped this frame (module on + toggle on). */
    public static boolean hideCelestial() {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        return m instanceof WorldCustomizer w && w.isEnabled() && w.noCelestial.value;
    }
}

package com.lume.client.module.modules.render;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Client-side world/weather/sky/fog visual tweaks, all in one place — Fog and Sky
 * used to be their own separate modules; folded in here since they're all "how the
 * world around you looks" and were getting scattered across the module list.
 */
public class WorldCustomizer extends Module {

    // --- weather ---
    public final ModeSetting   weather     = add(new ModeSetting("Weather", 2, "Off", "Storm", "Sunny", "Grey Rain"));
    public final BoolSetting   noCelestial = add(new BoolSetting("No Sun/Moon", false));
    public final BoolSetting   noClouds    = add(new BoolSetting("No Clouds", false));

    // --- fog (was its own "Fog" module) ---
    public final ColorSetting  fogColor    = add(new ColorSetting("Fog Color", false, 183, 170, 217));
    /** Multiplies vanilla's fog start/end distance — below 1 pulls fog closer, above 1 pushes it out. */
    public final SliderSetting fogDistance = add(new SliderSetting("Fog Distance", 1.0, 0.2, 3.0, false));
    /** Fog's own opacity/transparency (the shader's fog alpha) — 1 = normal vanilla fog, lower = more see-through. */
    public final SliderSetting fogDensity  = add(new SliderSetting("Fog Density", 1.0, 0.0, 1.0, false));

    // --- sky (was its own "Sky" module) ---
    public final ModeSetting   skyStyle    = add(new ModeSetting("Sky Style", 0, "Custom", "Dusk", "Nebula"));
    public final ColorSetting  skyColor    = add(new ColorSetting("Sky Color", false, 140, 120, 210));

    private static final int DUSK   = (210 << 16) | (110 << 8) | 90;
    private static final int NEBULA = (90 << 16) | (40 << 8) | 140;

    public WorldCustomizer() {
        super("World Customizer", "Погода, туман, небо, солнце/луна и облака (визуально)", Category.RENDER, -1);
    }

    /** True if the sun/moon should be skipped this frame (module on + toggle on). */
    public static boolean hideCelestial() {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        return m instanceof WorldCustomizer w && w.isEnabled() && w.noCelestial.value;
    }

    /** Effective sky RGB — the picker for "Custom", a fixed preset otherwise. */
    public int skyRgb() {
        return switch (skyStyle.index) {
            case 1 -> DUSK;
            case 2 -> NEBULA;
            default -> skyColor.rgb();
        };
    }

    private static WorldCustomizer get() {
        Module m = LumeClient.MODULES.getByName("World Customizer");
        return m instanceof WorldCustomizer w && w.isEnabled() ? w : null;
    }

    /** Forced rain gradient (0..1), or -1 to leave the real server weather alone. */
    public static float rainOverride() {
        WorldCustomizer w = get();
        if (w == null) return -1f;
        return switch (w.weather.index) {
            case 1, 3 -> 1f;   // Storm, Grey Rain
            case 2 -> 0f;      // Sunny
            default -> -1f;    // Off
        };
    }

    /** Forced thunder gradient (0..1), or -1 to leave the real server weather alone. */
    public static float thunderOverride() {
        WorldCustomizer w = get();
        if (w == null) return -1f;
        return switch (w.weather.index) {
            case 1 -> 1f;         // Storm
            case 2, 3 -> 0f;      // Sunny, Grey Rain (no lightning)
            default -> -1f;       // Off
        };
    }
}

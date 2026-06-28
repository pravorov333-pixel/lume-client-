package com.lume.client.module.setting;

/**
 * An RGB colour option. When {@link #accent} is on, callers should use the
 * theme's accent colour instead of the stored RGB — so the GUI shows an
 * "Accent" toggle plus three R/G/B sliders (revealed when accent is off).
 */
public class ColorSetting extends Setting {

    public boolean accent;   // true = follow the theme accent colour
    public int r, g, b;

    public ColorSetting(String name, boolean accent, int r, int g, int b) {
        super(name);
        this.accent = accent;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    /** The chosen colour as 0xRRGGBB (only valid when {@link #accent} is false). */
    public int rgb() {
        return (r << 16) | (g << 8) | b;
    }
}

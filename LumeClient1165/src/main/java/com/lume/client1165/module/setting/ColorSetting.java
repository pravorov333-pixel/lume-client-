package com.lume.client1165.module.setting;

public class ColorSetting extends Setting {
    public boolean accent;
    public int r, g, b;

    public ColorSetting(String name, boolean accent, int r, int g, int b) {
        super(name);
        this.accent = accent;
        this.r = r; this.g = g; this.b = b;
    }

    public int rgb() { return (r << 16) | (g << 8) | b; }
}

package com.lume.client1165.module.setting;

public class SliderSetting extends Setting {
    public double value;
    public final double min;
    public final double max;
    public final boolean integer;

    public SliderSetting(String name, double value, double min, double max, boolean integer) {
        super(name);
        this.min = min;
        this.max = max;
        this.integer = integer;
        this.value = value;
    }

    public int getInt() { return (int) Math.round(value); }

    public double fraction() { return max > min ? (value - min) / (max - min) : 0; }

    public void setFraction(double f) {
        f = Math.max(0, Math.min(1, f));
        double v = min + (max - min) * f;
        value = integer ? Math.round(v) : v;
    }

    public String display() { return integer ? String.valueOf(getInt()) : String.format("%.2f", value); }
}

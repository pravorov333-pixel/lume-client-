package com.lume.client1165.module.setting;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting {

    public final List<String> modes;
    public int index;

    public ModeSetting(String name, int index, String... modes) {
        super(name);
        this.modes = Arrays.asList(modes);   // Java 8 (no List.of)
        this.index = Math.max(0, Math.min(index, this.modes.size() - 1));
    }

    public String get() { return modes.get(index); }

    public void setByName(String name) {
        int i = modes.indexOf(name);
        if (i >= 0) index = i;
    }

    public void cycle(int dir) { index = Math.floorMod(index + dir, modes.size()); }
}

package com.lume.client.module.setting;

import java.util.List;

/** A pick-one-of-many option, shown as a "‹ value ›" cycler in the GUI. */
public class ModeSetting extends Setting {

    public final List<String> modes;
    public int index;

    public ModeSetting(String name, int index, String... modes) {
        super(name);
        this.modes = List.of(modes);
        this.index = Math.max(0, Math.min(index, this.modes.size() - 1));
    }

    public String get() { return modes.get(index); }

    public void setByName(String name) {
        int i = modes.indexOf(name);
        if (i >= 0) index = i;
    }

    public void cycle(int dir) {
        index = Math.floorMod(index + dir, modes.size());
    }
}

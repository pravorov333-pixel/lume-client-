package com.lume.client.module.setting;

/** A simple on/off option, shown as a small toggle pill in the GUI. */
public class BoolSetting extends Setting {

    public boolean value;

    public BoolSetting(String name, boolean value) {
        super(name);
        this.value = value;
    }
}

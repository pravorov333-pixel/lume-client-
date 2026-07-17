package com.lume.client.module.setting;

/** A free-text option, shown as an editable text field in the GUI. */
public class StringSetting extends Setting {

    public String value;

    public StringSetting(String name, String value) {
        super(name);
        this.value = value;
    }
}

package com.lume.client.module.setting;

/**
 * Base class for a single configurable option belonging to a module.
 * Subclasses: {@link BoolSetting}, {@link SliderSetting}, {@link ColorSetting}.
 */
public abstract class Setting {

    public final String name;

    protected Setting(String name) {
        this.name = name;
    }
}

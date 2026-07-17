package com.lume.client.module.setting;

/**
 * Base class for a single configurable option belonging to a module.
 * Subclasses: {@link BoolSetting}, {@link SliderSetting}, {@link ColorSetting}.
 */
public abstract class Setting {

    public final String name;

    /** True for settings that persist/load normally but are drawn by a module's own
     *  custom UI block instead of the generic settings-list loop. */
    public boolean hidden = false;

    protected Setting(String name) {
        this.name = name;
    }
}

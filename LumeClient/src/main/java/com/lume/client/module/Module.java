package com.lume.client.module;

import com.lume.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every Lume module (feature).
 * A module has a name, a category, an optional keybind and an on/off state.
 * Subclasses override onEnable/onDisable/onTick to do their work.
 */
public abstract class Module {

    /** How a keybind behaves. */
    public enum BindMode { TOGGLE, HOLD }

    /** Shortcut to the Minecraft client instance. */
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private int key;
    private boolean enabled;

    /** Whether this module is worth binding to a key (gameplay-useful). Shown in the Binds tab. */
    private boolean bindable = false;
    private BindMode bindMode = BindMode.TOGGLE;
    /** If false, the card can't be toggled on/off — only expanded for its settings. */
    private boolean toggleable = true;

    /** Customisation options shown when the module card is expanded. */
    private final List<Setting> settings = new ArrayList<>();

    public Module(String name, String description, Category category, int key) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = key;
    }

    /** Called once when the module is turned on. */
    public void onEnable() {}

    /** Called once when the module is turned off. */
    public void onDisable() {}

    /** Called every client tick while the module is on. */
    public void onTick() {}

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean state) {
        if (state == enabled) return;
        enabled = state;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    /** Register a setting (returns it so fields can be assigned inline). */
    protected <T extends Setting> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public List<Setting> getSettings() { return settings; }
    public boolean hasSettings() { return !settings.isEmpty(); }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public int getKey() { return key; }
    public void setKey(int key) { this.key = key; }

    public boolean isBindable() { return bindable; }
    protected void setBindable(boolean b) { this.bindable = b; }
    public boolean isToggleable() { return toggleable; }
    protected void setToggleable(boolean b) { this.toggleable = b; }
    public BindMode getBindMode() { return bindMode; }
    public void setBindMode(BindMode m) { this.bindMode = m; }
}

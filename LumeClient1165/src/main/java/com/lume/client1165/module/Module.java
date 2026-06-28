package com.lume.client1165.module;

import com.lume.client1165.module.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/** Base class for every Lume module (1.16.5 build, Java 8). */
public abstract class Module {

    public enum BindMode { TOGGLE, HOLD }

    protected final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private int key;
    private boolean enabled;

    private final List<Setting> settings = new ArrayList<Setting>();

    private boolean bindable = false;
    private BindMode bindMode = BindMode.TOGGLE;
    private boolean toggleable = true;

    public Module(String name, String description, Category category, int key) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = key;
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean state) {
        if (state == enabled) return;
        enabled = state;
        if (enabled) onEnable(); else onDisable();
    }

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
    public BindMode getBindMode() { return bindMode; }
    public void setBindMode(BindMode m) { this.bindMode = m; }
    public boolean isToggleable() { return toggleable; }
    protected void setToggleable(boolean b) { this.toggleable = b; }
}

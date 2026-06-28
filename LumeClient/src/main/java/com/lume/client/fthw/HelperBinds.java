package com.lume.client.fthw;

import com.lume.client.gui.Notifications;
import com.lume.client.module.setting.BoolSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Keybinds for the FT/HW Server Helper sub-functions. Each registered
 * {@link BoolSetting} can carry a key (its {@code key} field) that toggles it.
 * The Server tab assigns the keys; {@link com.lume.client.mixin.KeyboardMixin}
 * routes raw presses here. Persisted in the config by setting name.
 */
public final class HelperBinds {

    /** Sub-function toggles that may be keybound. */
    public static final List<BoolSetting> bound = new ArrayList<>();

    private HelperBinds() {}

    public static void register(BoolSetting s) {
        if (!bound.contains(s)) bound.add(s);
    }

    /** Toggle any sub-function bound to {@code key}. */
    public static void onKey(int key) {
        if (key < 0) return;
        for (BoolSetting s : bound) {
            if (s.key == key) {
                s.value = !s.value;
                Notifications.push(s.name + ": " + (s.value ? "вкл" : "выкл"), 0xFFB7AAD9, 1500);
                com.lume.client.Config.save();
            }
        }
    }

    public static BoolSetting byName(String name) {
        for (BoolSetting s : bound) if (s.name.equals(name)) return s;
        return null;
    }
}

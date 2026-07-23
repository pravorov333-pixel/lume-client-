package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Entry point for the visual Keybind Manager (see {@code gui.KeybindManagerScreen}) — replaces
 * the old "Auto Command" (single interval-repeated command). Has no regular settings of its own;
 * its ClickGUI card instead draws two action buttons (see {@code ClickGuiScreen}'s
 * {@code KeybindManager} special-case in {@code renderSettingsNvg}/{@code handleSettingClick},
 * kinds 21/22): "Bind command" (press a key, then set the command it should send) and
 * "Open manager" (the full keyboard overview).
 */
public class KeybindManager extends Module {
    public KeybindManager() {
        super("Keybind Manager", "Bind chat commands/macros to keys, with a visual keyboard overview", Category.CHAT, -1);
        setToggleable(false);   // pure UI entry point — nothing to turn on/off, only settings to open
    }

    /** No regular {@code Setting}s are registered (both rows are hand-drawn by ClickGuiScreen's
     *  special case) — override so the card still shows an expand chevron / settings panel. */
    @Override
    public boolean hasSettings() { return true; }
}

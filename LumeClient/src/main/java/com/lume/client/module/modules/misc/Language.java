package com.lume.client.module.modules.misc;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ModeSetting;

/**
 * Client language for CONTENT (potion names, HUD labels, notifications). Module
 * names stay English. RU switches the HUD font to a Cyrillic one (PT Sans).
 */
public class Language extends Module {

    public final ModeSetting lang = add(new ModeSetting("Language", 0, "English", "Русский"));

    public Language() {
        super("Language", "Client language (content)", Category.SETTINGS, -1);
        setToggleable(false);   // only expands; can't be turned on/off
    }

    public boolean isRu() { return lang.index == 1; }
}

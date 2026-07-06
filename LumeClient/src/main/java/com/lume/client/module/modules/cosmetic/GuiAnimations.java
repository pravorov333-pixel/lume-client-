package com.lume.client.module.modules.cosmetic;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

/** Smooth open animation (scale + ease) for vanilla screens — inventory, chat, menus, etc. */
public class GuiAnimations extends Module {

    public final SliderSetting duration = add(new SliderSetting("Duration ms", 180, 60, 500, true));

    public GuiAnimations() {
        super("GUI Animations", "Плавное появление ванильных экранов", Category.COSMETIC, -1);
    }

    public float durationMs() {
        return (float) Math.max(1, duration.value);
    }
}

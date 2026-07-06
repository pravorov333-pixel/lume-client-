package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

/** Tunes dropped-item physics client-side: fall gravity strength and ground bounce. */
public class ItemPhysics extends Module {

    public final SliderSetting gravity = add(new SliderSetting("Gravity", 1.0, 0.2, 3.0, false));
    public final SliderSetting bounce  = add(new SliderSetting("Bounce", 0.0, 0.0, 0.9, false));

    public ItemPhysics() {
        super("Item Physics", "Гравитация и отскок выпавших предметов", Category.RENDER, -1);
    }
}

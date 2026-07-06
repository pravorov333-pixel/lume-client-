package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;

/** Recolours the whole entity model, like vanilla's white hurt flash, but in a configurable colour. */
public class HitColor extends Module {

    public final ColorSetting color = add(new ColorSetting("Color", false, 224, 86, 86));

    public HitColor() {
        super("HitColor", "Цветная вспышка на цели при ударе", Category.RENDER, -1);
    }
}

package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ModeSetting;

/** Aspect ratio changer — stretches the view to a chosen ratio (4:3 PvP stretch, etc). */
public class Aspect extends Module {

    public final ModeSetting ratio = add(new ModeSetting("Ratio", 1, "16:9", "4:3", "1:1", "16:10", "21:9"));

    public Aspect() {
        super("Aspect", "Соотношение сторон экрана (растяжение)", Category.RENDER, -1);
    }

    /** Target aspect ratio (w/h), or 0 if none. */
    public float target() {
        return switch (ratio.index) {
            case 0 -> 16f / 9f;
            case 1 -> 4f / 3f;
            case 2 -> 1f;
            case 3 -> 16f / 10f;
            case 4 -> 21f / 9f;
            default -> 0f;
        };
    }
}

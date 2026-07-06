package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.util.math.MathHelper;

/** Client-side time of day — locks the sky/light to a chosen time (visual only). */
public class TimeChanger extends Module {

    public final SliderSetting time = add(new SliderSetting("Time", 6000, 0, 24000, true));

    public TimeChanger() {
        super("Time Changer", "Клиентское время суток (небо/свет)", Category.RENDER, -1);
    }

    /** Vanilla sky-angle formula for an arbitrary time of day. */
    public static float skyAngle(int t) {
        double d = MathHelper.fractionalPart(t / 24000.0 - 0.25);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float) ((d * 2.0 + e) / 3.0);
    }
}

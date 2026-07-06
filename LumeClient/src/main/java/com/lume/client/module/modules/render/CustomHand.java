package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

/** Repositions / rotates / scales the first-person held item. */
public class CustomHand extends Module {

    public final SliderSetting posX  = add(new SliderSetting("Pos X", 0.0, -1.0, 1.0, false));
    public final SliderSetting posY  = add(new SliderSetting("Pos Y", 0.0, -1.0, 1.0, false));
    public final SliderSetting posZ  = add(new SliderSetting("Pos Z", 0.0, -1.0, 1.0, false));
    public final SliderSetting scale = add(new SliderSetting("Scale", 1.0, 0.5, 2.0, false));
    public final SliderSetting rotX  = add(new SliderSetting("Rot X", 0.0, -180.0, 180.0, true));
    public final SliderSetting rotY  = add(new SliderSetting("Rot Y", 0.0, -180.0, 180.0, true));
    public final SliderSetting rotZ  = add(new SliderSetting("Rot Z", 0.0, -180.0, 180.0, true));

    public CustomHand() {
        super("Custom Hand", "Позиция, поворот и размер предмета в руке", Category.RENDER, -1);
    }
}

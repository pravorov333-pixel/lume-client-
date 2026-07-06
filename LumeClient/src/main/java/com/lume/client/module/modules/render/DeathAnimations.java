package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Death Animations — an extra ragdoll tumble on top of vanilla's own death tilt,
 * plus a coloured particle burst at the moment of death. Applied to any living
 * entity that dies in view (see LivingEntityRendererMixin, which reads the
 * entity's own render-state {@code deathTime}).
 */
public class DeathAnimations extends Module {

    public final BoolSetting   ragdoll = add(new BoolSetting("Ragdoll Spin", true));
    public final BoolSetting   burst   = add(new BoolSetting("Particle Burst", true));
    public final ColorSetting  color   = add(new ColorSetting("Color", true, 183, 170, 217));
    public final SliderSetting count   = add(new SliderSetting("Burst Count", 20, 5, 60, true));

    public DeathAnimations() {
        super("Death Animations", "Раг-долл при смерти + вспышка частиц", Category.RENDER, -1);
    }
}

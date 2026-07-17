package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ModeSetting;

/**
 * Death Animations — replaces vanilla's own death tilt/sink with a custom
 * one. One style picker covers everything now (Soul/Anvil/Thunderstrike/
 * Beam are all peers) — there's no separate particle picker any more, and
 * vanilla's own death render is suppressed whenever a style is chosen.
 * Applied in LivingEntityRendererMixin (suppresses vanilla) + DeathFx (the
 * actual custom visuals, world-rendered).
 */
public class DeathAnimations extends Module {

    public final ModeSetting animation = add(new ModeSetting("Animation", 0, "Off", "Soul", "Anvil", "Thunderstrike", "Beam"));
    public final BoolSetting sound = add(new BoolSetting("Sound", true));

    public DeathAnimations() {
        super("Death Animations", "Custom death animation", Category.RENDER, -1);
    }
}

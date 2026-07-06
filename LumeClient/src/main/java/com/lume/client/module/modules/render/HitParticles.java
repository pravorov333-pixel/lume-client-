package com.lume.client.module.modules.render;

import com.lume.client.fx.ParticleEngine;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Custom glowing hit particles (via ParticleEngine) spawned when you hit an
 * entity. "Default" style = plain vanilla crit/hit particles (untouched, not
 * suppressed); any other style replaces them with our own and suppresses the
 * vanilla ones (see ClientPlayNetworkHandlerMixin) so only ours show.
 */
public class HitParticles extends Module {

    public final ModeSetting   style = add(new ModeSetting("Style", 0, "Default", "Burst", "Spark", "Ring", "Nova"));
    public final ModeSetting   shape = add(new ModeSetting("Shape", 0, ParticleEngine.SHAPE_NAMES));
    public final ColorSetting  color = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting count = add(new SliderSetting("Count", 24, 6, 60, true));
    public final SliderSetting speed = add(new SliderSetting("Speed", 4.0, 1.0, 12.0, false));
    public final SliderSetting size  = add(new SliderSetting("Size", 0.22, 0.05, 0.6, false));

    public HitParticles() {
        super("Hit Particles", "Светящиеся частицы удара (или ванильные)", Category.RENDER, -1);
    }

    /** True = "Default" — plain vanilla particles, no custom spawn/suppression. */
    public boolean isDefault() { return style.index == 0; }

    /** ParticleEngine.hit() style index (0 Burst..3 Nova) — only meaningful when not Default. */
    public int engineStyle() { return Math.max(0, style.index - 1); }
}

package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Plays a client-side sound on hit and suppresses the vanilla attack sound.
 * "Only on Crit" → custom sound only fires on crits; normal hits keep the
 * vanilla sound. Suppression happens in {@code SoundManagerMixin}.
 */
public class HitSound extends Module {

    public final ModeSetting   sound      = add(new ModeSetting("Sound", 0, "Pling", "Bell", "Anvil", "Orb", "Bass Crit 1", "Bass Crit 2"));
    public final SliderSetting volume     = add(new SliderSetting("Volume", 1.0, 0.1, 2.0, false));
    public final SliderSetting pitch      = add(new SliderSetting("Pitch", 1.2, 0.5, 2.0, false));
    public final BoolSetting   onlyOnCrit = add(new BoolSetting("Only on Crit", false));
    /** "My Sounds" — overrides the Sound picker entirely with your own dropped-in .ogg. */
    public final BoolSetting   useMySound = add(new BoolSetting("Use My Sound", false));
    /** Name of the chosen file within the folder, when more than one is dropped in (session-only). */
    public String selectedFile = null;

    public HitSound() {
        super("HitSound", "Звук при ударе (убирает ванильный)", Category.RENDER, -1);
    }
}

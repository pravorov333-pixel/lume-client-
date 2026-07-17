package com.lume.client.module.modules.render;

import com.lume.client.fx.ParticleEngine;
import com.lume.client.fx.ParticleTexture;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.util.Identifier;

/**
 * Custom glowing hit particles (via ParticleEngine) spawned when you hit an
 * entity, always in the chosen Color — enabling this module always replaces
 * the vanilla crit/hit particles with ours (see ClientPlayNetworkHandlerMixin +
 * PlayerEntityMixin — vanilla crit/sweep particles for OUR OWN attacks are
 * spawned by a direct client-side call, not a packet, so both suppression
 * paths are needed) so only ours show and Color always has a visible effect.
 * "Only on Crit" → particles only spawn on a crit hit.
 * "Use My Particle" swaps the built-in glow shape for a user-dropped PNG
 * texture (see {@link ParticleTexture} — same drop-a-file idea as HitSound).
 */
public class HitParticles extends Module {

    public static final String FOLDER = "hitparticles";

    public final ModeSetting   style = add(new ModeSetting("Style", 0, "Burst", "Spark", "Ring", "Nova"));
    public final ModeSetting   shape = add(new ModeSetting("Shape", 0, ParticleEngine.SHAPE_NAMES));
    public final ColorSetting  color = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting count = add(new SliderSetting("Count", 24, 6, 60, true));
    public final SliderSetting speed = add(new SliderSetting("Speed", 4.0, 1.0, 12.0, false));
    public final SliderSetting size  = add(new SliderSetting("Size", 0.22, 0.05, 0.6, false));
    public final BoolSetting   onlyOnCrit = add(new BoolSetting("Only on Crit", false));
    public final BoolSetting   useMyParticle = add(new BoolSetting("Use My Particle", false));
    public String selectedFile = null;   // "My Particles" — which dropped-in .png to use (null = first found)

    // Loading happens here (throttled, onEnable/onTick), NOT inside resolveTexture() itself — a
    // real crash was traced to decoding the custom PNG straight from HitEffects.onAttack, i.e. on
    // every single hit, mid-combat. See ParticleTexture.Slot.
    private final ParticleTexture.Slot texSlot =
            new ParticleTexture.Slot(FOLDER, () -> useMyParticle.value, () -> selectedFile);

    public HitParticles() {
        super("Hit Particles", "Светящиеся частицы удара", Category.RENDER, -1);
        ParticleTexture.ensureReadme(FOLDER);
    }

    @Override
    public void onEnable() { texSlot.refresh(); }

    @Override
    public void onTick() { texSlot.refresh(); }   // cheap — throttled to ~1/sec, picks up a mid-session file change

    /** Resolves the currently selected drop-in texture, or null if "Use My Particle" is off / no file.
     *  Cheap — just returns whatever {@link #texSlot} already has cached, no disk I/O here. */
    public Identifier resolveTexture() {
        return texSlot.get();
    }
}

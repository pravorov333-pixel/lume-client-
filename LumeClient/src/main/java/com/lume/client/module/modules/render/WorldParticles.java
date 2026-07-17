package com.lume.client.module.modules.render;

import com.lume.client.fx.GlowParticle;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.fx.ParticleTexture;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.Random;

/**
 * Ambient soft-glow particles around the player, rendered by the additive
 * {@link ParticleEngine}. Pick a Type preset; physics is still fully tunable.
 * "Use My Particle" swaps the built-in glow shape for a user-dropped PNG
 * texture (see {@link ParticleTexture} — same drop-a-file idea as HitSound).
 */
public class WorldParticles extends Module {

    public static final String FOLDER = "worldparticles";

    public final ModeSetting   type       = add(new ModeSetting("Type", 0, "Orb", "Spark", "Ember", "Snow", "Star"));
    public final ModeSetting   shape      = add(new ModeSetting("Shape", 0, ParticleEngine.SHAPE_NAMES));
    public final ColorSetting  color      = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting rate       = add(new SliderSetting("Rate", 4, 1, 20, true));
    public final SliderSetting gravity    = add(new SliderSetting("Gravity", -0.4, -2.0, 2.0, false));
    public final SliderSetting lifetime   = add(new SliderSetting("Lifetime", 2.5, 0.5, 6.0, false));
    public final SliderSetting sizeS      = add(new SliderSetting("Size", 0.18, 0.05, 0.6, false));
    public final SliderSetting turbulence = add(new SliderSetting("Turbulence", 0.6, 0.0, 3.0, false));
    public final SliderSetting radius     = add(new SliderSetting("Radius", 8, 2, 20, true));
    public final BoolSetting   useMyParticle = add(new BoolSetting("Use My Particle", false));
    public String selectedFile = null;   // "My Particles" — which dropped-in .png to use (null = first found)

    private final Random rnd = new Random();

    public WorldParticles() {
        super("World Particles", "Мягкие светящиеся частицы вокруг игрока", Category.RENDER, -1);
        ParticleTexture.ensureReadme(FOLDER);
    }

    /** Resolves the currently selected drop-in texture, or null if "Use My Particle" is off / no file. */
    public Identifier resolveTexture() {
        if (!useMyParticle.value) return null;
        File[] files = ParticleTexture.list(FOLDER);
        if (files.length == 0) return null;
        File chosen = files[0];
        if (selectedFile != null) {
            for (File f : files) if (f.getName().equals(selectedFile)) { chosen = f; break; }
        }
        return ParticleTexture.get(chosen);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        // Type only shapes the physics preset now — colour always comes from the
        // Color setting (previously Ember/Snow silently overrode it to a fixed
        // orange/white, ignoring whatever the user picked; that was the bug).
        int rgb = color.accent ? Theme.accentRgb() : color.rgb();

        // per-type physics modifiers (each type moves clearly differently)
        float gMul = 1f, sMul = 1f, tMul = 1f, lMul = 1f;
        switch (type.index) {
            case 1 -> { sMul = 0.45f; tMul = 2.5f; lMul = 0.5f; }   // Spark — tiny, fast, jittery
            case 2 -> { gMul = -1.5f; sMul = 0.9f; tMul = 1.6f; }   // Ember — rises
            case 3 -> { gMul = 0.5f;  sMul = 1.3f; tMul = 0.3f; }   // Snow — falls, drifts
            case 4 -> { gMul = 0f;    sMul = 0.6f; tMul = 0.15f; lMul = 1.8f; }  // Star — small, slow twinkle
            default -> { }                                          // Orb — soft float
        }
        double r = radius.value;
        double px = mc.player.getX(), py = mc.player.getY() + 1, pz = mc.player.getZ();
        Identifier tex = resolveTexture();
        for (int i = 0; i < rate.getInt(); i++) {
            double x = px + (rnd.nextDouble() - 0.5) * 2 * r;
            double y = py + (rnd.nextDouble() - 0.5) * r;
            double z = pz + (rnd.nextDouble() - 0.5) * 2 * r;
            GlowParticle p = new GlowParticle(x, y, z, rgb);
            p.gravity = (float) (gravity.value * gMul);
            p.drag = 0.9f;
            p.turbulence = (float) (turbulence.value * tMul);
            p.size = (float) (sizeS.value * sMul);
            p.sizeEnd = p.size * 0.4f;
            p.alpha = 0.9f;
            p.maxLife = p.life = lifetime.value * lMul;
            p.vx = (rnd.nextDouble() - 0.5) * 0.2;
            p.vy = rnd.nextDouble() * 0.1;
            p.vz = (rnd.nextDouble() - 0.5) * 0.2;
            p.shape = shape.index;
            p.texture = tex;
            ParticleEngine.add(p);
        }
    }
}

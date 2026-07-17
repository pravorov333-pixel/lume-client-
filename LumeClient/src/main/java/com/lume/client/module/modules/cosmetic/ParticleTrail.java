package com.lume.client.module.modules.cosmetic;

import com.lume.client.fx.GlowParticle;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * Particle Trail — a light stream of glow particles left behind while
 * sprinting on the ground and/or gliding on an elytra. Reuses the same
 * soft-glow particle engine as World Particles, just spawned at the feet
 * (sprint) or back (glide) and drifting opposite your movement, instead of
 * floating in a cloud around you.
 */
public class ParticleTrail extends Module {

    public final BoolSetting   onSprint = add(new BoolSetting("On Sprint", true));
    public final BoolSetting   onGlide  = add(new BoolSetting("On Elytra", true));
    public final ModeSetting   shape    = add(new ModeSetting("Shape", 0, ParticleEngine.SHAPE_NAMES));
    public final ColorSetting  color    = add(new ColorSetting("Color", true, 183, 170, 217));
    public final SliderSetting rate     = add(new SliderSetting("Rate", 2, 1, 8, true));
    public final SliderSetting sizeS    = add(new SliderSetting("Size", 0.14, 0.05, 0.4, false));
    public final SliderSetting lifetime = add(new SliderSetting("Lifetime", 0.6, 0.2, 2.0, false));

    private final Random rnd = new Random();

    public ParticleTrail() {
        super("Particle Trail", "Glow particles while sprinting or gliding", Category.COSMETIC, -1);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        boolean sprint = onSprint.value && mc.player.isSprinting() && mc.player.isOnGround();
        boolean glide = onGlide.value && mc.player.isGliding();
        if (!sprint && !glide) return;

        int rgb = color.accent ? Theme.accentRgb() : color.rgb();
        double px = mc.player.getX(), pz = mc.player.getZ();
        double py = glide ? mc.player.getY() + mc.player.getHeight() * 0.6 : mc.player.getY() + 0.1;

        for (int i = 0; i < rate.getInt(); i++) {
            double x = px + (rnd.nextDouble() - 0.5) * 0.3;
            double y = py + (rnd.nextDouble() - 0.5) * 0.15;
            double z = pz + (rnd.nextDouble() - 0.5) * 0.3;
            GlowParticle p = new GlowParticle(x, y, z, rgb);
            p.gravity = glide ? 0f : 0.3f;
            p.drag = 0.9f;
            p.size = (float) sizeS.value;
            p.sizeEnd = 0f;
            p.alpha = 0.85f;
            p.maxLife = p.life = lifetime.value;
            p.vx = -mc.player.getVelocity().x * 0.4;
            p.vy = -mc.player.getVelocity().y * 0.2;
            p.vz = -mc.player.getVelocity().z * 0.4;
            p.shape = shape.index;
            ParticleEngine.add(p);
        }
    }
}

package com.lume.client.module.modules.cosmetic;

import com.lume.client.fx.GlowParticle;
import com.lume.client.fx.JumpFx;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;

import java.util.Random;

/**
 * Jump Particles — a one-shot effect at your feet whenever you jump. Three styles:
 * <b>Circle</b> — a ring that grows to swallow your hitbox, holds ~0.5s, fades (see {@link JumpFx}).
 * <b>Explosion</b> — a particle burst, capped so nothing reaches your own eye height (you shouldn't
 * see it yourself, just whoever's watching you in 3rd person / nearby).
 * <b>Wave</b> — concentric rings of ground tiles lighting up outward in sequence (see {@link JumpFx}).
 */
public class JumpParticles extends Module {

    public final ModeSetting  style = add(new ModeSetting("Style", 0, "Circle", "Explosion", "Wave"));
    public final ColorSetting color = add(new ColorSetting("Color", true, 183, 170, 217));

    private boolean wasOnGround = true;
    private final Random rnd = new Random();

    public JumpParticles() {
        super("Jump Particles", "One-shot effect at your feet when you jump", Category.COSMETIC, -1);
    }

    @Override
    public void onDisable() { wasOnGround = true; }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        boolean onGround = mc.player.isOnGround();
        boolean jumped = wasOnGround && !onGround && mc.player.getVelocity().y > 0.1;
        wasOnGround = onGround;
        if (!jumped) return;

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        int rgb = color.accent ? Theme.accentRgb() : color.rgb();

        switch (style.index) {
            case 0 -> JumpFx.spawnRing(x, y, z, rgb, 0.5f);   // block-sized (1 block diameter)
            case 1 -> spawnExplosion(x, y, z, rgb);
            case 2 -> JumpFx.spawnWave(x, y, z, rgb, 3, 0.9f);
        }
    }

    private void spawnExplosion(double x, double y, double z, int rgb) {
        float gravity = 2.2f;
        // Kinematics cap: a particle launched at v0 under constant gravity g peaks at v0²/(2g) —
        // clamp v0 so the peak height physically can't reach anywhere near your own eyes (80% of
        // the feet->eye gap), since you asked to not see this effect on yourself.
        double maxHeight = Math.max(0.1, (mc.player.getEyeY() - y) * 0.8);
        double maxV0 = Math.sqrt(2 * gravity * maxHeight);
        for (int i = 0; i < 24; i++) {
            GlowParticle p = new GlowParticle(x + (rnd.nextDouble() - 0.5) * 0.5, y + 0.05, z + (rnd.nextDouble() - 0.5) * 0.5, rgb);
            p.vy = Math.min(0.15 + rnd.nextDouble() * 0.35, maxV0);
            p.vx = (rnd.nextDouble() - 0.5) * 0.3;
            p.vz = (rnd.nextDouble() - 0.5) * 0.3;
            p.gravity = gravity;
            p.drag = 0.85f;
            p.size = 0.08f;
            p.sizeEnd = 0f;
            p.alpha = 0.9f;
            p.maxLife = p.life = 0.35 + rnd.nextDouble() * 0.25;
            ParticleEngine.add(p);
        }
    }
}

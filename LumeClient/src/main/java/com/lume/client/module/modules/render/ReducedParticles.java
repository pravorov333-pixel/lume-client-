package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.particle.ParticlesMode;

/**
 * Drops particle density to minimal for more FPS, restoring the previous
 * setting when disabled. No mixin needed — uses the particles option.
 */
public class ReducedParticles extends Module {

    private ParticlesMode previous = ParticlesMode.ALL;

    public ReducedParticles() {
        super("Reduced Particles", "Fewer particles, more FPS", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) {
            previous = mc.options.getParticles().getValue();
            mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.getParticles().setValue(previous);
        }
    }
}

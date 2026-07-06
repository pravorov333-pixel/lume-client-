package com.lume.client1165.module.modules.performance;

import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;
import net.minecraft.client.option.ParticlesMode;

public class ReducedParticles extends Module {

    private ParticlesMode previous = ParticlesMode.ALL;

    public ReducedParticles() {
        super("Reduced Particles", "Fewer particles, more FPS", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() {
        if (mc.options != null) {
            previous = mc.options.particles;
            mc.options.particles = ParticlesMode.MINIMAL;
        }
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.particles = previous;
    }
}

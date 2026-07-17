package com.lume.client.module.modules.render;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * No Hit Particles — a master "silence everything" switch, independent of
 * {@link HitParticles}. On: removes the vanilla crit/sweep particles AND our
 * own glowing hit particles, no matter what Hit Particles' own style/state is.
 * Separate module (not a setting on Hit Particles) so it's one click to go
 * fully particle-free without losing your Hit Particles configuration.
 */
public class NoHitParticles extends Module {

    public NoHitParticles() {
        super("No Hit Particles", "Removes ALL hit particles — vanilla and ours", Category.RENDER, -1);
    }

    public static boolean active() {
        Module m = LumeClient.MODULES.getByName("No Hit Particles");
        return m != null && m.isEnabled();
    }
}

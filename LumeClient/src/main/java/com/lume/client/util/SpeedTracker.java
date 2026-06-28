package com.lume.client.util;

import net.minecraft.client.MinecraftClient;

/**
 * Horizontal movement speed, sampled once per CLIENT TICK from the player's
 * authoritative position (not per render-frame), then averaged over a few ticks.
 * This makes the value rock-steady while running instead of flickering.
 */
public final class SpeedTracker {

    private static double lastX, lastZ;
    private static boolean has;
    private static final float[] ring = new float[6];
    private static int idx;

    private SpeedTracker() {}

    /** Call once per client tick. */
    public static void update(MinecraftClient mc) {
        if (mc.player == null) { has = false; return; }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (has) {
            double dx = x - lastX, dz = z - lastZ;
            ring[idx] = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0); // blocks per tick → per second
            idx = (idx + 1) % ring.length;
        }
        lastX = x;
        lastZ = z;
        has = true;
    }

    /** Averaged blocks/second. */
    public static float get() {
        float s = 0;
        for (float v : ring) s += v;
        return s / ring.length;
    }
}

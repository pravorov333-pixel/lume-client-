package com.lume.client.util;

/**
 * Small reusable "animate toward the latest target X" tracker — used for the
 * hotbar selection indicator (vanilla's own, and Custom Hotbar's rounded
 * replacement) so it slides between slots instead of snapping. Time-based
 * (not tick-based), so it's smooth regardless of framerate. One instance per
 * independent animated element — don't share between two different indicators.
 */
public final class SmoothSlot {

    private static final long DURATION_MS = 150;

    private float lastTarget = Float.NaN, fromX, toX;
    private long start = 0;

    /** Call once per frame with the instantaneous (unanimated) target X. Returns the eased X to draw at. */
    public float update(float targetX, boolean animate) {
        if (!animate || Float.isNaN(lastTarget)) {
            lastTarget = targetX;
            toX = targetX;
            return targetX;
        }
        if (targetX != lastTarget) {
            fromX = toX;
            toX = targetX;
            start = System.currentTimeMillis();
            lastTarget = targetX;
        }
        float p = Math.min(1f, (System.currentTimeMillis() - start) / (float) DURATION_MS);
        float e = 1f - (1f - p) * (1f - p);   // ease-out
        return fromX + (toX - fromX) * e;
    }
}

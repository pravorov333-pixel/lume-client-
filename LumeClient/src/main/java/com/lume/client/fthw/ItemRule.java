package com.lume.client.fthw;

/**
 * A FunTime/HolyWorld custom item the helper recognises by its display name.
 * {@code radius} (blocks) drives the ground ring; 0 = no ring (just info).
 * Radii are tunable estimates until verified in-game.
 */
public class ItemRule {

    public final String match;   // lowercase substring of the item's display name
    public final double radius;
    public final int color;
    public final String note;

    public ItemRule(String match, double radius, int color, String note) {
        this.match = match.toLowerCase();
        this.radius = radius;
        this.color = color;
        this.note = note;
    }
}

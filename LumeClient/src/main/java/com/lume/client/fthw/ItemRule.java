package com.lume.client.fthw;

/**
 * A FunTime/HolyWorld custom item the helper recognises by its display name.
 * Data-driven and EDITABLE in-game (Server tab) so the user can verify each item
 * against the live server and tune the numbers — radii/cooldowns from research
 * are only starting estimates.
 *
 * <p>{@code radius} (blocks) drives the ground ring (0 = no ring). {@code present}
 * = the user has confirmed this item currently exists on the server. {@code cat}
 * groups items in the encyclopedia (active throwable / passive sphere / talisman).
 */
public class ItemRule {

    public enum Cat { ACTIVE, SPHERE, TALISMAN }

    public final String name;        // display name (Russian)
    public final String match;       // lowercase substring of the item's display name
    public final Cat cat;
    public double radius;            // editable
    public int cooldownSec;          // editable (0 = unknown / passive)
    public final int color;
    public final String note;        // effect / stats description
    public boolean present = true;   // user-confirmed "exists on server right now"

    public ItemRule(String name, String match, Cat cat, double radius, int cooldownSec, int color, String note) {
        this.name = name;
        this.match = match.toLowerCase();
        this.cat = cat;
        this.radius = radius;
        this.cooldownSec = cooldownSec;
        this.color = color;
        this.note = note;
    }
}

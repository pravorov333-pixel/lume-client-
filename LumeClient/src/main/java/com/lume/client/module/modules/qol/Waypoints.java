package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Waypoints — saved location markers, drawn as 2D HUD markers (name + distance,
 * plus an edge arrow when off-screen). Add via the "Add Waypoint" key (B) or the
 * {@code .wp} chat commands; a "Death" marker is saved automatically on death.
 * Persisted in the config.
 *
 * <p>Rendering lives in {@code HudRenderer.renderWaypoints}.
 */
public class Waypoints extends Module {

    public static final class WP {
        public String name;
        public final double x, y, z;
        public int color;
        public WP(String name, double x, double y, double z, int color) {
            this.name = name; this.x = x; this.y = y; this.z = z; this.color = color;
        }
    }

    public static final List<WP> list = new ArrayList<>();

    /** Palette cycled through for auto-coloured waypoints. */
    private static final int[] PALETTE = {
            0xFFB7AAD9, 0xFF6FCF7F, 0xFFE8C15A, 0xFF6F9CE0, 0xFFE0789C, 0xFF63D6C4, 0xFFE0915A
    };
    private static int paletteIdx = 0;

    public final BoolSetting deathPoint = add(new BoolSetting("Death point", true));
    public final BoolSetting arrows = add(new BoolSetting("Edge arrows", true));

    public Waypoints() {
        super("Waypoints", "Saved location markers", Category.CHAT, -1);
    }

    public static int nextColor() {
        return PALETTE[paletteIdx++ % PALETTE.length];
    }

    public static void add(String name, double x, double y, double z, int color) {
        list.add(new WP(name, x, y, z, color));
    }
}

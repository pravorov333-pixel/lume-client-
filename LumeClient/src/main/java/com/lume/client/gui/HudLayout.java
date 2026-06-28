package com.lume.client.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-element HUD position offsets (GUI px) and per-element scale, set by the
 * ClickGUI editor (drag to move, slider under the selected element to resize).
 * Session-only for now (not persisted to disk).
 */
public final class HudLayout {

    private static final Map<String, int[]> offsets = new HashMap<>();
    private static final Map<String, Float> scales = new HashMap<>();

    private HudLayout() {}

    /** Mutable {dx, dy} offset for an element (created at {0,0} on first access). */
    public static int[] get(String name) {
        return offsets.computeIfAbsent(name, k -> new int[2]);
    }

    public static void set(String name, int dx, int dy) {
        int[] o = get(name);
        o[0] = dx;
        o[1] = dy;
    }

    public static float getScale(String name) {
        return scales.getOrDefault(name, 1f);
    }

    public static void setScale(String name, float s) {
        scales.put(name, Math.max(0.5f, Math.min(2f, s)));
    }

    /** Reset an element to its default position and size. */
    public static void reset(String name) {
        offsets.remove(name);
        scales.remove(name);
    }

    // --- config persistence access ---
    public static Map<String, int[]> offsetMap() { return offsets; }
    public static Map<String, Float> scaleMap() { return scales; }
}

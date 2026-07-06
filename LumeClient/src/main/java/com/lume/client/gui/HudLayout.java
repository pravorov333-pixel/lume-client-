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
    private static final Map<String, int[]> sizes = new HashMap<>();   // {w, h} — explicit resize, overrides auto-fit

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

    /** Explicit {w, h} resize override (GUI px, same unit as HudFrames base rects), or null = auto. */
    public static int[] getSize(String name) { return sizes.get(name); }

    public static void setSize(String name, int w, int h) {
        // "HUD" (main info panel) always renders as title row + one horizontal metrics row —
        // 32 GUI-px matches HudRenderer's HUD_MIN_LINES(2) floor at scale 1, so it can be
        // shrunk in Y much further than before (content no longer stacks vertically).
        int minH = "HUD".equals(name) ? 32 : 24;
        sizes.put(name, new int[]{ Math.max(60, w), Math.max(minH, h) });
    }

    /** Reset an element to its default position and size. */
    public static void reset(String name) {
        offsets.remove(name);
        scales.remove(name);
        sizes.remove(name);
    }

    /** Reset every HUD element (position, scale, resize) to its default — the "Reset HUD" button. */
    public static void resetAll() {
        offsets.clear();
        scales.clear();
        sizes.clear();
    }

    // --- config persistence access ---
    public static Map<String, int[]> offsetMap() { return offsets; }
    public static Map<String, Float> scaleMap() { return scales; }
    public static Map<String, int[]> sizeMap() { return sizes; }
}

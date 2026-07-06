package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;

/**
 * Toggle module that controls whether the on-screen HUD (watermark, FPS …) is
 * drawn, plus every optional info-line as its own sub-toggle (Coords, Ping, Day
 * Counter, CPS, Speed, Clock — these used to be separate top-level modules).
 * The actual drawing lives in {@link com.lume.client.gui.HudRenderer}. Position
 * is edited on-screen via the ClickGUI HUD editor (drag to move); the panel can
 * also be resized directly (corner handle) — see {@link com.lume.client.gui.HudLayout}
 * for the min-size-fits-3-lines enforcement.
 */
public class Hud extends Module {

    public final BoolSetting fps = add(new BoolSetting("FPS", true));
    public final BoolSetting coords = add(new BoolSetting("Coords", false));
    public final BoolSetting ping = add(new BoolSetting("Ping", false));
    public final BoolSetting dayCounter = add(new BoolSetting("Day Counter", false));
    public final BoolSetting cps = add(new BoolSetting("CPS", false));
    public final BoolSetting speed = add(new BoolSetting("Speed", false));
    public final BoolSetting clock = add(new BoolSetting("Clock", false));
    public final ColorSetting color = add(new ColorSetting("Color", true, 183, 170, 217));

    public Hud() {
        super("HUD", "On-screen info panel", Category.VISUALS, -1);
    }

    /** How many of the optional info-lines are currently on (panel comfortably fits 3). */
    public int activeLineCount() {
        int n = 0;
        if (fps.value) n++;
        if (coords.value) n++;
        if (ping.value) n++;
        if (dayCounter.value) n++;
        if (cps.value) n++;
        if (speed.value) n++;
        if (clock.value) n++;
        return n;
    }
}

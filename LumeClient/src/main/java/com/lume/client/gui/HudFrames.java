package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.fthw.ServerHelper;
import com.lume.client.module.modules.performance.JvmOptimizer;
import com.lume.client.module.modules.visual.ArmorHud;
import com.lume.client.module.modules.visual.TargetEsp;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the movable HUD element frames (name + default
 * GUI-space rect). Shared by the ClickGUI and the catalog sub-screens so HUD
 * elements can be dragged from any of them.
 */
public final class HudFrames {

    private HudFrames() {}

    /** Fills {@code names} and returns matching rects [x,y,w,h] in GUI px, offset by HudLayout. */
    public static List<int[]> list(int sw, int sh, List<String> names) {
        List<int[]> rects = new ArrayList<>();
        add(rects, names, "HUD", 6, 6, 134, 32);
        add(rects, names, "Potion HUD", sw - 150, 6, 144, 18);
        add(rects, names, "Module List", sw - 110, 6, 106, 40);
        TargetEsp espM = (TargetEsp) LumeClient.MODULES.getByName("Target ESP");
        addIf(rects, names, "Target HUD", sw / 2 - 95, 10, 190, 44, espM != null && espM.isEnabled() && espM.hud.value);
        add(rects, names, "Block Info", sw / 2 - 70, 10, 140, 32);
        add(rects, names, "Keystrokes", 12, sh - 150, 70, 70);
        add(rects, names, "Crit Helper", sw / 2 - 30, sh / 2 + 10, 60, 16);
        JvmOptimizer jvmM = (JvmOptimizer) LumeClient.MODULES.getByName("System Info");
        addIf(rects, names, "RAM Bar", 6, sh - 20, 100, 16, jvmM != null && jvmM.isEnabled() && jvmM.showRam.value);
        add(rects, names, "Inventory HUD", sw / 2 - 85, sh - 80, 170, 58);
        // Armor HUD's own footprint flips between a 4-wide row and a 4-tall column with its
        // "Flip Y axis" setting (see HudRenderer.renderArmor) — the drag frame has to flip with
        // it, or it stays stuck in the old orientation's shape and no longer traces the icons.
        ArmorHud armorM = (ArmorHud) LumeClient.MODULES.getByName("Armor HUD");
        boolean armorVert = armorM != null && armorM.flipY.value;
        int armorAx = sw / 2 + 95, armorAy = armorVert ? sh - 19 - 3 * 18 : sh - 19;
        if (armorVert) add(rects, names, "Armor HUD", armorAx - 6, armorAy - 1, 30, 74);
        else add(rects, names, "Armor HUD", armorAx - 6, armorAy - 1, 84, 20);
        add(rects, names, "Totem Counter", sw / 2 - 138, sh - 22, 46, 20);
        ServerHelper shm = (ServerHelper) LumeClient.MODULES.getByName("Server Helper");
        boolean ftOn = shm != null && shm.isEnabled();
        addIf(rects, names, "FT Events", 6, 150, 140, 50, ftOn && shm.eventsHud.value);
        addIf(rects, names, "Effects", 6, sh / 2 - 30, 120, 60, ftOn && shm.effects.value);
        addIf(rects, names, "Quick Commands", sw - 130, sh / 2 - 40, 124, 80, ftOn && shm.quickCmds.value);
        addIf(rects, names, "Item Helper", sw / 2 - 80, sh - 64, 160, 30, ftOn && shm.itemHelper.value);
        return rects;
    }

    private static void add(List<int[]> rects, List<String> names, String name, int bx, int by, int w, int h) {
        Module m = LumeClient.MODULES.getByName(name);
        if (m == null || !m.isEnabled()) return;
        int[] off = HudLayout.get(name);
        int[] size = HudLayout.getSize(name);
        if (size != null) { w = size[0]; h = size[1]; }
        rects.add(new int[]{ bx + off[0], by + off[1], w, h });
        names.add(name);
    }

    private static void addIf(List<int[]> rects, List<String> names, String name, int bx, int by, int w, int h, boolean visible) {
        if (!visible) return;
        int[] off = HudLayout.get(name);
        rects.add(new int[]{ bx + off[0], by + off[1], w, h });
        names.add(name);
    }
}

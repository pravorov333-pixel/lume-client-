package com.lume.client.module.modules.render;

import java.util.ArrayList;
import java.util.List;

/** Player-saved Custom Hand poses (right hand, full pose — Pos+Scale+Rot together), picked from
 *  inside {@code CustomHand}'s "Custom" style. Separate from the built-in named Style presets
 *  (Side/Lay/Far/Stand, hardcoded in {@code CustomHand}) — this list is entirely user-created via
 *  the ClickGUI's "Save current as…" field and persisted in the config (see {@code Config}). */
public final class HandPresets {
    private HandPresets() {}

    public static final class Preset {
        public final String name;
        public final double posX, posY, posZ, scale, rotX, rotY, rotZ;
        public Preset(String name, double posX, double posY, double posZ, double scale, double rotX, double rotY, double rotZ) {
            this.name = name;
            this.posX = posX; this.posY = posY; this.posZ = posZ;
            this.scale = scale;
            this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
        }
    }

    public static final List<Preset> list = new ArrayList<>();

    /** Saving under a name that already exists overwrites it (re-tuned and re-saved). */
    public static void save(String name, double posX, double posY, double posZ, double scale, double rotX, double rotY, double rotZ) {
        list.removeIf(p -> p.name.equalsIgnoreCase(name));
        list.add(new Preset(name, posX, posY, posZ, scale, rotX, rotY, rotZ));
    }

    public static void remove(String name) {
        list.removeIf(p -> p.name.equalsIgnoreCase(name));
    }

    public static Preset byName(String name) {
        for (Preset p : list) if (p.name.equals(name)) return p;
        return null;
    }
}

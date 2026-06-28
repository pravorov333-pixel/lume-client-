package com.lume.client.module.modules.fthw;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/**
 * FT/HW Helper (scaffold) — base for the FunTime/HolyWorld helper. Right now it
 * only detects the server (see {@link com.lume.client.fthw.ServerType}); event
 * timers/counters and visible-effect radii will hang off this. Use {@code .ft}
 * to see status.
 */
public class ServerHelper extends Module {

    public final BoolSetting showServer = add(new BoolSetting("Show server", true));

    public ServerHelper() {
        super("Server Helper", "FunTime / HolyWorld helper (WIP)", Category.CHAT, -1);
    }

    @Override
    public void onTick() {
        // only usable on a supported anarchy — auto-disable the moment we leave one
        if (com.lume.client.fthw.ServerType.current() == com.lume.client.fthw.ServerType.UNKNOWN) {
            setEnabled(false);
        }
    }
}

package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.social.Friends;

/**
 * Fast Waypoint — a momentary bind (Binds tab): pressing its key pings your
 * current position to every friend, showing a highlighted marker on their
 * screen for ~5s with a pling sound. Not a persistent module state — firing
 * immediately flips itself back off so the same key fires again next press.
 * The actual network/expiry logic lives in {@link Friends#pingHere()}.
 */
public class FastWaypoint extends Module {

    public final BoolSetting pingSound = add(new BoolSetting("Ping Sound", true));

    public FastWaypoint() {
        super("Fast Waypoint", "Ping your position to friends for 5s", Category.CHAT, -1);
        setBindable(true);
        setBindMode(BindMode.TOGGLE);
    }

    @Override
    public void onEnable() {
        Friends.pingHere();
        setEnabled(false);
    }
}

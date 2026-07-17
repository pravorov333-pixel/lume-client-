package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.module.setting.StringSetting;

/** Sends a chat message/command on a repeating interval while enabled (e.g. an AFK-fishing "/ping" keepalive). */
public class AutoCommand extends Module {

    public final StringSetting command = add(new StringSetting("Command", "/msg me afk"));
    public final SliderSetting interval = add(new SliderSetting("Interval (s)", 30, 1, 600, true));

    private long lastSend = 0;

    public AutoCommand() {
        super("Auto Command", "Sends a command/message on a repeating interval", Category.CHAT, -1);
    }

    @Override
    public void onEnable() { lastSend = System.currentTimeMillis(); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null || command.value.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now - lastSend < interval.getInt() * 1000L) return;
        lastSend = now;
        String text = command.value.trim();
        if (text.startsWith("/")) mc.getNetworkHandler().sendChatCommand(text.substring(1));
        else mc.getNetworkHandler().sendChatMessage(text);
    }
}

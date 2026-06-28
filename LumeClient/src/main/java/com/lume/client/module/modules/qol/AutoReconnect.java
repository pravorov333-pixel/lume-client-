package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * Auto-Reconnect — when you get kicked / lose connection, Lume waits a few
 * seconds and rejoins the same server automatically. Handy for anarchy queues.
 *
 * <p>The last server you joined is remembered by {@link com.lume.client.LumeClient}
 * (captured on connect). The countdown overlay on the disconnect screen is drawn
 * there too; this module only owns the timer and the actual rejoin.
 */
public class AutoReconnect extends Module {

    /** Seconds to wait before rejoining. */
    public static final int DELAY_SECONDS = 5;

    /** The server we were last connected to (set on join). */
    public static ServerInfo lastServer = null;

    private int ticks = 0;

    public AutoReconnect() {
        super("Auto Reconnect", "Rejoin the server after a disconnect", Category.CHAT, -1);
    }

    /** Seconds left until the rejoin fires (for the on-screen countdown). 0 = inactive. */
    public int secondsLeft() {
        if (!isEnabled() || lastServer == null) return 0;
        int left = DELAY_SECONDS - ticks / 20;
        return Math.max(0, left);
    }

    @Override
    public void onDisable() {
        ticks = 0;
    }

    @Override
    public void onTick() {
        // onTick still fires while sitting on the disconnect screen.
        if (mc.currentScreen instanceof DisconnectedScreen && lastServer != null) {
            ticks++;
            if (ticks >= DELAY_SECONDS * 20) {
                ticks = 0;
                reconnect();
            }
        } else {
            ticks = 0;
        }
    }

    private void reconnect() {
        ServerInfo info = lastServer;
        if (info == null) return;
        try {
            ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(info.address), info, false, null);
            System.out.println("[Lume] Auto Reconnect -> " + info.address);
        } catch (Throwable t) {
            System.out.println("[Lume] Auto Reconnect failed: " + t.getMessage());
        }
    }
}

package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.social.DiscordRpc;
import net.minecraft.client.network.ServerInfo;

/**
 * Discord Rich Presence — shows what server/mode you're on in your Discord
 * profile. No user-facing setting: the Client ID is baked into the client
 * itself (below) so it works out of the box for every install, same as the
 * Telegram bot token / Railway account elsewhere in this project — it's tied
 * to Lume's own Discord Application, created once at
 * discord.com/developers/applications, not something a user provides.
 */
public class DiscordPresence extends Module {

    // TODO: replace with Lume's actual Discord Application Client ID (Discord Developer
    // Portal -> Applications -> your app -> General Information -> Application ID).
    private static final String CLIENT_ID = "PASTE_DISCORD_APPLICATION_ID_HERE";

    private long lastUpdate = 0;

    public DiscordPresence() {
        super("Discord Rich Presence", "Shows what you're playing in Discord", Category.CHAT, -1);
        // Always on (no enable/disable toggle in ClickGUI — the card only exists so the
        // Client ID field is reachable) — see ModuleManager.init() for the default-enabled part.
        setToggleable(false);
    }

    @Override
    public void onEnable() {
        tryConnect();
    }

    @Override
    public void onDisable() {
        async(DiscordRpc::disconnect);
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastUpdate < 15000) return;
        lastUpdate = now;

        if (!DiscordRpc.connected()) { tryConnect(); return; }

        String state, details;
        if (mc.world == null) {
            state = "In Menu";
            details = "Idle";
        } else {
            ServerInfo s = mc.getCurrentServerEntry();
            state = s != null ? s.name : "Singleplayer";
            details = "Playing Lume Client";
        }
        async(() -> DiscordRpc.setActivity(state, details));
    }

    private void tryConnect() {
        if (CLIENT_ID.isEmpty() || CLIENT_ID.startsWith("PASTE_")) return;
        async(() -> DiscordRpc.connect(CLIENT_ID));
    }

    private static void async(Runnable r) {
        Thread t = new Thread(r, "Lume-DiscordRPC");
        t.setDaemon(true);
        t.start();
    }
}

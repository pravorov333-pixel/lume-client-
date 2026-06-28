package com.lume.client;

import com.lume.client.gui.ClickGuiScreen;
import com.lume.client.gui.HudRenderer;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.ModuleManager;
import com.lume.client.module.Module;
import com.lume.client.command.CommandManager;
import com.lume.client.fthw.EventManager;
import com.lume.client.module.modules.qol.AntiSpam;
import com.lume.client.module.modules.qol.AutoReconnect;
import com.lume.client.module.modules.qol.ChatTimestamps;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.util.SpeedTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Lume Client — entry point.
 * Wires up the module manager, the ClickGUI keybind (Right Shift), the HUD,
 * the chat QoL hooks and the cosmetic screen overlays.
 */
public class LumeClient implements ClientModInitializer {

    public static final String NAME = "Lume Client";
    public static final String VERSION = "1.0.0";

    public static final ModuleManager MODULES = new ModuleManager();

    private static KeyBinding openGuiKey;
    private static KeyBinding addWaypointKey;
    private boolean deathHandled = false;

    @Override
    public void onInitializeClient() {
        MODULES.init();
        Config.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> Config.save());

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lume.clickgui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.lume"));
        addWaypointKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lume.waypoint",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.lume"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                client.setScreen(new ClickGuiScreen());
            }
            while (addWaypointKey.wasPressed()) addWaypointHere(client);
            handleDeathWaypoint(client);
            SpeedTracker.update(client);
            EventManager.tick();
            MODULES.onTick();
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> HudRenderer.render(ctx));

        registerChatHooks();
        registerConnectionHooks();
        registerScreenHooks();

        System.out.println("[Lume] " + NAME + " " + VERSION + " initialized");
    }

    // --- Waypoints ----------------------------------------------------------

    private void addWaypointHere(MinecraftClient client) {
        Module m = MODULES.getByName("Waypoints");
        if (m instanceof Waypoints wp && wp.isEnabled() && client.player != null) {
            Waypoints.add("WP" + (Waypoints.list.size() + 1),
                    client.player.getX(), client.player.getY(), client.player.getZ(), Theme.accent());
            Config.save();
        }
    }

    private void handleDeathWaypoint(MinecraftClient client) {
        if (client.currentScreen instanceof DeathScreen) {
            if (!deathHandled) {
                deathHandled = true;
                Module m = MODULES.getByName("Waypoints");
                if (m instanceof Waypoints wp && wp.isEnabled() && wp.deathPoint.value && client.player != null) {
                    Waypoints.add("Death", client.player.getX(), client.player.getY(), client.player.getZ(), 0xFFE05656);
                    Config.save();
                }
            }
        } else {
            deathHandled = false;
        }
    }

    // --- Chat & QoL ---------------------------------------------------------

    private void registerChatHooks() {
        // Anti-Spam: drop duplicate incoming chat lines.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!overlay) EventManager.onChat(message.getString());   // observe for FT/HW events
            if (overlay) return true; // never touch action-bar messages
            Module m = MODULES.getByName("Anti-Spam");
            if (m instanceof AntiSpam as && as.isEnabled()) {
                return !as.shouldBlock(message);
            }
            return true;
        });

        // Lume chat commands (".wp", ".macro", ".bind", ".help") — handled locally, not sent.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !CommandManager.handle(message));

        // Chat Timestamps: prepend [HH:mm] to incoming chat lines.
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay) return message;
            Module m = MODULES.getByName("Chat Timestamps");
            if (m instanceof ChatTimestamps ts && ts.isEnabled()) {
                return ts.stamp(message);
            }
            return message;
        });
    }

    // --- Auto Reconnect server memory --------------------------------------

    private void registerConnectionHooks() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerInfo s = client.getCurrentServerEntry();
            if (s != null) AutoReconnect.lastServer = s;
        });
    }

    // --- Cosmetic screen overlays ------------------------------------------

    private void registerScreenHooks() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof DisconnectedScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, td) -> drawReconnectCountdown(s, ctx));
            } else if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, td) -> drawMenuLogo(s, ctx));
            }
        });
    }

    private static void drawReconnectCountdown(net.minecraft.client.gui.screen.Screen screen, DrawContext ctx) {
        Module m = MODULES.getByName("Auto Reconnect");
        if (!(m instanceof AutoReconnect ar) || !ar.isEnabled() || AutoReconnect.lastServer == null) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        String label = "Lume · reconnecting in " + ar.secondsLeft() + "s";
        int tw = RenderUtil.width(tr, label, 0.5f);
        int pw = tw + 28, ph = 22;
        int x = (screen.width - pw) / 2;
        int y = screen.height - 52;

        RenderUtil.glow(ctx, x, y, pw, ph, 8, Theme.accentRgb(), 3);
        RenderUtil.gradientRoundedRect(ctx, x, y, pw, ph, 8, Theme.winTop(), Theme.winBot());
        RenderUtil.text(ctx, tr, label, x + 14, y + 7, Theme.txt(), false, 0.5f);
    }

    private static void drawMenuLogo(net.minecraft.client.gui.screen.Screen screen, DrawContext ctx) {
        Module m = MODULES.getByName("Menu Logo");
        if (m == null || !m.isEnabled()) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int x = 12, y = 12, size = 22;
        RenderUtil.glow(ctx, x, y, size, size, 7, Theme.accentRgb(), 3);
        RenderUtil.drawLogo(ctx, x, y, size);
        int tx = x + size + 8;
        RenderUtil.text(ctx, tr, "lume", tx, y + 2, Theme.accent(), false, 0.6f);
        int lw = RenderUtil.width(tr, "lume", 0.6f);
        RenderUtil.text(ctx, tr, "client", tx + lw + 5, y + 3, Theme.txtDim(), false, 0.6f);
        RenderUtil.text(ctx, tr, "v" + VERSION, tx, y + 14, Theme.txtDim(), false, 0.4f);
    }
}

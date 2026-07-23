package com.lume.client.mixin;

import com.lume.client.Config;
import com.lume.client.gui.LoadingScreen;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.util.AltService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Custom Menu — paints our own logo+bar over "Connecting.../Logging in...", showing vanilla's own live status text (no real % here). */
@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Shadow private Text status;

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void lume$overlay(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!CustomMenu.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        String s = status != null ? status.getString() : null;
        LoadingScreen.draw(ctx, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(), -1f, s);
    }

    /**
     * Nickname-to-server binding (Custom Menu's Account Manager): if the server being connected
     * to has a saved nickname bound to it ({@link Config#accountServers}), switch to that
     * nickname instantly via {@link AltService} right before the connection actually starts. A
     * single hook on the static {@code connect(...)} entry point covers BOTH vanilla's own
     * Multiplayer server list AND Fast Connect — everything funnels through this one method.
     */
    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;" +
            "Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"), require = 0)
    private static void lume$applyAccountBinding(Screen parent, MinecraftClient client, ServerAddress address,
                                                  ServerInfo info, boolean quickPlay, CookieStorage cookies, CallbackInfo ci) {
        if (address == null || Config.accountServers.isEmpty()) return;
        String target = address.getAddress() + ":" + address.getPort();
        for (Map.Entry<String, String> en : Config.accountServers.entrySet()) {
            ServerAddress bound = ServerAddress.parse(en.getValue());
            if (target.equalsIgnoreCase(bound.getAddress() + ":" + bound.getPort())) {
                AltService.changeName(en.getKey());
                Config.preferredAccount = en.getKey();
                Config.save();
                return;
            }
        }
    }
}

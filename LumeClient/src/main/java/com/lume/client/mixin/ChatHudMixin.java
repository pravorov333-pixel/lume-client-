package com.lume.client.mixin;

import com.lume.client.LumeClient;
import com.lume.client.gui.Notifications;
import com.lume.client.gui.Theme;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CleanView;
import com.lume.client.module.modules.qol.BetterChat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Better Chat: Ctrl+Click a chat line (chat window open) copies its text. */
@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$hideChat(DrawContext ctx, int mouseX, int mouseY, int tickDelta, boolean focused, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (CleanView.hideChat() && !(mc.currentScreen instanceof ChatScreen)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void lume$copyOnClick(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        Module m = LumeClient.MODULES.getByName("Better Chat");
        if (!(m instanceof BetterChat bc) || !bc.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        long win = mc.getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (!ctrl) return;

        ChatHudAccessor acc = (ChatHudAccessor) (Object) this;
        int idx = acc.lume$getMessageIndex(mouseX, mouseY);
        List<ChatHudLine> messages = acc.lume$getMessages();
        if (idx < 0 || idx >= messages.size()) return;

        String text = messages.get(idx).content().getString();
        mc.keyboard.setClipboard(text);
        Notifications.push("Copied to clipboard", Theme.accent(), 1500);
        cir.setReturnValue(true);
    }
}

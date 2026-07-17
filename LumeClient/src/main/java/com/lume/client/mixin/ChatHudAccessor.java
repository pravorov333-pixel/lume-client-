package com.lume.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** Read access to {@code ChatHud}'s private message list/index lookup, for {@link ChatHudMixin}. */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("messages")
    List<ChatHudLine> lume$getMessages();

    @Invoker("getMessageIndex")
    int lume$getMessageIndex(double mouseX, double mouseY);
}

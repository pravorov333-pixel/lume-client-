package com.lume.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Public bridge to {@code MinecraftClient#doAttack}/{@code #doItemUse} (both private in vanilla) for Autoclicker/Fast XP. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Invoker("doAttack")
    boolean lume$doAttack();

    @Invoker("doItemUse")
    void lume$doItemUse();
}

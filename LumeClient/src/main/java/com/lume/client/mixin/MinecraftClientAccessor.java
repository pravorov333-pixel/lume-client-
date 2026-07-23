package com.lume.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Public bridge to {@code MinecraftClient#doAttack}/{@code #doItemUse} (both private in vanilla) for Autoclicker/Fast XP. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Invoker("doAttack")
    boolean lume$doAttack();

    @Invoker("doItemUse")
    void lume$doItemUse();

    /** {@code session} is declared {@code final} — {@code @Mutable} generates a setter for it
     *  anyway, letting {@code util.AltService} swap the active session in place for an instant
     *  (no-restart) nickname switch. */
    @Mutable
    @Accessor("session")
    void lume$setSession(Session session);
}

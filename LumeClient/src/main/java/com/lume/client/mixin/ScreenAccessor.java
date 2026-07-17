package com.lume.client.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Public bridge to {@code Screen#clearChildren} (protected in vanilla) for {@code LumeTitleMenu}. */
@Mixin(Screen.class)
public interface ScreenAccessor {
    @Invoker("clearChildren")
    void lume$clearChildren();
}

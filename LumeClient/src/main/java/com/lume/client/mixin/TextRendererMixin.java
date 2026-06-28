package com.lume.client.mixin;

import com.lume.client.module.modules.cosmetic.GameFont;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Redirects the vanilla "default" font to a Lume font chosen by the Game Font
 * module, so chat / tooltips / signs render in the picked typeface. Only the
 * default font is swapped — our own HUD/menu fonts and the unicode font are
 * untouched. require=0 so a mapping change can't crash the client.
 */
@Mixin(TextRenderer.class)
public class TextRendererMixin {

    private static final Identifier LUME$DEFAULT = Identifier.ofVanilla("default");

    @ModifyVariable(method = "getFontStorage", at = @At("HEAD"), argsOnly = true, require = 0)
    private Identifier lume$swapFont(Identifier id) {
        if (id == null || !id.equals(LUME$DEFAULT)) return id;
        Identifier rep = GameFont.selectedFont();
        return rep != null ? rep : id;
    }
}

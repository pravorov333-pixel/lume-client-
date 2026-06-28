package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ModeSetting;
import net.minecraft.util.Identifier;

/**
 * Game Font — replaces Minecraft's default font (chat, item tooltips, signs,
 * scoreboards, …) with a bundled TTF, chosen live in the client. The Lume HUD /
 * menu keep their own font; only vanilla "default" text changes.
 *
 * <p>The swap happens in {@code TextRendererMixin}, which calls
 * {@link #selectedFont()} to know which font id to substitute.
 */
public class GameFont extends Module {

    public final ModeSetting font =
            add(new ModeSetting("Font", 0, "Default", "Roboto", "Open Sans", "PT Sans", "Noto Sans", "JetBrains"));

    public GameFont() {
        super("Game Font", "Change the in-game (chat / tooltip) font", Category.COSMETIC, -1);
    }

    /** The Lume font id to use instead of minecraft:default, or null to leave vanilla. */
    public static Identifier selectedFont() {
        Module m = LumeClient.MODULES.getByName("Game Font");
        if (!(m instanceof GameFont gf) || !gf.isEnabled()) return null;
        return switch (gf.font.get()) {
            case "Roboto" -> Identifier.of("lume", "roboto");
            case "Open Sans" -> Identifier.of("lume", "opensans");
            case "PT Sans" -> Identifier.of("lume", "ptsans");
            case "Noto Sans" -> Identifier.of("lume", "notosans");
            case "JetBrains" -> Identifier.of("lume", "jbmono");
            default -> null; // "Default" → keep vanilla
        };
    }
}

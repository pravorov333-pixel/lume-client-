package com.lume.client.module.modules.qol;

import com.lume.client.LumeClient;
import com.lume.client.audio.CustomAudioPlayer;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.Locale;

/** Plays a mechanical-keyboard click on every key press — drop {@code .ogg} files into {@code
 *  lume/custom_sounds/keysounds}, named {@code <style>_click.ogg} and {@code <style>_space.ogg}
 *  per switch "style" (e.g. {@code 1_click.ogg}/{@code 1_space.ogg} for style 1). {@link #style}
 *  picks which style plays; within that style, {@code _space} plays for Space/Enter/Backspace
 *  and {@code _click} plays for every other key.
 *
 * <p>Fires on every raw key press regardless of whether a screen is open (typing in chat/menus
 * should click too, not just in-world) — see {@code KeyboardMixin}. */
public class KeySounds extends Module {

    public static final String FOLDER = "keysounds";

    public final SliderSetting volume = add(new SliderSetting("Volume", 60, 0, 100, true));
    public final ModeSetting style = add(new ModeSetting("Style", 0, "1", "2", "3", "4"));

    public KeySounds() {
        super("Key Sounds", "Mechanical-keyboard click on every key press (drop your own .ogg files in)", Category.CHAT, -1);
        CustomAudioPlayer.ensureReadme(FOLDER);
    }

    @Override
    public void onEnable() {
        CustomAudioPlayer.preload(FOLDER);   // decode once up front, not on the first keystroke
    }

    private static boolean isSpaceGroup(int key) {
        return key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_BACKSPACE;
    }

    /** Called from {@code KeyboardMixin} on every raw GLFW key press. */
    public static void onKeyPressed(int key) {
        Module m = LumeClient.MODULES.getByName("Key Sounds");
        if (!(m instanceof KeySounds ks) || !ks.isEnabled()) return;

        String stylePrefix = ks.style.get();   // "1" / "2" / "3"
        String suffix = isSpaceGroup(key) ? "space" : "click";
        String wanted = (stylePrefix + "_" + suffix).toLowerCase(Locale.ROOT);

        File match = null;
        for (File f : CustomAudioPlayer.list(FOLDER)) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            int dot = name.lastIndexOf('.');
            if ((dot < 0 ? name : name.substring(0, dot)).equals(wanted)) { match = f; break; }
        }
        if (match == null) {
            System.out.println("[Lume] KeySounds: no file named " + wanted + ".ogg in " + CustomAudioPlayer.folder(FOLDER));
            return;
        }
        CustomAudioPlayer.play(match, (float) (ks.volume.value / 100.0), 1f);
    }
}

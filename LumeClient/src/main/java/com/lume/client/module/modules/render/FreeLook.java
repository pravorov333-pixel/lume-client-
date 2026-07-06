package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.client.option.Perspective;

/**
 * FreeLook — decouples camera rotation from the player body. Bind-only (no menu
 * card — hidden from the module grid in ClickGuiScreen, set the key in the Binds
 * tab). While the bind key is held, mouse moves the camera but the player body
 * keeps facing the last direction; the view auto-switches to third-person (back)
 * so you can actually see the body turn, and reverts to whatever it was before
 * on release. Rendered via FreeLookMixin (see mixin/FreeLookMixin.java).
 */
public class FreeLook extends Module {

    public static float freeYaw   = 0f;
    public static float freePitch = 0f;
    public static boolean active  = false;   // true while the module is on

    private Perspective prevPerspective = Perspective.FIRST_PERSON;

    public FreeLook() {
        super("Free Look", "Rotate camera without turning the player", Category.RENDER, -1);
        setBindable(true);
        setBindMode(BindMode.HOLD);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            freeYaw   = mc.player.getYaw();
            freePitch = mc.player.getPitch();
        }
        if (mc.options != null) {
            prevPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
        // restore so the camera snaps back to player facing
        if (mc.player != null) {
            freeYaw   = mc.player.getYaw();
            freePitch = mc.player.getPitch();
        }
        if (mc.options != null) mc.options.setPerspective(prevPerspective);
    }
}

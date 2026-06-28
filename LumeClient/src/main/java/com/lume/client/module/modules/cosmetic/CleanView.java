package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/**
 * Clean View — removes jarring camera motion: the red tilt when you take damage
 * and (optionally) the walking view-bob. Cosmetic / comfort. Applied in
 * {@code GameRendererMixin}.
 */
public class CleanView extends Module {

    public final BoolSetting hurtCam = add(new BoolSetting("No hurt cam", true));
    public final BoolSetting viewBob = add(new BoolSetting("No view bob", false));

    public CleanView() {
        super("Clean View", "No hurt tilt / steadier view", Category.COSMETIC, -1);
    }

    public static boolean noHurtCam() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.hurtCam.value;
    }

    public static boolean noBob() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.viewBob.value;
    }
}

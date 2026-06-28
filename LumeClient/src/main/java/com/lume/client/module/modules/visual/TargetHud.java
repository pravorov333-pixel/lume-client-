package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/**
 * Target HUD — shows the name and health of the living entity you can hit
 * (within attack reach, under the crosshair), with a 3D head, a health bar and
 * a smooth damage animation.
 *
 * <p>Legit by design: uses the vanilla targeted entity (already wall-blocked &
 * reach-limited) and never shows an <b>invisible</b> entity. Rendering lives in
 * {@code HudRenderer}; this class holds the toggle + options.
 */
public class TargetHud extends Module {

    public final BoolSetting head = add(new BoolSetting("3D head", true));
    public final BoolSetting healthBar = add(new BoolSetting("Health bar", true));
    public final BoolSetting animate = add(new BoolSetting("Animate HP", true));

    public TargetHud() {
        super("Target HUD", "Show HP & name of what you can hit", Category.VISUALS, -1);
    }
}

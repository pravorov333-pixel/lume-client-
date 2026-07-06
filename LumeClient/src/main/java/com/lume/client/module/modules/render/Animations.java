package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Hand Animations — smooth first-person viewmodel motion, inspired by the
 * general idea behind "smooth viewmodel" mods (sway/bob/swing-ease), written
 * from scratch: view sway (the hand lags slightly behind camera turns), an
 * idle breathing bob, an extra sprint bob/tilt, and an eased swing punch —
 * on top of the original static rotation preset. All independently toggleable.
 */
public class Animations extends Module {

    public final ModeSetting   preset = add(new ModeSetting("Preset", 0,
            "Toward You", "Away", "Tilt In", "Tilt Out", "Vertical", "Flat", "Reverse", "Custom", "None"));
    public final SliderSetting angle  = add(new SliderSetting("Angle", 45, 0, 180, true));

    /** Only used when Preset = "Custom" — full free rotation on every axis. */
    public final SliderSetting customRotX = add(new SliderSetting("Custom Rot X", 0, -180, 180, true));
    public final SliderSetting customRotY = add(new SliderSetting("Custom Rot Y", 0, -180, 180, true));
    public final SliderSetting customRotZ = add(new SliderSetting("Custom Rot Z", 0, -180, 180, true));

    public final BoolSetting   sway       = add(new BoolSetting("View Sway", true));
    public final SliderSetting swayAmount = add(new SliderSetting("Sway Amount", 1.0, 0.2, 3.0, false));
    public final BoolSetting   idleBob    = add(new BoolSetting("Idle Bob", true));
    public final BoolSetting   sprintBob  = add(new BoolSetting("Sprint Bob", true));
    public final BoolSetting   swingEase  = add(new BoolSetting("Swing Ease", true));

    public Animations() {
        super("Hand Animations", "Плавные анимации руки: sway, bob, swing", Category.RENDER, -1);
    }
}

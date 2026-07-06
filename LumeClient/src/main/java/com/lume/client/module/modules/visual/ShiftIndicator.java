package com.lume.client.module.modules.visual;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Crit Helper — draggable/resizable HUD element (like any other) showing when a
 * critical hit is ready and when the attack cooldown is full. Two styles: a
 * filling Bar, or an accumulating 1..100 Percent readout (both re-colour as they
 * approach ready). A crit lands when you hit while airborne and falling
 * (velocity.y < 0), not sprinting. Rendered by HudRenderer.renderShiftIndicator().
 */
public class ShiftIndicator extends Module {

    public final ModeSetting  style = add(new ModeSetting("Style", 0, "Bar", "Percent"));
    public final SliderSetting size = add(new SliderSetting("Size", 16, 10, 30, true));
    public final ColorSetting color = add(new ColorSetting("Color", true, 183, 170, 217));

    public ShiftIndicator() {
        super("Crit Helper", "Показывает готовность крита и удара", Category.VISUALS, -1);
    }

    /** Returns true if a hit right now would land a critical. */
    public static boolean canCrit(net.minecraft.client.MinecraftClient mc) {
        if (mc.player == null) return false;
        return !mc.player.isOnGround()                     // must be airborne
                && mc.player.getVelocity().y < 0            // and moving downward
                && !mc.player.isInLava()
                && !mc.player.isSubmergedInWater()
                && !mc.player.isClimbing()
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                && !mc.player.isSprinting();                // sprint-hits cancel crits
    }
}

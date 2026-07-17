package com.lume.client.module.modules.cosmetic;

import com.lume.client.fx.BackTrail;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Trail — a single connected ribbon/line out of your back, not individual particles (that's
 * {@link com.lume.client.module.modules.render.ParticleTrail}). Only grows while you're actually
 * sprinting forward; the moment you stop it retracts (eats itself from the tail) until it catches
 * up and vanishes, instead of freezing in place. Fades along its length (not time) up to a
 * configurable max — see {@link BackTrail}.
 */
public class Trail extends Module {

    public final ModeSetting   style     = add(new ModeSetting("Style", 0, "Rectangle", "Thin Line"));
    public final ColorSetting  color     = add(new ColorSetting("Color", true, 183, 170, 217));
    public final SliderSetting maxLength = add(new SliderSetting("Max Length", 1.2, 0.2, 2.0, false));
    public final SliderSetting width     = add(new SliderSetting("Width", 0.25, 0.05, 0.6, false));

    public Trail() {
        super("Trail", "A connected ribbon trailing from your back", Category.COSMETIC, -1);
    }

    @Override
    public void onDisable() {
        BackTrail.clear();
    }

    @Override
    public void onTick() {
        BackTrail.tick((float) maxLength.value, isActive());
    }

    /** Sprinting AND moving mostly in the direction you're facing — not walking, not strafing/backpedaling. */
    private static boolean isActive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || !p.isSprinting()) return false;
        double vx = p.getVelocity().x, vz = p.getVelocity().z;
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < 0.05) return false;
        float yawRad = (float) Math.toRadians(p.bodyYaw);
        double fx = -MathHelper.sin(yawRad), fz = MathHelper.cos(yawRad);
        double dot = (vx * fx + vz * fz) / speed;   // 1 = straight forward, 0 = strafing, -1 = backpedaling
        return dot > 0.5;
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void renderWorld(WorldRenderContext ctx) {
        var m = com.lume.client.LumeClient.MODULES.getByName("Trail");
        if (!(m instanceof Trail t) || !t.isEnabled()) return;
        int rgb = t.color.accent ? Theme.accentRgb() : t.color.rgb();
        BackTrail.render(ctx, rgb, t.style.index, (float) t.width.value, (float) t.maxLength.value);
    }
}

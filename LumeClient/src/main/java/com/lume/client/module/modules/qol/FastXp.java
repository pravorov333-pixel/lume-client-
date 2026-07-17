package com.lume.client.module.modules.qol;

import com.lume.client.mixin.MinecraftClientAccessor;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.item.Items;

/** While you hold right-click with XP bottles in hand, rapid-fires the throw at a set rate. */
public class FastXp extends Module {

    public final SliderSetting cps = add(new SliderSetting("CPS", 12, 2, 20, true));

    private long lastUse = 0;

    public FastXp() {
        super("Fast XP", "Rapid-fires XP bottles while holding right click", Category.CHAT, -1);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) return;
        boolean holdingBottle = mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                || mc.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE);
        if (!holdingBottle || !mc.options.useKey.isPressed()) return;
        long now = System.currentTimeMillis();
        long interval = Math.max(1, Math.round(1000.0 / cps.value));
        if (now - lastUse < interval) return;
        lastUse = now;
        ((MinecraftClientAccessor) mc).lume$doItemUse();
    }
}

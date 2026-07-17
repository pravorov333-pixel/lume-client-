package com.lume.client.module.modules.cosmetic;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * GUI Animations — scoped to exactly four things now, each its own toggle:
 * Tab (player list), Chat (the typing prompt), Inventory (any container
 * screen) and Hotbar (item-switch pop). Nothing else animates any more.
 * Style picks the open-animation look for Tab/Chat/Inventory (Hotbar's pop
 * doesn't change with it — see HotbarMixin).
 */
public class GuiAnimations extends Module {

    public final SliderSetting duration = add(new SliderSetting("Duration ms", 180, 60, 500, true));
    public final ModeSetting style = add(new ModeSetting("Style", 0, "Scale", "Slide"));
    public final BoolSetting tab = add(new BoolSetting("Animate Tab", true));
    public final BoolSetting chat = add(new BoolSetting("Animate Chat", true));
    public final BoolSetting inventory = add(new BoolSetting("Animate Inventory", true));
    public final BoolSetting hotbar = add(new BoolSetting("Animate Hotbar", true));

    public GuiAnimations() {
        super("GUI Animations", "Плавный tab/chat/inventory/hotbar", Category.COSMETIC, -1);
    }

    public float durationMs() {
        return (float) Math.max(1, duration.value);
    }
}

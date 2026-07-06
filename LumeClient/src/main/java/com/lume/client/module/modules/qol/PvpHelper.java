package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * PvP Helper — figures out which food to eat in a fight and blinks its slot
 * green (hotbar or main inventory), instead of eating it for you. Priority:
 * (enchanted) golden apple first — the real PvP pick for absorption/regen —
 * then any other food, once hunger drops below the threshold.
 */
public class PvpHelper extends Module {

    public final SliderSetting threshold = add(new SliderSetting("Hunger < ", 15, 1, 19, true));

    public PvpHelper() {
        super("PvP Helper", "Highlights what to eat in a fight", Category.CHAT, -1);
    }

    /** Inventory slot (0..35, vanilla PlayerInventory numbering) to highlight, or -1 if none needed. */
    public int targetSlot() {
        if (mc.player == null || mc.player.getHungerManager().getFoodLevel() > threshold.getInt()) return -1;
        int best = -1, bestRank = -1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            int rank = foodRank(s);
            if (rank > bestRank) { bestRank = rank; best = i; }
        }
        return best;
    }

    /** Higher = better to eat in PvP. -1 = not food at all. */
    private static int foodRank(ItemStack s) {
        Item it = s.getItem();
        if (it == Items.ENCHANTED_GOLDEN_APPLE) return 3;
        if (it == Items.GOLDEN_APPLE) return 2;
        if (s.contains(DataComponentTypes.FOOD)) return 1;
        return -1;
    }
}

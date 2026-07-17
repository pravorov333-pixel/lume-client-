package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Auto Eat — eats food from your hotbar once hunger drops below the
 * threshold. Same food-priority ranking as PvP Helper (enchanted golden
 * apple first). Hotbar only, same "select + right-click" trick as Armor
 * Manager, so it never touches the main inventory.
 */
public class AutoEat extends Module {

    public final SliderSetting threshold = add(new SliderSetting("Hunger < ", 15, 1, 19, true));

    private int savedSlot = -1;

    public AutoEat() {
        super("Auto Eat", "Eats food from your hotbar when hungry", Category.CHAT, -1);
        setBindable(true);
    }

    @Override
    public void onDisable() { savedSlot = -1; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (savedSlot >= 0) {
            if (!mc.player.isUsingItem()) { mc.player.getInventory().selectedSlot = savedSlot; savedSlot = -1; }
            return;
        }
        if (mc.player.isUsingItem()) return;
        if (mc.player.getHungerManager().getFoodLevel() > threshold.getInt()) return;

        int slot = bestFoodSlot();
        if (slot < 0) return;
        savedSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
    }

    private int bestFoodSlot() {
        var inv = mc.player.getInventory();
        int best = -1, bestRank = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.contains(DataComponentTypes.FOOD)) continue;
            int rank = foodRank(s.getItem());
            if (rank > bestRank) { bestRank = rank; best = i; }
        }
        return best;
    }

    private static int foodRank(Item it) {
        if (it == Items.ENCHANTED_GOLDEN_APPLE) return 3;
        if (it == Items.GOLDEN_APPLE) return 2;
        return 1;
    }
}

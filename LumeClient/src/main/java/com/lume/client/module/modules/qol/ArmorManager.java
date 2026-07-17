package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;

/**
 * Armor Manager — auto-equips better armor from your HOTBAR when you pick
 * some up. Scoped to the hotbar only (never touches the main inventory), so
 * there's no risky manual slot-click bookkeeping — same "select the item,
 * then right-click to use it" trick as Auto Eat. Bindable: toggle it on while
 * mining/exploring, off otherwise.
 */
public class ArmorManager extends Module {

    private static final Map<Item, Integer> TIER = new HashMap<>();
    static {
        put(1, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        put(2, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
        put(3, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
        put(4, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.TURTLE_HELMET);
        put(5, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        put(6, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
    }
    private static void put(int tier, Item... items) { for (Item i : items) TIER.put(i, tier); }
    private static int tierOf(Item item) { return TIER.getOrDefault(item, 0); }

    private int savedSlot = -1;

    public ArmorManager() {
        super("Armor Manager", "Auto-equips better armor from your hotbar", Category.CHAT, -1);
        setBindable(true);
    }

    @Override
    public void onDisable() { savedSlot = -1; }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        // Mid-swap: wait for the "use item" (equip) action to finish, then restore the held slot.
        if (savedSlot >= 0) {
            if (!mc.player.isUsingItem()) { mc.player.getInventory().selectedSlot = savedSlot; savedSlot = -1; }
            return;
        }
        if (mc.player.isUsingItem()) return;   // don't interrupt eating/blocking/whatever else you're doing

        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            int tier = tierOf(s.getItem());
            if (tier == 0) continue;
            EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
            if (eq == null) continue;
            EquipmentSlot slot = eq.slot();
            if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                    && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) continue;
            if (tier <= tierOf(mc.player.getEquippedStack(slot).getItem())) continue;

            savedSlot = inv.selectedSlot;
            inv.selectedSlot = i;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            return;   // one swap per tick — let it land before re-scanning
        }
    }
}

package com.lume.client.module.modules.visual;

import com.lume.client.gui.Notifications;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.social.Friends;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Notifications — toasts (via {@link Notifications}) for things easy to miss
 * mid-game: gear about to break, and friends coming online/offline. Purely
 * informational, doesn't touch gameplay.
 */
public class Alerts extends Module {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
    };

    public final BoolSetting durability = add(new BoolSetting("Durability warning", true));
    public final SliderSetting threshold = add(new SliderSetting("Threshold %", 15, 5, 40, true));
    public final BoolSetting friendOnline = add(new BoolSetting("Friend online", true));
    public final BoolSetting friendOffline = add(new BoolSetting("Friend offline", false));

    private final Map<EquipmentSlot, Boolean> lowAlerted = new HashMap<>();
    private final Map<String, Boolean> friendWasOnline = new HashMap<>();
    private boolean friendStateSeeded = false;

    public Alerts() {
        super("Notifications", "Toasts for low durability and friends coming online", Category.VISUALS, -1);
    }

    @Override
    public void onDisable() {
        lowAlerted.clear();
        friendWasOnline.clear();
        friendStateSeeded = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (durability.value) checkDurability();
        if (friendOnline.value || friendOffline.value) checkFriends();
    }

    private void checkDurability() {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = mc.player.getEquippedStack(slot);
            boolean low = false;
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                int pct = (st.getMaxDamage() - st.getDamage()) * 100 / st.getMaxDamage();
                low = pct <= threshold.getInt();
            }
            boolean was = lowAlerted.getOrDefault(slot, false);
            if (low && !was) {
                Notifications.push(st.getName().getString() + " is about to break", 0xFFE05656, 3000);
            }
            lowAlerted.put(slot, low);
        }
    }

    private void checkFriends() {
        // First tick after enable/status-load just seeds state — otherwise every already-online
        // friend fires a toast the instant the module turns on.
        if (!friendStateSeeded) {
            for (String name : Friends.friendList) friendWasOnline.put(name.toLowerCase(Locale.ROOT), Friends.isOnline(name));
            friendStateSeeded = true;
            return;
        }
        Set<String> current = new HashSet<>();
        for (String name : Friends.friendList) {
            String key = name.toLowerCase(Locale.ROOT);
            current.add(key);
            boolean online = Friends.isOnline(name);
            boolean was = friendWasOnline.getOrDefault(key, online);
            if (online && !was && friendOnline.value) {
                Notifications.push(name + " is now online", 0xFF6FCF7F, 3000);
            } else if (!online && was && friendOffline.value) {
                Notifications.push(name + " went offline", 0xFF8C8C8C, 3000);
            }
            friendWasOnline.put(key, online);
        }
        friendWasOnline.keySet().retainAll(current);
    }
}

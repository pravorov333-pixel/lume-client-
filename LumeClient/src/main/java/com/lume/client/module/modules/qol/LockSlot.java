package com.lume.client.module.modules.qol;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.SliderSetting;

/** Locks a specific hotbar slot number so its item can't be thrown out with the drop key. */
public class LockSlot extends Module {

    public final SliderSetting slot = add(new SliderSetting("Slot", 1, 1, 9, true));

    public LockSlot() {
        super("Lock Slot", "Stops a chosen hotbar slot from being dropped", Category.CHAT, -1);
    }

    /** True if the currently-selected hotbar slot is the one locked (0-indexed, matches PlayerInventory.selectedSlot). */
    public static boolean lockedSlotSelected(int selectedSlot) {
        Module m = LumeClient.MODULES.getByName("Lock Slot");
        return m instanceof LockSlot ls && ls.isEnabled() && ls.slot.getInt() - 1 == selectedSlot;
    }
}

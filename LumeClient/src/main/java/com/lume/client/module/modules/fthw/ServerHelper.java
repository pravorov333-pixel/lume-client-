package com.lume.client.module.modules.fthw;

import com.lume.client.fthw.HelperBinds;
import com.lume.client.fthw.ServerType;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/**
 * FT/HW Helper — the FunTime/HolyWorld helper hub. Detects the server and drives
 * the legit awareness features (all toggleable + keybindable via {@link HelperBinds}):
 * <ul>
 *   <li><b>itemHelper</b> — when you hold a known item, show its radius ring,
 *       cooldown and a note;</li>
 *   <li><b>effects</b> — your own active (de)buffs with live countdowns;</li>
 *   <li><b>eventsHud</b> — running server events with a countdown + popups.</li>
 * </ul>
 * The item encyclopedia + per-item verify/tune lives in the Server tab.
 * Managed from the Server tab; hidden from the normal module grid.
 */
public class ServerHelper extends Module {

    public final BoolSetting showServer = add(new BoolSetting("Показывать сервер", true));
    public final BoolSetting itemHelper = add(new BoolSetting("Хелпер предметов (кулдаун+радиус)", true));
    public final BoolSetting enemyAlert = add(new BoolSetting("Алерт: тебя задело предметом", true));
    public final BoolSetting eventsHud  = add(new BoolSetting("Алерт ивентов + таймер", true));
    public final BoolSetting quickCmds  = add(new BoolSetting("Быстрые команды (HUD)", true));
    public final BoolSetting effects    = add(new BoolSetting("Эффекты на тебе", true));

    public ServerHelper() {
        super("Server Helper", "FunTime / HolyWorld helper", Category.CHAT, -1);
        setBindable(true);   // whole helper can be bound on/off in the Binds tab
        // sub-functions can carry their own keybinds (set in the Server tab)
        HelperBinds.register(itemHelper);
        HelperBinds.register(enemyAlert);
        HelperBinds.register(eventsHud);
        HelperBinds.register(quickCmds);
        HelperBinds.register(effects);
    }

    @Override
    public void onTick() {
        // only usable on a supported anarchy — auto-disable the moment we leave one
        if (ServerType.current() == ServerType.UNKNOWN) {
            setEnabled(false);
        }
    }
}

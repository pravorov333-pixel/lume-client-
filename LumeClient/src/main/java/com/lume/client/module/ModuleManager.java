package com.lume.client.module;

import com.lume.client.module.modules.cosmetic.BlockOutline;
import com.lume.client.module.modules.cosmetic.CleanView;
import com.lume.client.module.modules.cosmetic.CustomCrosshair;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.module.modules.cosmetic.GameFont;
import com.lume.client.module.modules.cosmetic.MenuLogo;
import com.lume.client.module.modules.fthw.ServerHelper;
import com.lume.client.module.modules.misc.HudScale;
import com.lume.client.module.modules.misc.Language;
import com.lume.client.module.modules.player.AutoSprint;
import com.lume.client.module.modules.qol.AntiSpam;
import com.lume.client.module.modules.qol.AutoReconnect;
import com.lume.client.module.modules.qol.ChatTimestamps;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.module.modules.render.FullBright;
import com.lume.client.module.modules.render.ReducedParticles;
import com.lume.client.module.modules.render.Zoom;
import com.lume.client.module.modules.visual.ArmorHud;
import com.lume.client.module.modules.visual.BlockInfo;
import com.lume.client.module.modules.visual.Clock;
import com.lume.client.module.modules.visual.Coords;
import com.lume.client.module.modules.visual.Cps;
import com.lume.client.module.modules.visual.DayCounter;
import com.lume.client.module.modules.visual.Hud;
import com.lume.client.module.modules.visual.InventoryHud;
import com.lume.client.module.modules.visual.Keystrokes;
import com.lume.client.module.modules.visual.ModuleList;
import com.lume.client.module.modules.visual.PingHud;
import com.lume.client.module.modules.visual.PotionHud;
import com.lume.client.module.modules.visual.Speed;
import com.lume.client.module.modules.visual.TargetHud;
import com.lume.client.module.modules.visual.TotemCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers and stores all modules, and routes ticks / keybinds to them.
 */
public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    /** Register every module here. Add new modules to this list. */
    public void init() {
        // Visuals
        register(new Hud());
        register(new Coords());
        register(new Keystrokes());
        register(new PotionHud());
        register(new ArmorHud());
        register(new InventoryHud());
        register(new TotemCounter());
        register(new PingHud());
        register(new DayCounter());
        register(new TargetHud());
        register(new ModuleList());
        register(new Cps());
        register(new Speed());
        register(new Clock());
        register(new BlockInfo());
        // Render
        register(new FullBright());
        register(new Zoom());
        register(new ReducedParticles());
        // Chat & QoL
        register(new AutoSprint());
        register(new AutoReconnect());
        register(new AntiSpam());
        register(new ChatTimestamps());
        register(new Waypoints());
        register(new ServerHelper());
        // Cosmetics
        register(new CustomCrosshair());
        register(new MenuLogo());
        register(new GameFont());
        register(new CustomMenu());
        register(new BlockOutline());
        register(new CleanView());
        // Settings
        register(new HudScale());
        register(new Language());

        // Sensible defaults so the client looks alive on first launch.
        Module hud = getByName("HUD");
        if (hud != null) hud.setEnabled(true);
        Module menuLogo = getByName("Menu Logo");
        if (menuLogo != null) menuLogo.setEnabled(true);
    }

    private void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModules(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) result.add(m);
        }
        return result;
    }

    public Module getByName(String name) {
        for (Module m : modules) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    /** Drive modules bound to the given GLFW key. HOLD mode follows {@code pressed}; TOGGLE flips on press. */
    public void onKey(int key, boolean pressed) {
        for (Module m : modules) {
            if (m.getKey() == -1 || m.getKey() != key) continue;
            if (m.getBindMode() == Module.BindMode.HOLD) m.setEnabled(pressed);
            else if (pressed) m.toggle();
        }
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick();
        }
    }
}

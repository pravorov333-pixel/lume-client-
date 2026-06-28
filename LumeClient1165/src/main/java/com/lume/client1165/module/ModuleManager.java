package com.lume.client1165.module;

import com.lume.client1165.module.modules.player.AutoSprint;
import com.lume.client1165.module.modules.render.FullBright;
import com.lume.client1165.module.modules.render.Zoom;
import com.lume.client1165.module.modules.visual.Coords;
import com.lume.client1165.module.modules.visual.Hud;
import com.lume.client1165.module.modules.visual.Keystrokes;

import java.util.ArrayList;
import java.util.List;

/** Registers and drives all modules (1.16.5 build). */
public class ModuleManager {

    private final List<Module> modules = new ArrayList<Module>();

    public void init() {
        // Visuals
        register(new Hud());
        register(new Coords());
        register(new Keystrokes());
        // Render
        register(new FullBright());
        register(new Zoom());
        // Player
        register(new AutoSprint());
    }

    private void register(Module module) { modules.add(module); }

    public List<Module> getModules() { return modules; }

    public List<Module> getModules(Category category) {
        List<Module> result = new ArrayList<Module>();
        for (Module m : modules) if (m.getCategory() == category) result.add(m);
        return result;
    }

    public Module getByName(String name) {
        for (Module m : modules) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    public void onKey(int key, boolean pressed) {
        for (Module m : modules) {
            if (m.getKey() == -1 || m.getKey() != key) continue;
            if (m.getBindMode() == Module.BindMode.HOLD) m.setEnabled(pressed);
            else if (pressed) m.toggle();
        }
    }

    public void onTick() {
        for (Module m : modules) if (m.isEnabled()) m.onTick();
    }
}

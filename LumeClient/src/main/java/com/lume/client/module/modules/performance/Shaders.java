package com.lume.client.module.modules.performance;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.irisshaders.iris.Iris;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Shaders — Iris-powered shader pack support. No settings, no shader-pack picker UI:
 * whatever single pack is sitting in Iris's own {@code shaderpacks/} folder gets
 * auto-selected and enabled with zero player interaction. This module's own
 * enable/disable toggle IS the emergency kill switch if a pack turns out to be
 * broken/unstable on the user's GPU.
 *
 * <p><b>Important:</b> Iris keeps its OWN persisted state ({@code iris.properties}),
 * entirely independent of Lume's module config. A module that simply *loads* already
 * disabled never calls {@link #onDisable} at all — {@link Module#setEnabled} only fires
 * onEnable/onDisable on an actual live transition, and {@code ModuleManager.onTick()} only
 * ticks modules that are currently enabled — so a disabled Shaders module had no way to
 * ever correct Iris's own "enableShaders=true" left over from a previous session. That's
 * exactly what caused the game to keep re-attempting the same (GPU-hanging) shader pack on
 * every subsequent launch even with this module turned off. {@link #forceSync} is called
 * unconditionally from LumeClient's own tick loop (see there) specifically to close that gap.
 */
public class Shaders extends Module {

    private long lastScan = 0;
    private static long lastForceSync = 0;

    public Shaders() {
        super("Shaders", "Auto-runs any shader pack dropped in shaderpacks/ (Iris)", Category.PERFORMANCE, -1);
    }

    @Override
    public void onEnable() { sync(true); }

    @Override
    public void onDisable() { sync(false); }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastScan < 3000) return;
        lastScan = now;
        sync(true);
    }

    /** Called every client tick regardless of this module's enabled state (see class doc) —
     *  throttled internally so it's cheap to call unconditionally. */
    public static void forceSync() {
        long now = System.currentTimeMillis();
        if (now - lastForceSync < 3000) return;
        lastForceSync = now;
        Module m = LumeClient.MODULES.getByName("Shaders");
        if (m instanceof Shaders s) s.sync(s.isEnabled());
    }

    /** Self-healing — brings Iris's actual state in line with {@code want}. When enabling,
     *  also (re)selects whatever pack is present. Safe to call repeatedly, including before
     *  Iris has finished its own init (each step is independently guarded, so an early
     *  failure here just gets silently retried on the next call). */
    private void sync(boolean want) {
        if (want) autoSelectPack();
        try {
            if (Iris.getIrisConfig().areShadersEnabled() != want) {
                Iris.toggleShaders(MinecraftClient.getInstance(), want);
            }
        } catch (Throwable t) {
            System.out.println("[Lume] Shaders: Iris not ready yet (" + t.getClass().getSimpleName() + "), will retry");
        }
    }

    private void autoSelectPack() {
        try {
            var mgr = Iris.getShaderpacksDirectoryManager();
            if (mgr == null) return;
            List<String> packs = mgr.enumerate();
            if (packs.isEmpty()) return;
            String current = Iris.getIrisConfig().getShaderPackName().orElse(null);
            if (current != null && packs.contains(current)) return;   // already on a valid pack
            Iris.getIrisConfig().setShaderPackName(packs.get(0));
            Iris.getIrisConfig().save();
            Iris.loadShaderpack();
        } catch (Throwable t) {
            System.out.println("[Lume] Shaders auto-select failed: " + t);
        }
    }
}

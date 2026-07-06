package com.lume.client.module.modules.performance;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/**
 * Shows JVM / system info in the ClickGUI expansion and optionally a RAM-usage
 * bar in the HUD. All data is read at render time (live values, no static snapshots).
 */
public class JvmOptimizer extends Module {

    public final BoolSetting showRam = add(new BoolSetting("RAM Bar HUD", false));

    public JvmOptimizer() {
        super("System Info", "JVM и RAM диагностика", Category.PERFORMANCE, -1);
    }

    // ---- Static helpers used by HudRenderer + ClickGuiScreen ----

    public static long usedMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1_048_576L;
    }

    public static long maxMb() {
        return Runtime.getRuntime().maxMemory() / 1_048_576L;
    }

    public static boolean is64bit() {
        String arch = System.getProperty("os.arch", "");
        return arch.contains("64") || arch.equalsIgnoreCase("amd64");
    }

    public static String javaVersion() {
        return System.getProperty("java.version", "?");
    }
}

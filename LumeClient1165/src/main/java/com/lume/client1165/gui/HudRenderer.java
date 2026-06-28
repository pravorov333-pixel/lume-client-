package com.lume.client1165.gui;

import com.lume.client1165.LumeClient1165;
import com.lume.client1165.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Lume HUD overlay for the 1.16.5 build — glass info panel (watermark + FPS),
 * Coords readout, and a Keystrokes overlay. Each piece is gated by its module
 * being enabled. Drawn via {@link HudRenderCallback} on a {@link MatrixStack}.
 */
public final class HudRenderer {

    private HudRenderer() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ms, tickDelta) -> render(ms));
    }

    private static boolean on(String moduleName) {
        Module m = LumeClient1165.MODULES.getByName(moduleName);
        return m != null && m.isEnabled();
    }

    private static void render(MatrixStack ms) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null) return;

        if (on("HUD")) renderInfoPanel(ms, mc);
        if (on("Coords") && mc.player != null) renderCoords(ms, mc);
        if (on("Keystrokes") && mc.player != null) renderKeystrokes(ms, mc);
    }

    private static int fps(MinecraftClient mc) {
        try {
            String s = mc.fpsDebugString;
            if (s != null && s.length() > 0) {
                int sp = s.indexOf(' ');
                return Integer.parseInt(sp > 0 ? s.substring(0, sp) : s);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /** Top-left glass panel: logo mark + "Lume" + fps line. */
    private static void renderInfoPanel(MatrixStack ms, MinecraftClient mc) {
        int x = 6, y = 6;
        String title = "Lume";
        String fpsLine = fps(mc) + " FPS";
        int tw = Math.max(mc.textRenderer.getWidth(title), mc.textRenderer.getWidth(fpsLine));
        int pw = 22 + tw + 12;
        int ph = 30;
        RenderUtil.roundedRect(ms, x, y, pw, ph, 8, Theme.winBg());
        RenderUtil.drawLogo(ms, x + 6, y + 7, 16);
        RenderUtil.text(ms, mc.textRenderer, title, x + 26, y + 6, Theme.txt(), false);
        RenderUtil.text(ms, mc.textRenderer, fpsLine, x + 26, y + 17, Theme.accent(), false);
    }

    private static void renderCoords(MatrixStack ms, MinecraftClient mc) {
        String s = String.format("XYZ  %.0f  %.0f  %.0f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        int sw = mc.textRenderer.getWidth(s);
        int pw = sw + 16, ph = 16;
        int x = (mc.getWindow().getScaledWidth() - pw) / 2;
        int y = mc.getWindow().getScaledHeight() - ph - 6;
        RenderUtil.roundedRect(ms, x, y, pw, ph, 6, Theme.winBg());
        RenderUtil.textVCentered(ms, mc.textRenderer, s, x + 8, y, ph, Theme.txt());
    }

    /** WASD + space key overlay, bottom-right. */
    private static void renderKeystrokes(MatrixStack ms, MinecraftClient mc) {
        int unit = 18, gap = 3;
        int blockW = unit * 3 + gap * 2;
        int baseX = mc.getWindow().getScaledWidth() - blockW - 8;
        int baseY = mc.getWindow().getScaledHeight() - (unit * 2 + gap + unit) - 8 - 20;

        boolean w = mc.options.keyForward.isPressed();
        boolean a = mc.options.keyLeft.isPressed();
        boolean s = mc.options.keyBack.isPressed();
        boolean d = mc.options.keyRight.isPressed();
        boolean space = mc.options.keyJump.isPressed();

        // W (centered top)
        key(ms, mc, baseX + unit + gap, baseY, unit, unit, "W", w);
        // A S D (middle row)
        key(ms, mc, baseX, baseY + unit + gap, unit, unit, "A", a);
        key(ms, mc, baseX + unit + gap, baseY + unit + gap, unit, unit, "S", s);
        key(ms, mc, baseX + (unit + gap) * 2, baseY + unit + gap, unit, unit, "D", d);
        // space bar (full width bottom)
        key(ms, mc, baseX, baseY + (unit + gap) * 2, blockW, unit - 4, "___", space);
    }

    private static void key(MatrixStack ms, MinecraftClient mc, int x, int y, int w, int h, String label, boolean down) {
        int bg = down ? Theme.accent() : Theme.glassRow();
        int fg = down ? 0xFFFFFFFF : Theme.txt();
        RenderUtil.roundedRect(ms, x, y, w, h, 5, bg);
        RenderUtil.textCentered(ms, mc.textRenderer, label, x, y, w, h, fg);
    }
}

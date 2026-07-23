package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Full-page Fast Connect list — a real screen (not the small corner dropdown), same
 *  server-list/add/connect/delete data and actions FastConnect always had. */
public class FastConnectScreen extends Screen {

    private final Screen parent;
    private long lastFrame = System.currentTimeMillis();
    private final long openTime = System.currentTimeMillis();

    private static final int WIN_W = 300, ROW_H = 24, ROW_GAP = 6;

    private final List<Object[]> hits = new ArrayList<>();
    private boolean formOpen = false;
    private String fcName = "", fcAddr = "";
    private String focused = null;

    public FastConnectScreen(Screen parent) {
        super(Text.literal("Lume — Fast Connect"));
        this.parent = parent;
    }

    @Override public void close() { if (client != null) client.setScreen(parent != null ? parent : new TitleScreen()); }
    @Override public boolean shouldPause() { return false; }

    private int sf() { return (int) Math.max(1, client.getWindow().getScaleFactor()); }
    private final Map<String, float[]> anim = new HashMap<>();
    private float[] animFor(String id) { return anim.computeIfAbsent(id, k -> new float[1]); }
    private static float approach(float cur, float target, float rate, float dt) { return cur + (target - cur) * Math.min(1f, rate * dt); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        CustomMenu.drawDimOverlay(ctx, width, height);
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        List<FastConnect.Entry> list = FastConnect.list;
        int rowsH = list.size() * (ROW_H + ROW_GAP);
        int formH = formOpen ? (2 * 20 + 6 + 24) : (ROW_H + ROW_GAP);
        int winH = 50 + rowsH + formH + 16;
        int W = WIN_W, H = winH;
        int x = (width - W) / 2, y = (height - H) / 2;
        float p = openAnim();
        int r = 14;

        int rowX = x + 14, rowW = WIN_W - 28;
        int ry = 40;

        try {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            RenderUtil.glow(ctx, x, y, W, H, r, 0x000000, 3);
            RenderUtil.gradientRoundedRect(ctx, x, y, W, H, r, fade(Theme.winTop(), p), fade(Theme.winBot(), p));
            RenderUtil.strokeRoundedRect(ctx, x, y, W, H, r, 1, fade(Theme.rim(), p));
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Fast Connect"), x, y + 12, W, 20, fade(Theme.txt(), p), 0.6f);

            int yy = y + ry;
            for (int i = 0; i < list.size(); i++) {
                FastConnect.Entry e = list.get(i);
                int delW = 16, delX = rowX + rowW - delW - 4;
                float[] rh = animFor("row:" + i);
                rh[0] = approach(rh[0], inside(mouseX, mouseY, rowX, yy, rowW, ROW_H) ? 1f : 0f, 14f, dt);
                RenderUtil.roundedRect(ctx, rowX, yy, rowW, ROW_H, 7, fade(Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), rh[0]), p));
                RenderUtil.textVCentered(ctx, tr, e.name, rowX + 10, yy, ROW_H, fade(Theme.txt(), p), 0.5f);
                RenderUtil.textVCentered(ctx, tr, e.address, rowX + rowW - 40, yy, ROW_H, fade(Theme.txtDim(), p), 0.44f);
                RenderUtil.textCentered(ctx, tr, "✕", delX, yy, delW, ROW_H, fade(Theme.txtDim(), p), 0.47f);
                hits.add(new Object[]{"connect", rowX, yy, rowW - delW - 6, ROW_H, i});
                hits.add(new Object[]{"delete", delX, yy, delW, ROW_H, i});
                yy += ROW_H + ROW_GAP;
            }

            if (!formOpen) {
                float[] ah = animFor("addRow");
                ah[0] = approach(ah[0], inside(mouseX, mouseY, rowX, yy, rowW, ROW_H) ? 1f : 0f, 14f, dt);
                RenderUtil.roundedRect(ctx, rowX, yy, rowW, ROW_H, 7, fade(Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ah[0]), p));
                RenderUtil.textCentered(ctx, tr, "+ " + com.lume.client.Lang.tUI("Add server"), rowX, yy, rowW, ROW_H, fade(Theme.accent(), p), 0.5f);
                hits.add(new Object[]{"openForm", rowX, yy, rowW, ROW_H});
                yy += ROW_H + ROW_GAP;
            } else {
                fieldVanilla(ctx, tr, "name", rowX, yy, rowW, 20, "server name", p);
                yy += 23;
                fieldVanilla(ctx, tr, "addr", rowX, yy, rowW, 20, "ip:port", p);
                yy += 26;
                int halfW = (rowW - 6) / 2;
                RenderUtil.roundedRect(ctx, rowX, yy, halfW, 22, 7, fade(Theme.accent(), p));
                RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Add"), rowX, yy, halfW, 22, fade(Theme.activeText(), p), 0.47f);
                RenderUtil.roundedRect(ctx, rowX + halfW + 6, yy, halfW, 22, 7, fade(Theme.glassRow(), p));
                RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Cancel"), rowX + halfW + 6, yy, halfW, 22, fade(Theme.txt(), p), 0.47f);
                hits.add(new Object[]{"saveForm", rowX, yy, halfW, 22});
                hits.add(new Object[]{"cancelForm", rowX + halfW + 6, yy, halfW, 22});
                yy += 22 + ROW_GAP;
            }

            int homeW = 90, homeH = 26;
            int homeX = x + (WIN_W - homeW) / 2;
            RenderUtil.roundedRect(ctx, homeX, yy, homeW, homeH, 8, fade(Theme.glassRow(), p));
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Home"), homeX, yy, homeW, homeH, fade(Theme.txt(), p), 0.47f);
            hits.add(new Object[]{"home", homeX, yy, homeW, homeH});
        } catch (Throwable t) {
            System.out.println("[Lume] FastConnectScreen render failed: " + t);
        }
        if (p < 1f) com.lume.client.nanovg.GlassRenderer.transitionOverlay(x, y, W, H, (1f - p) * 0.8f, 1f - p);
    }

    /** Multiplies an ARGB color's alpha by {@code p} — the DrawContext equivalent of NanoVG's
     *  {@code globalAlpha}, which has no per-call analogue here so each draw bakes it in. */
    private static int fade(int argb, float p) {
        int a = Math.round(((argb >>> 24) & 0xFF) * p);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private float openAnim() {
        float pr = (System.currentTimeMillis() - openTime) / 160f;
        if (pr >= 1f) return 1f;
        if (pr <= 0f) return 0f;
        return 1f - (1f - pr) * (1f - pr);
    }

    private String textFor(String id) { return "name".equals(id) ? fcName : fcAddr; }

    private void fieldVanilla(DrawContext ctx, TextRenderer tr, String id, int x, int y, int w, int h, String placeholder, float p) {
        boolean foc = id.equals(focused);
        String txt = textFor(id);
        String show = txt.isEmpty() && !foc ? placeholder : txt + (foc ? "_" : "");
        int color = txt.isEmpty() && !foc ? Theme.txtDim() : Theme.txt();
        RenderUtil.roundedRect(ctx, x, y, w, h, 5, fade(foc ? Theme.glassHov() : Theme.glassRow(), p));
        if (foc) RenderUtil.roundedRect(ctx, x, y + h - 1, w, 1, 1, fade(Theme.accent(), p));
        RenderUtil.textVCentered(ctx, tr, show, x + 6, y, h, fade(color, p), 0.44f);
        hits.add(new Object[]{"field:" + id, x, y, w, h});
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int S = sf();
        int mx = (int) (mouseX * S), my = (int) (mouseY * S);
        for (Object[] h : hits) {
            String kind = (String) h[0];
            int x = (int) h[1], y = (int) h[2], w = (int) h[3], hh = (int) h[4];
            if (mx < x * S || mx > (x + w) * S || my < y * S || my > (y + hh) * S) continue;
            switch (kind) {
                case "openForm" -> { formOpen = true; fcName = ""; fcAddr = ""; focused = "name"; }
                case "cancelForm" -> { formOpen = false; focused = null; }
                case "saveForm" -> { FastConnect.add(fcName, fcAddr); Config.save(); formOpen = false; focused = null; }
                case "connect" -> {
                    int i = (int) h[5];
                    if (i >= 0 && i < FastConnect.list.size()) {
                        FastConnect.Entry e = FastConnect.list.get(i);
                        MinecraftClient mc = MinecraftClient.getInstance();
                        ServerInfo info = new ServerInfo(e.name, e.address, ServerInfo.ServerType.OTHER);
                        ConnectScreen.connect(this, mc, ServerAddress.parse(e.address), info, false, null);
                    }
                }
                case "delete" -> {
                    int i = (int) h[5];
                    if (i >= 0 && i < FastConnect.list.size()) { FastConnect.remove(FastConnect.list.get(i)); Config.save(); }
                }
                case "field:name" -> focused = "name";
                case "field:addr" -> focused = "addr";
                case "home" -> close();
                default -> { }
            }
            return true;
        }
        focused = null;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focused != null && chr >= 32 && chr != 127) { setFieldValue(textFor(focused) + chr); return true; }
        return super.charTyped(chr, modifiers);
    }

    private void setFieldValue(String v) {
        if ("name".equals(focused)) fcName = v; else if ("addr".equals(focused)) fcAddr = v;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focused != null) { focused = null; return true; }
            close();
            return true;
        }
        if (focused != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String v = textFor(focused);
                if (!v.isEmpty()) setFieldValue(v.substring(0, v.length() - 1));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if ("name".equals(focused)) focused = "addr";
                else { FastConnect.add(fcName, fcAddr); Config.save(); formOpen = false; focused = null; }
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

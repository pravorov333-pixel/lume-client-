package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.MinecraftClient;
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

import static com.lume.client.nanovg.NanoVgRenderer.*;

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
    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }
    private final Map<String, float[]> anim = new HashMap<>();
    private float[] animFor(String id) { return anim.computeIfAbsent(id, k -> new float[1]); }
    private static float approach(float cur, float target, float rate, float dt) { return cur + (target - cur) * Math.min(1f, rate * dt); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        CustomMenu.drawDimOverlay(ctx, width, height);
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        int S = sf();
        List<FastConnect.Entry> list = FastConnect.list;
        int rowsH = list.size() * (ROW_H + ROW_GAP);
        int formH = formOpen ? (2 * 20 + 6 + 24) : (ROW_H + ROW_GAP);
        int winH = 50 + rowsH + formH + 16;
        int sw = width * S, sh = height * S;
        int W = WIN_W * S, H = winH * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        int mx = mouseX * S, my = mouseY * S;
        float p = openAnim();
        int r = 14 * S;

        int rowX = x / S + 14, rowW = WIN_W - 28;
        int ry = 40;

        try {
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                save(vg);
                globalAlpha(vg, p);
                shadow(vg, x, y, W, H, r, 22 * S, 0x70000000);
                shadow(vg, x, y, W, H, r, 30 * S, withAlpha(Theme.accentRgb(), 0x33));
                gradientRoundedRect(vg, x, y, W, H, r, Theme.winTop(), Theme.winBot());
                strokeRoundedRect(vg, x + 0.5f * S, y + 0.5f * S, W - S, H - S, r, S, Theme.rim());
                text(vg, x + W / 2f, y + 22 * S, 11 * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Fast Connect"));

                int yy = (int) (y / S) + ry;
                for (int i = 0; i < list.size(); i++) {
                    FastConnect.Entry e = list.get(i);
                    int delW = 16, delX = rowX + rowW - delW - 4;
                    float[] rh = animFor("row:" + i);
                    rh[0] = approach(rh[0], inside(mx, my, rowX * S, yy * S, rowW * S, ROW_H * S) ? 1f : 0f, 14f, dt);
                    roundedRect(vg, rowX * S, yy * S, rowW * S, ROW_H * S, 7 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), rh[0]));
                    text(vg, (rowX + 10) * S, (yy + ROW_H / 2f) * S, 9 * S, Theme.txt(), ALIGN_MIDDLE, e.name);
                    text(vg, (rowX + rowW - 40) * S, (yy + ROW_H / 2f) * S, 8 * S, Theme.txtDim(), ALIGN_MIDDLE, e.address);
                    text(vg, (delX + delW / 2f) * S, (yy + ROW_H / 2f) * S, 8.5f * S, Theme.txtDim(), ALIGN_CENTER_MIDDLE, "✕");
                    hits.add(new Object[]{"connect", rowX, yy, rowW - delW - 6, ROW_H, i});
                    hits.add(new Object[]{"delete", delX, yy, delW, ROW_H, i});
                    yy += ROW_H + ROW_GAP;
                }

                if (!formOpen) {
                    float[] ah = animFor("addRow");
                    ah[0] = approach(ah[0], inside(mx, my, rowX * S, yy * S, rowW * S, ROW_H * S) ? 1f : 0f, 14f, dt);
                    roundedRect(vg, rowX * S, yy * S, rowW * S, ROW_H * S, 7 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ah[0]));
                    text(vg, (rowX + rowW / 2f) * S, (yy + ROW_H / 2f) * S, 9 * S, Theme.accent(), ALIGN_CENTER_MIDDLE, "+ " + com.lume.client.Lang.tUI("Add server"));
                    hits.add(new Object[]{"openForm", rowX, yy, rowW, ROW_H});
                    yy += ROW_H + ROW_GAP;
                } else {
                    fieldNvg(vg, "name", rowX, yy, rowW, 20, "server name", S);
                    yy += 23;
                    fieldNvg(vg, "addr", rowX, yy, rowW, 20, "ip:port", S);
                    yy += 26;
                    int halfW = (rowW - 6) / 2;
                    roundedRect(vg, rowX * S, yy * S, halfW * S, 22 * S, 7 * S, Theme.accent());
                    text(vg, (rowX + halfW / 2f) * S, (yy + 11) * S, 8.5f * S, Theme.activeText(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Add"));
                    roundedRect(vg, (rowX + halfW + 6) * S, yy * S, halfW * S, 22 * S, 7 * S, Theme.glassRow());
                    text(vg, (rowX + halfW + 6 + halfW / 2f) * S, (yy + 11) * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Cancel"));
                    hits.add(new Object[]{"saveForm", rowX, yy, halfW, 22});
                    hits.add(new Object[]{"cancelForm", rowX + halfW + 6, yy, halfW, 22});
                    yy += 22 + ROW_GAP;
                }

                int homeW = 90, homeH = 26;
                float homeX = x / S + (WIN_W - homeW) / 2f;
                roundedRect(vg, homeX * S, yy * S, homeW * S, homeH * S, 8 * S, Theme.glassRow());
                text(vg, (homeX + homeW / 2f) * S, (yy + homeH / 2f) * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Home"));
                hits.add(new Object[]{"home", (int) homeX, yy, homeW, homeH});

                restore(vg);
            });
        } catch (Throwable t) {
            System.out.println("[Lume] FastConnectScreen render failed: " + t);
        }
        if (p < 1f) com.lume.client.nanovg.GlassRenderer.transitionOverlay(x, y, W, H, (1f - p) * 0.8f, 1f - p);
    }

    private float openAnim() {
        float pr = (System.currentTimeMillis() - openTime) / 160f;
        if (pr >= 1f) return 1f;
        if (pr <= 0f) return 0f;
        return 1f - (1f - pr) * (1f - pr);
    }

    private String textFor(String id) { return "name".equals(id) ? fcName : fcAddr; }

    private void fieldNvg(long vg, String id, int x, int y, int w, int h, String placeholder, int S) {
        boolean foc = id.equals(focused);
        String txt = textFor(id);
        String show = txt.isEmpty() && !foc ? placeholder : txt + (foc ? "_" : "");
        int color = txt.isEmpty() && !foc ? Theme.txtDim() : Theme.txt();
        roundedRect(vg, x * S, y * S, w * S, h * S, 5 * S, foc ? Theme.glassHov() : Theme.glassRow());
        if (foc) roundedRect(vg, x * S, (y + h - 1) * S, w * S, S, 1, Theme.accent());
        text(vg, (x + 6) * S, (y + h / 2f) * S, 8 * S, color, ALIGN_MIDDLE, show);
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

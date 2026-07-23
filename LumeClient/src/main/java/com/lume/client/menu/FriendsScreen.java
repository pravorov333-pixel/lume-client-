package com.lume.client.menu;

import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import com.lume.client.social.Friends;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lume.client.nanovg.NanoVgRenderer.*;

/** Full-page Friends list — a real screen (not the small corner dropdown), same
 *  friend-list/add/connect/delete data and actions Friends always had. */
public class FriendsScreen extends Screen {

    private final Screen parent;
    private long lastFrame = System.currentTimeMillis();
    private final long openTime = System.currentTimeMillis();

    private static final int WIN_W = 300, ROW_H = 24, ROW_GAP = 6;

    private final List<Object[]> hits = new ArrayList<>();
    private String friendAddName = "";
    private String focused = null;

    public FriendsScreen(Screen parent) {
        super(Text.literal("Lume — Friends"));
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
        List<String> friends = new ArrayList<>(Friends.friendList);
        friends.sort((a, b) -> Boolean.compare(Friends.isOnline(b), Friends.isOnline(a)));   // online first

        int rowsH = friends.size() * (ROW_H + ROW_GAP);
        int fieldH = 26;
        int winH = 50 + rowsH + fieldH + 16 + 16 + 26;
        int sw = width * S, sh = height * S;
        int W = WIN_W * S, H = winH * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        int mx = mouseX * S, my = mouseY * S;
        float p = openAnim();
        int r = 14 * S;

        int rowX = x / S + 14, rowW = WIN_W - 28;

        try {
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                save(vg);
                globalAlpha(vg, p);
                shadow(vg, x, y, W, H, r, 22 * S, 0x70000000);
                shadow(vg, x, y, W, H, r, 30 * S, withAlpha(Theme.accentRgb(), 0x33));
                gradientRoundedRect(vg, x, y, W, H, r, Theme.winTop(), Theme.winBot());
                strokeRoundedRect(vg, x + 0.5f * S, y + 0.5f * S, W - S, H - S, r, S, Theme.rim());
                text(vg, x + W / 2f, y + 22 * S, 11 * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Friends"));

                int yy = (int) (y / S) + 40;
                for (String name : friends) {
                    boolean online = Friends.isOnline(name);
                    int delW = 16, delX = rowX + rowW - delW - 4;
                    float[] rh = animFor("row:" + name);
                    rh[0] = approach(rh[0], inside(mx, my, rowX * S, yy * S, rowW * S, ROW_H * S) ? 1f : 0f, 14f, dt);
                    roundedRect(vg, rowX * S, yy * S, rowW * S, ROW_H * S, 7 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), rh[0]));
                    circle(vg, (rowX + 10) * S, (yy + ROW_H / 2f) * S, 2.5f * S, online ? 0xFF6FCF7F : Theme.txtDim());
                    text(vg, (rowX + 18) * S, (yy + ROW_H / 2f) * S, 9 * S, Theme.txt(), ALIGN_MIDDLE, name);
                    text(vg, (delX + delW / 2f) * S, (yy + ROW_H / 2f) * S, 8.5f * S, Theme.txtDim(), ALIGN_CENTER_MIDDLE, "✕");
                    hits.add(new Object[]{"friendConnect", rowX, yy, rowW - delW - 6, ROW_H, name});
                    hits.add(new Object[]{"friendDelete", delX, yy, delW, ROW_H, name});
                    yy += ROW_H + ROW_GAP;
                }

                boolean foc = "add".equals(focused);
                int addW = 70, fieldW = rowW - addW - 6;
                roundedRect(vg, rowX * S, yy * S, fieldW * S, fieldH * S, 7 * S, foc ? Theme.glassHov() : Theme.glassRow());
                if (foc) roundedRect(vg, rowX * S, (yy + fieldH - 1) * S, fieldW * S, S, 1, Theme.accent());
                String show = friendAddName.isEmpty() && !foc ? com.lume.client.Lang.tUI("friend name") : friendAddName + (foc ? "_" : "");
                text(vg, (rowX + 8) * S, (yy + fieldH / 2f) * S, 8.5f * S, friendAddName.isEmpty() && !foc ? Theme.txtDim() : Theme.txt(), ALIGN_MIDDLE, show);
                int addX = rowX + fieldW + 6;
                roundedRect(vg, addX * S, yy * S, addW * S, fieldH * S, 7 * S, Theme.accent());
                text(vg, (addX + addW / 2f) * S, (yy + fieldH / 2f) * S, 8.5f * S, Theme.activeText(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Add"));
                hits.add(new Object[]{"field", rowX, yy, fieldW, fieldH});
                hits.add(new Object[]{"friendAdd", addX, yy, addW, fieldH});
                yy += fieldH + 16;

                int homeW = 90, homeH = 26;
                float homeX = x / S + (WIN_W - homeW) / 2f;
                roundedRect(vg, homeX * S, yy * S, homeW * S, homeH * S, 8 * S, Theme.glassRow());
                text(vg, (homeX + homeW / 2f) * S, (yy + homeH / 2f) * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Home"));
                hits.add(new Object[]{"home", (int) homeX, yy, homeW, homeH});

                restore(vg);
            });
        } catch (Throwable t) {
            System.out.println("[Lume] FriendsScreen render failed: " + t);
        }
        if (p < 1f) com.lume.client.nanovg.GlassRenderer.transitionOverlay(x, y, W, H, (1f - p) * 0.8f, 1f - p);
    }

    private float openAnim() {
        float pr = (System.currentTimeMillis() - openTime) / 160f;
        if (pr >= 1f) return 1f;
        if (pr <= 0f) return 0f;
        return 1f - (1f - pr) * (1f - pr);
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
                case "friendConnect" -> {
                    String friendName = (String) h[5];
                    Friends.Status st = Friends.statusOf(friendName);
                    if (st != null && st.online && st.server != null) Friends.connectTo(st.server);
                }
                case "friendDelete" -> Friends.removeFriend((String) h[5]);
                case "friendAdd" -> { Friends.addFriend(friendAddName.trim()); friendAddName = ""; }
                case "field" -> focused = "add";
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
        if ("add".equals(focused) && chr >= 32 && chr != 127) { friendAddName += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (focused != null) { focused = null; return true; }
            close();
            return true;
        }
        if ("add".equals(focused)) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!friendAddName.isEmpty()) friendAddName = friendAddName.substring(0, friendAddName.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                Friends.addFriend(friendAddName.trim());
                friendAddName = "";
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package com.lume.client.menu;

import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.social.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final Map<String, float[]> anim = new HashMap<>();
    private float[] animFor(String id) { return anim.computeIfAbsent(id, k -> new float[1]); }
    private static float approach(float cur, float target, float rate, float dt) { return cur + (target - cur) * Math.min(1f, rate * dt); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    /** Multiplies an ARGB color's alpha by {@code p} — the DrawContext equivalent of NanoVG's
     *  {@code globalAlpha}, which has no per-call analogue here so each draw bakes it in. */
    private static int fade(int argb, float p) {
        int a = Math.round(((argb >>> 24) & 0xFF) * p);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        CustomMenu.drawDimOverlay(ctx, width, height);
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        List<String> friends = new ArrayList<>(Friends.friendList);
        friends.sort((a, b) -> Boolean.compare(Friends.isOnline(b), Friends.isOnline(a)));   // online first

        int rowsH = friends.size() * (ROW_H + ROW_GAP);
        int fieldH = 26;
        int winH = 50 + rowsH + fieldH + 16 + 16 + 26;
        int W = WIN_W, H = winH;
        int x = (width - W) / 2, y = (height - H) / 2;
        float p = openAnim();
        int r = 14;

        int rowX = x + 14, rowW = WIN_W - 28;

        try {
            TextRenderer tr = MinecraftClient.getInstance().textRenderer;
            RenderUtil.glow(ctx, x, y, W, H, r, 0x000000, 3);
            RenderUtil.gradientRoundedRect(ctx, x, y, W, H, r, fade(Theme.winTop(), p), fade(Theme.winBot(), p));
            RenderUtil.strokeRoundedRect(ctx, x, y, W, H, r, 1, fade(Theme.rim(), p));
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Friends"), x, y + 12, W, 20, fade(Theme.txt(), p), 0.6f);

            int yy = y + 40;
            for (String name : friends) {
                boolean online = Friends.isOnline(name);
                int delW = 16, delX = rowX + rowW - delW - 4;
                float[] rh = animFor("row:" + name);
                rh[0] = approach(rh[0], inside(mouseX, mouseY, rowX, yy, rowW, ROW_H) ? 1f : 0f, 14f, dt);
                RenderUtil.roundedRect(ctx, rowX, yy, rowW, ROW_H, 7, fade(Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), rh[0]), p));
                RenderUtil.roundedRect(ctx, rowX + 7, yy + ROW_H / 2 - 3, 5, 5, 3, fade(online ? 0xFF6FCF7F : Theme.txtDim(), p));
                RenderUtil.textVCentered(ctx, tr, name, rowX + 18, yy, ROW_H, fade(Theme.txt(), p), 0.5f);
                RenderUtil.textCentered(ctx, tr, "✕", delX, yy, delW, ROW_H, fade(Theme.txtDim(), p), 0.47f);
                hits.add(new Object[]{"friendConnect", rowX, yy, rowW - delW - 6, ROW_H, name});
                hits.add(new Object[]{"friendDelete", delX, yy, delW, ROW_H, name});
                yy += ROW_H + ROW_GAP;
            }

            boolean foc = "add".equals(focused);
            int addW = 70, fieldW = rowW - addW - 6;
            RenderUtil.roundedRect(ctx, rowX, yy, fieldW, fieldH, 7, fade(foc ? Theme.glassHov() : Theme.glassRow(), p));
            if (foc) RenderUtil.roundedRect(ctx, rowX, yy + fieldH - 1, fieldW, 1, 1, fade(Theme.accent(), p));
            String show = friendAddName.isEmpty() && !foc ? com.lume.client.Lang.tUI("friend name") : friendAddName + (foc ? "_" : "");
            RenderUtil.textVCentered(ctx, tr, show, rowX + 8, yy, fieldH, fade(friendAddName.isEmpty() && !foc ? Theme.txtDim() : Theme.txt(), p), 0.47f);
            int addX = rowX + fieldW + 6;
            RenderUtil.roundedRect(ctx, addX, yy, addW, fieldH, 7, fade(Theme.accent(), p));
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Add"), addX, yy, addW, fieldH, fade(Theme.activeText(), p), 0.47f);
            hits.add(new Object[]{"field", rowX, yy, fieldW, fieldH});
            hits.add(new Object[]{"friendAdd", addX, yy, addW, fieldH});
            yy += fieldH + 16;

            int homeW = 90, homeH = 26;
            int homeX = x + (WIN_W - homeW) / 2;
            RenderUtil.roundedRect(ctx, homeX, yy, homeW, homeH, 8, fade(Theme.glassRow(), p));
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Home"), homeX, yy, homeW, homeH, fade(Theme.txt(), p), 0.47f);
            hits.add(new Object[]{"home", homeX, yy, homeW, homeH});
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

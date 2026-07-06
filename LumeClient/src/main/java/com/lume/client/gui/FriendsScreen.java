package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Full-screen Friends tab — local friend list + online status placeholder. */
public class FriendsScreen extends LumeSubScreen {

    private static final int WIN_W = 520, WIN_H = 356;

    private static final List<String> friends = new ArrayList<>(); // simple in-memory for now
    private String addName = "";
    private boolean addFocused = false;
    private int[] addBox = { 0, 0, 0, 0 };
    private int[] addBtnCoords = { 0, 0, 0, 0 };
    private final List<int[]> delBtns = new ArrayList<>();

    public FriendsScreen(Screen parent) {
        super(Text.literal("Friends – Lume"), parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);   // same background blur as the main menu
        ctx.fill(0, 0, width, height, Theme.backdrop());
        drawHudEditor(ctx, mouseX, mouseY);          // draggable HUD frames stay visible here
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        int W = WIN_W * S, H = WIN_H * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        computeTotal(WIN_W, WIN_H);
        int mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        int margin = 24 * S;
        int gy = y + 42 * S;
        int sx = x + margin, ew = W - margin * 2;
        int rowH = 32 * S, gap = 6 * S;

        delBtns.clear();

        NanoVgRenderer.frame(vg -> {
            applyTransform(vg, S, sw, sh);
            drawWindowFrame(vg, x, y, W, H, S, mx, my, 3);

            // Online-status notice
            NanoVgRenderer.text(vg, sx, gy + 4 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    com.lume.client.Lang.tUI("Online status coming soon — add names for now:"));

            int listY = gy + 20 * S;
            int clipBot = y + H - 72 * S;
            NanoVgRenderer.save(vg);
            NanoVgRenderer.scissor(vg, x, listY, W, clipBot - listY);
            int cur = 0;
            for (int i = 0; i < friends.size(); i++) {
                String name = friends.get(i);
                int ry = listY + cur;
                if (ry + rowH >= listY && ry <= clipBot) {
                    boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + rowH;
                    NanoVgRenderer.roundedRect(vg, sx, ry, ew, rowH, 9 * S, hov ? Theme.glassHov() : Theme.glassRow());
                    // Offline dot
                    NanoVgRenderer.circle(vg, sx + 14 * S, ry + rowH / 2f, 4 * S, Theme.pillOff());
                    NanoVgRenderer.text(vg, sx + 24 * S, ry + rowH / 2f, 12 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, name);
                    NanoVgRenderer.text(vg, sx + ew - 50 * S, ry + rowH / 2f, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI("offline"));
                    // Delete
                    NanoVgRenderer.text(vg, sx + ew - 16 * S, ry + rowH / 2f, 10 * S, 0xFFE05656, NanoVgRenderer.ALIGN_MIDDLE, "✕");
                    delBtns.add(new int[]{ i, sx + ew - 22 * S, ry, 22 * S, rowH });
                }
                cur += rowH + gap;
            }
            if (friends.isEmpty()) {
                NanoVgRenderer.text(vg, sx + ew / 2f, listY + 40 * S, 11 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                        com.lume.client.Lang.tUI("List is empty — add a player name below"));
            }
            NanoVgRenderer.restore(vg);

            // Add friend field
            int ibAreaY = y + H - 62 * S;
            NanoVgRenderer.roundedRect(vg, x + 10 * S, ibAreaY - 2 * S, W - 20 * S, Math.max(1, S), 0.5f, Theme.border());
            NanoVgRenderer.text(vg, sx, ibAreaY + 8 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI("Add to list:"));

            int ibW = ew - 80 * S, ibH = 28 * S;
            int ibX = sx, ibY = ibAreaY + 22 * S;
            NanoVgRenderer.roundedRect(vg, ibX, ibY, ibW, ibH, 8 * S, addFocused ? Theme.glassHov() : Theme.glassRow());
            NanoVgRenderer.strokeRoundedRect(vg, ibX + 0.5f * S, ibY + 0.5f * S, ibW - S, ibH - S, 8 * S, S,
                    addFocused ? Theme.accent() : Theme.rim());
            String placeholder = com.lume.client.Lang.tUI("player name…");
            String shown = addName.isEmpty() && !addFocused ? placeholder : addName + (addFocused ? "|" : "");
            NanoVgRenderer.text(vg, ibX + 10 * S, ibY + ibH / 2f, 10 * S,
                    addName.isEmpty() && !addFocused ? Theme.txtDim() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, shown);
            addBox = new int[]{ ibX, ibY, ibW, ibH };

            int applyX = ibX + ibW + 8 * S, applyW = ew - ibW - 8 * S;
            NanoVgRenderer.gradientRoundedRect(vg, applyX, ibY, applyW, ibH, 8 * S, Theme.accent(), Theme.accent2());
            NanoVgRenderer.text(vg, applyX + applyW / 2f, ibY + ibH / 2f, 10 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Add"));
            addBtnCoords = new int[]{ applyX, ibY, applyW, ibH };
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (tryNavClick(mouseX, mouseY)) return true;

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        double mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        // Add field
        if (mx >= addBox[0] && mx <= addBox[0] + addBox[2] && my >= addBox[1] && my <= addBox[1] + addBox[3]) {
            addFocused = true; return true;
        }
        addFocused = false;
        // Add button
        if (mx >= addBtnCoords[0] && mx <= addBtnCoords[0] + addBtnCoords[2]
                && my >= addBtnCoords[1] && my <= addBtnCoords[1] + addBtnCoords[3]) {
            addFriend(); return true;
        }
        // Delete buttons
        for (int[] d : delBtns) {
            if (mx >= d[1] && mx <= d[1] + d[3] && my >= d[2] && my <= d[2] + d[4]) {
                int idx = d[0];
                if (idx >= 0 && idx < friends.size()) friends.remove(idx);
                return true;
            }
        }
        if (tryHudDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addFriend() {
        String name = addName.trim();
        if (!name.isEmpty() && !friends.contains(name)) friends.add(name);
        addName = "";
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (addFocused && chr >= 32 && chr != 127 && chr != ' ') { addName += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (addFocused) {
            if (key == 256) { addFocused = false; return true; }
            if (key == 257 || key == 335) { addFriend(); return true; }
            if (key == 259 && !addName.isEmpty()) { addName = addName.substring(0, addName.length() - 1); return true; }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

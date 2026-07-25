package com.lume.client.gui;

import com.lume.client.Lang;
import com.lume.client.nanovg.NanoVgRenderer;
import com.lume.client.social.Friends;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen Friends tab — cross-server presence, friend requests, "Connect"
 * / "TPA" per friend, and shared cross-server markers. All state lives in
 * {@link Friends} (network I/O in {@link com.lume.client.social.FriendsNet}),
 * this class only renders it and dispatches clicks. Backend must be reachable
 * (see /LumeFriendsServer) for online status to populate — offline just shows
 * everyone as offline, nothing breaks.
 */
public class FriendsScreen extends LumeSubScreen {

    private static final int WIN_W = 520, WIN_H = 356;

    private record Hit(String kind, String arg, int x, int y, int w, int h) {}

    private String addName = "";
    private boolean addFocused = false;
    private int[] addBox = { 0, 0, 0, 0 };
    private int[] addBtnCoords = { 0, 0, 0, 0 };
    private int[] shareBtnCoords = { 0, 0, 0, 0 };
    private final List<Hit> hits = new ArrayList<>();

    private float scroll = 0f, scrollTarget = 0f;
    private long lastFrame = System.currentTimeMillis();

    public FriendsScreen(Screen parent) {
        super(Text.literal("Friends – Lume"), parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, Theme.backdrop());
        drawHudEditor(ctx, mouseX, mouseY);
        NanoVgRenderer.ensureInit();

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        scroll = approach(scroll, scrollTarget, 14f, dt);

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        int W = WIN_W * S, H = WIN_H * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        computeTotal(WIN_W, WIN_H);
        int mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        int margin = 22 * S;
        int sx = x + margin, ew = W - margin * 2;
        int listY = y + 50 * S;
        int shareBarY = y + H - 96 * S;
        int addBarY = y + H - 62 * S;
        int clipBot = shareBarY - 8 * S;
        int rowH = 30 * S, gap = 6 * S;

        hits.clear();

        List<String> myFriends = Friends.friendList;
        List<String> myIncoming = Friends.incoming;
        List<Friends.Point> myPoints = Friends.pointsHere();

        if (!NanoVgRenderer.ready()) {
            renderLegacyContent(ctx, S, sw, sh, x, y, W, H, mx, my, dt, sx, ew, listY, shareBarY,
                    addBarY, clipBot, rowH, gap, myFriends, myIncoming, myPoints);
            drawOpenTransition(S, sw, sh, x, y, W, H);
            return;
        }

        drawGlassBackdrop(S, sw, sh, x, y, W, H, 18 * S);
        ctx.draw();   // flush DrawContext's own queued geometry before raw-GL NanoVG draws
        NvgTextQueue.begin(ClickGuiScreen.getWinOffX() * S, ClickGuiScreen.getWinOffY() * S, sw / 2.0, sh / 2.0, total);
        NanoVgRenderer.frame(vg -> {
            applyTransform(vg, S, sw, sh);
            drawWindowFrame(vg, x, y, W, H, S, mx, my, 3, dt);

            NanoVgRenderer.save(vg);
            NanoVgRenderer.scissor(vg, x, listY, W, clipBot - listY);
            int cur = (int) -scroll;

            if (!myIncoming.isEmpty()) {
                cur = drawSectionTitle(vg, sx, listY, cur, S, Lang.tUI("Incoming requests"));
                for (String name : myIncoming) {
                    int ry = listY + cur;
                    if (rowVisible(ry, rowH, listY, clipBot)) {
                        boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + rowH;
                        NanoVgRenderer.roundedRect(vg, sx, ry, ew, rowH, 9 * S, hov ? Theme.glassHov() : Theme.glassRow());
                        NanoVgRenderer.text(vg, sx + 12 * S, ry + rowH / 2f, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, name);

                        int btnW = 26 * S, btnH = 22 * S, gapB = 6 * S;
                        int declX = sx + ew - 12 * S - btnW, declY = ry + (rowH - btnH) / 2;
                        int accX = declX - gapB - btnW;
                        NanoVgRenderer.roundedRect(vg, accX, declY, btnW, btnH, 6 * S, 0xFF3E9C5E);
                        NanoVgRenderer.text(vg, accX + btnW / 2f, declY + btnH / 2f, 11 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, "✓");
                        NanoVgRenderer.roundedRect(vg, declX, declY, btnW, btnH, 6 * S, 0xFFB2453F);
                        NanoVgRenderer.text(vg, declX + btnW / 2f, declY + btnH / 2f, 11 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, "✕");
                        hits.add(new Hit("accept", name, accX, declY, btnW, btnH));
                        hits.add(new Hit("decline", name, declX, declY, btnW, btnH));
                    }
                    cur += rowH + gap;
                }
                cur += 8 * S;
            }

            long onlineCount = myFriends.stream().filter(Friends::isOnline).count();
            cur = drawSectionTitle(vg, sx, listY, cur, S,
                    Lang.tUI("Friends") + " (" + onlineCount + "/" + myFriends.size() + " " + Lang.tUI("online") + ")");

            if (myFriends.isEmpty()) {
                int ry = listY + cur;
                if (rowVisible(ry, rowH, listY, clipBot)) {
                    NanoVgRenderer.text(vg, sx, ry + rowH / 2f, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                            Lang.tUI("No friends yet — add a player name below"));
                }
                cur += rowH + gap;
            }

            for (String name : myFriends) {
                int ry = listY + cur;
                if (rowVisible(ry, rowH, listY, clipBot)) {
                    boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + rowH;
                    boolean online = Friends.isOnline(name);
                    boolean sameServer = Friends.onSameServer(name);
                    Friends.Status st = Friends.statusOf(name);

                    NanoVgRenderer.roundedRect(vg, sx, ry, ew, rowH, 9 * S, hov ? Theme.glassHov() : Theme.glassRow());
                    NanoVgRenderer.circle(vg, sx + 14 * S, ry + rowH / 2f, 4 * S, online ? 0xFF6FCF7F : Theme.pillOff());
                    NanoVgRenderer.text(vg, sx + 24 * S, ry + rowH / 2f, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, name);

                    String statusTxt;
                    if (!online) statusTxt = Lang.tUI("offline");
                    else if (sameServer) statusTxt = Lang.tUI("online · this server");
                    else statusTxt = Lang.tUI("online") + (st != null && st.server != null ? " · " + st.server : "");

                    int btnW = 46 * S, btnH = 22 * S, gapB = 6 * S;
                    int removeX = sx + ew - 12 * S - 22 * S, removeY = ry + (rowH - btnH) / 2;
                    NanoVgRenderer.text(vg, removeX + 11 * S, removeY + btnH / 2f, 10 * S, 0xFFE05656, NanoVgRenderer.ALIGN_CENTER_MIDDLE, "✕");
                    hits.add(new Hit("remove", name, removeX, removeY, 22 * S, btnH));

                    int rightEdge = removeX - gapB;
                    if (online && sameServer) {
                        int tpaX = rightEdge - btnW;
                        NanoVgRenderer.gradientRoundedRect(vg, tpaX, removeY, btnW, btnH, 6 * S, Theme.accent(), Theme.accent2());
                        NanoVgRenderer.text(vg, tpaX + btnW / 2f, removeY + btnH / 2f, 9 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "TPA");
                        hits.add(new Hit("tpa", name, tpaX, removeY, btnW, btnH));
                        rightEdge = tpaX - gapB;
                    } else if (online && st != null && st.server != null && !st.server.equals("singleplayer")) {
                        int connW = 66 * S;
                        int connX = rightEdge - connW;
                        NanoVgRenderer.roundedRect(vg, connX, removeY, connW, btnH, 6 * S, Theme.glassHov());
                        NanoVgRenderer.strokeRoundedRect(vg, connX + 0.5f * S, removeY + 0.5f * S, connW - S, btnH - S, 6 * S, S, Theme.accent());
                        NanoVgRenderer.text(vg, connX + connW / 2f, removeY + btnH / 2f, 9 * S, Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, Lang.tUI("Connect"));
                        hits.add(new Hit("connect", st.server, connX, removeY, connW, btnH));
                        rightEdge = connX - gapB;
                    }

                    float statusW = NanoVgRenderer.textWidth(vg, 9 * S, statusTxt);
                    NanoVgRenderer.text(vg, rightEdge - 8 * S - statusW, ry + rowH / 2f, 9 * S,
                            Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, statusTxt);
                }
                cur += rowH + gap;
            }

            if (!myPoints.isEmpty()) {
                cur += 8 * S;
                cur = drawSectionTitle(vg, sx, listY, cur, S, Lang.tUI("Shared points here"));
                String me = Friends.myName();
                for (Friends.Point p : myPoints) {
                    int ry = listY + cur;
                    if (rowVisible(ry, rowH, listY, clipBot)) {
                        boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + rowH;
                        NanoVgRenderer.roundedRect(vg, sx, ry, ew, rowH, 9 * S, hov ? Theme.glassHov() : Theme.glassRow());
                        NanoVgRenderer.circle(vg, sx + 14 * S, ry + rowH / 2f, 4 * S, 0xFF6F9CE0);
                        NanoVgRenderer.text(vg, sx + 24 * S, ry + rowH / 2f, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE,
                                p.name + "  —  " + p.from);
                        if (me != null && me.equalsIgnoreCase(p.from)) {
                            int dX = sx + ew - 12 * S - 22 * S, dY = ry + (rowH - 22 * S) / 2;
                            NanoVgRenderer.text(vg, dX + 11 * S, dY + 11 * S, 10 * S, 0xFFE05656, NanoVgRenderer.ALIGN_CENTER_MIDDLE, "✕");
                            hits.add(new Hit("deletepoint", String.valueOf(p.id), dX, dY, 22 * S, 22 * S));
                        }
                    }
                    cur += rowH + gap;
                }
            }

            int contentH = cur;
            NanoVgRenderer.restore(vg);

            int viewH = clipBot - listY;
            if (contentH > viewH) {
                float ratio = (float) viewH / contentH;
                int barH = Math.max(20 * S, (int) (viewH * ratio));
                int barY = listY + (int) (scroll / Math.max(1, contentH - viewH) * (viewH - barH));
                NanoVgRenderer.roundedRect(vg, x + W - 8 * S, barY, 3 * S, barH, 1.5f * S, Theme.rim());
            }

            // Share-my-location bar
            NanoVgRenderer.text(vg, sx, shareBarY + 4 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    Lang.tUI("Share your position with all friends:"));
            int shW = 150 * S, shH = 26 * S, shX = sx, shY = shareBarY + 16 * S;
            boolean canShare = Friends.myName() != null;
            NanoVgRenderer.gradientRoundedRect(vg, shX, shY, shW, shH, 8 * S,
                    canShare ? Theme.accent() : Theme.glassRow(), canShare ? Theme.accent2() : Theme.glassRow());
            NanoVgRenderer.text(vg, shX + shW / 2f, shY + shH / 2f, 10 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                    Lang.tUI("Share position"));
            shareBtnCoords = new int[]{ shX, shY, shW, shH };

            // Add friend field
            NanoVgRenderer.roundedRect(vg, x + 10 * S, addBarY - 2 * S, W - 20 * S, Math.max(1, S), 0.5f, Theme.border());
            NanoVgRenderer.text(vg, sx, addBarY + 8 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, Lang.tUI("Add friend:"));

            int ibW = ew - 80 * S, ibH = 28 * S;
            int ibX = sx, ibY = addBarY + 22 * S;
            NanoVgRenderer.roundedRect(vg, ibX, ibY, ibW, ibH, 8 * S, addFocused ? Theme.glassHov() : Theme.glassRow());
            NanoVgRenderer.strokeRoundedRect(vg, ibX + 0.5f * S, ibY + 0.5f * S, ibW - S, ibH - S, 8 * S, S,
                    addFocused ? Theme.accent() : Theme.rim());
            String placeholder = Lang.tUI("player name…");
            String shown = addName.isEmpty() && !addFocused ? placeholder : addName + (addFocused ? "|" : "");
            NanoVgRenderer.text(vg, ibX + 10 * S, ibY + ibH / 2f, 10 * S,
                    addName.isEmpty() && !addFocused ? Theme.txtDim() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, shown);
            addBox = new int[]{ ibX, ibY, ibW, ibH };

            int applyX = ibX + ibW + 8 * S, applyW = ew - ibW - 8 * S;
            NanoVgRenderer.gradientRoundedRect(vg, applyX, ibY, applyW, ibH, 8 * S, Theme.accent(), Theme.accent2());
            NanoVgRenderer.text(vg, applyX + applyW / 2f, ibY + ibH / 2f, 10 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, Lang.tUI("Add"));
            addBtnCoords = new int[]{ applyX, ibY, applyW, ibH };
        });
        NvgTextQueue.flush(ctx);
        drawOpenTransition(S, sw, sh, x, y, W, H);
    }

    private boolean rowVisible(int ry, int rowH, int listY, int clipBot) {
        return ry + rowH >= listY && ry <= clipBot;
    }

    private int drawSectionTitle(long vg, int sx, int listY, int cur, int S, String label) {
        NanoVgRenderer.text(vg, sx, listY + cur + 6 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, label.toUpperCase(Locale.ROOT));
        return cur + 20 * S;
    }

    private int drawSectionTitleLegacy(DrawContext ctx, int sx, int listY, int cur, int S, String label) {
        RenderUtil.textVCentered(ctx, this.textRenderer, label.toUpperCase(Locale.ROOT), sx, listY + cur, 12 * S, Theme.txtDim(), 9f / 18f);
        return cur + 20 * S;
    }

    /** DrawContext port of the NanoVG frame body above — same layout math, 1:1 primitive swap
     *  per the established NanoVG-removal conversion pattern. */
    private void renderLegacyContent(DrawContext ctx, int S, int sw, int sh, int x, int y, int W, int H,
                                      int mx, int my, float dt, int sx, int ew, int listY, int shareBarY,
                                      int addBarY, int clipBot, int rowH, int gap,
                                      List<String> myFriends, List<String> myIncoming, List<Friends.Point> myPoints) {
        applyTransformLegacy(ctx, S, sw, sh);
        drawWindowFrameLegacy(ctx, x, y, W, H, S, mx, my, 3, dt);
        var tr = this.textRenderer;

        ctx.enableScissor(x, listY, x + W, clipBot);
        int cur = (int) -scroll;

        if (!myIncoming.isEmpty()) {
            cur = drawSectionTitleLegacy(ctx, sx, listY, cur, S, Lang.tUI("Incoming requests"));
            for (String name : myIncoming) {
                int ry = listY + cur;
                if (rowVisible(ry, rowH, listY, clipBot)) {
                    boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + rowH;
                    sdfFill(ctx, S, sx, ry, ew, rowH, 9 * S, hov ? Theme.glassHov() : Theme.glassRow());
                    RenderUtil.textVCentered(ctx, tr, name, sx + 12 * S, ry, rowH, Theme.txt(), 11f / 18f);

                    int btnW = 26 * S, btnH = 22 * S, gapB = 6 * S;
                    int declX = sx + ew - 12 * S - btnW, declY = ry + (rowH - btnH) / 2;
                    int accX = declX - gapB - btnW;
                    sdfFill(ctx, S, accX, declY, btnW, btnH, 6 * S, 0xFF3E9C5E);
                    RenderUtil.textCentered(ctx, tr, "✓", accX, declY, btnW, btnH, 0xFFFFFFFF, 11f / 18f);
                    sdfFill(ctx, S, declX, declY, btnW, btnH, 6 * S, 0xFFB2453F);
                    RenderUtil.textCentered(ctx, tr, "✕", declX, declY, btnW, btnH, 0xFFFFFFFF, 11f / 18f);
                    hits.add(new Hit("accept", name, accX, declY, btnW, btnH));
                    hits.add(new Hit("decline", name, declX, declY, btnW, btnH));
                }
                cur += rowH + gap;
            }
            cur += 8 * S;
        }

        long onlineCount = myFriends.stream().filter(Friends::isOnline).count();
        cur = drawSectionTitleLegacy(ctx, sx, listY, cur, S,
                Lang.tUI("Friends") + " (" + onlineCount + "/" + myFriends.size() + " " + Lang.tUI("online") + ")");

        if (myFriends.isEmpty()) {
            int ry = listY + cur;
            if (rowVisible(ry, rowH, listY, clipBot)) {
                RenderUtil.textVCentered(ctx, tr, Lang.tUI("No friends yet — add a player name below"), sx, ry, rowH, Theme.txtDim(), 10f / 18f);
            }
            cur += rowH + gap;
        }

        for (String name : myFriends) {
            int ry = listY + cur;
            if (rowVisible(ry, rowH, listY, clipBot)) {
                boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + rowH;
                boolean online = Friends.isOnline(name);
                boolean sameServer = Friends.onSameServer(name);
                Friends.Status st = Friends.statusOf(name);

                sdfFill(ctx, S, sx, ry, ew, rowH, 9 * S, hov ? Theme.glassHov() : Theme.glassRow());
                sdfFill(ctx, S, sx + 10 * S, ry + rowH / 2 - 4 * S, 8 * S, 8 * S, 4 * S, online ? 0xFF6FCF7F : Theme.pillOff());
                RenderUtil.textVCentered(ctx, tr, name, sx + 24 * S, ry, rowH, Theme.txt(), 11f / 18f);

                String statusTxt;
                if (!online) statusTxt = Lang.tUI("offline");
                else if (sameServer) statusTxt = Lang.tUI("online · this server");
                else statusTxt = Lang.tUI("online") + (st != null && st.server != null ? " · " + st.server : "");

                int btnW = 46 * S, btnH = 22 * S, gapB = 6 * S;
                int removeX = sx + ew - 12 * S - 22 * S, removeY = ry + (rowH - btnH) / 2;
                RenderUtil.textCentered(ctx, tr, "✕", removeX, removeY, 22 * S, btnH, 0xFFE05656, 10f / 18f);
                hits.add(new Hit("remove", name, removeX, removeY, 22 * S, btnH));

                int rightEdge = removeX - gapB;
                if (online && sameServer) {
                    int tpaX = rightEdge - btnW;
                    sdfFill(ctx, S, tpaX, removeY, btnW, btnH, 6 * S, Theme.accent());
                    RenderUtil.textCentered(ctx, tr, "TPA", tpaX, removeY, btnW, btnH, Theme.activeText(), 9f / 18f);
                    hits.add(new Hit("tpa", name, tpaX, removeY, btnW, btnH));
                    rightEdge = tpaX - gapB;
                } else if (online && st != null && st.server != null && !st.server.equals("singleplayer")) {
                    int connW = 66 * S;
                    int connX = rightEdge - connW;
                    sdfFillOutline(ctx, S, connX, removeY, connW, btnH, 6 * S, Theme.glassHov(), Theme.accent(), S);
                    RenderUtil.textCentered(ctx, tr, Lang.tUI("Connect"), connX, removeY, connW, btnH, Theme.accent(), 9f / 18f);
                    hits.add(new Hit("connect", st.server, connX, removeY, connW, btnH));
                    rightEdge = connX - gapB;
                }

                int statusW = RenderUtil.width(tr, statusTxt, 9f / 18f);
                RenderUtil.textVCentered(ctx, tr, statusTxt, rightEdge - 8 * S - statusW, ry, rowH, Theme.txtDim(), 9f / 18f);
            }
            cur += rowH + gap;
        }

        if (!myPoints.isEmpty()) {
            cur += 8 * S;
            cur = drawSectionTitleLegacy(ctx, sx, listY, cur, S, Lang.tUI("Shared points here"));
            String me = Friends.myName();
            for (Friends.Point p : myPoints) {
                int ry = listY + cur;
                if (rowVisible(ry, rowH, listY, clipBot)) {
                    sdfFill(ctx, S, sx, ry, ew, rowH, 9 * S, Theme.glassRow());
                    sdfFill(ctx, S, sx + 10 * S, ry + rowH / 2 - 4 * S, 8 * S, 8 * S, 4 * S, 0xFF6F9CE0);
                    RenderUtil.textVCentered(ctx, tr, p.name + "  —  " + p.from, sx + 24 * S, ry, rowH, Theme.txt(), 11f / 18f);
                    if (me != null && me.equalsIgnoreCase(p.from)) {
                        int dX = sx + ew - 12 * S - 22 * S, dY = ry + (rowH - 22 * S) / 2;
                        RenderUtil.textCentered(ctx, tr, "✕", dX, dY, 22 * S, 22 * S, 0xFFE05656, 10f / 18f);
                        hits.add(new Hit("deletepoint", String.valueOf(p.id), dX, dY, 22 * S, 22 * S));
                    }
                }
                cur += rowH + gap;
            }
        }

        int contentH = cur;
        ctx.disableScissor();

        int viewH = clipBot - listY;
        if (contentH > viewH) {
            float ratio = (float) viewH / contentH;
            int barH = Math.max(20 * S, (int) (viewH * ratio));
            int barY = listY + (int) (scroll / Math.max(1, contentH - viewH) * (viewH - barH));
            sdfFill(ctx, S, x + W - 8 * S, barY, 3 * S, barH, S, Theme.rim());
        }

        // Share-my-location bar
        RenderUtil.textVCentered(ctx, tr, Lang.tUI("Share your position with all friends:"), sx, shareBarY, 12 * S, Theme.txtDim(), 9f / 18f);
        int shW = 150 * S, shH = 26 * S, shX = sx, shY = shareBarY + 16 * S;
        boolean canShare = Friends.myName() != null;
        sdfFill(ctx, S, shX, shY, shW, shH, 8 * S, canShare ? Theme.accent() : Theme.glassRow());
        RenderUtil.textCentered(ctx, tr, Lang.tUI("Share position"), shX, shY, shW, shH, Theme.activeText(), 10f / 18f);
        shareBtnCoords = new int[]{ shX, shY, shW, shH };

        // Add friend field
        RenderUtil.roundedRect(ctx, x + 10 * S, addBarY - 2 * S, W - 20 * S, Math.max(1, S), Math.max(1, S) / 2, Theme.border());
        RenderUtil.textVCentered(ctx, tr, Lang.tUI("Add friend:"), sx, addBarY, 12 * S, Theme.txtDim(), 9f / 18f);

        int ibW = ew - 80 * S, ibH = 28 * S;
        int ibX = sx, ibY = addBarY + 22 * S;
        sdfFillOutline(ctx, S, ibX, ibY, ibW, ibH, 8 * S,
                addFocused ? Theme.glassHov() : Theme.glassRow(), addFocused ? Theme.accent() : Theme.rim(), S);
        String placeholder = Lang.tUI("player name…");
        String shown = addName.isEmpty() && !addFocused ? placeholder : addName + (addFocused ? "|" : "");
        RenderUtil.textVCentered(ctx, tr, shown, ibX + 10 * S, ibY, ibH,
                addName.isEmpty() && !addFocused ? Theme.txtDim() : Theme.txt(), 10f / 18f);
        addBox = new int[]{ ibX, ibY, ibW, ibH };

        int applyX = ibX + ibW + 8 * S, applyW = ew - ibW - 8 * S;
        sdfFill(ctx, S, applyX, ibY, applyW, ibH, 8 * S, Theme.accent());
        RenderUtil.textCentered(ctx, tr, Lang.tUI("Add"), applyX, ibY, applyW, ibH, Theme.activeText(), 10f / 18f);
        addBtnCoords = new int[]{ applyX, ibY, applyW, ibH };

        ctx.getMatrices().pop();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * 30;
        scrollTarget = Math.max(0, scrollTarget);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (tryNavClick(mouseX, mouseY)) return true;

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        double mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        if (mx >= addBox[0] && mx <= addBox[0] + addBox[2] && my >= addBox[1] && my <= addBox[1] + addBox[3]) {
            addFocused = true; return true;
        }
        addFocused = false;

        if (mx >= addBtnCoords[0] && mx <= addBtnCoords[0] + addBtnCoords[2]
                && my >= addBtnCoords[1] && my <= addBtnCoords[1] + addBtnCoords[3]) {
            addFriend(); return true;
        }
        if (mx >= shareBtnCoords[0] && mx <= shareBtnCoords[0] + shareBtnCoords[2]
                && my >= shareBtnCoords[1] && my <= shareBtnCoords[1] + shareBtnCoords[3]) {
            Friends.sharePointHere("all"); return true;
        }

        for (Hit h : hits) {
            if (mx >= h.x() && mx <= h.x() + h.w() && my >= h.y() && my <= h.y() + h.h()) {
                switch (h.kind()) {
                    case "accept" -> Friends.acceptFriend(h.arg());
                    case "decline" -> Friends.declineFriend(h.arg());
                    case "remove" -> Friends.removeFriend(h.arg());
                    case "tpa" -> Friends.tpaTo(h.arg());
                    case "connect" -> Friends.connectTo(h.arg());
                    case "deletepoint" -> {
                        for (Friends.Point p : Friends.points) {
                            if (String.valueOf(p.id).equals(h.arg())) { Friends.deletePoint(p); break; }
                        }
                    }
                }
                return true;
            }
        }

        if (tryHudDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addFriend() {
        String name = addName.trim();
        if (!name.isEmpty()) Friends.addFriend(name);
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

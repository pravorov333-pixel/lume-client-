package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Base for full-screen sub-menus (Events / Config / Friends).
 *  Shares the exact window frame + open animation + transform of ClickGuiScreen
 *  so every catalog looks identical to the main menu — only the content differs. */
abstract class LumeSubScreen extends Screen {

    protected final Screen parent;
    protected final int[] navX = new int[4], navW = new int[4];
    protected int navBarY, navBarH;

    // Open animation + transform state (mirrors ClickGuiScreen)
    protected final long openTime = System.currentTimeMillis();
    protected float total = 1f;                       // last computed window scale
    protected int[] themeBtn = { 0, 0, 0, 0 };        // window-local coords
    protected int[] colorsBtn = { 0, 0, 0, 0 };       // window-local coords
    private float themeHoverT = 0f, colorsHoverT = 0f;

    // HUD editor state (drag HUD elements from any catalog)
    protected String selectedHud = null, dragHud = null;
    protected int dragMode = 0, grabA, grabB;
    protected double grabMx, grabMy;
    protected String lastFrameClickName = "";
    protected long lastFrameClickT = 0;
    protected int[] hudResizeHandle = null;   // {x,y,w,h} GUI px — "HUD" panel corner resize handle
    protected int[] hudResizeBase = null;     // {w,h} panel size at drag start

    protected LumeSubScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    // ---- open animation + auto-fit (identical to ClickGuiScreen) ----

    protected float anim() {
        float p = (System.currentTimeMillis() - openTime) / 200f;
        if (p > 1f) p = 1f;
        return 1f - (1f - p) * (1f - p);
    }

    protected float fitScale(int winW, int winH) {
        float fw = this.width  / (float) (winW + 24);
        float fh = this.height / (float) (winH + 24);
        return Math.min(1f, Math.min(fw, fh));
    }

    /** Compute + store the current window scale (user scale × auto-fit). Used to zoom in
     *  from 96% on open too — a visible size change on every catalog switch, not wanted —
     *  now always at real size; {@link #drawOpenTransition} does a blur-dissolve instead. */
    protected float computeTotal(int winW, int winH) {
        total = ClickGuiScreen.getWinScale() * fitScale(winW, winH);
        return total;
    }

    /** Blur-dissolve open transition (see ClickGuiScreen's own copy of this idea) — call
     *  AFTER the NanoVG frame that drew [x,y,W,H] (window-local) so it captures the freshly
     *  drawn sharp content and blurs THAT, fading out as anim() finishes. */
    protected void drawOpenTransition(int S, int sw, int sh, int x, int y, int W, int H) {
        float p = anim();
        if (p >= 1f) return;
        double cx = sw / 2.0, cy = sh / 2.0;
        double sx0 = ClickGuiScreen.getWinOffX() * S + cx + total * (x - cx);
        double sy0 = ClickGuiScreen.getWinOffY() * S + cy + total * (y - cy);
        double sx1 = ClickGuiScreen.getWinOffX() * S + cx + total * (x + W - cx);
        double sy1 = ClickGuiScreen.getWinOffY() * S + cy + total * (y + H - cy);
        int gx = (int) Math.round(sx0), gy = (int) Math.round(sy0);
        int gw = (int) Math.round(sx1 - sx0), gh = (int) Math.round(sy1 - sy0);
        com.lume.client.nanovg.GlassRenderer.transitionOverlay(gx, gy, gw, gh, (1f - p) * 0.8f, 1f - p);
    }

    protected int localMx(double mouseX, int S, int sw) {
        double cx = sw / 2.0;
        return (int) Math.round((mouseX * S - ClickGuiScreen.getWinOffX() * S - cx) / total + cx);
    }

    protected int localMy(double mouseY, int S, int sh) {
        double cy = sh / 2.0;
        return (int) Math.round((mouseY * S - ClickGuiScreen.getWinOffY() * S - cy) / total + cy);
    }

    /** Apply the window transform inside a NanoVG frame (call first, before drawing). */
    protected void applyTransform(long vg, int S, int sw, int sh) {
        double cx = sw / 2.0, cy = sh / 2.0;
        NanoVgRenderer.translate(vg, ClickGuiScreen.getWinOffX() * S, ClickGuiScreen.getWinOffY() * S);
        NanoVgRenderer.translate(vg, (float) cx, (float) cy);
        NanoVgRenderer.scale(vg, total, total);
        NanoVgRenderer.translate(vg, (float) -cx, (float) -cy);
    }

    /** Premium Glass backdrop — must be called BEFORE ctx.draw()/NanoVgRenderer.frame() (raw
     *  GL, not routed through NanoVG), using the SAME window-local [x,y,W,H] the subclass is
     *  about to hand to drawWindowFrame. No-op unless Full Glass is the active style. */
    protected void drawGlassBackdrop(int S, int sw, int sh, int x, int y, int W, int H, int r) {
        if (Theme.getGlassStyle() != 1) return;
        double cx = sw / 2.0, cy = sh / 2.0;
        com.lume.client.nanovg.GlassRenderer.panelWindowLocal(
                ClickGuiScreen.getWinOffX() * S, ClickGuiScreen.getWinOffY() * S, cx, cy, total,
                x, y, W, H, r, Theme.getGlassBlur(), Theme.getGlassDistort());
    }

    /** The lume logo mark: half-square (triangle) with a circle centred inside. */
    protected void nvgLogo(long vg, float x, float y, float s) {
        NanoVgRenderer.logoMark(vg, x, y, s);
    }

    /**
     * Draws the full window skeleton exactly like the main menu: top nav bar,
     * layered shadows, gradient glass, rim, header (logo + wordmark) and theme
     * toggle. Content is drawn afterwards by the subclass.
     */
    protected void drawWindowFrame(long vg, int x, int y, int W, int H, int S, int mx, int my, int activeTab, float dt) {
        drawNavBar(vg, x, y, W, S, mx, my, activeTab, dt);

        int r = 18 * S;
        NanoVgRenderer.shadow(vg, x, y, W, H, r, 22 * S, 0x70000000);
        NanoVgRenderer.shadow(vg, x, y, W, H, r, 30 * S, withAlpha(Theme.accentRgb(), 0x33));
        NanoVgRenderer.gradientRoundedRect(vg, x, y, W, H, r, Theme.winTop(), Theme.winBot());
        NanoVgRenderer.strokeRoundedRect(vg, x + 0.75f * S, y + 0.75f * S, W - 1.5f * S, H - 1.5f * S, r, 1.2f * S, Theme.rim());

        // header: centred wordmark at the top (logo removed)
        Wordmark.drawCentered(vg, x + W / 2f, y + 20 * S, 15 * S, 255);

        // Theme toggle + Customize Colors — identical icon buttons to ClickGuiScreen's
        // header (and the title-screen popover), drawn via the shared ThemeIcons helper
        // so this can't visually drift from the main menu.
        int tbw = 22 * S, tbh = 22 * S, tbx = x + W - tbw - 20 * S, tby = y + 14 * S;
        boolean tbHov = mx >= tbx && mx <= tbx + tbw && my >= tby && my <= tby + tbh;
        themeHoverT = approach(themeHoverT, tbHov ? 1f : 0f, 12f, dt);

        int cbw = 22 * S, cbh = 22 * S, cbx = tbx - cbw - 6 * S, cby = tby;
        boolean cbHov = mx >= cbx && mx <= cbx + cbw && my >= cby && my <= cby + cbh;
        colorsHoverT = approach(colorsHoverT, cbHov ? 1f : 0f, 12f, dt);

        ThemeIcons.drawTheme(vg, tbx, tby, S, themeHoverT);
        ThemeIcons.drawColors(vg, cbx, cby, S, colorsHoverT);
        themeBtn = new int[]{ tbx, tby, tbw, tbh };
        colorsBtn = new int[]{ cbx, cby, cbw, cbh };
    }

    protected void drawNavBar(long vg, int x, int y, int W, int S, int mx, int my, int activeTab, float dt) {
        // Delegates to NavBar so the sliding pill is shared with ClickGuiScreen's own
        // "Menu" tab — one continuous animation across both screen classes instead of
        // each owning a separate, unaware-of-each-other copy of the same bar.
        int[] yh = NavBar.draw(vg, x, y, W, S, activeTab, dt, navX, navW);
        navBarY = yh[0]; navBarH = yh[1];
    }

    // ---- HUD editor: draggable element frames, shown in every catalog ----

    protected void drawHudEditor(DrawContext ctx, int mouseX, int mouseY) {
        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        List<String> names = new ArrayList<>();
        List<int[]> rects = HudFrames.list(width, height, names);
        var m = ctx.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        for (int i = 0; i < rects.size(); i++) {
            int[] rc = rects.get(i);
            String label = names.get(i);
            boolean sel = label.equals(selectedHud);
            boolean dragging = dragMode == 3 && label.equals(dragHud);
            int rx = rc[0] * S, ry = rc[1] * S, rw = rc[2] * S, rh = rc[3] * S;
            // No idle overlay/label — the element itself is the grab target. Only while actually
            // being dragged does a plain white outline trace its exact bounds (drag feedback).
            if (dragging) RenderUtil.strokeRect(ctx, rx, ry, rw, rh, Math.max(1, S), 0xFFFFFFFF);

            // window-style corner resize handle (independent w/h) — "HUD" panel only for now
            if (sel && label.equals("HUD")) {
                int hs = 7;
                int hx = rc[0] + rc[2] - hs, hy = rc[1] + rc[3] - hs;
                hudResizeHandle = new int[]{ hx, hy, hs, hs };
                int hxS = hx * S, hyS = hy * S, hsS = hs * S;
                RenderUtil.roundedRect(ctx, hxS, hyS, hsS, hsS, 2 * S, 0xFFFFFFFF);
                RenderUtil.roundedRect(ctx, hxS + Math.round(S * 0.75f), hyS + Math.round(S * 0.75f),
                        hsS - Math.round(S * 1.5f), hsS - Math.round(S * 1.5f), S, Theme.accent());
            }
        }
        m.pop();
    }

    /** Try to grab a HUD frame under the cursor (GUI px). Double-click resets it. */
    protected boolean tryHudDrag(double mouseX, double mouseY) {
        // corner resize handle of the currently selected element takes priority
        if (selectedHud != null && hudResizeHandle != null
                && mouseX >= hudResizeHandle[0] - 3 && mouseX <= hudResizeHandle[0] + hudResizeHandle[2] + 3
                && mouseY >= hudResizeHandle[1] - 3 && mouseY <= hudResizeHandle[1] + hudResizeHandle[3] + 3) {
            List<String> nn = new ArrayList<>();
            List<int[]> rr = HudFrames.list(width, height, nn);
            int idx = nn.indexOf(selectedHud);
            int[] cur = idx >= 0 ? rr.get(idx) : new int[]{0, 0, 134, 60};
            hudResizeBase = new int[]{ cur[2], cur[3] };
            dragMode = 4; dragHud = selectedHud; grabMx = mouseX; grabMy = mouseY;
            return true;
        }
        List<String> names = new ArrayList<>();
        List<int[]> rects = HudFrames.list(width, height, names);
        for (int i = rects.size() - 1; i >= 0; i--) {
            int[] rc = rects.get(i);
            if (mouseX >= rc[0] && mouseX <= rc[0] + rc[2] && mouseY >= rc[1] && mouseY <= rc[1] + rc[3]) {
                String name = names.get(i);
                long now = System.currentTimeMillis();
                if (name.equals(lastFrameClickName) && now - lastFrameClickT < 350) {
                    HudLayout.reset(name); lastFrameClickT = 0; return true;
                }
                lastFrameClickT = now; lastFrameClickName = name; selectedHud = name;
                dragMode = 3; dragHud = name;
                int[] off = HudLayout.get(name);
                grabA = off[0]; grabB = off[1]; grabMx = mouseX; grabMy = mouseY;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && dragMode == 3 && dragHud != null) {
            HudLayout.set(dragHud, grabA + (int) Math.round(mouseX - grabMx), grabB + (int) Math.round(mouseY - grabMy));
            return true;
        }
        if (button == 0 && dragMode == 4 && dragHud != null && hudResizeBase != null) {
            int w = hudResizeBase[0] + (int) Math.round(mouseX - grabMx);
            int h = hudResizeBase[1] + (int) Math.round(mouseY - grabMy);
            HudLayout.setSize(dragHud, w, h);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) { dragMode = 0; dragHud = null; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected void handleNavClick(int i) {
        Screen cg = parent instanceof ClickGuiScreen ? parent : new ClickGuiScreen();
        switch (i) {
            case 0 -> client.setScreen(cg);
            case 1 -> { if (!(this instanceof EventsScreen)) client.setScreen(new EventsScreen(cg)); }
            case 2 -> { if (!(this instanceof ConfigScreen))  client.setScreen(new ConfigScreen(cg)); }
            case 3 -> { if (!(this instanceof FriendsScreen)) client.setScreen(new FriendsScreen(cg)); }
        }
    }

    /** Nav + theme-toggle hit-testing, in window-local coords. */
    protected boolean tryNavClick(double mouseX, double mouseY) {
        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        int mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);
        // theme toggle
        if (mx >= themeBtn[0] && mx <= themeBtn[0] + themeBtn[2]
                && my >= themeBtn[1] && my <= themeBtn[1] + themeBtn[3]) {
            Theme.toggle();
            ThemeSync.save();
            return true;
        }
        // Customize Colors
        if (mx >= colorsBtn[0] && mx <= colorsBtn[0] + colorsBtn[2]
                && my >= colorsBtn[1] && my <= colorsBtn[1] + colorsBtn[3]) {
            if (client != null) client.setScreen(new ColorsScreen(this));
            return true;
        }
        // nav tabs
        if (navBarH > 0 && my >= navBarY - 3 * S && my <= navBarY + navBarH + 3 * S) {
            for (int i = 0; i < 4; i++) {
                if (navX[i] != 0 && mx >= navX[i] && mx <= navX[i] + navW[i]) {
                    handleNavClick(i);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }

    protected static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * Math.min(1f, rate * dt);
    }

    protected static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    protected static String fmtDur(long sec) {
        if (sec < 0) return "—";
        if (sec < 90) return sec + "с";
        long m = sec / 60;
        return m < 90 ? m + "м" : (m / 60) + "ч " + (m % 60) + "м";
    }
}

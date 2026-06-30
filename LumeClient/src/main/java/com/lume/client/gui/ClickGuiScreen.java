package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.fthw.EventManager;
import com.lume.client.fthw.ItemRule;
import com.lume.client.fthw.ItemRules;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import com.lume.client.module.modules.cosmetic.CustomCrosshair;
import com.lume.client.module.modules.fthw.ServerHelper;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.Setting;
import com.lume.client.module.setting.SliderSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lume ClickGUI — original card-grid layout (not a sidebar, not Pulse).
 * Centred header (logo + theme), full-width search, a segmented category pill,
 * and a grid of module CARDS where the whole card is the toggle and the name is
 * centred. Cream/lavender liquid glass, native-resolution, hover light-follow.
 */
public class ClickGuiScreen extends Screen {

    private static final int WIN_W = 520;
    private static final int WIN_H = 356;
    private static final int GRID_TOP = 118;
    private static final int CARD_H = 38;
    private static final int CARD_GAP = 8;

    private final long openTime = System.currentTimeMillis();
    private static int selectedCat = 0;   // persists across menu open/close
    private String search = "";

    private int scale = 1;
    private int[] segX = new int[0];
    private int[] segW = new int[0];
    private int segY = 0, segH = 0;
    private int[] themeBtn = new int[]{0, 0, 0, 0};

    // Per-element animation state: key -> {hover, enable, press 1→0, expand}.
    private final Map<String, float[]> anim = new HashMap<>();
    private long lastFrame = System.currentTimeMillis();

    // Smooth vertical scroll of the module grid (native px).
    private float scroll = 0f;
    private float scrollTarget = 0f;

    // Which module cards are expanded to show their settings.
    private final Set<String> expanded = new HashSet<>();

    // Clickable regions recorded during render (absolute native coords).
    private final List<CHit> cHits = new ArrayList<>();
    private final List<SHit> sHits = new ArrayList<>();
    private SHit activeSlider = null;
    private int lastClipTop = 0, lastClipBot = 0;

    // Window move/resize (session-only) + drag state.
    private static int winOffX = 0, winOffY = 0;   // GUI px
    private static float winScale = 1f;
    private int dragMode = 0;                       // 0 none, 1 window-move, 2 window-resize, 3 HUD-move
    private String dragHud = null;
    private double grabMx, grabMy;                  // GUI px at grab
    private int grabA, grabB;                       // window/HUD offset at grab
    private float grabScale;
    private float curTotal = 1f;                    // window scale×anim this frame (for scissor)
    private static String selectedHud = null;       // HUD element whose size slider is shown
    private int[] hudSliderTrack = null;            // {x,y,w,h} GUI px of the selected element's size slider
    private long lastFrameClickT = 0;
    private String lastFrameClickName = null;

    // Binds tab
    private Module bindingModule = null;             // module currently capturing a key
    private final List<Object[]> bindHits = new ArrayList<>();   // {Module, x, y, w, h}

    // GUI text fields (Waypoints manager + search). focusedField: "search"/"name"/"x"/"y"/"z"/null
    private String focusedField = null;
    private int[] searchBox = new int[]{0, 0, 0, 0};
    private String wpName = "", wpX = "", wpY = "", wpZ = "";
    private final List<Object[]> wpHits = new ArrayList<>();      // {String kind, int x, y, w, h}

    private int[] serverToggle = new int[]{0, 0, 0, 0};

    // Server tab: scrollable encyclopedia + sub-function binds
    private BoolSetting bindingSetting = null;                    // sub-function capturing a key
    private com.lume.client.fthw.QuickCommands.Cmd bindingQuickCmd = null;  // quick-command capturing a key
    private final List<Object[]> serverHits = new ArrayList<>();  // {String kind, int x, y, w, h, Object ref}
    private int serverContentH = 0;

    private boolean isBindsTab() { return search.isEmpty() && selectedCat == Category.values().length; }
    private boolean isServerTab() { return search.isEmpty() && selectedCat == Category.values().length + 1; }

    private String tabTitle(int i) {
        Category[] c = Category.values();
        String en = i < c.length ? c[i].title : (i == c.length ? "Binds" : "Server");
        return com.lume.client.Lang.tCat(en);
    }

    private String keyDisplay(int code) {
        if (code < 0) return "None";
        try { return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString(); }
        catch (Exception e) { return "Key" + code; }
    }

    public ClickGuiScreen() { super(Text.literal("Lume")); }

    private float[] animFor(String key) { return anim.computeIfAbsent(key, k -> new float[5]); }

    // --- window state access (for config persistence) ---
    public static int getWinOffX() { return winOffX; }
    public static int getWinOffY() { return winOffY; }
    public static float getWinScale() { return winScale; }
    public static void setWindow(int x, int y, float s) {
        winOffX = x; winOffY = y; winScale = Math.max(0.6f, Math.min(1.8f, s));
    }

    @Override
    public void close() {
        com.lume.client.Config.save();   // persist GUI edits when the menu closes
        super.close();
    }

    /** A module card's clickable header + (optional) settings arrow. */
    private static final class CHit {
        Module m; int hx, hy, hw, hh; boolean hasArrow; int ax, ay, aw, ah;
    }

    /** A settings control's clickable region. kind: 0 bool, 1 slider, 2 colour-accent, 3 colour-channel. */
    private static final class SHit {
        Setting s; int kind; int x, y, w, h; int trackX, trackW; int channel;
    }

    /** Frame-rate independent easing toward a target. */
    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * Math.min(1f, rate * dt);
    }

    private int sf() { return (int) Math.max(1, this.client.getWindow().getScaleFactor()); }

    /**
     * Auto-fit factor so the window never overflows the screen at large GUI scales.
     * The window is WIN_W×WIN_H GUI-logical units; if the screen (this.width/height,
     * also GUI-logical) is smaller, shrink to fit with a small margin. ≤1 always.
     */
    private float fitScale() {
        float fw = this.width  / (float) (WIN_W + 24);
        float fh = this.height / (float) (WIN_H + 24);
        return Math.min(1f, Math.min(fw, fh));
    }

    private void text(DrawContext ctx, String s, int x, int y, int color, float vis) {
        RenderUtil.text(ctx, this.textRenderer, s, x, y, color, false, vis * scale);
    }
    private int width(String s, float vis) { return RenderUtil.width(this.textRenderer, s, vis * scale); }

    private void glass(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int rimW) {
        RenderUtil.roundedRect(ctx, x, y, w, h, r, Theme.rim());
        RenderUtil.roundedRect(ctx, x + rimW, y + rimW, w - 2 * rimW, h - 2 * rimW, Math.max(2, r - rimW), fill);
    }

    private float anim() {
        float p = (System.currentTimeMillis() - openTime) / 200f;
        if (p > 1f) p = 1f;
        return 1f - (1f - p) * (1f - p);
    }

    private List<Module> modules() {
        List<Module> out = new ArrayList<>();
        if (!search.isEmpty()) {
            String q = search.toLowerCase();
            for (Module m : LumeClient.MODULES.getModules())
                if (m.getName().toLowerCase().contains(q) || com.lume.client.Lang.tName(m.getName()).toLowerCase().contains(q)) out.add(m);
        } else if (selectedCat < Category.values().length) {
            for (Module m : LumeClient.MODULES.getModules(Category.values()[selectedCat]))
                if (!m.getName().equals("Server Helper")) out.add(m);   // managed in the Server tab
        }
        return out;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, this.width, this.height, Theme.backdrop());

        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        int S = sf();
        this.scale = S;
        int sw = this.width * S, sh = this.height * S;

        // NanoVG-rendered menu (smooth) — all tabs. Falls back to the DrawContext
        // path only if NanoVG is unavailable.
        NanoVgRenderer.ensureInit();
        if (NanoVgRenderer.ready()) {
            renderNvgMain(ctx, mouseX, mouseY, dt, S);
            return;
        }

        // HUD editor frames (screen space) — draggable element placeholders
        drawHudFrames(ctx, S, mouseX, mouseY);

        // Window transform: open-anim × user scale × auto-fit, shifted by user offset.
        double cx = sw / 2.0, cy = sh / 2.0;
        float p = anim();
        float total = (0.96f + 0.04f * p) * winScale * fitScale();
        this.curTotal = total;
        int mx = (int) Math.round((mouseX * S - winOffX * S - cx) / total + cx); // local mouse
        int my = (int) Math.round((mouseY * S - winOffY * S - cy) / total + cy);

        var mtx = ctx.getMatrices();
        mtx.push();
        mtx.scale(1f / S, 1f / S, 1f);
        mtx.translate(winOffX * S, winOffY * S, 0.0);
        mtx.translate(cx, cy, 0.0);
        mtx.scale(total, total, 1.0f);
        mtx.translate(-cx, -cy, 0.0);

        int W = WIN_W * S, H = WIN_H * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        int r = 18 * S;

        // Panel
        RenderUtil.roundedRect(ctx, x + 3 * S, y + 7 * S, W - 6 * S, H, r, Theme.shadow());
        RenderUtil.glow(ctx, x, y, W, H, r, Theme.accentRgb(), 5);
        glass(ctx, x, y, W, H, r, Theme.winBg(), 2 * S);
        RenderUtil.roundedRect(ctx, x + 16 * S, y + 2 * S, W - 32 * S, Math.max(1, S), 1 * S, Theme.border());

        // Header: logo + wordmark
        RenderUtil.drawLogo(ctx, x + 20 * S, y + 15 * S, 22 * S);
        text(ctx, "lume", x + 20 * S + 28 * S, y + 17 * S, Theme.accent(), 0.6f);
        text(ctx, "client", x + 20 * S + 28 * S + width("lume", 0.6f) + 6 * S, y + 18 * S, Theme.txtDim(), 0.6f);

        // Theme toggle (right) — animated hover + press pulse
        int tbw = 56 * S, tbh = 22 * S, tbx = x + W - tbw - 20 * S, tby = y + 14 * S;
        boolean tbHov = inside(mx, my, tbx, tby, tbw, tbh);
        float[] ta = animFor("_theme");
        ta[0] = approach(ta[0], tbHov ? 1f : 0f, 12f, dt);
        ta[2] = Math.max(0f, ta[2] - dt * 5f);
        var mt = ctx.getMatrices();
        mt.push();
        float tps = 1f - 0.07f * ta[2];
        mt.translate(tbx + tbw / 2.0, tby + tbh / 2.0, 0.0);
        mt.scale(tps, tps, 1f);
        mt.translate(-(tbx + tbw / 2.0), -(tby + tbh / 2.0), 0.0);
        glass(ctx, tbx, tby, tbw, tbh, 11 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ta[0]), S);
        if (ta[0] > 0.01f) RenderUtil.containedGlow(ctx, tbx + S, tby + S, tbw - 2 * S, tbh - 2 * S, (tbx + tbw / 2), (tby + tbh / 2), 20 * S, Theme.colorLerp(0xFFFFFF, Theme.accentRgb(), 0.4f), ta[0]);
        String tl = Theme.isDark() ? "Dark" : "Light";
        RenderUtil.textCentered(ctx, this.textRenderer, tl, tbx, tby, tbw, tbh, Theme.txt(), 0.5f * scale);
        mt.pop();
        themeBtn = new int[]{ tbx, tby, tbw, tbh };

        // Search (full width) — click to focus; vanilla font so any text renders
        int sx = x + 20 * S, sy = y + 46 * S, swid = W - 40 * S, shei = 26 * S;
        searchBox = new int[]{ sx, sy, swid, shei };
        boolean searchFocused = "search".equals(focusedField);
        glass(ctx, sx, sy, swid, shei, 10 * S, searchFocused ? Theme.glassHov() : Theme.glassRow(), S);
        if (searchFocused) RenderUtil.roundedRect(ctx, sx, sy + shei - Math.max(1, S), swid, Math.max(1, S), 1, Theme.accent());
        boolean empty = search.isEmpty() && !searchFocused;
        String shown = empty ? "Search modules…" : search + (searchFocused ? "_" : "");
        RenderUtil.vanillaText(ctx, this.textRenderer, shown, sx + 12 * S, sy + (shei - 8 * S) / 2.0, empty ? Theme.txtDim() : Theme.txt(), S);

        // Category segmented pill (categories + a "Binds" tab)
        int tabs = Category.values().length + 2;
        segX = new int[tabs];
        segW = new int[tabs];
        segH = 26 * S;
        segY = y + 82 * S;
        int padSeg = 11 * S;
        int segTotal = 0;
        int[] ww = new int[tabs];
        for (int i = 0; i < tabs; i++) { ww[i] = width(tabTitle(i), 0.5f) + padSeg * 2; segTotal += ww[i]; }
        int barX = x + (W - segTotal) / 2;
        glass(ctx, barX - 4 * S, segY - 3 * S, segTotal + 8 * S, segH + 6 * S, 13 * S, Theme.glassRow(), S);
        int cx2 = barX;
        for (int i = 0; i < tabs; i++) {
            segX[i] = cx2; segW[i] = ww[i];
            boolean sel = i == selectedCat && search.isEmpty();
            if (sel) {
                RenderUtil.glow(ctx, cx2, segY, ww[i], segH, 12 * S, Theme.accentRgb(), 4);
                RenderUtil.gradientRoundedRect(ctx, cx2, segY, ww[i], segH, 12 * S, Theme.accent(), Theme.accent2());
            }
            int tw = width(tabTitle(i), 0.5f);
            text(ctx, tabTitle(i), cx2 + (ww[i] - tw) / 2, segY + 8 * S, sel ? 0xFFFFFFFF : Theme.txtDim(), 0.5f);
            cx2 += ww[i];
        }

        // Binds tab — list every module with its toggle key
        if (isBindsTab()) {
            renderBinds(ctx, x, y, W, H, S, mx, my, dt);
            int gripX = x + W - 14 * S, gripY = y + H - 14 * S;
            boolean gripHov = inside(mx, my, gripX, gripY, 14 * S, 14 * S);
            for (int i = 0; i < 3; i++) {
                int o = (3 - i) * 3 * S;
                RenderUtil.roundedRect(ctx, x + W - o - 2 * S, y + H - 5 * S, o, 2 * S, S, gripHov ? Theme.accent() : Theme.txtDim());
            }
            mtx.pop();
            return;
        }
        if (isServerTab()) {
            renderServer(ctx, x, y, W, H, S, mx, my, dt);
            int gripX2 = x + W - 14 * S, gripY2 = y + H - 14 * S;
            boolean gripHov2 = inside(mx, my, gripX2, gripY2, 14 * S, 14 * S);
            for (int i = 0; i < 3; i++) {
                int o = (3 - i) * 3 * S;
                RenderUtil.roundedRect(ctx, x + W - o - 2 * S, y + H - 5 * S, o, 2 * S, S, gripHov2 ? Theme.accent() : Theme.txtDim());
            }
            mtx.pop();
            return;
        }

        // Module cards grid (2 columns, scrollable, cards expand to show settings)
        List<Module> mods = modules();
        int margin = 20 * S, gap = CARD_GAP * S;
        int cardW = (W - margin * 2 - gap) / 2;
        int gx0 = x + margin, gx1 = x + margin + cardW + gap;
        int gy = y + GRID_TOP * S;
        int clipTop = gy - 2 * S, clipBot = y + H - 12 * S;
        int visH = clipBot - gy;
        int headerH = CARD_H * S;
        lastClipTop = clipTop; lastClipBot = clipBot;

        // animate expand + measure each card's height
        int n = mods.size();
        int[] cardH = new int[n];
        for (int i = 0; i < n; i++) {
            Module mm = mods.get(i);
            float[] an = animFor(mm.getName());
            boolean exp = expanded.contains(mm.getName()) && mm.hasSettings();
            an[3] = approach(an[3], exp ? 1f : 0f, 11f, dt);
            int panelH = mm.hasSettings() ? panelHeight(mm, S) : 0;
            cardH[i] = headerH + Math.round(an[3] * panelH);
        }
        int rowsN = (n + 1) / 2;
        int[] rowTop = new int[rowsN];
        int contentH = 0;
        for (int rr = 0, cum = 0; rr < rowsN; rr++) {
            int hgt = cardH[rr * 2];
            if (rr * 2 + 1 < n) hgt = Math.max(hgt, cardH[rr * 2 + 1]);
            rowTop[rr] = cum;
            cum += hgt + CARD_GAP * S;
            contentH = cum - CARD_GAP * S;
        }

        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        int scrollI = Math.round(scroll);

        cHits.clear();
        sHits.clear();
        wpHits.clear();

        winScissor(ctx, x, clipTop, x + W, clipBot);
        for (int i = 0; i < n; i++) {
            Module m = mods.get(i);
            int col = i % 2, row = i / 2;
            int rx = col == 0 ? gx0 : gx1;
            int cy0 = gy + rowTop[row] - scrollI;   // card top
            int ch = cardH[i];

            float[] ca = animFor(m.getName());
            if (cy0 + ch < clipTop || cy0 > clipBot) {           // off-screen: keep easing sane
                ca[0] = approach(ca[0], 0f, 14f, dt);
                ca[2] = Math.max(0f, ca[2] - dt * 4.5f);
                ca[4] = approach(ca[4], 0f, 14f, dt);
                continue;
            }

            boolean inClip = my >= clipTop && my <= clipBot;
            boolean hov = inside(mx, my, rx, cy0, cardW, headerH) && inClip;          // header → toggle
            boolean cardHov = inside(mx, my, rx, cy0, cardW, ch) && inClip;           // whole card → glow
            boolean en = m.isToggleable() && m.isEnabled();   // non-toggleable cards stay neutral
            ca[0] = approach(ca[0], hov ? 1f : 0f, 14f, dt);
            ca[1] = approach(ca[1], en ? 1f : 0f, 11f, dt);
            ca[2] = Math.max(0f, ca[2] - dt * 4.5f);
            ca[4] = approach(ca[4], cardHov ? 1f : 0f, 14f, dt);
            float ha = ca[0], ea = ca[1], pa = ca[2], ex = ca[3], gha = ca[4];

            int e = Math.round(ha * S * (1f - ex));   // hover lift (off while expanded)
            int dx = rx - e, dy = cy0 - e, dw = cardW + 2 * e, dh = ch + 2 * e;

            var mc2 = ctx.getMatrices();
            mc2.push();
            float ps = 1f - 0.06f * pa;                 // press pulse around the header
            mc2.translate(dx + dw / 2.0, dy + headerH / 2.0, 0.0);
            mc2.scale(ps, ps, 1f);
            mc2.translate(-(dx + dw / 2.0), -(dy + headerH / 2.0), 0.0);

            RenderUtil.roundedRect(ctx, dx + 1 * S, dy + 2 * S, dw, dh, 11 * S, Theme.shadow());
            if (ea > 0.01f) RenderUtil.glow(ctx, dx, dy, dw, dh, 11 * S, Theme.accentRgb(), Math.max(1, Math.round(4 * ea)));

            int base = Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ha);
            int onFill = withAlpha(Theme.accentRgb(), Theme.isDark() ? 0x4D : 0x40);
            int fill = Theme.colorLerp(base, onFill, ea);
            glass(ctx, dx, dy, dw, dh, 11 * S, fill, S);
            RenderUtil.roundedRect(ctx, dx + 8 * S, dy + 1 * S, dw - 16 * S, Math.max(1, S), 1 * S, Theme.border());

            // contained hover glow following the cursor across the WHOLE card
            // (header + expanded settings area), clipped to the card
            if (gha > 0.01f) {
                int hx = (int) Math.max(dx + 10 * S, Math.min(mx, dx + dw - 10 * S));
                int hyc = (int) Math.max(dy + 10 * S, Math.min(my, dy + dh - 10 * S));
                int glowCol = Theme.colorLerp(0xFFFFFF, Theme.accentRgb(), 0.35f);
                RenderUtil.containedGlow(ctx, dx + 2 * S, dy + 2 * S, dw - 4 * S, dh - 4 * S, hx, hyc, 30 * S, glowCol, gha);
            }

            if (pa > 0.01f) RenderUtil.roundedRect(ctx, dx, dy, dw, headerH, 11 * S, withAlpha(0xFFFFFF, Math.round(pa * 55)));

            // centred name (leave room on the right for the settings arrow)
            int col2 = Theme.colorLerp(Theme.txt(), Theme.isDark() ? 0xFFFFFFFF : 0xFF3A3147, ea);
            int nameRightPad = m.hasSettings() ? 20 * S : 0;
            RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tName(m.getName()), dx, dy, dw - nameRightPad, headerH, col2, 0.52f * scale);

            // on-indicator dot (top-right)
            if (ea > 0.02f) {
                int ds = Math.max(1, Math.round(4 * S * ea));
                RenderUtil.roundedRect(ctx, dx + dw - 9 * S, dy + 6 * S + (4 * S - ds) / 2, ds, ds, ds / 2,
                        withAlpha(Theme.accentRgb(), Math.round(255 * ea)));
            }

            // settings arrow + record click regions
            CHit chit = new CHit();
            chit.m = m; chit.hx = rx; chit.hy = cy0; chit.hw = cardW; chit.hh = headerH;
            if (m.hasSettings()) {
                chevron(ctx, dx + dw - 13 * S, dy + headerH / 2, 8 * S, ex, Theme.txtDim());
                chit.hasArrow = true; chit.ax = dx + dw - 24 * S; chit.ay = dy; chit.aw = 24 * S; chit.ah = headerH;
            }
            cHits.add(chit);

            // expanded settings (revealed by the growing card via a nested scissor)
            if (ex > 0.01f && m.hasSettings()) {
                winScissor(ctx, dx, dy, dx + dw, dy + dh);
                renderSettings(ctx, m, dx, cy0 + headerH, dw, S, mx, my);
                ctx.disableScissor();
            }

            mc2.pop();
        }
        ctx.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbW = 3 * S;
            int sbX = x + W - margin / 2 - sbW;
            RenderUtil.roundedRect(ctx, sbX, gy, sbW, visH, sbW, Theme.glassRow());
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) contentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / maxScroll));
            RenderUtil.roundedRect(ctx, sbX, thumbY, sbW, thumbH, sbW, Theme.accent());
        }

        // Resize grip (bottom-right corner) — drag to scale the whole window
        int gripX = x + W - 14 * S, gripY = y + H - 14 * S;
        boolean gripHov = inside(mx, my, gripX, gripY, 14 * S, 14 * S);
        for (int i = 0; i < 3; i++) {
            int o = (3 - i) * 3 * S;
            RenderUtil.roundedRect(ctx, x + W - o - 2 * S, y + H - 5 * S, o, 2 * S, S, gripHov ? Theme.accent() : Theme.txtDim());
        }

        mtx.pop();
    }

    // ======================================================================
    //  NanoVG main view (smooth) — window, header, search, tabs, module cards
    //  Reuses the exact same layout/coords as the old renderer so the existing
    //  mouseClicked hit-testing (segX/segW/themeBtn/searchBox/cHits) keeps working.
    //  Settings-expansion + Binds/Server tabs still use the DrawContext path.
    // ======================================================================
    private void renderNvgMain(DrawContext ctx, int mouseX, int mouseY, float dt, int S) {
        int sw = this.width * S, sh = this.height * S;
        drawHudFrames(ctx, S, mouseX, mouseY);   // HUD editor frames (DrawContext, behind)

        double cx = sw / 2.0, cy = sh / 2.0;
        float p = anim();
        float total = (0.96f + 0.04f * p) * winScale * fitScale();
        this.curTotal = total;
        int mx = (int) Math.round((mouseX * S - winOffX * S - cx) / total + cx);
        int my = (int) Math.round((mouseY * S - winOffY * S - cy) / total + cy);

        final int W = WIN_W * S, H = WIN_H * S;
        final int x = (sw - W) / 2, y = (sh - H) / 2;
        final int r = 18 * S;
        final int mxF = mx, myF = my;

        // layout/anim that needs to persist to hit-testing is computed here (outside the lambda)
        final boolean fCards = !isBindsTab() && !isServerTab();
        List<Module> mods = fCards ? modules() : new ArrayList<>();
        int margin = 20 * S, gap = CARD_GAP * S;
        int cardW = (W - margin * 2 - gap) / 2;
        int gx0 = x + margin, gx1 = x + margin + cardW + gap;
        int gy = y + GRID_TOP * S;
        int clipTop = gy - 2 * S, clipBot = y + H - 12 * S, visH = clipBot - gy;
        int headerH = CARD_H * S;
        lastClipTop = clipTop; lastClipBot = clipBot;
        int n = mods.size();

        // masonry (cards tab only): each card's height grows with its expand anim;
        // the two columns flow INDEPENDENTLY so expanding pushes only its own column.
        final int[] cardHpx = new int[n];
        int contentH = 0;
        if (fCards) {
            for (int i = 0; i < n; i++) {
                Module m = mods.get(i);
                float[] ca = animFor(m.getName());
                boolean exp = expanded.contains(m.getName()) && m.hasSettings();
                ca[3] = approach(ca[3], exp ? 1f : 0f, 11f, dt);
                int panelH = m.hasSettings() ? panelHeightNvg(m, S) : 0;
                cardHpx[i] = headerH + Math.round(ca[3] * panelH);
            }
            int colA = 0, colB = 0;
            for (int i = 0; i < n; i++) { if (i % 2 == 0) colA += cardHpx[i] + gap; else colB += cardHpx[i] + gap; }
            contentH = Math.max(0, Math.max(colA, colB) - gap);
            int maxScroll = Math.max(0, contentH - visH);
            scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
            scroll = approach(scroll, scrollTarget, 16f, dt);
            if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        }
        final int scrollI = Math.round(scroll);
        cHits.clear(); sHits.clear(); wpHits.clear(); bindHits.clear(); serverHits.clear();

        final int fContentH = contentH, fVisH = visH;

        NanoVgRenderer.frame(vg -> {
            NanoVgRenderer.translate(vg, winOffX * S, winOffY * S);
            NanoVgRenderer.translate(vg, (float) cx, (float) cy);
            NanoVgRenderer.scale(vg, total, total);
            NanoVgRenderer.translate(vg, (float) -cx, (float) -cy);

            // panel: soft dark shadow + faint accent glow + gradient glass + rim
            NanoVgRenderer.shadow(vg, x, y, W, H, r, 22 * S, 0x70000000);
            NanoVgRenderer.shadow(vg, x, y, W, H, r, 30 * S, withAlpha(Theme.accentRgb(), 0x33));
            NanoVgRenderer.gradientRoundedRect(vg, x, y, W, H, r, Theme.winTop(), Theme.winBot());
            NanoVgRenderer.strokeRoundedRect(vg, x + 0.75f * S, y + 0.75f * S, W - 1.5f * S, H - 1.5f * S, r, 1.2f * S, Theme.rim());

            // header: logo + wordmark
            nvgLogo(vg, x + 20 * S, y + 15 * S, 22 * S);
            float hcy = y + 15 * S + 11 * S;
            NanoVgRenderer.text(vg, x + 52 * S, hcy, 15 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, "lume");
            float lw = NanoVgRenderer.textWidth(vg, 15 * S, "lume");
            NanoVgRenderer.text(vg, x + 52 * S + lw + 6 * S, hcy, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "client");

            // theme toggle
            int tbw = 56 * S, tbh = 22 * S, tbx = x + W - tbw - 20 * S, tby = y + 14 * S;
            boolean tbHov = inside(mxF, myF, tbx, tby, tbw, tbh);
            float[] ta = animFor("_theme");
            ta[0] = approach(ta[0], tbHov ? 1f : 0f, 12f, dt);
            NanoVgRenderer.roundedRect(vg, tbx, tby, tbw, tbh, 11 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ta[0]));
            NanoVgRenderer.strokeRoundedRect(vg, tbx + 0.5f * S, tby + 0.5f * S, tbw - S, tbh - S, 11 * S, S, Theme.rim());
            NanoVgRenderer.text(vg, tbx + tbw / 2f, tby + tbh / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, Theme.isDark() ? "Dark" : "Light");
            themeBtn = new int[]{ tbx, tby, tbw, tbh };

            // search
            int sx = x + 20 * S, sy = y + 46 * S, swid = W - 40 * S, shei = 26 * S;
            searchBox = new int[]{ sx, sy, swid, shei };
            boolean searchFocused = "search".equals(focusedField);
            NanoVgRenderer.roundedRect(vg, sx, sy, swid, shei, 10 * S, searchFocused ? Theme.glassHov() : Theme.glassRow());
            if (searchFocused) NanoVgRenderer.roundedRect(vg, sx + 4 * S, sy + shei - 2 * S, swid - 8 * S, Math.max(1, S), S, Theme.accent());
            boolean empty = search.isEmpty() && !searchFocused;
            String shown = empty ? "Поиск модулей…" : search + (searchFocused ? "|" : "");
            NanoVgRenderer.text(vg, sx + 12 * S, sy + shei / 2f, 11 * S, empty ? Theme.txtDim() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, shown);

            // category tabs (auto-shrink to always fit the window width)
            int tabs = Category.values().length + 2;
            segX = new int[tabs]; segW = new int[tabs];
            segH = 26 * S; segY = y + 82 * S;
            float tFont = 11 * S; int padSeg = 11 * S;
            int[] ww = new int[tabs]; int segTotal = 0;
            for (int i = 0; i < tabs; i++) { ww[i] = (int) NanoVgRenderer.textWidth(vg, tFont, tabTitle(i)) + padSeg * 2; segTotal += ww[i]; }
            int maxBarW = W - 16 * S;
            if (segTotal > maxBarW) {
                float ts = (float) maxBarW / segTotal;
                tFont *= ts; padSeg = Math.max(3 * S, Math.round(padSeg * ts));
                segTotal = 0;
                for (int i = 0; i < tabs; i++) { ww[i] = (int) NanoVgRenderer.textWidth(vg, tFont, tabTitle(i)) + padSeg * 2; segTotal += ww[i]; }
            }
            int barX = x + (W - segTotal) / 2;
            NanoVgRenderer.roundedRect(vg, barX - 4 * S, segY - 3 * S, segTotal + 8 * S, segH + 6 * S, 13 * S, Theme.glassRow());
            int cx2 = barX;
            for (int i = 0; i < tabs; i++) {
                segX[i] = cx2; segW[i] = ww[i];
                boolean sel = i == selectedCat && search.isEmpty();
                if (sel) NanoVgRenderer.gradientRoundedRect(vg, cx2, segY, ww[i], segH, 12 * S, Theme.accent(), Theme.accent2());
                NanoVgRenderer.text(vg, cx2 + ww[i] / 2f, segY + segH / 2f, tFont, sel ? 0xFFFFFFFF : Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, tabTitle(i));
                cx2 += ww[i];
            }

            if (fCards) {
            // module cards — masonry (2 independent columns), expandable settings
            NanoVgRenderer.save(vg);
            NanoVgRenderer.scissor(vg, x, clipTop, W, fVisH);
            int colYa = gy - scrollI, colYb = gy - scrollI;
            for (int i = 0; i < n; i++) {
                Module m = mods.get(i);
                boolean colLeft = i % 2 == 0;
                int rx = colLeft ? gx0 : gx1;
                int cy0 = colLeft ? colYa : colYb;
                int ch = cardHpx[i];
                if (colLeft) colYa += ch + gap; else colYb += ch + gap;

                float[] ca = animFor(m.getName());
                if (cy0 + ch < clipTop || cy0 > clipBot) {
                    ca[0] = approach(ca[0], 0f, 14f, dt);
                    ca[1] = approach(ca[1], (m.isToggleable() && m.isEnabled()) ? 1f : 0f, 11f, dt);
                    continue;
                }
                boolean inClip = myF >= clipTop && myF <= clipBot;
                boolean hov = inside(mxF, myF, rx, cy0, cardW, headerH) && inClip;
                boolean en = m.isToggleable() && m.isEnabled();
                ca[0] = approach(ca[0], hov ? 1f : 0f, 14f, dt);
                ca[1] = approach(ca[1], en ? 1f : 0f, 11f, dt);
                float ha = ca[0], ea = ca[1], ex = ca[3];
                boolean isExp = ex > 0.01f && m.hasSettings();

                int e = isExp ? 0 : Math.round(ha * 2 * S);     // no hover lift while expanded
                int dx = rx - e, dy = cy0 - e, dw = cardW + 2 * e, dh = ch + 2 * e;

                NanoVgRenderer.shadow(vg, dx, dy + S, dw, dh, 11 * S, 8 * S, 0x55000000);
                if (ea > 0.01f) NanoVgRenderer.shadow(vg, dx, dy, dw, dh, 11 * S, (10 + 6 * ea) * S, withAlpha(Theme.accentRgb(), Math.round(0x55 * ea)));
                int base = Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ha);
                int onFill = withAlpha(Theme.accentRgb(), Theme.isDark() ? 0x4D : 0x40);
                int fill = Theme.colorLerp(base, onFill, ea);
                NanoVgRenderer.roundedRect(vg, dx, dy, dw, dh, 11 * S, fill);
                NanoVgRenderer.strokeRoundedRect(vg, dx + 0.5f * S, dy + 0.5f * S, dw - S, dh - S, 11 * S, S, withAlpha(0xFFFFFF, Math.round(0x30 + 0x40 * ha)));

                int nameCol = Theme.colorLerp(Theme.txt(), Theme.isDark() ? 0xFFFFFFFF : 0xFF3A3147, ea);
                int namePad = m.hasSettings() ? 20 * S : 0;
                NanoVgRenderer.text(vg, dx + (dw - namePad) / 2f, dy + headerH / 2f, 12 * S, nameCol, NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tName(m.getName()));
                if (ea > 0.02f) NanoVgRenderer.circle(vg, dx + dw - 11 * S, dy + 9 * S, Math.max(1.5f, 2.5f * S * ea), withAlpha(Theme.accentRgb(), Math.round(255 * ea)));

                CHit chit = new CHit();
                chit.m = m; chit.hx = rx; chit.hy = cy0; chit.hw = cardW; chit.hh = headerH;
                if (m.hasSettings()) {
                    nvgChevron(vg, dx + dw - 14 * S, dy + headerH / 2f, 7 * S, ex, Theme.txtDim());
                    chit.hasArrow = true; chit.ax = dx + dw - 24 * S; chit.ay = dy; chit.aw = 24 * S; chit.ah = headerH;
                }
                cHits.add(chit);

                if (isExp) {
                    NanoVgRenderer.save(vg);
                    NanoVgRenderer.intersectScissor(vg, dx, dy, dw, dh);
                    renderSettingsNvg(vg, m, dx, cy0 + headerH, dw, S, mxF, myF);
                    NanoVgRenderer.restore(vg);
                }
            }
            NanoVgRenderer.restore(vg);

            // scrollbar
            int maxScroll = Math.max(0, fContentH - fVisH);
            if (maxScroll > 0) {
                int sbW = 3 * S, sbX = x + W - margin / 2 - sbW;
                NanoVgRenderer.roundedRect(vg, sbX, gy, sbW, fVisH, sbW / 2f, Theme.glassRow());
                int thumbH = Math.max(14 * S, Math.round(fVisH * (fVisH / (float) fContentH)));
                int thumbY = gy + Math.round((fVisH - thumbH) * (scroll / maxScroll));
                NanoVgRenderer.roundedRect(vg, sbX, thumbY, sbW, thumbH, sbW / 2f, Theme.accent());
            }
            } else if (isBindsTab()) {
                drawBindsNvg(vg, x, y, W, H, S, mxF, myF, dt, gy, clipTop, clipBot, fVisH, margin);
            } else {
                drawServerNvg(vg, x, y, W, H, S, mxF, myF, dt, gy, clipTop, clipBot, fVisH, margin);
            }

            // resize grip
            boolean gripHov = inside(mxF, myF, x + W - 14 * S, y + H - 14 * S, 14 * S, 14 * S);
            for (int i = 0; i < 3; i++) {
                int o = (3 - i) * 3 * S;
                NanoVgRenderer.roundedRect(vg, x + W - o - 2 * S, y + H - 5 * S, o, 2 * S, S, gripHov ? Theme.accent() : Theme.txtDim());
            }
        });
    }

    /** Lume logo mark drawn via NanoVG. */
    private void nvgLogo(long vg, float x, float y, float s) {
        NanoVgRenderer.gradientRoundedRect(vg, x, y, s, s, Math.max(4, s / 4), 0xFFB7AAD9, 0xFF8E7FC0);
        float barW = Math.max(2, s / 7f);
        float lx = x + s * 0.30f, top = y + s * 0.28f, bot = y + s * 0.72f;
        NanoVgRenderer.roundedRect(vg, lx, top, barW, bot - top, 1, 0xFFFFFFFF);
        NanoVgRenderer.roundedRect(vg, lx, bot - barW, s * 0.4f, barW, 1, 0xFFFFFFFF);
        float dot = Math.max(2, s / 6f);
        NanoVgRenderer.roundedRect(vg, x + s * 0.60f, y + s * 0.20f, dot, dot, dot / 2, 0xFFFFFFFF);
    }

    /** Settings-expand chevron: ▸ collapsed → ▾ expanded (rotated by {@code ex}). */
    private void nvgChevron(long vg, float cx, float cy, float size, float ex, int color) {
        NanoVgRenderer.save(vg);
        NanoVgRenderer.translate(vg, cx, cy);
        NanoVgRenderer.rotate(vg, (float) (Math.PI / 2.0 * ex));
        NanoVgRenderer.triangle(vg, -size * 0.5f, -size * 0.6f, -size * 0.5f, size * 0.6f, size * 0.5f, 0, color);
        NanoVgRenderer.restore(vg);
    }

    /** Height of a module's NanoVG settings panel (bool/slider/mode/color only). */
    private int panelHeightNvg(Module m, int S) {
        int h = 4 * S;
        for (Setting s : m.getSettings()) h += settingHeight(s, S);
        return h + 8 * S;
    }

    /** Render a module's settings via NanoVG, recording {@code sHits} so the existing
     *  click/drag handlers work unchanged. (Crosshair preview / waypoint manager /
     *  event list are skipped here for now — only bool/slider/mode/color.) */
    private void renderSettingsNvg(long vg, Module m, int x0, int yTop, int w, int S, int mx, int my) {
        NanoVgRenderer.roundedRect(vg, x0 + 10 * S, yTop, w - 20 * S, Math.max(1, S), 0.5f, Theme.border());
        int sx = x0 + 14 * S, swid = w - 28 * S, yy = yTop + 4 * S;
        for (Setting s : m.getSettings()) {
            int h = settingHeight(s, S);
            if (s instanceof BoolSetting bs) renderBoolNvg(vg, bs, sx, yy, swid, h, S);
            else if (s instanceof SliderSetting ss) renderSliderNvg(vg, ss, sx, yy, swid, h, S);
            else if (s instanceof ModeSetting ms) renderModeNvg(vg, ms, sx, yy, swid, h, S);
            else if (s instanceof ColorSetting cs) renderColorNvg(vg, cs, sx, yy, swid, S);
            yy += h;
        }
    }

    private void renderBoolNvg(long vg, BoolSetting bs, int x, int y, int w, int h, int S) {
        NanoVgRenderer.text(vg, x, y + h / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, bs.name);
        int pw = 18 * S, ph = 10 * S, px = x + w - pw, py = y + (h - ph) / 2;
        NanoVgRenderer.roundedRect(vg, px, py, pw, ph, ph / 2f, bs.value ? Theme.accent() : Theme.pillOff());
        int kd = ph - 4 * S, kx = bs.value ? px + pw - kd - 2 * S : px + 2 * S;
        NanoVgRenderer.roundedRect(vg, kx, py + 2 * S, kd, kd, kd / 2f, 0xFFFFFFFF);
        SHit hit = new SHit(); hit.s = bs; hit.kind = 0; hit.x = x; hit.y = y; hit.w = w; hit.h = h; sHits.add(hit);
    }

    private void renderSliderNvg(long vg, SliderSetting ss, int x, int y, int w, int h, int S) {
        NanoVgRenderer.text(vg, x, y + 6 * S, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, ss.name);
        String val = ss.display();
        float vw = NanoVgRenderer.textWidth(vg, 10 * S, val);
        NanoVgRenderer.text(vg, x + w - vw, y + 6 * S, 10 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, val);
        int ty = y + 14 * S, th = 4 * S;
        NanoVgRenderer.roundedRect(vg, x, ty, w, th, th / 2f, Theme.pillOff());
        int fw = Math.round(w * (float) ss.fraction());
        if (fw > 0) NanoVgRenderer.roundedRect(vg, x, ty, Math.max(th, fw), th, th / 2f, Theme.accent());
        int kd = 8 * S, kx = x + Math.round(w * (float) ss.fraction());
        NanoVgRenderer.circle(vg, Math.min(x + w - kd / 2f, Math.max(x + kd / 2f, kx)), ty + th / 2f, kd / 2f, 0xFFFFFFFF);
        SHit hit = new SHit(); hit.s = ss; hit.kind = 1; hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.trackX = x; hit.trackW = w; sHits.add(hit);
    }

    private void renderModeNvg(long vg, ModeSetting ms, int x, int y, int w, int h, int S) {
        NanoVgRenderer.text(vg, x, y + h / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, ms.name);
        String disp = "‹ " + ms.get() + " ›";
        float dw = NanoVgRenderer.textWidth(vg, 10 * S, disp);
        NanoVgRenderer.text(vg, x + w - dw, y + h / 2f, 10 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, disp);
        SHit hit = new SHit(); hit.s = ms; hit.kind = 4; hit.x = x; hit.y = y; hit.w = w; hit.h = h; sHits.add(hit);
    }

    private void renderColorNvg(long vg, ColorSetting cs, int x, int y, int w, int S) {
        int row = 15 * S;
        NanoVgRenderer.text(vg, x, y + row / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, cs.name);
        int pw = 18 * S, ph = 10 * S, px = x + w - pw, py = y + (row - ph) / 2;
        NanoVgRenderer.roundedRect(vg, px, py, pw, ph, ph / 2f, cs.accent ? Theme.accent() : Theme.pillOff());
        int kd = ph - 4 * S, kx = cs.accent ? px + pw - kd - 2 * S : px + 2 * S;
        NanoVgRenderer.roundedRect(vg, kx, py + 2 * S, kd, kd, kd / 2f, 0xFFFFFFFF);
        int swx = px - 16 * S, swatch = cs.accent ? Theme.accent() : (0xFF000000 | cs.rgb());
        NanoVgRenderer.roundedRect(vg, swx, py, 12 * S, ph, 3 * S, swatch);
        SHit at = new SHit(); at.s = cs; at.kind = 2; at.x = px; at.y = y; at.w = pw; at.h = row; sHits.add(at);
        if (!cs.accent) {
            int yy = y + row;
            channelNvg(vg, cs, 0, "R", 0xFFE05656, x, yy, w, S); yy += 14 * S;
            channelNvg(vg, cs, 1, "G", 0xFF6FCF7F, x, yy, w, S); yy += 14 * S;
            channelNvg(vg, cs, 2, "B", 0xFF6F9CE0, x, yy, w, S);
        }
    }

    private void channelNvg(long vg, ColorSetting cs, int idx, String label, int chCol, int x, int y, int w, int S) {
        int h = 14 * S;
        NanoVgRenderer.text(vg, x, y + h / 2f, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, label);
        int tx = x + 12 * S, tw = w - 12 * S, ty = y + (h - 4 * S) / 2, th = 4 * S;
        NanoVgRenderer.roundedRect(vg, tx, ty, tw, th, th / 2f, Theme.pillOff());
        int val = idx == 0 ? cs.r : idx == 1 ? cs.g : cs.b;
        float frac = val / 255f;
        if (frac > 0) NanoVgRenderer.roundedRect(vg, tx, ty, Math.max(th, Math.round(tw * frac)), th, th / 2f, chCol);
        int kd = 8 * S, kx = tx + Math.round(tw * frac);
        NanoVgRenderer.circle(vg, Math.min(tx + tw - kd / 2f, Math.max(tx + kd / 2f, kx)), ty + th / 2f, kd / 2f, 0xFFFFFFFF);
        SHit hit = new SHit(); hit.s = cs; hit.kind = 3; hit.channel = idx; hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.trackX = tx; hit.trackW = tw; sHits.add(hit);
    }

    // ---- NanoVG Binds tab -------------------------------------------------
    private void drawBindsNvg(long vg, int x, int y, int W, int H, int S, int mx, int my, float dt,
                              int gy, int clipTop, int clipBot, int visH, int margin) {
        int rowH = 22 * S, gapr = 6 * S, listW = W - margin * 2, rx = x + margin;
        List<Module> binds = new ArrayList<>();
        for (Module m : LumeClient.MODULES.getModules()) if (m.isBindable()) binds.add(m);
        int contentH = Math.max(0, binds.size() * (rowH + gapr) - gapr);
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        int scrollI = Math.round(scroll);

        NanoVgRenderer.save(vg);
        NanoVgRenderer.scissor(vg, rx - 2 * S, clipTop, listW + 4 * S, visH);
        for (int i = 0; i < binds.size(); i++) {
            Module m = binds.get(i);
            int ry = gy + i * (rowH + gapr) - scrollI;
            if (ry + rowH < clipTop || ry > clipBot) continue;
            boolean binding = m == bindingModule;
            boolean hov = inside(mx, my, rx, ry, listW, rowH) && my >= clipTop && my <= clipBot;
            NanoVgRenderer.roundedRect(vg, rx, ry, listW, rowH, 8 * S, (hov || binding) ? Theme.glassHov() : Theme.glassRow());
            NanoVgRenderer.text(vg, rx + 12 * S, ry + rowH / 2f, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tName(m.getName()));
            String kd = binding ? "нажми клавишу…" : keyDisplay(m.getKey());
            int chipW = (int) NanoVgRenderer.textWidth(vg, 10 * S, kd) + 16 * S, chipX = rx + listW - chipW - 8 * S, chipY = ry + (rowH - 15 * S) / 2;
            NanoVgRenderer.roundedRect(vg, chipX, chipY, chipW, 15 * S, 7 * S, binding ? withAlpha(Theme.accentRgb(), 0x66) : Theme.pillOff());
            NanoVgRenderer.text(vg, chipX + chipW / 2f, chipY + 7.5f * S, 10 * S, binding ? 0xFFFFFFFF : Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, kd);
            String md = m.getBindMode() == Module.BindMode.HOLD ? "HOLD" : "TOGGLE";
            int modeW = (int) NanoVgRenderer.textWidth(vg, 9 * S, md) + 12 * S, modeX = chipX - modeW - 6 * S;
            NanoVgRenderer.roundedRect(vg, modeX, chipY, modeW, 15 * S, 7 * S, Theme.pillOff());
            NanoVgRenderer.text(vg, modeX + modeW / 2f, chipY + 7.5f * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, md);
            bindHits.add(new Object[]{ m, rx, ry, listW, rowH, modeX, modeW, chipY });
        }
        NanoVgRenderer.restore(vg);
        if (maxScroll > 0) {
            int sbW = 3 * S, sbX = x + W - margin / 2 - sbW;
            NanoVgRenderer.roundedRect(vg, sbX, gy, sbW, visH, sbW / 2f, Theme.glassRow());
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) contentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / maxScroll));
            NanoVgRenderer.roundedRect(vg, sbX, thumbY, sbW, thumbH, sbW / 2f, Theme.accent());
        }
    }

    // ---- NanoVG Server tab (FT/HW helper) ---------------------------------
    private void nvgPill(long vg, boolean on, int px, int py, int pw, int ph, int S) {
        NanoVgRenderer.roundedRect(vg, px, py, pw, ph, ph / 2f, on ? Theme.accent() : Theme.pillOff());
        int kd = ph - 4 * S, kx = on ? px + pw - kd - 2 * S : px + 2 * S;
        NanoVgRenderer.roundedRect(vg, kx, py + 2 * S, kd, kd, kd / 2f, 0xFFFFFFFF);
    }

    private void drawServerNvg(long vg, int x, int y, int W, int H, int S, int mx, int my, float dt,
                               int gy, int clipTop, int clipBot, int visH, int margin) {
        int sx = x + margin, w = W - margin * 2;
        com.lume.client.fthw.ServerType st = com.lume.client.fthw.ServerType.current();
        boolean supported = st != com.lume.client.fthw.ServerType.UNKNOWN;
        ServerHelper sh = (ServerHelper) LumeClient.MODULES.getByName("Server Helper");
        boolean on = sh != null && sh.isEnabled();

        if (!supported) {
            // fast-connect buttons (you're not on FunTime/HolyWorld)
            NanoVgRenderer.text(vg, sx + 2 * S, gy + 6 * S, 11 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Не на сервере. Быстрый вход:");
            int bh = 32 * S, by = gy + 20 * S;
            NanoVgRenderer.gradientRoundedRect(vg, sx, by, w, bh, 11 * S, Theme.accent(), Theme.accent2());
            NanoVgRenderer.text(vg, sx + w / 2f, by + bh / 2f, 13 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, "Играть на FunTime");
            serverHits.add(new Object[]{ "connect:funtime", sx, by, w, bh, null });
            by += bh + 8 * S;
            NanoVgRenderer.roundedRect(vg, sx, by, w, bh, 11 * S, Theme.glassHov());
            NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, by + 0.5f * S, w - S, bh - S, 11 * S, S, Theme.rim());
            NanoVgRenderer.text(vg, sx + w / 2f, by + bh / 2f, 13 * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "Играть на HolyWorld");
            serverHits.add(new Object[]{ "connect:holyworld", sx, by, w, bh, null });
            return;
        }

        int maxScroll = Math.max(0, serverContentH - visH);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        int scrollI = Math.round(scroll);

        NanoVgRenderer.save(vg);
        NanoVgRenderer.scissor(vg, x, clipTop, W, visH);
        int cur = 0;

        { // master enable toggle
            int ry = gy + cur - scrollI;
            NanoVgRenderer.text(vg, sx + 2 * S, ry + 11 * S, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, "Включить хелпер");
            int pw = 40 * S, ph = 18 * S, px = sx + w - pw, py = ry;
            nvgPill(vg, on, px, py, pw, ph, S);
            serverHits.add(new Object[]{ "master", px, py, pw, ph, null });
            cur += 26 * S;
        }

        if (on) {
            NanoVgRenderer.text(vg, sx + 2 * S, gy + cur - scrollI + 7 * S, 11 * S, 0xFF6FCF7F, NanoVgRenderer.ALIGN_MIDDLE, "Сервер: " + st.display() + "  ·  Активно");
            cur += 16 * S;
            NanoVgRenderer.text(vg, sx + 2 * S, gy + cur - scrollI + 6 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Функции (клик по чипу = бинд клавиши):");
            cur += 14 * S;
            BoolSetting[] subs = { sh.itemHelper, sh.effects, sh.eventsHud, sh.showServer };
            for (BoolSetting bs : subs) {
                int ry = gy + cur - scrollI, rh = 17 * S;
                if (ry + rh >= clipTop && ry <= clipBot) {
                    NanoVgRenderer.text(vg, sx + 6 * S, ry + rh / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, bs.name);
                    int pw = 30 * S, ph = 14 * S, px = sx + w - pw, py = ry + S;
                    nvgPill(vg, bs.value, px, py, pw, ph, S);
                    serverHits.add(new Object[]{ "subToggle", px, py, pw, ph, bs });
                    if (com.lume.client.fthw.HelperBinds.bound.contains(bs)) {
                        boolean cap = bs == bindingSetting;
                        String kd = cap ? "клавиша…" : keyDisplay(bs.key);
                        int chipW = (int) NanoVgRenderer.textWidth(vg, 9 * S, kd) + 12 * S, chipX = px - chipW - 8 * S;
                        NanoVgRenderer.roundedRect(vg, chipX, py, chipW, ph, ph / 2f, cap ? withAlpha(Theme.accentRgb(), 0x66) : Theme.pillOff());
                        NanoVgRenderer.text(vg, chipX + chipW / 2f, py + ph / 2f, 9 * S, cap ? 0xFFFFFFFF : Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, kd);
                        serverHits.add(new Object[]{ "subBind", chipX, py, chipW, ph, bs });
                    }
                }
                cur += rh;
            }
            cur += 6 * S;

            NanoVgRenderer.text(vg, sx + 2 * S, gy + cur - scrollI + 6 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Предметы — отметь что есть на сервере:");
            cur += 14 * S;
            String[] catNames = { "Активные", "Сферы", "Талисманы" };
            ItemRule.Cat[] cats = { ItemRule.Cat.ACTIVE, ItemRule.Cat.SPHERE, ItemRule.Cat.TALISMAN };
            for (int ci = 0; ci < cats.length; ci++) {
                int ryh = gy + cur - scrollI;
                if (ryh + 12 * S >= clipTop && ryh <= clipBot)
                    NanoVgRenderer.text(vg, sx + 4 * S, ryh + 6 * S, 10 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, catNames[ci]);
                cur += 13 * S;
                for (ItemRule it : ItemRules.byCat(cats[ci])) {
                    int ry = gy + cur - scrollI, rh = 16 * S;
                    if (ry + rh >= clipTop && ry <= clipBot) {
                        int cb = 11 * S, cbx = sx + 4 * S, cby = ry + (rh - cb) / 2;
                        NanoVgRenderer.roundedRect(vg, cbx, cby, cb, cb, 3 * S, it.present ? it.color : Theme.pillOff());
                        if (it.present) NanoVgRenderer.roundedRect(vg, cbx + (cb - 4 * S) / 2, cby + (cb - 4 * S) / 2, 4 * S, 4 * S, S, 0xFFFFFFFF);
                        serverHits.add(new Object[]{ "present", cbx, ry, w, rh, it });
                        NanoVgRenderer.text(vg, cbx + cb + 6 * S, ry + rh / 2f, 10 * S, it.present ? Theme.txt() : Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, it.name);
                        StringBuilder rb = new StringBuilder();
                        if (it.radius > 0) rb.append("R").append((int) it.radius).append(" ");
                        if (it.cooldownSec > 0) rb.append(it.cooldownSec).append("с");
                        if (rb.length() > 0) {
                            float rw = NanoVgRenderer.textWidth(vg, 10 * S, rb.toString());
                            NanoVgRenderer.text(vg, sx + w - rw, ry + rh / 2f, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, rb.toString());
                        }
                    }
                    cur += 16 * S;
                }
                cur += 2 * S;
            }

            cur += 4 * S;
            int rye = gy + cur - scrollI;
            if (rye + 12 * S >= clipTop && rye <= clipBot)
                NanoVgRenderer.text(vg, sx + 2 * S, rye + 6 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Расписание ивентов (учится по чату):");
            cur += 14 * S;
            for (com.lume.client.fthw.EventRule r : EventManager.rules) {
                int ry = gy + cur - scrollI;
                if (ry + 12 * S >= clipTop && ry <= clipBot) {
                    int left = -1;
                    for (EventManager.Active a : EventManager.active) if (a.rule == r) { left = a.secondsLeft(); break; }
                    int col; String line;
                    if (left >= 0) { col = 0xFF6FCF7F; line = "● " + r.name + " — идёт, " + left + "с"; }
                    else {
                        long eta = r.etaSec(), ago = r.agoSec();
                        if (eta > 0) { col = 0xFFE8C15A; line = "◷ " + r.name + " — ≈ через " + fmtDur(eta); }
                        else if (ago >= 0) { col = Theme.txtDim(); line = "○ " + r.name + " — был " + fmtDur(ago) + " назад"; }
                        else { col = Theme.txtDim(); line = "○ " + r.name + " — ещё не видел"; }
                    }
                    NanoVgRenderer.text(vg, sx + 6 * S, ry + 6 * S, 10 * S, col, NanoVgRenderer.ALIGN_MIDDLE, line);
                }
                cur += 13 * S;
            }

            // quick commands — bind a key + ▶ send
            cur += 6 * S;
            int ryq = gy + cur - scrollI;
            if (ryq + 12 * S >= clipTop && ryq <= clipBot)
                NanoVgRenderer.text(vg, sx + 2 * S, ryq + 6 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Быстрые команды (бинд клавиши · ▶ отправить):");
            cur += 14 * S;
            for (com.lume.client.fthw.QuickCommands.Cmd c : com.lume.client.fthw.QuickCommands.list) {
                int ry = gy + cur - scrollI, rh = 16 * S;
                if (ry + rh >= clipTop && ry <= clipBot) {
                    NanoVgRenderer.text(vg, sx + 6 * S, ry + rh / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, c.label + "  " + c.command);
                    int sbw = 22 * S, sbx = sx + w - sbw, sby = ry + S, sbh = 14 * S;
                    NanoVgRenderer.roundedRect(vg, sbx, sby, sbw, sbh, sbh / 2f, Theme.accent());
                    NanoVgRenderer.triangle(vg, sbx + sbw / 2f - 2 * S, sby + sbh / 2f - 3 * S, sbx + sbw / 2f - 2 * S, sby + sbh / 2f + 3 * S, sbx + sbw / 2f + 4 * S, sby + sbh / 2f, 0xFFFFFFFF);
                    serverHits.add(new Object[]{ "qcmdSend", sbx, sby, sbw, sbh, c });
                    boolean cap = c == bindingQuickCmd;
                    String kd = cap ? "клавиша…" : keyDisplay(c.key);
                    int chipW = (int) NanoVgRenderer.textWidth(vg, 9 * S, kd) + 12 * S, chipX = sbx - chipW - 6 * S, chipY = ry + S;
                    NanoVgRenderer.roundedRect(vg, chipX, chipY, chipW, 14 * S, 7 * S, cap ? withAlpha(Theme.accentRgb(), 0x66) : Theme.pillOff());
                    NanoVgRenderer.text(vg, chipX + chipW / 2f, chipY + 7 * S, 9 * S, cap ? 0xFFFFFFFF : Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, kd);
                    serverHits.add(new Object[]{ "qcmdBind", chipX, chipY, chipW, 14 * S, c });
                }
                cur += 16 * S;
            }
        }

        NanoVgRenderer.restore(vg);
        serverContentH = cur;
        if (maxScroll > 0) {
            int sbW = 3 * S, sbX = x + W - margin / 2 - sbW;
            NanoVgRenderer.roundedRect(vg, sbX, gy, sbW, visH, sbW / 2f, Theme.glassRow());
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) serverContentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / maxScroll));
            NanoVgRenderer.roundedRect(vg, sbX, thumbY, sbW, thumbH, sbW / 2f, Theme.accent());
        }
    }

    /** Quick-connect to a server from inside the game (Server tab fast-connect). */
    private void fastConnect(String name, String address) {
        final MinecraftClient mc = this.client;
        try {
            final ServerInfo info = new ServerInfo(name, address, ServerInfo.ServerType.OTHER);
            final ServerAddress addr = ServerAddress.parse(address);
            // leave the current world FIRST (esp. the singleplayer integrated server),
            // then connect on the next client tick so the teardown has finished —
            // otherwise the world unloads but the connect screen never comes up (black).
            if (mc.world != null) {
                mc.world.disconnect();
                mc.disconnect();
            }
            mc.execute(() -> {
                try {
                    System.out.println("[Lume] fast connect → " + address);
                    ConnectScreen.connect(new TitleScreen(), mc, addr, info, false, null);
                } catch (Exception e) {
                    System.out.println("[Lume] fast connect (deferred) failed: " + e);
                }
            });
        } catch (Exception e) {
            System.out.println("[Lume] fast connect failed: " + e);
        }
    }

    // ---- Server tab (FT/HW helper) ----------------------------------------

    /** Small toggle pill used across the Server tab. */
    private void serverPill(DrawContext ctx, boolean on, int px, int py, int pw, int ph, int S) {
        RenderUtil.roundedRect(ctx, px, py, pw, ph, ph / 2, on ? Theme.accent() : Theme.pillOff());
        int kd = ph - 4 * S, kx = on ? px + pw - kd - 2 * S : px + 2 * S;
        RenderUtil.roundedRect(ctx, kx, py + 2 * S, kd, kd, kd / 2, 0xFFFFFFFF);
    }

    private void renderServer(DrawContext ctx, int x, int y, int W, int H, int S, int mx, int my, float dt) {
        int margin = 20 * S, gy = y + GRID_TOP * S;
        int clipTop = gy - 2 * S, clipBot = y + H - 12 * S, visH = clipBot - gy;
        lastClipTop = clipTop; lastClipBot = clipBot;
        int sx = x + margin, w = W - margin * 2;
        serverHits.clear();

        com.lume.client.fthw.ServerType st = com.lume.client.fthw.ServerType.current();
        boolean supported = st != com.lume.client.fthw.ServerType.UNKNOWN;
        ServerHelper sh = (ServerHelper) LumeClient.MODULES.getByName("Server Helper");
        boolean on = sh != null && sh.isEnabled();

        if (!supported) {
            serverToggle = new int[]{0, 0, 0, 0};
            RenderUtil.vanillaText(ctx, this.textRenderer, "Клиент поддерживает только FunTime и HolyWorld.", sx + 2 * S, gy, Theme.txtDim(), S);
            return;
        }

        // scroll bookkeeping (content height measured last frame)
        int maxScroll = Math.max(0, serverContentH - visH);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        int scrollI = Math.round(scroll);

        winScissor(ctx, x, clipTop, x + W, clipBot);
        int cur = 0;

        // master enable toggle
        {
            int ry = gy + cur - scrollI;
            RenderUtil.vanillaText(ctx, this.textRenderer, "Включить хелпер", sx + 2 * S, ry + 6 * S, Theme.txt(), S);
            int pw = 40 * S, ph = 18 * S, px = sx + w - pw, py = ry;
            serverPill(ctx, on, px, py, pw, ph, S);
            serverToggle = new int[]{ px, py, pw, ph };
            serverHits.add(new Object[]{ "master", px, py, pw, ph, null });
            cur += 26 * S;
        }

        if (on) {
            RenderUtil.vanillaText(ctx, this.textRenderer, "Сервер: " + st.display() + "  ·  Активно", sx + 2 * S, gy + cur - scrollI, 0xFF6FCF7F, S);
            cur += 16 * S;

            // sub-functions (toggle + optional keybind chip)
            RenderUtil.vanillaText(ctx, this.textRenderer, "Функции (клик по чипу = бинд клавиши):", sx + 2 * S, gy + cur - scrollI, Theme.txtDim(), S);
            cur += 14 * S;
            BoolSetting[] subs = { sh.itemHelper, sh.effects, sh.eventsHud, sh.showServer };
            for (BoolSetting bs : subs) {
                int ry = gy + cur - scrollI, rh = 17 * S;
                if (ry + rh >= clipTop && ry <= clipBot) {
                    RenderUtil.vanillaText(ctx, this.textRenderer, bs.name, sx + 6 * S, ry + 4 * S, Theme.txt(), S);
                    int pw = 30 * S, ph = 14 * S, px = sx + w - pw, py = ry + S;
                    serverPill(ctx, bs.value, px, py, pw, ph, S);
                    serverHits.add(new Object[]{ "subToggle", px, py, pw, ph, bs });
                    if (com.lume.client.fthw.HelperBinds.bound.contains(bs)) {
                        boolean cap = bs == bindingSetting;
                        String kd = cap ? "клавиша…" : keyDisplay(bs.key);
                        int kw = RenderUtil.vanillaWidth(this.textRenderer, kd, S);
                        int chipW = kw + 12 * S, chipX = px - chipW - 8 * S;
                        RenderUtil.roundedRect(ctx, chipX, py, chipW, ph, ph / 2, cap ? withAlpha(Theme.accentRgb(), 0x66) : Theme.pillOff());
                        RenderUtil.vanillaText(ctx, this.textRenderer, kd, chipX + 6 * S, py + 3 * S, cap ? 0xFFFFFFFF : Theme.accent(), S);
                        serverHits.add(new Object[]{ "subBind", chipX, py, chipW, ph, bs });
                    }
                }
                cur += rh;
            }
            cur += 6 * S;

            // item encyclopedia — per-item "present" verify
            RenderUtil.vanillaText(ctx, this.textRenderer, "Предметы — отметь что есть на сервере:", sx + 2 * S, gy + cur - scrollI, Theme.txtDim(), S);
            cur += 14 * S;
            String[] catNames = { "Активные", "Сферы", "Талисманы" };
            ItemRule.Cat[] cats = { ItemRule.Cat.ACTIVE, ItemRule.Cat.SPHERE, ItemRule.Cat.TALISMAN };
            for (int ci = 0; ci < cats.length; ci++) {
                int ryh = gy + cur - scrollI;
                if (ryh + 12 * S >= clipTop && ryh <= clipBot)
                    RenderUtil.vanillaText(ctx, this.textRenderer, catNames[ci], sx + 4 * S, ryh, Theme.accent(), S);
                cur += 13 * S;
                for (ItemRule it : ItemRules.byCat(cats[ci])) {
                    int ry = gy + cur - scrollI, rh = 16 * S;
                    if (ry + rh >= clipTop && ry <= clipBot) {
                        int cb = 11 * S, cbx = sx + 4 * S, cby = ry + (rh - cb) / 2;
                        RenderUtil.roundedRect(ctx, cbx, cby, cb, cb, 3 * S, it.present ? it.color : Theme.pillOff());
                        if (it.present) RenderUtil.roundedRect(ctx, cbx + (cb - 4 * S) / 2, cby + (cb - 4 * S) / 2, 4 * S, 4 * S, S, 0xFFFFFFFF);
                        serverHits.add(new Object[]{ "present", cbx, ry, w, rh, it });
                        RenderUtil.vanillaText(ctx, this.textRenderer, it.name, cbx + cb + 6 * S, ry + 4 * S, it.present ? Theme.txt() : Theme.txtDim(), S);
                        StringBuilder rb = new StringBuilder();
                        if (it.radius > 0) rb.append("R").append((int) it.radius).append(" ");
                        if (it.cooldownSec > 0) rb.append(it.cooldownSec).append("с");
                        if (rb.length() > 0) {
                            int rw = RenderUtil.vanillaWidth(this.textRenderer, rb.toString(), S);
                            RenderUtil.vanillaText(ctx, this.textRenderer, rb.toString(), sx + w - rw, ry + 4 * S, Theme.txtDim(), S);
                        }
                    }
                    cur += 16 * S;
                }
                cur += 2 * S;
            }

            // events — learned schedule
            cur += 4 * S;
            int rye = gy + cur - scrollI;
            if (rye + 12 * S >= clipTop && rye <= clipBot)
                RenderUtil.vanillaText(ctx, this.textRenderer, "Расписание ивентов (учится по чату):", sx + 2 * S, rye, Theme.txtDim(), S);
            cur += 14 * S;
            for (com.lume.client.fthw.EventRule r : EventManager.rules) {
                int ry = gy + cur - scrollI;
                if (ry + 12 * S >= clipTop && ry <= clipBot) {
                    int left = -1;
                    for (EventManager.Active a : EventManager.active) if (a.rule == r) { left = a.secondsLeft(); break; }
                    int col; String line;
                    if (left >= 0) { col = 0xFF6FCF7F; line = "● " + r.name + " — идёт, " + left + "с"; }
                    else {
                        long eta = r.etaSec(), ago = r.agoSec();
                        if (eta > 0) { col = 0xFFE8C15A; line = "◷ " + r.name + " — ≈ через " + fmtDur(eta); }
                        else if (ago >= 0) { col = Theme.txtDim(); line = "○ " + r.name + " — был " + fmtDur(ago) + " назад"; }
                        else { col = Theme.txtDim(); line = "○ " + r.name + " — ещё не видел"; }
                    }
                    RenderUtil.vanillaText(ctx, this.textRenderer, line, sx + 6 * S, ry, col, S);
                }
                cur += 13 * S;
            }
        }

        ctx.disableScissor();
        serverContentH = cur;

        if (maxScroll > 0) {
            int sbW = 3 * S, sbX = x + W - margin / 2 - sbW;
            RenderUtil.roundedRect(ctx, sbX, gy, sbW, visH, sbW, Theme.glassRow());
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) serverContentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / maxScroll));
            RenderUtil.roundedRect(ctx, sbX, thumbY, sbW, thumbH, sbW, Theme.accent());
        }
    }

    private static String fmtDur(long sec) {
        if (sec < 0) return "—";
        if (sec < 90) return sec + "с";
        long m = sec / 60;
        if (m < 90) return m + "м";
        return (m / 60) + "ч " + (m % 60) + "м";
    }

    // ---- Binds tab --------------------------------------------------------

    private void renderBinds(DrawContext ctx, int x, int y, int W, int H, int S, int mx, int my, float dt) {
        int margin = 20 * S;
        int gy = y + GRID_TOP * S;
        int clipTop = gy - 2 * S, clipBot = y + H - 12 * S, visH = clipBot - gy;
        lastClipTop = clipTop; lastClipBot = clipBot;

        int rowH = 22 * S, gapr = 6 * S, listW = W - margin * 2, rx = x + margin;
        List<Module> binds = new ArrayList<>();
        for (Module m : LumeClient.MODULES.getModules()) if (m.isBindable()) binds.add(m);
        int contentH = Math.max(0, binds.size() * (rowH + gapr) - gapr);
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        int scrollI = Math.round(scroll);

        bindHits.clear();
        winScissor(ctx, x + margin - 2 * S, clipTop, x + W - margin + 2 * S, clipBot);
        for (int i = 0; i < binds.size(); i++) {
            Module m = binds.get(i);
            int ry = gy + i * (rowH + gapr) - scrollI;
            if (ry + rowH < clipTop || ry > clipBot) continue;
            boolean binding = m == bindingModule;
            boolean hov = inside(mx, my, rx, ry, listW, rowH) && my >= clipTop && my <= clipBot;
            RenderUtil.roundedRect(ctx, rx, ry, listW, rowH, 8 * S, (hov || binding) ? Theme.glassHov() : Theme.glassRow());
            RenderUtil.textVCentered(ctx, this.textRenderer, com.lume.client.Lang.tName(m.getName()), rx + 12 * S, ry, rowH, Theme.txt(), 0.5f * S);
            int chipY = ry + (rowH - 15 * S) / 2;
            // key chip (rightmost)
            String kd = binding ? "press a key…" : keyDisplay(m.getKey());
            int kw = RenderUtil.width(this.textRenderer, kd, 0.46f * S);
            int chipW = kw + 16 * S, chipX = rx + listW - chipW - 8 * S;
            RenderUtil.roundedRect(ctx, chipX, chipY, chipW, 15 * S, 7 * S, binding ? withAlpha(Theme.accentRgb(), 0x66) : Theme.pillOff());
            RenderUtil.textVCentered(ctx, this.textRenderer, kd, chipX + 8 * S, chipY, 15 * S, binding ? 0xFFFFFFFF : Theme.accent(), 0.46f * S);
            // mode chip (HOLD / TOGGLE), left of the key chip
            String md = m.getBindMode() == Module.BindMode.HOLD ? "HOLD" : "TOGGLE";
            int mw = RenderUtil.width(this.textRenderer, md, 0.4f * S);
            int modeW = mw + 12 * S, modeX = chipX - modeW - 6 * S;
            RenderUtil.roundedRect(ctx, modeX, chipY, modeW, 15 * S, 7 * S, Theme.pillOff());
            RenderUtil.textVCentered(ctx, this.textRenderer, md, modeX + 6 * S, chipY, 15 * S, Theme.txtDim(), 0.4f * S);
            bindHits.add(new Object[]{ m, rx, ry, listW, rowH, modeX, modeW, chipY });
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int sbW = 3 * S, sbX = x + W - margin / 2 - sbW;
            RenderUtil.roundedRect(ctx, sbX, gy, sbW, visH, sbW, Theme.glassRow());
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) contentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / maxScroll));
            RenderUtil.roundedRect(ctx, sbX, thumbY, sbW, thumbH, sbW, Theme.accent());
        }
    }

    // ---- HUD editor (drag elements while the menu is open) ----------------

    /** Movable HUD elements: name + base rect (GUI px) given screen size. */
    private List<int[]> hudFrames(int sw, int sh, List<String> names) {
        List<int[]> rects = new ArrayList<>();
        addFrame(rects, names, "HUD", 6, 6, 134, 60, sw, sh);
        addFrame(rects, names, "Potion HUD", sw - 150, 6, 144, 18, sw, sh);
        addFrame(rects, names, "Module List", sw - 110, 6, 106, 40, sw, sh);
        addFrame(rects, names, "Target HUD", sw / 2 - 95, 10, 190, 44, sw, sh);
        addFrame(rects, names, "Block Info", sw / 2 - 70, 10, 140, 32, sw, sh);
        addFrame(rects, names, "Keystrokes", 12, sh - 150, 70, 70, sw, sh);
        addFrame(rects, names, "Inventory HUD", sw / 2 - 85, sh - 80, 170, 58, sw, sh);
        addFrame(rects, names, "Armor HUD", sw / 2 + 87, sh - 22, 84, 20, sw, sh);
        addFrame(rects, names, "Totem Counter", sw / 2 - 138, sh - 22, 46, 20, sw, sh);
        return rects;
    }

    private void addFrame(List<int[]> rects, List<String> names, String name, int bx, int by, int w, int h, int sw, int sh) {
        Module m = LumeClient.MODULES.getByName(name);
        if (m == null || !m.isEnabled()) return;
        int[] off = HudLayout.get(name);
        rects.add(new int[]{ bx + off[0], by + off[1], w, h });
        names.add(name);
    }

    private void drawHudFrames(DrawContext ctx, int S, int mouseX, int mouseY) {
        List<String> names = new ArrayList<>();
        List<int[]> rects = hudFrames(this.width, this.height, names);
        hudSliderTrack = null;
        var m = ctx.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        for (int i = 0; i < rects.size(); i++) {
            int[] rc = rects.get(i);
            String label = names.get(i);
            boolean sel = label.equals(selectedHud);
            boolean hov = sel || (dragMode == 3 && label.equals(dragHud))
                    || (mouseX >= rc[0] && mouseX <= rc[0] + rc[2] && mouseY >= rc[1] && mouseY <= rc[1] + rc[3]);
            int rx = rc[0] * S, ry = rc[1] * S, rw = rc[2] * S, rh = rc[3] * S;
            RenderUtil.roundedRect(ctx, rx, ry, rw, rh, 5 * S, withAlpha(Theme.accentRgb(), hov ? 0x55 : 0x22));
            RenderUtil.roundedRect(ctx, rx, ry, rw, Math.max(1, S), 1, sel ? Theme.accent() : withAlpha(Theme.accentRgb(), 0x88));
            if (sel) RenderUtil.roundedRect(ctx, rx, ry, rw, rh, 5 * S, withAlpha(0xFFFFFF, 0x14));
            int tw = RenderUtil.width(this.textRenderer, label, 0.42f * S);
            RenderUtil.textVCentered(ctx, this.textRenderer, label, rx + (rw - tw) / 2, ry, rh, hov ? 0xFFFFFFFF : Theme.txt(), 0.42f * S);

            // size slider + reset hint for the selected element
            if (sel) {
                int slX = rc[0], slY = rc[1] + rc[3] + 5, slW = Math.max(70, rc[2]), slH = 6;
                hudSliderTrack = new int[]{ slX, slY, slW, slH };
                int tx = slX * S, ty = slY * S, tW = slW * S, tH = slH * S;
                RenderUtil.roundedRect(ctx, tx, ty, tW, tH, tH / 2, withAlpha(0x000000, 0x99));
                float frac = (HudLayout.getScale(label) - 0.5f) / 1.5f;
                int fw = Math.max(tH, Math.round(tW * frac));
                RenderUtil.roundedRect(ctx, tx, ty, fw, tH, tH / 2, Theme.accent());
                int kd = 10 * S, kx = tx + Math.round(tW * frac);
                RenderUtil.roundedRect(ctx, Math.min(tx + tW - kd, Math.max(tx, kx - kd / 2)), ty + tH / 2 - kd / 2, kd, kd, kd / 2, 0xFFFFFFFF);
                String hint = "double-click to reset  ·  " + String.format("%.2fx", HudLayout.getScale(label));
                RenderUtil.text(ctx, this.textRenderer, hint, tx, ty + tH + 3 * S, withAlpha(0xFFFFFF, 0xAA), true, 0.36f * S);
            }
        }
        m.pop();
    }

    // ---- settings panel rendering -----------------------------------------

    private static final int PREVIEW_H = 46;

    private int settingHeight(Setting s, int S) {
        if (s instanceof SliderSetting) return 22 * S;
        if (s instanceof ModeSetting) return 15 * S;
        if (s instanceof ColorSetting cs) return 15 * S + (cs.accent ? 0 : 3 * 14 * S);
        return 15 * S; // bool
    }

    private int panelHeight(Module m, int S) {
        int h = 4 * S;
        if (m instanceof CustomCrosshair) h += PREVIEW_H * S + 4 * S;
        for (Setting s : m.getSettings()) h += settingHeight(s, S);
        if (m instanceof Waypoints) h += wpManagerHeight(S);
        if (m instanceof ServerHelper) h += (EventManager.rules.size() + 1) * 12 * S + 6 * S;
        return h + 8 * S;
    }

    private int wpManagerHeight(int S) {
        return 6 * S + 18 * S + 6 * S + Waypoints.list.size() * 16 * S;
    }

    private void renderSettings(DrawContext ctx, Module m, int x0, int yTop, int w, int S, int mx, int my) {
        RenderUtil.roundedRect(ctx, x0 + 10 * S, yTop, w - 20 * S, Math.max(1, S), S, Theme.border()); // separator
        int sx = x0 + 14 * S, swid = w - 28 * S;
        int yy = yTop + 4 * S;

        // live crosshair preview
        if (m instanceof CustomCrosshair) {
            int ph = PREVIEW_H * S;
            RenderUtil.roundedRect(ctx, sx, yy, swid, ph, 8 * S, 0x55000000);
            winScissor(ctx, sx, yy, sx + swid, yy + ph);
            HudRenderer.drawCrosshair(ctx, sx + swid / 2, yy + ph / 2, S);
            ctx.disableScissor();
            yy += ph + 4 * S;
        }

        for (Setting s : m.getSettings()) {
            int h = settingHeight(s, S);
            if (s instanceof BoolSetting bs) renderBool(ctx, bs, sx, yy, swid, h, S);
            else if (s instanceof SliderSetting ss) renderSlider(ctx, ss, sx, yy, swid, h, S);
            else if (s instanceof ModeSetting ms) renderMode(ctx, ms, sx, yy, swid, h, S);
            else if (s instanceof ColorSetting cs) renderColor(ctx, cs, sx, yy, swid, S);
            yy += h;
        }
        if (m instanceof Waypoints) renderWaypointManager(ctx, sx, yy + 4 * S, swid, S);
        if (m instanceof ServerHelper) renderEventList(ctx, sx, yy + 4 * S, swid, S);
    }

    /** Read-only list of configured FT/HW events (with live countdown if active). Cyrillic → vanilla font. */
    private void renderEventList(DrawContext ctx, int sx, int yy, int swid, int S) {
        RenderUtil.vanillaText(ctx, this.textRenderer, "Ивенты (FunTime):", sx, yy, Theme.txtDim(), S);
        int ly = yy + 12 * S;
        for (EventRuleName er : eventRows()) {
            int col = er.left >= 0 ? 0xFF6FCF7F : Theme.txt();
            String line = er.left >= 0 ? er.name + " — " + er.left + "с" : er.name;
            RenderUtil.vanillaText(ctx, this.textRenderer, line, sx + 4 * S, ly, col, S);
            ly += 12 * S;
        }
    }

    private record EventRuleName(String name, int left) {}

    private List<EventRuleName> eventRows() {
        List<EventRuleName> out = new ArrayList<>();
        for (com.lume.client.fthw.EventRule r : EventManager.rules) {
            int left = -1;
            for (EventManager.Active a : EventManager.active) if (a.rule == r) { left = a.secondsLeft(); break; }
            out.add(new EventRuleName(r.name, left));
        }
        return out;
    }

    // ---- Waypoints manager (GUI input fields) -----------------------------

    private String bufGet(String id) {
        return switch (id) { case "name" -> wpName; case "x" -> wpX; case "y" -> wpY; default -> wpZ; };
    }
    private void bufSet(String id, String v) {
        switch (id) { case "name" -> wpName = v; case "x" -> wpX = v; case "y" -> wpY = v; case "z" -> wpZ = v; }
    }

    private void field(DrawContext ctx, String id, int x, int y, int w, int h, String placeholder, int S) {
        boolean foc = id.equals(focusedField);
        RenderUtil.roundedRect(ctx, x, y, w, h, 5 * S, foc ? Theme.glassHov() : Theme.glassRow());
        if (foc) RenderUtil.roundedRect(ctx, x, y + h - Math.max(1, S), w, Math.max(1, S), 1, Theme.accent());
        String txt = bufGet(id);
        String show = txt.isEmpty() && !foc ? placeholder : txt + (foc ? "_" : "");
        RenderUtil.textVCentered(ctx, this.textRenderer, show, x + 5 * S, y, h, txt.isEmpty() && !foc ? Theme.txtDim() : Theme.txt(), 0.42f * S);
        wpHits.add(new Object[]{ id, x, y, w, h });
    }

    private void renderWaypointManager(DrawContext ctx, int sx, int yy, int swid, int S) {
        int rh = 15 * S, addW = 32 * S;
        int nameW = Math.round(swid * 0.30f);
        int numW = (swid - nameW - addW - 4 * 4 * S) / 3;
        int fx = sx;
        field(ctx, "name", fx, yy, nameW, rh, "name", S); fx += nameW + 4 * S;
        field(ctx, "x", fx, yy, numW, rh, "x", S); fx += numW + 4 * S;
        field(ctx, "y", fx, yy, numW, rh, "y", S); fx += numW + 4 * S;
        field(ctx, "z", fx, yy, numW, rh, "z", S);
        int addX = sx + swid - addW;
        RenderUtil.roundedRect(ctx, addX, yy, addW, rh, 5 * S, Theme.accent());
        int aw = RenderUtil.width(this.textRenderer, "Add", 0.42f * S);
        RenderUtil.textVCentered(ctx, this.textRenderer, "Add", addX + (addW - aw) / 2, yy, rh, 0xFFFFFFFF, 0.42f * S);
        wpHits.add(new Object[]{ "add", addX, yy, addW, rh });

        int ly = yy + 18 * S + 6 * S;
        for (int i = 0; i < Waypoints.list.size(); i++) {
            Waypoints.WP w = Waypoints.list.get(i);
            int ry = ly + i * 16 * S, hh = 14 * S;
            RenderUtil.roundedRect(ctx, sx, ry, swid, hh, 4 * S, Theme.glassRow());
            RenderUtil.roundedRect(ctx, sx + 4 * S, ry + (hh - 8 * S) / 2, 8 * S, 8 * S, 2 * S, w.color);
            wpHits.add(new Object[]{ "color:" + i, sx + 2 * S, ry, 16 * S, hh });
            String t = w.name + "  " + (int) w.x + " " + (int) w.y + " " + (int) w.z;
            RenderUtil.textVCentered(ctx, this.textRenderer, t, sx + 18 * S, ry, hh, Theme.txt(), 0.4f * S);
            int dx = sx + swid - 14 * S;
            RenderUtil.textVCentered(ctx, this.textRenderer, "X", dx, ry, hh, 0xFFE05656, 0.45f * S);
            wpHits.add(new Object[]{ "del:" + i, dx - 3 * S, ry, 16 * S, hh });
        }
    }

    private void wpAdd() {
        double px, py, pz;
        boolean coords = !wpX.isEmpty() && !wpY.isEmpty() && !wpZ.isEmpty();
        if (coords) {
            try { px = Double.parseDouble(wpX); py = Double.parseDouble(wpY); pz = Double.parseDouble(wpZ); }
            catch (Exception e) { return; }
        } else if (this.client.player != null) {
            px = this.client.player.getX(); py = this.client.player.getY(); pz = this.client.player.getZ();
        } else return;
        String name = wpName.isEmpty() ? "WP" + (Waypoints.list.size() + 1) : wpName;
        Waypoints.add(name, px, py, pz, Waypoints.nextColor());
        wpName = wpX = wpY = wpZ = ""; focusedField = null;
        com.lume.client.Config.save();
    }

    private void handleWpHit(String kind) {
        if (kind.equals("add")) { wpAdd(); return; }
        if (kind.startsWith("del:")) {
            int i = Integer.parseInt(kind.substring(4));
            if (i >= 0 && i < Waypoints.list.size()) { Waypoints.list.remove(i); com.lume.client.Config.save(); }
            return;
        }
        if (kind.startsWith("color:")) {
            int i = Integer.parseInt(kind.substring(6));
            if (i >= 0 && i < Waypoints.list.size()) { Waypoints.list.get(i).color = Waypoints.nextColor(); com.lume.client.Config.save(); }
            return;
        }
        focusedField = kind;   // a text field
    }

    private void renderMode(DrawContext ctx, ModeSetting ms, int x, int y, int w, int h, int S) {
        RenderUtil.textVCentered(ctx, this.textRenderer, ms.name, x, y, h, Theme.txt(), 0.42f * S);
        String disp = "‹ " + ms.get() + " ›";
        int dw = RenderUtil.width(this.textRenderer, disp, 0.42f * S);
        RenderUtil.textVCentered(ctx, this.textRenderer, disp, x + w - dw, y, h, Theme.accent(), 0.42f * S);
        SHit hit = new SHit(); hit.s = ms; hit.kind = 4; hit.x = x; hit.y = y; hit.w = w; hit.h = h; sHits.add(hit);
    }

    private void renderBool(DrawContext ctx, BoolSetting bs, int x, int y, int w, int h, int S) {
        RenderUtil.textVCentered(ctx, this.textRenderer, bs.name, x, y, h, Theme.txt(), 0.42f * S);
        int pw = 18 * S, ph = 10 * S, px = x + w - pw, py = y + (h - ph) / 2;
        RenderUtil.roundedRect(ctx, px, py, pw, ph, ph / 2, bs.value ? Theme.accent() : Theme.pillOff());
        int kd = ph - 4 * S, kx = bs.value ? px + pw - kd - 2 * S : px + 2 * S;
        RenderUtil.roundedRect(ctx, kx, py + 2 * S, kd, kd, kd / 2, 0xFFFFFFFF);
        SHit hit = new SHit(); hit.s = bs; hit.kind = 0; hit.x = x; hit.y = y; hit.w = w; hit.h = h; sHits.add(hit);
    }

    private void renderSlider(DrawContext ctx, SliderSetting ss, int x, int y, int w, int h, int S) {
        RenderUtil.textVCentered(ctx, this.textRenderer, ss.name, x, y, 12 * S, Theme.txt(), 0.42f * S);
        String val = ss.display();
        int vw = RenderUtil.width(this.textRenderer, val, 0.42f * S);
        RenderUtil.textVCentered(ctx, this.textRenderer, val, x + w - vw, y, 12 * S, Theme.accent(), 0.42f * S);
        int ty = y + 14 * S, th = 4 * S;
        RenderUtil.roundedRect(ctx, x, ty, w, th, th / 2, Theme.pillOff());
        int fw = Math.round(w * (float) ss.fraction());
        if (fw > 0) RenderUtil.roundedRect(ctx, x, ty, Math.max(th, fw), th, th / 2, Theme.accent());
        int kd = 8 * S, kx = x + Math.round(w * (float) ss.fraction());
        RenderUtil.roundedRect(ctx, Math.min(x + w - kd, Math.max(x, kx - kd / 2)), ty + th / 2 - kd / 2, kd, kd, kd / 2, 0xFFFFFFFF);
        SHit hit = new SHit(); hit.s = ss; hit.kind = 1; hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.trackX = x; hit.trackW = w; sHits.add(hit);
    }

    private void renderColor(DrawContext ctx, ColorSetting cs, int x, int y, int w, int S) {
        int row = 15 * S;
        RenderUtil.textVCentered(ctx, this.textRenderer, cs.name, x, y, row, Theme.txt(), 0.42f * S);
        int pw = 18 * S, ph = 10 * S, px = x + w - pw, py = y + (row - ph) / 2;
        RenderUtil.roundedRect(ctx, px, py, pw, ph, ph / 2, cs.accent ? Theme.accent() : Theme.pillOff());
        int kd = ph - 4 * S, kx = cs.accent ? px + pw - kd - 2 * S : px + 2 * S;
        RenderUtil.roundedRect(ctx, kx, py + 2 * S, kd, kd, kd / 2, 0xFFFFFFFF);
        int swx = px - 16 * S, swatch = cs.accent ? Theme.accent() : (0xFF000000 | cs.rgb());
        RenderUtil.roundedRect(ctx, swx, py, 12 * S, ph, 3 * S, swatch);
        SHit at = new SHit(); at.s = cs; at.kind = 2; at.x = px; at.y = y; at.w = pw; at.h = row; sHits.add(at);

        if (!cs.accent) {
            int yy = y + row;
            channel(ctx, cs, 0, "R", 0xFFE05656, x, yy, w, S); yy += 14 * S;
            channel(ctx, cs, 1, "G", 0xFF6FCF7F, x, yy, w, S); yy += 14 * S;
            channel(ctx, cs, 2, "B", 0xFF6F9CE0, x, yy, w, S);
        }
    }

    private void channel(DrawContext ctx, ColorSetting cs, int idx, String label, int chCol, int x, int y, int w, int S) {
        int h = 14 * S;
        RenderUtil.textVCentered(ctx, this.textRenderer, label, x, y, h, Theme.txtDim(), 0.4f * S);
        int tx = x + 12 * S, tw = w - 12 * S, ty = y + (h - 4 * S) / 2, th = 4 * S;
        RenderUtil.roundedRect(ctx, tx, ty, tw, th, th / 2, Theme.pillOff());
        int val = idx == 0 ? cs.r : idx == 1 ? cs.g : cs.b;
        float frac = val / 255f;
        if (frac > 0) RenderUtil.roundedRect(ctx, tx, ty, Math.max(th, Math.round(tw * frac)), th, th / 2, chCol);
        int kd = 8 * S, kx = tx + Math.round(tw * frac);
        RenderUtil.roundedRect(ctx, Math.min(tx + tw - kd, Math.max(tx, kx - kd / 2)), ty + th / 2 - kd / 2, kd, kd, kd / 2, 0xFFFFFFFF);
        SHit hit = new SHit(); hit.s = cs; hit.kind = 3; hit.channel = idx; hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.trackX = tx; hit.trackW = tw; sHits.add(hit);
    }

    /** Small chevron that rotates from ▶ (collapsed) to ▼ (expanded) by {@code ex}. */
    private void chevron(DrawContext ctx, int cx, int cy, int size, float ex, int color) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90f * (1f - ex)));
        int wdt = size, hgt = Math.max(2, size / 2);
        for (int r = 0; r < hgt; r++) {
            float t = r / (float) hgt;
            int half = Math.round(wdt / 2f * (1f - t));
            ctx.fill(-half, -hgt / 2 + r, half, -hgt / 2 + r + 1, color);
        }
        m.pop();
    }

    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /**
     * enableScissor for a rectangle given in LOCAL native coords.
     *
     * <p>Since MC 1.21.4, {@code DrawContext.enableScissor} transforms the rect by
     * the <b>current matrix</b> (matrices.peek().getPositionMatrix()). All scissor
     * calls here happen while the window's native-res matrix (scale 1/S + move +
     * scale) is active, so we pass the native coords straight through and let that
     * matrix map them to screen space — exactly where the content is drawn. (In
     * 1.21.1 enableScissor did NOT transform, which is why this used to map by hand.)
     */
    private void winScissor(DrawContext ctx, int nx1, int ny1, int nx2, int ny2) {
        ctx.enableScissor(nx1, ny1, nx2, ny2);
    }

    /** Local (window-space) mouse X, undoing the window move/scale transform.
     *  Uses {@link #curTotal} (anim × winScale × fit) so clicks match what's drawn. */
    private double localMx(double mouseX) {
        int S = sf();
        double cx = this.width * S / 2.0;
        return (mouseX * S - winOffX * S - cx) / curTotal + cx;
    }
    private double localMy(double mouseY) {
        int S = sf();
        double cy = this.height * S / 2.0;
        return (mouseY * S - winOffY * S - cy) / curTotal + cy;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int S = sf();
            double mlx = localMx(mouseX), mly = localMy(mouseY);
            int W = WIN_W * S, H = WIN_H * S;
            int wx = (this.width * S - W) / 2, wy = (this.height * S - H) / 2;
            boolean inWindow = mlx >= wx && mlx <= wx + W && mly >= wy && mly <= wy + H;
            focusedField = null;   // any click defocuses; specific handlers below re-focus

            // search box → focus it
            if (inWindow && inside(mlx, mly, searchBox[0], searchBox[1], searchBox[2], searchBox[3])) {
                focusedField = "search";
                return true;
            }

            // resize grip (bottom-right)
            if (inside(mlx, mly, wx + W - 14 * S, wy + H - 14 * S, 14 * S, 14 * S)) {
                dragMode = 2; grabMx = mouseX; grabMy = mouseY; grabScale = winScale; return true;
            }

            if (inWindow) {
                if (inside(mlx, mly, themeBtn[0], themeBtn[1], themeBtn[2], themeBtn[3])) { Theme.toggle(); animFor("_theme")[2] = 1f; return true; }
                for (int i = 0; i < segX.length; i++) {
                    if (inside(mlx, mly, segX[i], segY - 3 * S, segW[i], segH + 6 * S)) { selectedCat = i; search = ""; scroll = scrollTarget = 0f; bindingModule = null; bindingSetting = null; bindingQuickCmd = null; return true; }
                }
                if (isBindsTab()) {
                    if (mly >= lastClipTop && mly <= lastClipBot) {
                        for (Object[] h : bindHits) {
                            Module m = (Module) h[0];
                            int modeX = (int) h[5], modeW = (int) h[6], chipY = (int) h[7];
                            if (inside(mlx, mly, modeX, chipY, modeW, 15 * S)) {   // mode chip → switch HOLD/TOGGLE
                                m.setBindMode(m.getBindMode() == Module.BindMode.HOLD ? Module.BindMode.TOGGLE : Module.BindMode.HOLD);
                                com.lume.client.Config.save();
                                return true;
                            }
                            if (inside(mlx, mly, (int) h[1], (int) h[2], (int) h[3], (int) h[4])) {
                                bindingModule = (bindingModule == m) ? null : m;   // click again to cancel
                                return true;
                            }
                        }
                        bindingModule = null;
                        return true;
                    }
                    // header area → fall through to window drag
                }
                if (isServerTab()) {
                    if (mly >= lastClipTop && mly <= lastClipBot) {
                        for (Object[] h : serverHits) {
                            if (!inside(mlx, mly, (int) h[1], (int) h[2], (int) h[3], (int) h[4])) continue;
                            String kind = (String) h[0];
                            switch (kind) {
                                case "master" -> {
                                    Module shm = LumeClient.MODULES.getByName("Server Helper");
                                    if (shm != null) { shm.toggle(); com.lume.client.Config.save(); }
                                }
                                case "subToggle" -> { BoolSetting bs = (BoolSetting) h[5]; bs.value = !bs.value; com.lume.client.Config.save(); }
                                case "subBind" -> { BoolSetting bs = (BoolSetting) h[5]; bindingSetting = (bindingSetting == bs) ? null : bs; }
                                case "present" -> { ItemRule it = (ItemRule) h[5]; it.present = !it.present; com.lume.client.Config.save(); }
                                case "connect:funtime" -> fastConnect("FunTime", "mc.funtime.su");
                                case "connect:holyworld" -> fastConnect("HolyWorld", "mc.holyworld.ru");
                                case "qcmdSend" -> com.lume.client.fthw.QuickCommands.send((com.lume.client.fthw.QuickCommands.Cmd) h[5]);
                                case "qcmdBind" -> { var qc = (com.lume.client.fthw.QuickCommands.Cmd) h[5]; bindingQuickCmd = (bindingQuickCmd == qc) ? null : qc; }
                            }
                            return true;
                        }
                        bindingSetting = null;
                        return true;   // consume content clicks
                    }
                    // header → fall through to window drag
                }
                if (mly >= lastClipTop && mly <= lastClipBot) {
                    for (Object[] h : wpHits) {
                        if (inside(mlx, mly, (int) h[1], (int) h[2], (int) h[3], (int) h[4])) { handleWpHit((String) h[0]); return true; }
                    }
                    for (SHit h : sHits) {
                        if (inside(mlx, mly, h.x, h.y, h.w, h.h)) { handleSettingClick(h, mlx); return true; }
                    }
                    for (CHit c : cHits) {
                        if (c.hasArrow && inside(mlx, mly, c.ax, c.ay, c.aw, c.ah)) { toggleExpanded(c.m); return true; }
                        if (inside(mlx, mly, c.hx, c.hy, c.hw, c.hh)) {
                            if (c.m.isToggleable()) { c.m.toggle(); animFor(c.m.getName())[2] = 1f; }
                            else toggleExpanded(c.m);   // settings-only card → expand
                            return true;
                        }
                    }
                }
                // header area (top row, not on a control) → drag the window
                if (mly <= wy + 42 * S) { dragMode = 1; grabMx = mouseX; grabMy = mouseY; grabA = winOffX; grabB = winOffY; return true; }
                return true; // consume other clicks inside the window
            }

            // outside the window → HUD editor

            // 1) size slider of the selected element
            if (selectedHud != null && hudSliderTrack != null
                    && inside(mouseX, mouseY, hudSliderTrack[0], hudSliderTrack[1], hudSliderTrack[2], hudSliderTrack[3] + 4)) {
                dragMode = 5;
                updateHudSize(mouseX);
                return true;
            }

            // 2) a HUD element frame: select + (double-click resets) + start move
            List<String> names = new ArrayList<>();
            List<int[]> rects = hudFrames(this.width, this.height, names);
            for (int i = rects.size() - 1; i >= 0; i--) {
                int[] rc = rects.get(i);
                if (mouseX >= rc[0] && mouseX <= rc[0] + rc[2] && mouseY >= rc[1] && mouseY <= rc[1] + rc[3]) {
                    String name = names.get(i);
                    long now = System.currentTimeMillis();
                    if (name.equals(lastFrameClickName) && now - lastFrameClickT < 350) {
                        HudLayout.reset(name);            // double-click → reset position + size
                        lastFrameClickT = 0;
                        return true;
                    }
                    lastFrameClickT = now;
                    lastFrameClickName = name;
                    selectedHud = name;
                    dragMode = 3; dragHud = name;
                    int[] off = HudLayout.get(name);
                    grabA = off[0]; grabB = off[1]; grabMx = mouseX; grabMy = mouseY;
                    return true;
                }
            }
            selectedHud = null;   // clicked empty space → deselect
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateHudSize(double mouseX) {
        if (selectedHud == null || hudSliderTrack == null) return;
        double frac = (mouseX - hudSliderTrack[0]) / (double) hudSliderTrack[2];
        frac = Math.max(0, Math.min(1, frac));
        HudLayout.setScale(selectedHud, (float) (0.5 + frac * 1.5));
    }

    private void handleSettingClick(SHit h, double mxNative) {
        switch (h.kind) {
            case 0 -> ((BoolSetting) h.s).value = !((BoolSetting) h.s).value;
            case 2 -> ((ColorSetting) h.s).accent = !((ColorSetting) h.s).accent;
            case 1, 3 -> { activeSlider = h; updateSlider(h, mxNative); }
            case 4 -> ((ModeSetting) h.s).cycle(mxNative < h.x + h.w / 2.0 ? -1 : 1);
        }
    }

    private void updateSlider(SHit h, double mxNative) {
        double frac = h.trackW > 0 ? (mxNative - h.trackX) / h.trackW : 0;
        frac = Math.max(0, Math.min(1, frac));
        if (h.kind == 1) {
            ((SliderSetting) h.s).setFraction(frac);
        } else {
            ColorSetting cs = (ColorSetting) h.s;
            int v = (int) Math.round(frac * 255);
            if (h.channel == 0) cs.r = v; else if (h.channel == 1) cs.g = v; else cs.b = v;
        }
    }

    private void toggleExpanded(Module m) {
        String k = m.getName();
        if (!expanded.remove(k)) expanded.add(k);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0) {
            switch (dragMode) {
                case 1 -> { winOffX = grabA + (int) Math.round(mouseX - grabMx); winOffY = grabB + (int) Math.round(mouseY - grabMy); return true; }
                case 2 -> { winScale = Math.max(0.6f, Math.min(1.8f, grabScale + (float) ((mouseY - grabMy) * 0.006))); return true; }
                case 3 -> { if (dragHud != null) HudLayout.set(dragHud, grabA + (int) Math.round(mouseX - grabMx), grabB + (int) Math.round(mouseY - grabMy)); return true; }
                case 5 -> { updateHudSize(mouseX); return true; }
            }
            if (activeSlider != null) { updateSlider(activeSlider, localMx(mouseX)); return true; }
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) { dragMode = 0; dragHud = null; activeSlider = null; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * 30 * sf();
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (bindingModule != null || bindingSetting != null || bindingQuickCmd != null) return true;   // consume while capturing a bind
        if (!(chr >= 32 && chr != 127)) return super.charTyped(chr, modifiers);
        if ("search".equals(focusedField)) { search += chr; scroll = scrollTarget = 0f; return true; }
        if (focusedField != null) {                         // waypoint fields
            boolean numeric = !focusedField.equals("name");
            if (!numeric || (chr >= '0' && chr <= '9') || chr == '-' || chr == '.')
                bufSet(focusedField, bufGet(focusedField) + chr);
            return true;
        }
        return super.charTyped(chr, modifiers);             // nothing focused → don't capture
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // capturing a module bind on the Binds tab
        if (bindingModule != null) {
            bindingModule.setKey(keyCode == 256 ? -1 : keyCode);   // Esc = unbind
            bindingModule = null;
            com.lume.client.Config.save();
            return true;
        }
        // capturing a sub-function bind on the Server tab
        if (bindingSetting != null) {
            bindingSetting.key = keyCode == 256 ? -1 : keyCode;    // Esc = unbind
            bindingSetting = null;
            com.lume.client.Config.save();
            return true;
        }
        // capturing a quick-command bind on the Server tab
        if (bindingQuickCmd != null) {
            bindingQuickCmd.key = keyCode == 256 ? -1 : keyCode;
            bindingQuickCmd = null;
            com.lume.client.Config.save();
            return true;
        }
        if ("search".equals(focusedField)) {
            if (keyCode == 256) { focusedField = null; return true; }                                   // Esc
            if (keyCode == 259 && !search.isEmpty()) { search = search.substring(0, search.length() - 1); scroll = scrollTarget = 0f; }
            return true;
        }
        if (focusedField != null) {                         // waypoint fields
            if (keyCode == 256) { focusedField = null; return true; }
            if (keyCode == 257 || keyCode == 335) { wpAdd(); return true; }
            if (keyCode == 259) { String c = bufGet(focusedField); if (!c.isEmpty()) bufSet(focusedField, c.substring(0, c.length() - 1)); return true; }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}

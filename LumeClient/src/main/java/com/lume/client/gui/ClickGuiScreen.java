package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.fthw.EventManager;
import com.lume.client.fthw.ItemRule;
import com.lume.client.fthw.ItemRules;
import com.lume.client.nanovg.GlassRenderer;
import com.lume.client.nanovg.NanoVgRenderer;
import com.lume.client.nanovg.SdfRenderer;
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
import com.lume.client.module.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
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

    // Category.values() clones its backing array on every call — this screen calls it ~30+
    // times per rendered frame (tab bar layout/draw, isBindsTab/isServerTab, module filtering),
    // so caching the one immutable array once avoids that many small allocations every frame
    // the menu is open. Never mutate this array.
    private static final Category[] CATS = Category.values();

    private final long openTime = System.currentTimeMillis();
    private static int selectedCat = 0;   // persists across menu open/close
    private String search = "";

    private int scale = 1;
    private int[] segX = new int[0];
    private int[] segW = new int[0];
    private int segY = 0, segH = 0;
    private int[] themeBtn = new int[]{0, 0, 0, 0};
    private int[] colorsBtn = new int[]{0, 0, 0, 0};

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

    // BindPopup is DrawContext-based (see task: NanoVG removal) but this window's content is
    // still drawn through NanoVG's own pan/zoom transform — renderKeybindManagerButtonsNvg only
    // has the window-LOCAL anchor available, so it stashes that here and the actual ctx-based
    // render call happens after the NanoVG frame closes (see renderNvgMain), converted to real
    // screen coordinates via the same window-local -> framebuffer formula used for the panel
    // rect itself (gsx0/gsy0 below). Deliberately NOT zoom-scaled with the window (a small utility
    // popup staying a fixed size regardless of window zoom reads fine, like an OS tooltip).
    private boolean bindPopupPending = false;
    private int bindPopupLocalX, bindPopupLocalY;

    // Colour picker: which ColorSetting's palette is open, HSV state + hex buffer.
    private ColorSetting openColor = null;
    private String colorHex = "";
    private float pickH = 0f, pickS = 0f, pickV = 1f;   // current hue/sat/value while a picker is open
    private SHit activePicker = null;                    // SV square / hue bar being dragged
    private float catPillX = -1f, catPillW = 0f;         // animated accent pill for the category selector
    private static final int PAL_H = 80;                 // palette panel height (GUI px)

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
    private int[] hudResizeHandle = null;           // {x,y,w,h} GUI px corner-drag handle ("HUD" panel only — window-style resize)
    private int[] hudResizeBase = null;             // {w,h} the panel's size at drag start
    private int[] hudCardRect = null;               // {x,y,w,h} native px — last drawn rect of the "HUD" module card
    private long hudWarnUntil = 0L;                 // >now → show the "4+ lines" red warning near the HUD card
    private long lastFrameClickT = 0;
    private String lastFrameClickName = null;

    // Binds tab
    private Module bindingModule = null;             // module currently capturing a key
    private final List<Object[]> bindHits = new ArrayList<>();   // {Module, x, y, w, h}

    // KeybindManager "Bind command" button — true while waiting for the next keypress, which
    // opens KeybindManagerScreen with that key pre-selected for a new command bind.
    private boolean bindingMacroKey = false;

    // GUI text fields (Waypoints manager + search). focusedField: "search"/"name"/"coords"/"customstring"/null
    private String focusedField = null;
    private StringSetting focusedString = null;   // which StringSetting "customstring" refers to
    private int[] searchBox = new int[]{0, 0, 0, 0};
    private String wpName = "", wpCoords = "";   // wpCoords: free-form "x y z" (space or comma separated)
    private final List<Object[]> wpHits = new ArrayList<>();      // {String kind, int x, y, w, h}

    private int[] serverToggle = new int[]{0, 0, 0, 0};

    // Top nav bar (Menu / Events / Config / Friends)
    private static int topSection = 0;   // 0=Menu 1=Events 2=Config 3=Friends
    private int[] topNavSegX = new int[4];
    private int[] topNavSegW = new int[4];
    private int topNavSegY = 0, topNavSegH = 0;
    private final List<Object[]> configHits = new ArrayList<>();  // {kind, name, x,y,w,h}

    // Server tab: scrollable encyclopedia + sub-function binds
    private BoolSetting bindingSetting = null;                    // sub-function capturing a key
    private com.lume.client.fthw.QuickCommands.Cmd bindingQuickCmd = null;  // quick-command capturing a key
    private final List<Object[]> serverHits = new ArrayList<>();  // {String kind, int x, y, w, h, Object ref}
    private int serverContentH = 0;

    private boolean isBindsTab()   { return search.isEmpty() && selectedCat == CATS.length; }
    private boolean isServerTab()  { return search.isEmpty() && selectedCat == CATS.length + 1; }
    private boolean isEventsTab()  { return search.isEmpty() && topSection == 1; }
    private boolean isConfigTab()  { return search.isEmpty() && topSection == 2; }
    private boolean isFriendsTab() { return search.isEmpty() && topSection == 3; }

    private String tabTitle(int i) {
        Category[] c = CATS;
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
    /** Factory reset — window centred at scale 1, light theme (matches a fresh install). */
    public static void resetToDefaults() {
        winOffX = 0; winOffY = 0; winScale = 1f;
        Theme.setDark(false);
        com.lume.client.Config.save();
    }

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
        String tag;   // free-form payload for kinds that don't map to a Setting (e.g. "My Sounds" filename)
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
    private void textBold(DrawContext ctx, String s, int x, int y, int color, float vis) {
        RenderUtil.textBold(ctx, this.textRenderer, s, x, y, color, vis * scale);
    }
    private int width(String s, float vis) { return RenderUtil.width(this.textRenderer, s, vis * scale); }

    private void glass(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int rimW) {
        RenderUtil.roundedRect(ctx, x, y, w, h, r, Theme.rim());
        RenderUtil.roundedRect(ctx, x + rimW, y + rimW, w - 2 * rimW, h - 2 * rimW, Math.max(2, r - rimW), fill);
    }

    /** SDF version of {@link #glass} — fill + thin rim outline in one shader draw instead of two
     *  stacked CPU rects, for crisp edges. Must be called from inside the window's own pushed
     *  matrix (uses the same winOffX/winOffY/cx/cy/total every other SDF call in this render()
     *  tree uses). */
    private void sdfGlass(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int S,
                           double cx, double cy, float total) {
        ctx.draw();
        SdfRenderer.boxWindowLocal(winOffX * S, winOffY * S, cx, cy, total, x, y, w, h, r,
                fill, Theme.rim(), Math.max(1f, S), 0, 0f);
    }

    /** Premium icon button background: SDF fill (matches ThemeIcons' own flat-square shape/colour
     *  exactly, so the two layers merge seamlessly) + a contour-hugging glow, with a small upward
     *  lift on hover (position only — no width/height stretch), then the actual glyph on top via
     *  the existing {@link ThemeIcons} legacy helpers (moon/sun/dots), drawn at the same lifted y
     *  so the icon visually moves with its button. */
    private void drawPremiumIconBtn(DrawContext ctx, int x, int y, int w, int h, float hoverAmt, int S,
                                     int winOffX, int winOffY, double cx, double cy, float total, boolean isTheme) {
        int lift = Math.round(hoverAmt * 2f) * S;
        int by = y - lift;
        int bg = Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), hoverAmt);
        int glowA = Math.round(hoverAmt * 140f);
        int glow = (glowA << 24) | (Theme.accentRgb() & 0xFFFFFF);
        ctx.draw();
        SdfRenderer.boxWindowLocal(winOffX * S, winOffY * S, cx, cy, total, x, by, w, h, w / 3,
                bg, 0, 0f, glow, (3f + hoverAmt * 6f) * S);
        if (isTheme) ThemeIcons.drawThemeLegacy(ctx, x, by, w, bg);
        else ThemeIcons.drawColorsLegacy(ctx, x, by, w, bg);
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
                if (m.getName().toLowerCase().contains(q)) out.add(m);
        } else if (selectedCat < CATS.length) {
            for (Module m : LumeClient.MODULES.getModules(CATS[selectedCat]))
                if (!m.getName().equals("Server Helper") && !m.getName().equals("Free Look")
                        && !m.getName().equals("Discord Rich Presence")) out.add(m);   // bind-only / always-on, no menu card
        }
        return out;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, this.width, this.height, Theme.backdrop());
        // Movable HUD elements render for real (crisp, full brightness) here, on TOP of the dark
        // backdrop that would otherwise dim them — before, the editor only showed a flat labelled
        // placeholder box over the dimmed real HUD; now you drag the actual thing, not a stand-in.
        HudRenderer.render(ctx);

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

        // Top nav bar (Menu / Events / Config / Friends) — mirrors renderNvgMain's own
        // drawTopNavNvg call; "Menu" (this screen) is always the active tab here.
        int activeTopTab = search.isEmpty() ? topSection : -1;
        int[] navYH = NavBar.drawLegacy(ctx, x, y, W, S, activeTopTab, dt, topNavSegX, topNavSegW,
                winOffX * S, winOffY * S, cx, cy, total);
        topNavSegY = navYH[0]; topNavSegH = navYH[1];

        // Panel — pixel-perfect SDF rounded rect (raw GL, see SdfRenderer) instead of RenderUtil's
        // CPU-coverage approximation. Flat minimalist chrome: flat fill + thin rim, no glow/bloom.
        // ctx.draw() flushes everything queued so far (backdrop fill, HUD frames) before this raw
        // GL write, same ordering discipline GlassRenderer's own calls already need.
        ctx.draw();
        SdfRenderer.boxWindowLocal(winOffX * S, winOffY * S, cx, cy, total, x, y, W, H, r,
                Theme.winBg(), Theme.rim(), 1.5f * S, 0, 0f);
        RenderUtil.roundedRect(ctx, x + 16 * S, y + 2 * S, W - 32 * S, Math.max(1, S), 1 * S, Theme.border());

        // Header: centred wordmark + a SEPARATE logo mark pinned to the top-left corner — matches
        // the NanoVG header's layout exactly (Wordmark.drawCentered + a standalone corner
        // nvgLogo() call there). A previous pass here accidentally glued the two into one
        // left-aligned lockup, and the wordmark's scale (0.6, unscaled by S) rendered smaller
        // than intended at any GUI scale above 100% — RenderUtil.text's scale, like every other
        // size/position argument in this whole render() tree, needs *S to match the 1/S the
        // outer mtx.scale(1f/S,...) divides everything back down by at the very end.
        RenderUtil.drawLogo(ctx, x + 16 * S, y + 12 * S, 22 * S);
        // Baked wordmark (see gen_menu.py's wordmark.png) instead of live vanilla-font text —
        // matches the reference's exact Montserrat styling, same asset LumeTitleMenu's main-menu
        // logo lockup uses, just the text-only half (this header keeps its own separate corner
        // star mark above, unlike the main menu's combined star+text lockup).
        int wmW = 130, wmH = wmW * 30 / 220;
        com.lume.client.menu.MenuAssets.blit(ctx, com.lume.client.menu.MenuAssets.WORDMARK,
                x + (W - wmW * S) / 2, y + 12 * S + (22 * S - wmH * S) / 2, wmW * S, wmH * S);

        // Theme + Colors icon buttons (top-right) — same icon glyphs as the NanoVG header
        // (ThemeIcons.drawThemeLegacy/drawColorsLegacy, already built for this), same layout
        // LumeSubScreen's header uses so every catalog matches. Premium hover: small upward
        // lift (no width/height stretch) + a contour-hugging glow via the SDF shader.
        int tbw = 22 * S, tbh = 22 * S, tbx = x + W - tbw - 20 * S, tby = y + 14 * S;
        boolean tbHov = inside(mx, my, tbx, tby, tbw, tbh);
        float[] ta = animFor("_theme");
        ta[0] = approach(ta[0], tbHov ? 1f : 0f, 12f, dt);

        int cbw = 22 * S, cbh = 22 * S, cbx = tbx - cbw - 6 * S, cby = tby;
        boolean cbHov = inside(mx, my, cbx, cby, cbw, cbh);
        float[] ca0 = animFor("_colors");
        ca0[0] = approach(ca0[0], cbHov ? 1f : 0f, 12f, dt);

        drawPremiumIconBtn(ctx, tbx, tby, tbw, tbh, ta[0], S, winOffX, winOffY, cx, cy, total, true);
        drawPremiumIconBtn(ctx, cbx, cby, cbw, cbh, ca0[0], S, winOffX, winOffY, cx, cy, total, false);
        themeBtn = new int[]{ tbx, tby, tbw, tbh };
        colorsBtn = new int[]{ cbx, cby, cbw, cbh };

        // Search (full width) — click to focus; vanilla font so any text renders
        int sx = x + 20 * S, sy = y + 46 * S, swid = W - 40 * S, shei = 26 * S;
        searchBox = new int[]{ sx, sy, swid, shei };
        boolean searchFocused = "search".equals(focusedField);
        sdfGlass(ctx, sx, sy, swid, shei, 10 * S, searchFocused ? Theme.glassHov() : Theme.glassRow(), S, cx, cy, total);
        if (searchFocused) RenderUtil.roundedRect(ctx, sx, sy + shei - Math.max(1, S), swid, Math.max(1, S), 1, Theme.accent());
        boolean empty = search.isEmpty() && !searchFocused;
        String shown = empty ? "Search modules…" : search + (searchFocused ? "_" : "");
        RenderUtil.vanillaText(ctx, this.textRenderer, shown, sx + 12 * S, sy + (shei - 8 * S) / 2.0, empty ? Theme.txtDim() : Theme.txt(), S);

        // Category segmented pill (categories + Binds + Server)
        int tabs = CATS.length + 2;
        segX = new int[tabs];
        segW = new int[tabs];
        segH = 26 * S;
        segY = y + 82 * S;
        int padSeg = 11 * S;
        int segTotal = 0;
        int[] ww = new int[tabs];
        for (int i = 0; i < tabs; i++) { ww[i] = width(tabTitle(i), 0.5f) + padSeg * 2; segTotal += ww[i]; }
        int barX = x + (W - segTotal) / 2;
        sdfGlass(ctx, barX - 4 * S, segY - 3 * S, segTotal + 8 * S, segH + 6 * S, 13 * S, Theme.glassRow(), S, cx, cy, total);
        int cx2 = barX;
        for (int i = 0; i < tabs; i++) {
            segX[i] = cx2; segW[i] = ww[i];
            boolean sel = i == selectedCat && search.isEmpty();
            if (sel) {
                // Active tab: SDF outline + glow, fill matches the background (no separate
                // "lighter panel" look — the glow is what makes it read as selected).
                ctx.draw();
                SdfRenderer.boxWindowLocal(winOffX * S, winOffY * S, cx, cy, total, cx2, segY, ww[i], segH, 12 * S,
                        Theme.winBg(), Theme.accent(), 1.5f * S, Theme.accent(), 6f * S);
            }
            int tw = width(tabTitle(i), 0.5f);
            textBold(ctx, tabTitle(i), cx2 + (ww[i] - tw) / 2, segY + 8 * S, sel ? Theme.activeText() : Theme.txtDim(), 0.5f);
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
            renderBindPopupIfPending(ctx, cx, cy, total, S, mouseX, mouseY, dt);
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
            renderBindPopupIfPending(ctx, cx, cy, total, S, mouseX, mouseY, dt);
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
            float ha = ca[0], ea = ca[1], pa = ca[2], ex = ca[3];

            // Premium hover: position-only lift (no width/height stretch, per the "без
            // растягиваний" ask) + an SDF background whose contour-hugging glow grows with
            // hover — same lift/glow curve RenderUtil.premiumBg uses, inlined here since this
            // file's coordinates are native px, not DrawContext-logical (calling premiumBg
            // directly would double-apply the S factor).
            int liftS = Math.round(ha * 2f) * S;
            int dx = rx, dy = cy0 - liftS, dw = cardW, dh = ch;

            RenderUtil.roundedRect(ctx, dx + 1 * S, dy + 2 * S, dw, dh, 11 * S, Theme.shadow());

            int base = Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ha);
            int onFill = withAlpha(Theme.accentRgb(), Theme.isDark() ? 0x4D : 0x40);
            int fill = Theme.colorLerp(base, onFill, ea);
            int glowA = Math.round(ha * 140f);
            int glow = (glowA << 24) | (Theme.accentRgb() & 0xFFFFFF);
            ctx.draw();
            SdfRenderer.boxWindowLocal(winOffX * S, winOffY * S, cx, cy, total, dx, dy, dw, dh, 11 * S,
                    fill, Theme.rim(), Math.max(1f, S), glow, (3f + ha * 6f) * S);
            RenderUtil.roundedRect(ctx, dx + 8 * S, dy + 1 * S, dw - 16 * S, Math.max(1, S), 1 * S, Theme.border());

            if (pa > 0.01f) RenderUtil.roundedRect(ctx, dx, dy, dw, headerH, 11 * S, withAlpha(0xFFFFFF, Math.round(pa * 55)));

            // centred name (leave room on the right for the settings arrow) — bold, per the
            // "шрифт потолще" ask (also thickens the Cyrillic vanilla-font fallback).
            int col2 = Theme.colorLerp(Theme.txt(), Theme.activeText(), ea);
            int nameRightPad = m.hasSettings() ? 20 * S : 0;
            RenderUtil.textBoldCentered(ctx, this.textRenderer, m.getName(), dx, dy, dw - nameRightPad, headerH, col2, 0.52f * scale);

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
                renderSettings(ctx, m, dx, cy0 + headerH, dw, S, mx, my, dt);
                ctx.disableScissor();
            }
        }
        ctx.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbW = 3 * S;
            int sbX = x + W - margin / 2 - sbW;
            ctx.draw();
            SdfRenderer.boxWindowLocal(winOffX * S, winOffY * S, cx, cy, total, sbX, gy, sbW, visH, sbW, Theme.glassRow(), 0, 0f, 0, 0f);
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) contentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / maxScroll));
            ctx.draw();
            SdfRenderer.boxWindowLocal(winOffX * S, winOffY * S, cx, cy, total, sbX, thumbY, sbW, thumbH, sbW, Theme.accent(), 0, 0f, 0, 0f);
        }

        // Resize grip (bottom-right corner) — drag to scale the whole window
        int gripX = x + W - 14 * S, gripY = y + H - 14 * S;
        boolean gripHov = inside(mx, my, gripX, gripY, 14 * S, 14 * S);
        for (int i = 0; i < 3; i++) {
            int o = (3 - i) * 3 * S;
            RenderUtil.roundedRect(ctx, x + W - o - 2 * S, y + H - 5 * S, o, 2 * S, S, gripHov ? Theme.accent() : Theme.txtDim());
        }

        mtx.pop();
        renderBindPopupIfPending(ctx, cx, cy, total, S, mouseX, mouseY, dt);
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
        // Open transition used to zoom the window in from 96% — a visible size change, not
        // wanted. Window is now always at its real size; a brief blur-dissolve (see the
        // transitionOverlay call after this frame's NanoVG draw) carries the "just opened"
        // feel instead.
        float total = winScale * fitScale();
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

        // Window-local [x,y,W,H] → real on-screen framebuffer rect, for the raw-GL glass
        // passes below (both need this outside any NanoVG frame — NanoVG's own transform
        // only affects draws made through it).
        double gsx0 = winOffX * S + cx + total * (x - cx), gsy0 = winOffY * S + cy + total * (y - cy);
        double gsx1 = winOffX * S + cx + total * (x + W - cx), gsy1 = winOffY * S + cy + total * (y + H - cy);
        final int panelSx = (int) Math.round(gsx0), panelSy = (int) Math.round(gsy0);
        final int panelSw = (int) Math.round(gsx1 - gsx0), panelSh = (int) Math.round(gsy1 - gsy0);
        final float panelSr = r * total;

        // Premium Glass backdrop — real captured+blurred+refracted world/HUD behind the
        // panel, sampled BEFORE the panel exists (raw GL, must happen outside any NanoVG
        // frame). The translucent gradient fill drawn just below then tints it, same as
        // it always tinted whatever was there before.
        if (Theme.getGlassStyle() == 1) {
            GlassRenderer.panel(panelSx, panelSy, panelSw, panelSh, panelSr, Theme.getGlassBlur(), Theme.getGlassDistort());
        }

        ctx.draw();   // flush DrawContext's own queued geometry (HudRenderer.render() etc.) before raw-GL NanoVG draws
        NanoVgRenderer.frame(vg -> {
            NanoVgRenderer.translate(vg, winOffX * S, winOffY * S);
            NanoVgRenderer.translate(vg, (float) cx, (float) cy);
            NanoVgRenderer.scale(vg, total, total);
            NanoVgRenderer.translate(vg, (float) -cx, (float) -cy);

            // ---- top nav bar (above the glass panel, in window-local coords) ----
            drawTopNavNvg(vg, x, y, W, S, mxF, myF, dt);

            // panel: soft dark shadow + faint accent glow + gradient glass + rim
            NanoVgRenderer.shadow(vg, x, y, W, H, r, 22 * S, 0x70000000);
            NanoVgRenderer.shadow(vg, x, y, W, H, r, 30 * S, withAlpha(Theme.accentRgb(), 0x33));
            NanoVgRenderer.gradientRoundedRect(vg, x, y, W, H, r, Theme.winTop(), Theme.winBot());
            NanoVgRenderer.strokeRoundedRect(vg, x + 0.75f * S, y + 0.75f * S, W - 1.5f * S, H - 1.5f * S, r, 1.2f * S, Theme.rim());

            // header: centred wordmark at the top (logo removed)
            Wordmark.drawCentered(vg, x + W / 2f, y + 20 * S, 15 * S, 255);

            // logo mark — top-left corner
            int logoS = 22 * S;
            float logoX = x + 16 * S, logoY = y + 12 * S;
            NanoVgRenderer.bloom(vg, logoX, logoY, logoS, logoS, 6 * S, 8 * S, Theme.accentRgb(), 0x66);
            nvgLogo(vg, logoX, logoY, logoS);

            // Theme toggle (top-right) + Customize Colors — small square icon buttons,
            // same shape/animation the launcher uses everywhere theme switching appears.
            // Drawing itself lives in ThemeIcons so ClickGUI, the sub-screens, and the
            // title-screen popover can never visually drift from each other.
            int tbw = 22 * S, tbh = 22 * S, tby = y + 14 * S;
            int tbx = x + W - tbw - 20 * S;
            boolean tbHov = inside(mxF, myF, tbx, tby, tbw, tbh);
            float[] ta = animFor("_theme");
            ta[0] = approach(ta[0], tbHov ? 1f : 0f, 12f, dt);

            int cbw = 22 * S, cbh = 22 * S, cbx = tbx - cbw - 6 * S, cby = tby;
            boolean cbHov = inside(mxF, myF, cbx, cby, cbw, cbh);
            float[] ca2 = animFor("_colors");
            ca2[0] = approach(ca2[0], cbHov ? 1f : 0f, 12f, dt);

            ThemeIcons.drawTheme(vg, tbx, tby, S, ta[0]);
            ThemeIcons.drawColors(vg, cbx, cby, S, ca2[0]);
            themeBtn = new int[]{ tbx, tby, tbw, tbh };
            colorsBtn = new int[]{ cbx, cby, cbw, cbh };

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
            int tabs = CATS.length + 2;
            segX = new int[tabs]; segW = new int[tabs];
            segH = 26 * S; segY = y + 82 * S;
            float tFont = 10 * S; int padSeg = 10 * S;
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
            for (int i = 0; i < tabs; i++) { segX[i] = cx2; segW[i] = ww[i]; cx2 += ww[i]; }
            // animated accent pill — the purple fill slides to the selected category
            if (search.isEmpty() && selectedCat >= 0 && selectedCat < tabs) {
                float tx = segX[selectedCat], tw = segW[selectedCat];
                if (catPillX < 0) { catPillX = tx; catPillW = tw; }   // first frame: snap into place
                catPillX = approach(catPillX, tx, 18f, dt);
                catPillW = approach(catPillW, tw, 18f, dt);
                NanoVgRenderer.bloom(vg, catPillX, segY, catPillW, segH, 12 * S, 11 * S, Theme.accentRgb(), 0x99);
                NanoVgRenderer.gradientRoundedRect(vg, catPillX, segY, catPillW, segH, 12 * S, Theme.accent(), Theme.accent2());
            }
            for (int i = 0; i < tabs; i++) {
                boolean sel = i == selectedCat && search.isEmpty();
                NanoVgRenderer.text(vg, segX[i] + segW[i] / 2f, segY + segH / 2f, tFont, sel ? Theme.activeText() : Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, tabTitle(i));
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
                if (m.getName().equals("HUD")) hudCardRect = new int[]{ dx, dy, dw, dh };

                NanoVgRenderer.shadow(vg, dx, dy + S, dw, dh, 11 * S, 8 * S, 0x55000000);
                if (ea > 0.01f) NanoVgRenderer.bloom(vg, dx, dy, dw, dh, 11 * S, (13 + 8 * ea) * S, Theme.accentRgb(), Math.round(0x88 * ea));
                int base = Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ha);
                int onFill = withAlpha(Theme.accentRgb(), Theme.isDark() ? 0x4D : 0x40);
                int fill = Theme.colorLerp(base, onFill, ea);
                NanoVgRenderer.roundedRect(vg, dx, dy, dw, dh, 11 * S, fill);
                NanoVgRenderer.strokeRoundedRect(vg, dx + 0.5f * S, dy + 0.5f * S, dw - S, dh - S, 11 * S, S, withAlpha(0xFFFFFF, Math.round(0x30 + 0x40 * ha)));

                int nameCol = Theme.colorLerp(Theme.txt(), Theme.activeText(), ea);
                int namePad = m.hasSettings() ? 20 * S : 0;
                NanoVgRenderer.text(vg, dx + (dw - namePad) / 2f, dy + headerH / 2f, 12 * S, nameCol, NanoVgRenderer.ALIGN_CENTER_MIDDLE, m.getName());
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
                    renderSettingsNvg(vg, m, dx, cy0 + headerH, dw, S, mxF, myF, dt);
                    NanoVgRenderer.restore(vg);
                }
            }
            NanoVgRenderer.restore(vg);

            // "4+ lines" warning — anchored to the HUD card itself (menu-native, not the
            // in-game toast, since the ClickGUI covers the HUD and the toast was invisible).
            if (hudCardRect != null && System.currentTimeMillis() < hudWarnUntil) {
                long left = hudWarnUntil - System.currentTimeMillis();
                float a = Math.min(1f, left / 300f);   // quick fade at the very end
                int alpha = Math.round(a * 255);
                String warnText = com.lume.client.Lang.tUI("May not fit the panel");
                int wtw = (int) NanoVgRenderer.textWidth(vg, 9.5f * S, warnText);
                int wpw = wtw + 16 * S, wph = 20 * S;
                int wx = hudCardRect[0] + hudCardRect[2] / 2 - wpw / 2;
                int wy = hudCardRect[1] - wph - 6 * S;
                int red = 0xFFE05656;
                NanoVgRenderer.roundedRect(vg, wx, wy, wpw, wph, 8 * S, withAlpha(0xFF1A1010, Math.round(alpha * 0.9f)));
                NanoVgRenderer.strokeRoundedRect(vg, wx + 0.5f * S, wy + 0.5f * S, wpw - S, wph - S, 8 * S, S, withAlpha(red, alpha));
                NanoVgRenderer.text(vg, wx + wpw / 2f, wy + wph / 2f, 9.5f * S, withAlpha(red, alpha), NanoVgRenderer.ALIGN_CENTER_MIDDLE, warnText);
            }

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
            } else if (isServerTab()) {
                drawServerNvg(vg, x, y, W, H, S, mxF, myF, dt, gy, clipTop, clipBot, fVisH, margin);
            }

            // resize grip
            boolean gripHov = inside(mxF, myF, x + W - 14 * S, y + H - 14 * S, 14 * S, 14 * S);
            for (int i = 0; i < 3; i++) {
                int o = (3 - i) * 3 * S;
                NanoVgRenderer.roundedRect(vg, x + W - o - 2 * S, y + H - 5 * S, o, 2 * S, S, gripHov ? Theme.accent() : Theme.txtDim());
            }
        });

        // Blur-dissolve open transition — replaces the old scale-up-from-96% pop (which
        // visibly changed the window's size). Blur strength ramps 1→0 as p goes 0→1 over
        // anim()'s ~200ms, so it reads as "coming into focus" rather than growing.
        if (p < 1f) GlassRenderer.transitionOverlay(panelSx, panelSy, panelSw, panelSh, (1f - p) * 0.8f, 1f - p);

        // BindPopup (DrawContext-based) drawn last, on top of everything.
        renderBindPopupIfPending(ctx, cx, cy, total, S, mouseX, mouseY, dt);
    }

    /** Lume logo mark: half-square (triangle) with a circle centred inside. */
    private void nvgLogo(long vg, float x, float y, float s) {
        NanoVgRenderer.logoMark(vg, x, y, s);
    }

    /** Settings-expand chevron: ▸ collapsed → ▾ expanded (rotated by {@code ex}). */
    private void nvgChevron(long vg, float cx, float cy, float size, float ex, int color) {
        NanoVgRenderer.save(vg);
        NanoVgRenderer.translate(vg, cx, cy);
        NanoVgRenderer.rotate(vg, (float) (Math.PI / 2.0 * ex));
        NanoVgRenderer.triangle(vg, -size * 0.5f, -size * 0.6f, -size * 0.5f, size * 0.6f, size * 0.5f, 0, color);
        NanoVgRenderer.restore(vg);
    }

    /** Custom Hand's Pos X/Y/Z/Scale sliders: only the currently-selected hand's (Right/Left
     *  tab) are shown — Pos/Scale always work regardless of Custom/Style, so unlike before
     *  they are NOT hidden just because Style owns the right hand's rotation. (Rotation pivot
     *  is no longer a slider at all — see HandGeometryPivot, computed from the item's own
     *  mesh.) */
    private boolean hideCustomHandRightPos(Module m, Setting s) {
        if (!(m instanceof com.lume.client.module.modules.render.CustomHand ch)) return false;
        boolean isRightPos = s == ch.rPosX || s == ch.rPosY || s == ch.rPosZ || s == ch.rScale;
        boolean isLeftPos  = s == ch.lPosX || s == ch.lPosY || s == ch.lPosZ || s == ch.lScale;
        boolean rightTabActive = ch.hand.index == 0;
        if (isRightPos) return !rightTabActive;
        if (isLeftPos) return rightTabActive;
        return false;
    }

    /** Height of a module's NanoVG settings panel (bool/slider/mode/color only). */
    private int panelHeightNvg(Module m, int S) {
        int h = 4 * S;
        if (m instanceof CustomCrosshair) h += PREVIEW_H * S + 4 * S;
        for (Setting s : m.getSettings()) { if (!s.hidden && !hideCustomHandRightPos(m, s)) h += settingHeight(s, S); }
        if (m instanceof com.lume.client.module.modules.performance.JvmOptimizer) h += 78 * S;
        if (m instanceof com.lume.client.module.modules.render.HitSound) {
            int n = com.lume.client.audio.CustomAudioPlayer.list("hitsound").length;
            h += 16 * S + 26 * S + Math.max(1, n) * 18 * S;
        }
        if (m instanceof com.lume.client.module.modules.render.WorldParticles wp && wp.useMyParticle.value) {
            int n = com.lume.client.fx.ParticleTexture.list(com.lume.client.module.modules.render.WorldParticles.FOLDER).length;
            h += 16 * S + 26 * S + Math.max(1, n) * 18 * S;
        }
        if (m instanceof com.lume.client.module.modules.render.HitParticles hpz && hpz.useMyParticle.value) {
            int n = com.lume.client.fx.ParticleTexture.list(com.lume.client.module.modules.render.HitParticles.FOLDER).length;
            h += 16 * S + 26 * S + Math.max(1, n) * 18 * S;
        }
        if (m instanceof com.lume.client.module.modules.visual.Hud) h += 26 * S;
        if (m instanceof com.lume.client.module.modules.render.CustomHand ch) {
            h += 20 * S + 4 * S;   // hand tabs
            h += 15 * S + 4 * S;   // Idle/Sprint Sway toggle
            boolean showStyle = ch.hand.index == 0;
            h += (showStyle ? 2 : 1) * (15 * S + 4 * S);   // Style (right hand only) + Animation (always)
            if (showStyle && ch.style.index == com.lume.client.module.modules.render.CustomHand.STYLE_CUSTOM) {
                h += 3 * (15 * S + 4 * S);   // Custom Rot X/Y/Z sliders
                h += handPresetsHeight(S);   // saved-preset rows + name field + Save button
            }
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE
                    || ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_TILT)
                h += 15 * S + 4 * S;   // Swing Angle slider
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE)
                h += 15 * S + 4 * S;   // Pivot slider
            h += 2 * (15 * S + 4 * S);   // Outline toggle + Fill mode
            if (ch.outline.value)
                h += 15 * S + (ch.outlineColor == openColor ? PAL_H * S : 0) + 4 * S;   // Outline colour
            if (ch.fill.index != 0)
                h += 15 * S + 4 * S;   // Fill Opacity slider
            if (ch.fill.index == 1)
                h += 15 * S + (ch.fillColor == openColor ? PAL_H * S : 0) + 4 * S;      // Fill colour
            h += 15 * S + 4 * S;   // copy-pose text row
            h += 22 * S + 4 * S;   // paste button
            h += 26 * S;           // reset button
        }
        if (m instanceof Waypoints) h += wpManagerHeight(S);
        if (m instanceof com.lume.client.module.modules.qol.KeybindManager) h += 2 * (22 * S + 4 * S);
        return h + 8 * S;
    }

    /** Saved-preset rows + name field + Save button, under Custom Hand's "Custom" style. */
    private int handPresetsHeight(int S) {
        int n = com.lume.client.module.modules.render.HandPresets.list.size();
        return 12 * S + n * (16 * S + 2 * S) + (15 * S + 4 * S) + (18 * S + 4 * S);
    }

    /** Render a module's settings via NanoVG, recording {@code sHits} so the existing
     *  click/drag handlers work unchanged. (Crosshair preview / waypoint manager /
     *  event list are skipped here for now — only bool/slider/mode/color.) */
    private void renderSettingsNvg(long vg, Module m, int x0, int yTop, int w, int S, int mx, int my, float dt) {
        NanoVgRenderer.roundedRect(vg, x0 + 10 * S, yTop, w - 20 * S, Math.max(1, S), 0.5f, Theme.border());
        int sx = x0 + 14 * S, swid = w - 28 * S, yy = yTop + 4 * S;
        // live crosshair preview
        if (m instanceof CustomCrosshair cc) {
            int ph = PREVIEW_H * S;
            NanoVgRenderer.roundedRect(vg, sx, yy, swid, ph, 8 * S, 0x55000000);
            nvgCrosshairPreview(vg, cc, sx + swid / 2, yy + ph / 2, S);
            yy += ph + 4 * S;
        }
        for (Setting s : m.getSettings()) {
            if (s.hidden || hideCustomHandRightPos(m, s)) continue;
            int h = settingHeight(s, S);
            if (s instanceof BoolSetting bs) renderBoolNvg(vg, bs, sx, yy, swid, h, S);
            else if (s instanceof SliderSetting ss) renderSliderNvg(vg, ss, sx, yy, swid, h, S);
            else if (s instanceof ModeSetting ms) renderModeNvg(vg, ms, sx, yy, swid, h, S);
            else if (s instanceof StringSetting ts) renderStringNvg(vg, ts, sx, yy, swid, h, S, mx, my, dt);
            else if (s instanceof ColorSetting cs) renderColorNvg(vg, cs, sx, yy, swid, S, mx, my, dt);
            yy += h;
        }
        if (m instanceof com.lume.client.module.modules.performance.JvmOptimizer) {
            renderJvmInfoNvg(vg, sx, yy + 4 * S, swid, S);
        }
        if (m instanceof com.lume.client.module.modules.render.HitSound hs2) {
            NanoVgRenderer.text(vg, sx, yy + 8 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI("My Sounds"));
            yy += 16 * S;
            int bh = 22 * S;
            NanoVgRenderer.roundedRect(vg, sx, yy, swid, bh, 8 * S, Theme.glassHov());
            NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, yy + 0.5f * S, swid - S, bh - S, 8 * S, S, Theme.rim());
            NanoVgRenderer.text(vg, sx + swid / 2f, yy + bh / 2f, 9.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                    com.lume.client.Lang.tUI("Open Sounds Folder"));
            SHit fh = new SHit(); fh.s = null; fh.kind = 9; fh.x = sx; fh.y = yy; fh.w = swid; fh.h = bh; sHits.add(fh);
            yy += bh + 4 * S;

            java.io.File[] files = com.lume.client.audio.CustomAudioPlayer.list("hitsound");
            if (files.length == 0) {
                NanoVgRenderer.text(vg, sx, yy + 8 * S, 8.5f * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                        com.lume.client.Lang.tUI("No files yet — drop an .ogg above"));
                yy += 18 * S;
            } else {
                for (java.io.File f : files) {
                    boolean sel = f.getName().equals(hs2.selectedFile) || (hs2.selectedFile == null && f == files[0]);
                    int rh = 16 * S;
                    NanoVgRenderer.roundedRect(vg, sx, yy, swid, rh, 5 * S, sel ? withAlpha(Theme.accentRgb(), 0x44) : Theme.glassRow());
                    NanoVgRenderer.text(vg, sx + 8 * S, yy + rh / 2f, 8.5f * S, sel ? Theme.accent() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, f.getName());
                    SHit rhit = new SHit(); rhit.s = null; rhit.kind = 11; rhit.tag = f.getName();
                    rhit.x = sx; rhit.y = yy; rhit.w = swid; rhit.h = rh; sHits.add(rhit);
                    yy += rh + 2 * S;
                }
            }
        }
        if (m instanceof com.lume.client.module.modules.render.WorldParticles wp2 && wp2.useMyParticle.value) {
            yy = renderParticlePickerNvg(vg, com.lume.client.module.modules.render.WorldParticles.FOLDER, "world", wp2.selectedFile, sx, yy, swid, S);
        }
        if (m instanceof com.lume.client.module.modules.render.HitParticles hp2 && hp2.useMyParticle.value) {
            yy = renderParticlePickerNvg(vg, com.lume.client.module.modules.render.HitParticles.FOLDER, "hit", hp2.selectedFile, sx, yy, swid, S);
        }
        if (m instanceof com.lume.client.module.modules.visual.Hud) {
            int bh = 22 * S;
            NanoVgRenderer.roundedRect(vg, sx, yy, swid, bh, 8 * S, withAlpha(0xFFE05656, 0x33));
            NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, yy + 0.5f * S, swid - S, bh - S, 8 * S, S, withAlpha(0xFFE05656, 0x66));
            NanoVgRenderer.text(vg, sx + swid / 2f, yy + bh / 2f, 9.5f * S, 0xFFE05656, NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                    com.lume.client.Lang.tUI("Reset all HUD elements"));
            SHit rh = new SHit(); rh.s = null; rh.kind = 10; rh.x = sx; rh.y = yy; rh.w = swid; rh.h = bh; sHits.add(rh);
            yy += bh;
        }
        if (m instanceof com.lume.client.module.modules.render.CustomHand ch) {
            // --- hand tabs: Right | Left — picks which hand's Pos/Scale sliders show above ---
            int tabH = 20 * S, tabGap = 4 * S;
            int tabW = (swid - tabGap) / 2;
            String[] tabNames = { com.lume.client.Lang.tUI("Right"), com.lume.client.Lang.tUI("Left") };
            for (int i = 0; i < 2; i++) {
                int tx = sx + i * (tabW + tabGap);
                boolean sel = ch.hand.index == i;
                NanoVgRenderer.roundedRect(vg, tx, yy, tabW, tabH, 7 * S, sel ? Theme.accent() : Theme.glassRow());
                NanoVgRenderer.text(vg, tx + tabW / 2f, yy + tabH / 2f, 9 * S, sel ? Theme.activeText() : Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, tabNames[i]);
                SHit th = new SHit(); th.s = ch.hand; th.kind = 15; th.channel = i; th.x = tx; th.y = yy; th.w = tabW; th.h = tabH; sHits.add(th);
            }
            yy += tabH + 4 * S;

            // --- Idle/Sprint Sway on/off ---
            renderBoolNvg(vg, ch.sway, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;

            // --- Style (right hand only) — named resting pose — then Animation, always shown
            //     (it swings/freezes the item regardless of which Style is picked). ---
            if (ch.hand.index == 0) {
                renderModeNvg(vg, ch.style, sx, yy, swid, 15 * S, S);
                yy += 15 * S + 4 * S;
                if (ch.style.index == com.lume.client.module.modules.render.CustomHand.STYLE_CUSTOM) {
                    renderSliderNvg(vg, ch.rRotX, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
                    renderSliderNvg(vg, ch.rRotY, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
                    renderSliderNvg(vg, ch.rRotZ, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
                    yy = renderHandPresetsNvg(vg, ch, sx, yy, swid, S, mx, my, dt);
                }
            }
            renderModeNvg(vg, ch.animation, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;
            // Swing Angle only matters for the two tilt animations (Simple / Tilt).
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE
                    || ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_TILT) {
                renderSliderNvg(vg, ch.swingAngle, sx, yy, swid, 15 * S, S);
                yy += 15 * S + 4 * S;
            }
            // Swing Pivot — Simple only (Tilt keeps its own auto-detected centre pivot, untouched).
            // Direct hand-space offset now, not a fraction of the item's own (tiny) mesh bounds.
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE) {
                renderSliderNvg(vg, ch.swingPivotY, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
            }

            // --- Outline (enlarged rim behind the item) / Fill (recolour) — both hands ---
            renderBoolNvg(vg, ch.outline, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;
            renderModeNvg(vg, ch.fill, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;
            // Outline and Fill each have their own colour now (CustomHand.outlineColor / fillColor);
            // show whichever picker(s) are relevant. (Was a single ch.handColor that no longer exists.)
            if (ch.outline.value) {
                renderColorNvg(vg, ch.outlineColor, sx, yy, swid, S, mx, my, dt);
                yy += 15 * S + (ch.outlineColor == openColor ? PAL_H * S : 0) + 4 * S;
            }
            if (ch.fill.index != 0) {
                renderSliderNvg(vg, ch.fillOpacity, sx, yy, swid, 15 * S, S);
                yy += 15 * S + 4 * S;
            }
            if (ch.fill.index == 1) {
                renderColorNvg(vg, ch.fillColor, sx, yy, swid, S, mx, my, dt);
                yy += 15 * S + (ch.fillColor == openColor ? PAL_H * S : 0) + 4 * S;
            }

            // --- copy current pose as text (paste it back to build a new named Style from it) ---
            {
                int copyH = 15 * S;
                int copyBtnW = 44 * S;
                int textW = swid - copyBtnW - 4 * S;
                NanoVgRenderer.roundedRect(vg, sx, yy, textW, copyH, 6 * S, Theme.glassRow());
                String exportStr = ch.exportText();
                float fitSize = NanoVgRenderer.fitSize(vg, 8 * S, exportStr, textW - 10 * S, 5.5f * S);
                NanoVgRenderer.text(vg, sx + 6 * S, yy + copyH / 2f, fitSize, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, exportStr);
                int copyBtnX = sx + textW + 4 * S;
                NanoVgRenderer.roundedRect(vg, copyBtnX, yy, copyBtnW, copyH, 6 * S, Theme.glassHov());
                NanoVgRenderer.text(vg, copyBtnX + copyBtnW / 2f, yy + copyH / 2f, 8 * S, Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Copy"));
                SHit ch19 = new SHit(); ch19.s = null; ch19.kind = 19; ch19.x = copyBtnX; ch19.y = yy; ch19.w = copyBtnW; ch19.h = copyH; sHits.add(ch19);
                yy += copyH + 4 * S;
            }

            // --- paste a pose (own text, or one you copied earlier) from the clipboard ---
            {
                int pasteH = 22 * S;
                NanoVgRenderer.roundedRect(vg, sx, yy, swid, pasteH, 8 * S, Theme.glassHov());
                NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, yy + 0.5f * S, swid - S, pasteH - S, 8 * S, S, Theme.rim());
                NanoVgRenderer.text(vg, sx + swid / 2f, yy + pasteH / 2f, 9.5f * S, Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                        com.lume.client.Lang.tUI("Paste"));
                SHit ph20 = new SHit(); ph20.s = null; ph20.kind = 20; ph20.x = sx; ph20.y = yy; ph20.w = swid; ph20.h = pasteH; sHits.add(ph20);
                yy += pasteH + 4 * S;
            }

            // --- reset ---
            int bh = 22 * S;
            NanoVgRenderer.roundedRect(vg, sx, yy, swid, bh, 8 * S, withAlpha(0xFFE05656, 0x33));
            NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, yy + 0.5f * S, swid - S, bh - S, 8 * S, S, withAlpha(0xFFE05656, 0x66));
            NanoVgRenderer.text(vg, sx + swid / 2f, yy + bh / 2f, 9.5f * S, 0xFFE05656, NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                    com.lume.client.Lang.tUI("Reset to default"));
            SHit rh = new SHit(); rh.s = null; rh.kind = 12; rh.x = sx; rh.y = yy; rh.w = swid; rh.h = bh; sHits.add(rh);
            yy += bh;
        }
        if (m instanceof Waypoints) renderWaypointManagerNvg(vg, sx, yy + 4 * S, swid, S);
        if (m instanceof com.lume.client.module.modules.qol.KeybindManager) renderKeybindManagerButtonsNvg(vg, sx, yy, swid, S, mx, my, dt);
    }

    /** Saved-preset rows (click = load pose, ✕ = delete, kinds 23/24) + name field + Save button
     *  (kind 25) — Custom Hand's "Custom" style, right hand only. Returns the y position after
     *  everything this drew (same convention as {@link #renderParticlePickerNvg}). */
    private int renderHandPresetsNvg(long vg, com.lume.client.module.modules.render.CustomHand ch, int sx, int yy, int swid, int S, int mx, int my, float dt) {
        NanoVgRenderer.text(vg, sx, yy + 6 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI("Saved Presets"));
        yy += 12 * S;
        for (com.lume.client.module.modules.render.HandPresets.Preset p : com.lume.client.module.modules.render.HandPresets.list) {
            int rh = 16 * S;
            NanoVgRenderer.roundedRect(vg, sx, yy, swid, rh, 5 * S, Theme.glassRow());
            NanoVgRenderer.text(vg, sx + 8 * S, yy + rh / 2f, 8.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, p.name);
            int dx = sx + swid - 14 * S;
            NanoVgRenderer.text(vg, dx, yy + rh / 2f, 9 * S, 0xFFE05656, NanoVgRenderer.ALIGN_MIDDLE, "✕");
            SHit lh = new SHit(); lh.s = null; lh.kind = 23; lh.tag = p.name; lh.x = sx; lh.y = yy; lh.w = swid - 18 * S; lh.h = rh; sHits.add(lh);
            SHit dh = new SHit(); dh.s = null; dh.kind = 24; dh.tag = p.name; dh.x = dx - 3 * S; dh.y = yy; dh.w = 16 * S; dh.h = rh; sHits.add(dh);
            yy += rh + 2 * S;
        }
        renderStringNvg(vg, ch.presetName, sx, yy, swid, 15 * S, S, mx, my, dt);
        yy += 15 * S + 4 * S;
        int bh = 18 * S;
        NanoVgRenderer.roundedRect(vg, sx, yy, swid, bh, 6 * S, Theme.accent());
        NanoVgRenderer.text(vg, sx + swid / 2f, yy + bh / 2f, 9 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Save current as…"));
        SHit sh = new SHit(); sh.s = null; sh.kind = 25; sh.x = sx; sh.y = yy; sh.w = swid; sh.h = bh; sHits.add(sh);
        yy += bh + 4 * S;
        return yy;
    }

    /** "Bind command" (kind 21 — press a key, then set what it sends, via {@link BindPopup}
     *  anchored right below THIS button — no full-screen takeover for a quick single-key bind)
     *  and "Open manager" (kind 22 — the full visual keyboard) action buttons for the
     *  KeybindManager module's card. Same accent-button look as the Custom Hand Copy/Paste
     *  buttons (kinds 19/20). */
    private void renderKeybindManagerButtonsNvg(long vg, int sx, int yy, int swid, int S, int mx, int my, float dt) {
        int bh = 22 * S, gap = 4 * S;
        boolean capturing = bindingMacroKey;
        NanoVgRenderer.roundedRect(vg, sx, yy, swid, bh, 8 * S, capturing ? Theme.accent() : Theme.glassHov());
        NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, yy + 0.5f * S, swid - S, bh - S, 8 * S, S, Theme.rim());
        NanoVgRenderer.text(vg, sx + swid / 2f, yy + bh / 2f, 9.5f * S, capturing ? Theme.activeText() : Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                capturing ? com.lume.client.Lang.tUI("Press a key…") : com.lume.client.Lang.tUI("Bind command"));
        SHit bh1 = new SHit(); bh1.s = null; bh1.kind = 21; bh1.x = sx; bh1.y = yy; bh1.w = swid; bh1.h = bh; sHits.add(bh1);
        int bindBtnBottom = yy + bh;
        yy += bh + gap;
        NanoVgRenderer.roundedRect(vg, sx, yy, swid, bh, 8 * S, Theme.glassHov());
        NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, yy + 0.5f * S, swid - S, bh - S, 8 * S, S, Theme.rim());
        NanoVgRenderer.text(vg, sx + swid / 2f, yy + bh / 2f, 9.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Open manager"));
        SHit bh2 = new SHit(); bh2.s = null; bh2.kind = 22; bh2.x = sx; bh2.y = yy; bh2.w = swid; bh2.h = bh; sHits.add(bh2);

        if (BindPopup.isActive()) {
            bindPopupPending = true;
            bindPopupLocalX = sx;
            bindPopupLocalY = bindBtnBottom + 4 * S;
        } else {
            bindPopupPending = false;
        }
    }

    /** NanoVG mirror of {@link #renderWaypointManager} — name + one free-form "x y z"
     *  coords field (paste-friendly, Ctrl+V) + Add, and the saved-waypoint rows. Reuses
     *  the same {@code wpHits}/{@code wpAdd()} plumbing, since click routing doesn't
     *  care which renderer produced the hit rectangles. */
    private void renderWaypointManagerNvg(long vg, int sx, int yy, int swid, int S) {
        int rh = 15 * S, addW = 32 * S;
        int nameW = Math.round(swid * 0.30f);
        int coordsW = swid - nameW - addW - 2 * 4 * S;
        int fx = sx;
        fieldNvg(vg, "name", fx, yy, nameW, rh, "name", S); fx += nameW + 4 * S;
        fieldNvg(vg, "coords", fx, yy, coordsW, rh, "x y z", S);
        int addX = sx + swid - addW;
        NanoVgRenderer.gradientRoundedRect(vg, addX, yy, addW, rh, 5 * S, Theme.accent(), Theme.accent2());
        NanoVgRenderer.text(vg, addX + addW / 2f, yy + rh / 2f, 9 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Add"));
        wpHits.add(new Object[]{ "add", addX, yy, addW, rh });

        int ly = yy + 18 * S + 6 * S;
        List<Waypoints.WP> vis = Waypoints.visible();
        for (int i = 0; i < vis.size(); i++) {
            Waypoints.WP w = vis.get(i);
            int ry = ly + i * 16 * S, hh = 14 * S;
            NanoVgRenderer.roundedRect(vg, sx, ry, swid, hh, 4 * S, Theme.glassRow());
            NanoVgRenderer.roundedRect(vg, sx + 4 * S, ry + (hh - 8 * S) / 2, 8 * S, 8 * S, 2 * S, w.color);
            wpHits.add(new Object[]{ "color:" + i, sx + 2 * S, ry, 16 * S, hh });
            String t = w.name + "  " + (int) w.x + " " + (int) w.y + " " + (int) w.z;
            NanoVgRenderer.text(vg, sx + 18 * S, ry + hh / 2f, 8.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, t);
            int dx = sx + swid - 14 * S;
            NanoVgRenderer.text(vg, dx, ry + hh / 2f, 9 * S, 0xFFE05656, NanoVgRenderer.ALIGN_MIDDLE, "✕");
            wpHits.add(new Object[]{ "del:" + i, dx - 3 * S, ry, 16 * S, hh });
        }
    }

    /** "My Particles" — drop-in .png picker (Open Folder button + selectable file rows),
     *  same idea as HitSound's "My Sounds". {@code tagPrefix} ("world"/"hit") disambiguates
     *  which module's folder/selection a click belongs to (see handleSettingClick kinds 16/17).
     *  Returns the y position after everything this drew. */
    private int renderParticlePickerNvg(long vg, String folderKey, String tagPrefix, String selectedFile, int sx, int yy, int swid, int S) {
        NanoVgRenderer.text(vg, sx, yy + 8 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI("My Particles"));
        yy += 16 * S;
        int bh = 22 * S;
        NanoVgRenderer.roundedRect(vg, sx, yy, swid, bh, 8 * S, Theme.glassHov());
        NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, yy + 0.5f * S, swid - S, bh - S, 8 * S, S, Theme.rim());
        NanoVgRenderer.text(vg, sx + swid / 2f, yy + bh / 2f, 9.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                com.lume.client.Lang.tUI("Open Particles Folder"));
        SHit fh = new SHit(); fh.s = null; fh.kind = 16; fh.tag = folderKey; fh.x = sx; fh.y = yy; fh.w = swid; fh.h = bh; sHits.add(fh);
        yy += bh + 4 * S;

        java.io.File[] files = com.lume.client.fx.ParticleTexture.list(folderKey);
        if (files.length == 0) {
            NanoVgRenderer.text(vg, sx, yy + 8 * S, 8.5f * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    com.lume.client.Lang.tUI("No files yet — drop a .png above"));
            yy += 18 * S;
        } else {
            for (java.io.File f : files) {
                boolean sel = f.getName().equals(selectedFile) || (selectedFile == null && f == files[0]);
                int rh = 16 * S;
                NanoVgRenderer.roundedRect(vg, sx, yy, swid, rh, 5 * S, sel ? withAlpha(Theme.accentRgb(), 0x44) : Theme.glassRow());
                NanoVgRenderer.text(vg, sx + 8 * S, yy + rh / 2f, 8.5f * S, sel ? Theme.accent() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, f.getName());
                SHit rhit = new SHit(); rhit.s = null; rhit.kind = 17; rhit.tag = tagPrefix + ":" + f.getName();
                rhit.x = sx; rhit.y = yy; rhit.w = swid; rhit.h = rh; sHits.add(rhit);
                yy += rh + 2 * S;
            }
        }
        return yy;
    }

    private void fieldNvg(long vg, String id, int x, int y, int w, int h, String placeholder, int S) {
        boolean foc = id.equals(focusedField);
        NanoVgRenderer.roundedRect(vg, x, y, w, h, 5 * S, foc ? Theme.glassHov() : Theme.glassRow());
        if (foc) NanoVgRenderer.roundedRect(vg, x, y + h - Math.max(1, S), w, Math.max(1, S), 0, Theme.accent());
        String txt = bufGet(id);
        String show = txt.isEmpty() && !foc ? placeholder : txt + (foc ? "_" : "");
        NanoVgRenderer.text(vg, x + 5 * S, y + h / 2f, 8.5f * S, txt.isEmpty() && !foc ? Theme.txtDim() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, show);
        wpHits.add(new Object[]{ id, x, y, w, h });
    }

    /** Live crosshair preview drawn with NanoVG (matches HudRenderer.drawCrosshair look). */
    private void nvgCrosshairPreview(long vg, CustomCrosshair cc, int cx, int cy, int S) {
        int size = cc.size.getInt() * S;
        int th = cc.thickness.getInt() * S;
        int gap = cc.gap.getInt() * S;
        int col = 0xFF000000 | (cc.color.accent ? Theme.accentRgb() : cc.color.rgb());
        int oc = 0xAA000000;
        // outline
        if (cc.outline.value) {
            int o = Math.max(1, S);
            NanoVgRenderer.roundedRect(vg, cx - th / 2f - o, cy - gap - size - o, th + 2 * o, size + 2 * o, 0, oc);
            NanoVgRenderer.roundedRect(vg, cx - th / 2f - o, cy + gap - o, th + 2 * o, size + 2 * o, 0, oc);
            NanoVgRenderer.roundedRect(vg, cx - gap - size - o, cy - th / 2f - o, size + 2 * o, th + 2 * o, 0, oc);
            NanoVgRenderer.roundedRect(vg, cx + gap - o, cy - th / 2f - o, size + 2 * o, th + 2 * o, 0, oc);
        }
        // arms
        NanoVgRenderer.roundedRect(vg, cx - th / 2f, cy - gap - size, th, size, 0, col);
        NanoVgRenderer.roundedRect(vg, cx - th / 2f, cy + gap, th, size, 0, col);
        NanoVgRenderer.roundedRect(vg, cx - gap - size, cy - th / 2f, size, th, 0, col);
        NanoVgRenderer.roundedRect(vg, cx + gap, cy - th / 2f, size, th, 0, col);
        // center dot
        if (cc.dot.value) NanoVgRenderer.roundedRect(vg, cx - th / 2f, cy - th / 2f, th, th, 0, col);
    }

    /** JVM / RAM info panel rendered inside the System Info module expansion. */
    private void renderJvmInfoNvg(long vg, int x, int y, int w, int S) {
        NanoVgRenderer.roundedRect(vg, x - 4 * S, y, w + 8 * S, Math.max(1, S), 0.5f, Theme.border());
        int yy = y + 6 * S;

        // Java version + arch
        String arch = com.lume.client.module.modules.performance.JvmOptimizer.is64bit() ? "64-bit ✓" : "32-bit ✗";
        String jv = "Java " + com.lume.client.module.modules.performance.JvmOptimizer.javaVersion() + "  ·  " + arch;
        NanoVgRenderer.text(vg, x, yy + 5 * S, 9 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, jv);
        yy += 14 * S;

        // RAM label
        long used = com.lume.client.module.modules.performance.JvmOptimizer.usedMb();
        long max = com.lume.client.module.modules.performance.JvmOptimizer.maxMb();
        NanoVgRenderer.text(vg, x, yy + 5 * S, 9 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE,
                "RAM: " + used + " MB  /  " + max + " MB");
        yy += 14 * S;

        // RAM bar
        float frac = max > 0 ? (float) used / max : 0f;
        int barH = 5 * S;
        NanoVgRenderer.roundedRect(vg, x, yy, w, barH, barH / 2f, Theme.pillOff());
        int barCol = frac < 0.6f ? 0xFF6FCF7F : frac < 0.8f ? 0xFFE8C15A : 0xFFE05656;
        int fw = Math.max(barH, Math.round(w * frac));
        NanoVgRenderer.roundedRect(vg, x, yy, fw, barH, barH / 2f, barCol);
        yy += barH + 10 * S;

        // Recommended JVM flags
        NanoVgRenderer.text(vg, x, yy + 4 * S, 8 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Флаги JVM (рекомендуемые):");
        yy += 14 * S;
        NanoVgRenderer.text(vg, x, yy + 2 * S, 8 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, "-Xmx4G -Xms4G -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions");
        yy += 12 * S;
        NanoVgRenderer.text(vg, x, yy + 2 * S, 8 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, "-XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50");
    }

    private void renderBoolNvg(long vg, BoolSetting bs, int x, int y, int w, int h, int S) {
        NanoVgRenderer.text(vg, x, y + h / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI(bs.name));
        int pw = 18 * S, ph = 10 * S, px = x + w - pw, py = y + (h - ph) / 2;
        NanoVgRenderer.roundedRect(vg, px, py, pw, ph, ph / 2f, bs.value ? Theme.accent() : Theme.pillOff());
        int kd = ph - 4 * S, kx = bs.value ? px + pw - kd - 2 * S : px + 2 * S;
        NanoVgRenderer.roundedRect(vg, kx, py + 2 * S, kd, kd, kd / 2f, 0xFFFFFFFF);
        SHit hit = new SHit(); hit.s = bs; hit.kind = 0; hit.x = x; hit.y = y; hit.w = w; hit.h = h; sHits.add(hit);
    }

    private void renderSliderNvg(long vg, SliderSetting ss, int x, int y, int w, int h, int S) {
        NanoVgRenderer.text(vg, x, y + 6 * S, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI(ss.name));
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
        NanoVgRenderer.text(vg, x, y + h / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI(ms.name));
        String disp = "‹ " + ms.get() + " ›";
        float dw = NanoVgRenderer.textWidth(vg, 10 * S, disp);
        NanoVgRenderer.text(vg, x + w - dw, y + h / 2f, 10 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, disp);
        SHit hit = new SHit(); hit.s = ms; hit.kind = 4; hit.x = x; hit.y = y; hit.w = w; hit.h = h; sHits.add(hit);
    }

    private void renderStringNvg(long vg, StringSetting ts, int x, int y, int w, int h, int S, int mx, int my, float dt) {
        NanoVgRenderer.text(vg, x, y + h / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI(ts.name));
        boolean foc = "customstring".equals(focusedField) && focusedString == ts;
        int bw = Math.round(w * 0.55f), bh = 11 * S, bx = x + w - bw, by = y + (h - bh) / 2;
        NanoVgRenderer.roundedRect(vg, bx, by, bw, bh, 4 * S, foc ? Theme.glassHov() : Theme.glassRow());
        // Premium-glass rim: brightens smoothly on hover (same approach()/dt lerp every hover
        // state in this file already uses), not an instant on/off — focused stays a flat accent
        // colour (the "committed" state reads better fixed, not pulsing).
        float[] sa = animFor("str:" + ts.name);
        boolean hov = inside(mx, my, bx, by, bw, bh);
        sa[0] = approach(sa[0], hov ? 1f : 0f, 14f, dt);
        // Same 0x30->0x70 alpha range the module-card hover rim already uses (see renderModulesNvg).
        int rim = foc ? Theme.accent() : withAlpha(0xFFFFFF, Math.round(0x30 + 0x40 * sa[0]));
        NanoVgRenderer.strokeRoundedRect(vg, bx + 0.5f * S, by + 0.5f * S, bw - S, bh - S, 4 * S, S, rim);
        String shown = ts.value + (foc ? "|" : "");
        NanoVgRenderer.text(vg, bx + 6 * S, by + bh / 2f, 9 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, shown);
        SHit hit = new SHit(); hit.s = ts; hit.kind = 18; hit.x = bx; hit.y = y; hit.w = bw; hit.h = h; sHits.add(hit);
    }

    private void renderColorNvg(long vg, ColorSetting cs, int x, int y, int w, int S, int mx, int my, float dt) {
        int row = 15 * S;
        NanoVgRenderer.text(vg, x, y + row / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI(cs.name));

        // "Accent" toggle pill — follow the theme accent instead of a fixed RGB.
        // Without this, a ColorSetting that got saved with accent=true (e.g. from
        // an older build) has NO way back to a custom colour: the swatch/HSV/hex
        // below only ever write r/g/b, which onAttack-style readers ignore whenever
        // accent is true — exactly the "picking a colour does nothing" bug.
        int sw = 30 * S, sh = 11 * S, sxb = x + w - sw, syb = y + (row - sh) / 2;
        int aw = 42 * S, agap = 4 * S, axb = sxb - agap - aw;
        boolean acc = cs.accent;
        NanoVgRenderer.roundedRect(vg, axb, syb, aw, sh, 4 * S, acc ? withAlpha(Theme.accentRgb(), 0x55) : Theme.glassRow());
        // Premium-glass rim, brightens smoothly on hover — same 0x30->0x70 range/approach()/dt
        // lerp as the module-card hover rim and the StringSetting field above; the "committed"
        // states (Accent on / palette open) stay a flat accent colour, unanimated.
        float[] aa = animFor("colacc:" + cs.name);
        aa[0] = approach(aa[0], inside(mx, my, axb, syb, aw, sh) ? 1f : 0f, 14f, dt);
        int accRim = acc ? Theme.accent() : withAlpha(0xFFFFFF, Math.round(0x30 + 0x40 * aa[0]));
        NanoVgRenderer.strokeRoundedRect(vg, axb + 0.5f * S, syb + 0.5f * S, aw - S, sh - S, 4 * S, S, accRim);
        NanoVgRenderer.text(vg, axb + aw / 2f, syb + sh / 2f, 7.2f * S, acc ? Theme.accent() : Theme.txtDim(),
                NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Accent"));
        SHit accHit = new SHit(); accHit.s = cs; accHit.kind = 13; accHit.x = axb; accHit.y = y; accHit.w = aw; accHit.h = row; sHits.add(accHit);

        // colour swatch button (click → open palette)
        NanoVgRenderer.roundedRect(vg, sxb, syb, sw, sh, 4 * S, 0xFF000000 | (acc ? Theme.accentRgb() & 0xFFFFFF : cs.rgb()));
        float[] swa = animFor("colsw:" + cs.name);
        swa[0] = approach(swa[0], inside(mx, my, sxb, syb, sw, sh) ? 1f : 0f, 14f, dt);
        int swRim = cs == openColor ? Theme.accent() : withAlpha(0xFFFFFF, Math.round(0x30 + 0x40 * swa[0]));
        NanoVgRenderer.strokeRoundedRect(vg, sxb + 0.5f * S, syb + 0.5f * S, sw - S, sh - S, 4 * S, S, swRim);
        SHit open = new SHit(); open.s = cs; open.kind = 2; open.x = sxb; open.y = y; open.w = sw; open.h = row; sHits.add(open);

        if (cs != openColor) return;

        // --- HSV picker: saturation/value square + hue bar ---
        int py = y + row + 4 * S;
        int hueW = 12 * S, gap2 = 4 * S;
        int sqW = w - hueW - gap2, sqH = 52 * S;
        int hueColor = 0xFF000000 | hsvToRgb(pickH, 1f, 1f);
        // SV square: hue base → white (left) → black (bottom)
        NanoVgRenderer.roundedRect(vg, x, py, sqW, sqH, 3 * S, hueColor);
        NanoVgRenderer.fillLinearGradient(vg, x, py, sqW, sqH, 3 * S, x, py, x + sqW, py, 0xFFFFFFFF, 0x00FFFFFF);
        NanoVgRenderer.fillLinearGradient(vg, x, py, sqW, sqH, 3 * S, x, py, x, py + sqH, 0x00000000, 0xFF000000);
        NanoVgRenderer.strokeRoundedRect(vg, x + 0.5f * S, py + 0.5f * S, sqW - S, sqH - S, 3 * S, S, Theme.rim());
        // SV cursor
        float curX = x + pickS * sqW, curY = py + (1f - pickV) * sqH;
        NanoVgRenderer.circle(vg, curX, curY, 3.5f * S, 0xFF000000);
        NanoVgRenderer.circle(vg, curX, curY, 2.5f * S, 0xFFFFFFFF);
        SHit sv = new SHit(); sv.s = cs; sv.kind = 7; sv.x = x; sv.y = py; sv.w = sqW; sv.h = sqH; sHits.add(sv);

        // hue bar (vertical rainbow, 6 segments)
        int hx = x + sqW + gap2;
        int[] hueStops = { 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000 };
        float seg = sqH / 6f;
        for (int i = 0; i < 6; i++)
            NanoVgRenderer.fillLinearGradient(vg, hx, py + i * seg, hueW, seg, 0, hx, py + i * seg, hx, py + (i + 1) * seg, hueStops[i], hueStops[i + 1]);
        NanoVgRenderer.strokeRoundedRect(vg, hx + 0.5f * S, py + 0.5f * S, hueW - S, sqH - S, 2 * S, S, Theme.rim());
        float hueY = py + (pickH / 360f) * sqH;
        NanoVgRenderer.roundedRect(vg, hx - 2 * S, hueY - 1.5f * S, hueW + 4 * S, 3 * S, 1.5f * S, 0xFFFFFFFF);
        SHit hb = new SHit(); hb.s = cs; hb.kind = 8; hb.x = hx; hb.y = py; hb.w = hueW; hb.h = sqH; sHits.add(hb);

        // hex field
        int hy = py + sqH + 4 * S, hh = 14 * S;
        boolean foc = "colorhex".equals(focusedField) && openColor == cs;
        NanoVgRenderer.roundedRect(vg, x, hy, w, hh, 5 * S, foc ? Theme.glassHov() : Theme.glassRow());
        NanoVgRenderer.strokeRoundedRect(vg, x + 0.5f * S, hy + 0.5f * S, w - S, hh - S, 5 * S, S, foc ? Theme.accent() : Theme.rim());
        String shown = "#" + (foc ? colorHex + "|" : String.format("%06X", cs.rgb() & 0xFFFFFF));
        NanoVgRenderer.text(vg, x + 8 * S, hy + hh / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, shown);
        SHit hex = new SHit(); hex.s = cs; hex.kind = 6; hex.x = x; hex.y = hy; hex.w = w; hex.h = hh; sHits.add(hex);
    }

    // ---- HSV helpers ----
    private static int hsvToRgb(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        switch ((int) (h / 60f) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d != 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60; if (h < 0) h += 360;
        }
        return new float[]{ h, max == 0 ? 0 : d / max, max };
    }

    private void openPicker(ColorSetting cs) {
        float[] hsv = rgbToHsv(cs.rgb());
        pickH = hsv[0]; pickS = hsv[1]; pickV = hsv[2];
    }

    private void applyPick() {
        if (openColor == null) return;
        int c = hsvToRgb(pickH, pickS, pickV);
        openColor.r = (c >> 16) & 0xFF; openColor.g = (c >> 8) & 0xFF; openColor.b = c & 0xFF;
        openColor.accent = false;   // dragging the picker means "I want this exact colour", not the theme accent
    }

    private void updatePicker(SHit h, double mx, double my) {
        if (openColor == null) return;
        if (h.kind == 7) {
            pickS = (float) Math.max(0, Math.min(1, (mx - h.x) / h.w));
            pickV = (float) Math.max(0, Math.min(1, 1 - (my - h.y) / h.h));
        } else {
            pickH = (float) Math.max(0, Math.min(1, (my - h.y) / h.h)) * 360f;
        }
        applyPick();
    }

    /** Apply the hex buffer (6 digits) to the open colour setting + sync the HSV picker. */
    private void applyHex() {
        if (openColor == null || colorHex.length() != 6) return;
        try {
            int c = Integer.parseInt(colorHex, 16);
            openColor.r = (c >> 16) & 0xFF; openColor.g = (c >> 8) & 0xFF; openColor.b = c & 0xFF;
            openColor.accent = false;
            openPicker(openColor);
        } catch (NumberFormatException ignored) {}
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
            NanoVgRenderer.text(vg, rx + 12 * S, ry + rowH / 2f, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, m.getName());
            String kd = binding ? "нажми клавишу…" : keyDisplay(m.getKey());
            int chipW = (int) NanoVgRenderer.textWidth(vg, 10 * S, kd) + 16 * S, chipX = rx + listW - chipW - 8 * S, chipY = ry + (rowH - 15 * S) / 2;
            NanoVgRenderer.roundedRect(vg, chipX, chipY, chipW, 15 * S, 7 * S, binding ? withAlpha(Theme.accentRgb(), 0x66) : Theme.pillOff());
            NanoVgRenderer.text(vg, chipX + chipW / 2f, chipY + 7.5f * S, 10 * S, binding ? Theme.activeText() : Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, kd);
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
            // fast-connect button (you're not on FunTime)
            NanoVgRenderer.text(vg, sx + 2 * S, gy + 6 * S, 11 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Не на сервере. Быстрый вход:");
            int bh = 32 * S, by = gy + 20 * S;
            NanoVgRenderer.shadow(vg, sx, by, w, bh, 11 * S, 12 * S, withAlpha(Theme.accentRgb(), 0x55));
            NanoVgRenderer.gradientRoundedRect(vg, sx, by, w, bh, 11 * S, Theme.accent(), Theme.accent2());
            NanoVgRenderer.text(vg, sx + w / 2f, by + bh / 2f, 13 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "▶  FunTime");
            serverHits.add(new Object[]{ "connect:funtime", sx, by, w, bh, null });
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
                        NanoVgRenderer.text(vg, chipX + chipW / 2f, py + ph / 2f, 9 * S, cap ? Theme.activeText() : Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, kd);
                        serverHits.add(new Object[]{ "subBind", chipX, py, chipW, ph, bs });
                    }
                }
                cur += rh;
            }
            cur += 8 * S;
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
                    if (left >= 0) { col = 0xFF6FCF7F; line = left > 0 ? "● " + r.name + " — идёт, " + left + "с" : "● " + r.name + " — идёт"; }
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
                    NanoVgRenderer.text(vg, chipX + chipW / 2f, chipY + 7 * S, 9 * S, cap ? Theme.activeText() : Theme.accent(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, kd);
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

    // ---- NanoVG Events tab ------------------------------------------------
    private void drawEventsNvg(long vg, int x, int y, int W, int H, int S, int gy, int clipTop, int clipBot, int visH, int margin, float dt) {
        int sx = x + margin, w = W - margin * 2, rowH = 30 * S, gapr = 6 * S;

        com.lume.client.fthw.TelegramEvents.load();
        boolean tg = com.lume.client.fthw.TelegramEvents.available();

        String hdr;
        int n;
        java.util.List<com.lume.client.fthw.TelegramEvents.Ev> evs = null;
        if (tg) {
            long age = com.lume.client.fthw.TelegramEvents.ageSec();
            hdr = "Ивенты всех анархий · Telegram" + (age >= 0 ? " · " + (age < 60 ? age + "с" : (age / 60) + "м") + " назад" : "");
            evs = new ArrayList<>(com.lume.client.fthw.TelegramEvents.events());
            evs.sort((a, b) -> a.anarchy.length() != b.anarchy.length() ? a.anarchy.length() - b.anarchy.length() : a.anarchy.compareTo(b.anarchy));
            n = evs.size();
        } else {
            com.lume.client.fthw.ServerType st = com.lume.client.fthw.ServerType.current();
            hdr = "Ивенты · подключи Telegram в лаунчере (кнопка «Telegram ивенты»). Пока — " + (st == com.lume.client.fthw.ServerType.UNKNOWN ? "нет сервера" : st.display());
            n = EventManager.rules.size();
        }

        int contentH = 22 * S + Math.max(0, n * (rowH + gapr) - gapr);
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        int scrollI = Math.round(scroll);

        NanoVgRenderer.save(vg);
        NanoVgRenderer.scissor(vg, x, clipTop, W, visH);
        int cur = 0;
        NanoVgRenderer.text(vg, sx + 2 * S, gy + cur - scrollI + 7 * S, 11 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, hdr);
        cur += 22 * S;

        if (tg) {
            for (com.lume.client.fthw.TelegramEvents.Ev e : evs) {
                int ry = gy + cur - scrollI;
                if (ry + rowH >= clipTop && ry <= clipBot) {
                    boolean active = !e.phase.isEmpty() && !e.phase.toLowerCase().contains("ожидан");
                    int dotCol = active ? 0xFF6FCF7F : 0xFFE8C15A;
                    NanoVgRenderer.roundedRect(vg, sx, ry, w, rowH, 9 * S, Theme.glassRow());
                    NanoVgRenderer.roundedRect(vg, sx, ry, 3 * S, rowH, 2 * S, dotCol);
                    NanoVgRenderer.text(vg, sx + 12 * S, ry + 11 * S, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, "Анархия " + e.anarchy + "  ·  " + e.name);
                    String sub = e.phase + (e.rarity.isEmpty() ? "" : "  ·  " + e.rarity);
                    NanoVgRenderer.text(vg, sx + 12 * S, ry + 22 * S, 10 * S, active ? 0xFF6FCF7F : Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, sub);
                    if (!e.time.isEmpty() && !e.time.toLowerCase().contains("загруз")) {
                        float tw = NanoVgRenderer.textWidth(vg, 13 * S, e.time);
                        NanoVgRenderer.text(vg, sx + w - tw - 12 * S, ry + rowH / 2f, 13 * S, dotCol, NanoVgRenderer.ALIGN_MIDDLE, e.time);
                    }
                }
                cur += rowH + gapr;
            }
        } else {
            for (com.lume.client.fthw.EventRule r : EventManager.rules) {
                int ry = gy + cur - scrollI;
                if (ry + rowH >= clipTop && ry <= clipBot) {
                    int left = -1;
                    for (EventManager.Active a : EventManager.active) if (a.rule == r) { left = a.secondsLeft(); break; }
                    long eta = r.etaSec(), ago = r.agoSec();
                    int dotCol; String status; int statusCol;
                    if (left >= 0) { dotCol = 0xFF6FCF7F; status = "идёт сейчас"; statusCol = 0xFF6FCF7F; }
                    else if (eta > 0) { dotCol = 0xFFE8C15A; status = "≈ через " + fmtDur(eta); statusCol = 0xFFE8C15A; }
                    else if (ago >= 0) { dotCol = Theme.txtDim(); status = "был " + fmtDur(ago) + " назад"; statusCol = Theme.txtDim(); }
                    else { dotCol = Theme.pillOff(); status = "ещё не видел"; statusCol = Theme.txtDim(); }
                    NanoVgRenderer.roundedRect(vg, sx, ry, w, rowH, 9 * S, Theme.glassRow());
                    NanoVgRenderer.roundedRect(vg, sx, ry, 3 * S, rowH, 2 * S, dotCol);
                    NanoVgRenderer.text(vg, sx + 12 * S, ry + 11 * S, 12 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, r.name);
                    NanoVgRenderer.text(vg, sx + 12 * S, ry + 22 * S, 10 * S, statusCol, NanoVgRenderer.ALIGN_MIDDLE, status);
                    String big = left > 0 ? left + "с" : (eta > 0 ? fmtDur(eta) : "");
                    if (!big.isEmpty()) {
                        float tw = NanoVgRenderer.textWidth(vg, 16 * S, big);
                        NanoVgRenderer.text(vg, sx + w - tw - 12 * S, ry + rowH / 2f, 16 * S, left >= 0 ? 0xFF6FCF7F : 0xFFE8C15A, NanoVgRenderer.ALIGN_MIDDLE, big);
                    }
                }
                cur += rowH + gapr;
            }
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

    // ---- Top navigation bar (Menu / Events / Config / Friends) ---------------

    private void drawTopNavNvg(long vg, int x, int y, int W, int S, int mx, int my, float dt) {
        // Delegates to NavBar so the sliding pill is shared with LumeSubScreen's own
        // Events/Config/Friends bar — one continuous animation across both screen classes
        // instead of ClickGuiScreen owning a separate, unaware copy of the same bar.
        int activeTab = search.isEmpty() ? topSection : -1;
        int[] yh = NavBar.draw(vg, x, y, W, S, activeTab, dt, topNavSegX, topNavSegW);
        topNavSegY = yh[0]; topNavSegH = yh[1];
    }

    // ---- NanoVG Config tab --------------------------------------------------

    private void drawConfigNvg(long vg, int x, int y, int W, int H, int S, int mx, int my, float dt,
                               int gy, int clipTop, int clipBot, int visH, int margin) {
        configHits.clear();
        int sx = x + margin, w = W - margin * 2;
        java.util.List<String> profiles = com.lume.client.util.ConfigProfiles.list();
        String active = com.lume.client.util.ConfigProfiles.activeProfile;

        int rowH = 26 * S, gap = 6 * S;
        int contentH = 22 * S + 28 * S + profiles.size() * (rowH + gap) + 8 * S;
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = Math.max(0f, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        int scrollI = Math.round(scroll);

        NanoVgRenderer.save(vg);
        NanoVgRenderer.scissor(vg, x, clipTop, W, visH);
        int cur = 0;

        NanoVgRenderer.text(vg, sx + 2 * S, gy + cur - scrollI + 8 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Профили · нажми для загрузки");
        cur += 22 * S;

        // Save + New buttons
        int btnH = 22 * S, btnW = (w - 8 * S) / 2;
        int ry = gy + cur - scrollI;
        NanoVgRenderer.gradientRoundedRect(vg, sx, ry, btnW, btnH, 9 * S, Theme.accent(), Theme.accent2());
        NanoVgRenderer.text(vg, sx + btnW / 2f, ry + btnH / 2f, 10 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "Сохранить");
        configHits.add(new Object[]{ "save", active, sx, ry, btnW, btnH });
        NanoVgRenderer.roundedRect(vg, sx + btnW + 8 * S, ry, btnW, btnH, 9 * S, Theme.glassHov());
        NanoVgRenderer.strokeRoundedRect(vg, sx + btnW + 8 * S + 0.5f * S, ry + 0.5f * S, btnW - S, btnH - S, 9 * S, S, Theme.rim());
        NanoVgRenderer.text(vg, sx + btnW + 8 * S + btnW / 2f, ry + btnH / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "Новый профиль");
        configHits.add(new Object[]{ "new", "", sx + btnW + 8 * S, ry, btnW, btnH });
        cur += btnH + 8 * S;

        for (String name : profiles) {
            int ry2 = gy + cur - scrollI;
            if (ry2 + rowH >= clipTop && ry2 <= clipBot) {
                boolean isActive = name.equals(active);
                boolean hov = inside(mx, my, sx, ry2, w, rowH) && my >= clipTop && my <= clipBot;
                NanoVgRenderer.roundedRect(vg, sx, ry2, w, rowH, 9 * S,
                        isActive ? withAlpha(Theme.accentRgb(), 0x44) : (hov ? Theme.glassHov() : Theme.glassRow()));
                if (isActive) NanoVgRenderer.roundedRect(vg, sx, ry2, 3 * S, rowH, 2 * S, Theme.accent());
                NanoVgRenderer.text(vg, sx + 12 * S, ry2 + rowH / 2f, 11 * S,
                        isActive ? Theme.accent() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, name);
                if (isActive) {
                    float aw = NanoVgRenderer.textWidth(vg, 9 * S, "✓ активен");
                    NanoVgRenderer.text(vg, sx + w - (int) aw - 8 * S, ry2 + rowH / 2f, 9 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, "✓ активен");
                }
                configHits.add(new Object[]{ "load", name, sx, ry2, w, rowH });
            }
            cur += rowH + gap;
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

    // ---- NanoVG Friends tab (placeholder) -----------------------------------

    private void drawFriendsNvg(long vg, int x, int y, int W, int H, int S, int gy, int visH, int margin) {
        int sx = x + margin, w = W - margin * 2;
        float font = 12 * S;
        NanoVgRenderer.text(vg, sx + w / 2f, gy + visH / 2f - 14 * S, font, Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "Coming soon");
        NanoVgRenderer.text(vg, sx + w / 2f, gy + visH / 2f + 4 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "Онлайн-статус друзей появится здесь");
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
            RenderUtil.vanillaText(ctx, this.textRenderer, "Клиент поддерживает только FunTime.", sx + 2 * S, gy, Theme.txtDim(), S);
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
                        RenderUtil.vanillaText(ctx, this.textRenderer, kd, chipX + 6 * S, py + 3 * S, cap ? Theme.activeText() : Theme.accent(), S);
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
                    if (left >= 0) { col = 0xFF6FCF7F; line = left > 0 ? "● " + r.name + " — идёт, " + left + "с" : "● " + r.name + " — идёт"; }
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
            RenderUtil.textVCentered(ctx, this.textRenderer, m.getName(), rx + 12 * S, ry, rowH, Theme.txt(), 0.5f * S);
            int chipY = ry + (rowH - 15 * S) / 2;
            // key chip (rightmost)
            String kd = binding ? "press a key…" : keyDisplay(m.getKey());
            int kw = RenderUtil.width(this.textRenderer, kd, 0.46f * S);
            int chipW = kw + 16 * S, chipX = rx + listW - chipW - 8 * S;
            RenderUtil.roundedRect(ctx, chipX, chipY, chipW, 15 * S, 7 * S, binding ? withAlpha(Theme.accentRgb(), 0x66) : Theme.pillOff());
            RenderUtil.textVCentered(ctx, this.textRenderer, kd, chipX + 8 * S, chipY, 15 * S, binding ? Theme.activeText() : Theme.accent(), 0.46f * S);
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
        return HudFrames.list(sw, sh, names);
    }

    private void addFrame(List<int[]> rects, List<String> names, String name, int bx, int by, int w, int h, int sw, int sh) {
        Module m = LumeClient.MODULES.getByName(name);
        if (m == null || !m.isEnabled()) return;
        int[] off = HudLayout.get(name);
        rects.add(new int[]{ bx + off[0], by + off[1], w, h });
        names.add(name);
    }

    /** Add a movable HUD frame gated by an arbitrary visibility flag (for sub-elements). */
    private void addFrameIf(List<int[]> rects, List<String> names, String name, int bx, int by, int w, int h, boolean visible) {
        if (!visible) return;
        int[] off = HudLayout.get(name);
        rects.add(new int[]{ bx + off[0], by + off[1], w, h });
        names.add(name);
    }

    private void drawHudFrames(DrawContext ctx, int S, int mouseX, int mouseY) {
        List<String> names = new ArrayList<>();
        List<int[]> rects = hudFrames(this.width, this.height, names);
        hudSliderTrack = null;
        hudResizeHandle = null;
        var m = ctx.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        for (int i = 0; i < rects.size(); i++) {
            int[] rc = rects.get(i);
            String label = names.get(i);
            boolean sel = label.equals(selectedHud);
            boolean dragging = dragMode == 3 && label.equals(dragHud);
            int rx = rc[0] * S, ry = rc[1] * S, rw = rc[2] * S, rh = rc[3] * S;
            // No idle overlay/label any more — the element itself is the grab target (see
            // tryHudDrag's hit-test against the same rects). Only while actually being dragged
            // does a plain white outline trace its exact bounds, as drag feedback.
            if (dragging) RenderUtil.strokeRect(ctx, rx, ry, rw, rh, Math.max(1, S), 0xFFFFFFFF);

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

            // window-style corner resize handle (independent width/height) — "HUD" panel only for now
            if (sel && label.equals("HUD")) {
                int hs = 7;   // handle size, GUI px
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

    // ---- settings panel rendering -----------------------------------------

    private static final int PREVIEW_H = 46;

    private int settingHeight(Setting s, int S) {
        if (s instanceof SliderSetting) return 22 * S;
        if (s instanceof ModeSetting) return 15 * S;
        if (s instanceof ColorSetting cs) return 15 * S + (cs == openColor ? PAL_H * S : 0);
        return 15 * S; // bool / string
    }

    private int panelHeight(Module m, int S) {
        int h = 4 * S;
        if (m instanceof CustomCrosshair) h += PREVIEW_H * S + 4 * S;
        for (Setting s : m.getSettings()) { if (!s.hidden && !hideCustomHandRightPos(m, s)) h += settingHeight(s, S); }
        if (m instanceof com.lume.client.module.modules.performance.JvmOptimizer) h += 78 * S;
        if (m instanceof com.lume.client.module.modules.render.HitSound) {
            int n = com.lume.client.audio.CustomAudioPlayer.list("hitsound").length;
            h += 16 * S + 26 * S + Math.max(1, n) * 18 * S;
        }
        if (m instanceof com.lume.client.module.modules.render.WorldParticles wp && wp.useMyParticle.value) {
            int n = com.lume.client.fx.ParticleTexture.list(com.lume.client.module.modules.render.WorldParticles.FOLDER).length;
            h += 16 * S + 26 * S + Math.max(1, n) * 18 * S;
        }
        if (m instanceof com.lume.client.module.modules.render.HitParticles hpz && hpz.useMyParticle.value) {
            int n = com.lume.client.fx.ParticleTexture.list(com.lume.client.module.modules.render.HitParticles.FOLDER).length;
            h += 16 * S + 26 * S + Math.max(1, n) * 18 * S;
        }
        if (m instanceof com.lume.client.module.modules.visual.Hud) h += 26 * S;
        if (m instanceof com.lume.client.module.modules.render.CustomHand ch) {
            h += 20 * S + 4 * S;   // hand tabs
            h += 15 * S + 4 * S;   // Idle/Sprint Sway toggle
            boolean showStyle = ch.hand.index == 0;
            h += (showStyle ? 2 : 1) * (15 * S + 4 * S);   // Style (right hand only) + Animation (always)
            if (showStyle && ch.style.index == com.lume.client.module.modules.render.CustomHand.STYLE_CUSTOM) {
                h += 3 * (15 * S + 4 * S);   // Custom Rot X/Y/Z sliders
                h += handPresetsHeight(S);   // saved-preset rows + name field + Save button
            }
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE
                    || ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_TILT)
                h += 15 * S + 4 * S;   // Swing Angle slider
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE)
                h += 15 * S + 4 * S;   // Pivot slider
            h += 2 * (15 * S + 4 * S);   // Outline toggle + Fill mode
            if (ch.outline.value)
                h += 15 * S + (ch.outlineColor == openColor ? PAL_H * S : 0) + 4 * S;   // Outline colour
            if (ch.fill.index != 0)
                h += 15 * S + 4 * S;   // Fill Opacity slider
            if (ch.fill.index == 1)
                h += 15 * S + (ch.fillColor == openColor ? PAL_H * S : 0) + 4 * S;      // Fill colour
            h += 15 * S + 4 * S;   // copy-pose text row
            h += 22 * S + 4 * S;   // paste button
            h += 26 * S;           // reset button
        }
        if (m instanceof Waypoints) h += wpManagerHeight(S);
        if (m instanceof ServerHelper) h += (EventManager.rules.size() + 1) * 12 * S + 6 * S;
        if (m instanceof com.lume.client.module.modules.qol.KeybindManager) h += 2 * (22 * S + 4 * S);
        return h + 8 * S;
    }

    private int wpManagerHeight(int S) {
        return 6 * S + 18 * S + 6 * S + Waypoints.visible().size() * 16 * S;
    }

    private void renderSettings(DrawContext ctx, Module m, int x0, int yTop, int w, int S, int mx, int my, float dt) {
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
            if (s.hidden || hideCustomHandRightPos(m, s)) continue;
            int h = settingHeight(s, S);
            if (s instanceof BoolSetting bs) renderBool(ctx, bs, sx, yy, swid, h, S);
            else if (s instanceof SliderSetting ss) renderSlider(ctx, ss, sx, yy, swid, h, S);
            else if (s instanceof ModeSetting ms) renderMode(ctx, ms, sx, yy, swid, h, S);
            else if (s instanceof StringSetting ts) renderString(ctx, ts, sx, yy, swid, h, S);
            else if (s instanceof ColorSetting cs) renderColor(ctx, cs, sx, yy, swid, S, mx, my, dt);
            yy += h;
        }
        if (m instanceof com.lume.client.module.modules.performance.JvmOptimizer) {
            renderJvmInfo(ctx, sx, yy + 4 * S, swid, S);
        }
        if (m instanceof com.lume.client.module.modules.render.HitSound hs2) {
            RenderUtil.textVCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("My Sounds"), sx, yy, 16 * S, Theme.txtDim(), 0.5f * S);
            yy += 16 * S;
            int bh = 22 * S;
            RenderUtil.roundedRect(ctx, sx, yy, swid, bh, 8 * S, Theme.glassHov());
            RenderUtil.strokeRoundedRect(ctx, sx, yy, swid, bh, 8 * S, Math.max(1, S), Theme.rim());
            RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Open Sounds Folder"), sx, yy, swid, bh, Theme.txt(), 0.53f * S);
            SHit fh = new SHit(); fh.s = null; fh.kind = 9; fh.x = sx; fh.y = yy; fh.w = swid; fh.h = bh; sHits.add(fh);
            yy += bh + 4 * S;

            java.io.File[] files = com.lume.client.audio.CustomAudioPlayer.list("hitsound");
            if (files.length == 0) {
                RenderUtil.textVCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("No files yet — drop an .ogg above"), sx, yy, 18 * S, Theme.txtDim(), 0.47f * S);
                yy += 18 * S;
            } else {
                for (java.io.File f : files) {
                    boolean sel = f.getName().equals(hs2.selectedFile) || (hs2.selectedFile == null && f == files[0]);
                    int rh = 16 * S;
                    RenderUtil.roundedRect(ctx, sx, yy, swid, rh, 5 * S, sel ? withAlpha(Theme.accentRgb(), 0x44) : Theme.glassRow());
                    RenderUtil.textVCentered(ctx, this.textRenderer, f.getName(), sx + 8 * S, yy, rh, sel ? Theme.accent() : Theme.txt(), 0.47f * S);
                    SHit rhit = new SHit(); rhit.s = null; rhit.kind = 11; rhit.tag = f.getName();
                    rhit.x = sx; rhit.y = yy; rhit.w = swid; rhit.h = rh; sHits.add(rhit);
                    yy += rh + 2 * S;
                }
            }
        }
        if (m instanceof com.lume.client.module.modules.render.WorldParticles wp2 && wp2.useMyParticle.value) {
            yy = renderParticlePicker(ctx, com.lume.client.module.modules.render.WorldParticles.FOLDER, "world", wp2.selectedFile, sx, yy, swid, S);
        }
        if (m instanceof com.lume.client.module.modules.render.HitParticles hp2 && hp2.useMyParticle.value) {
            yy = renderParticlePicker(ctx, com.lume.client.module.modules.render.HitParticles.FOLDER, "hit", hp2.selectedFile, sx, yy, swid, S);
        }
        if (m instanceof com.lume.client.module.modules.visual.Hud) {
            int bh = 22 * S;
            RenderUtil.roundedRect(ctx, sx, yy, swid, bh, 8 * S, withAlpha(0xFFE05656, 0x33));
            RenderUtil.strokeRoundedRect(ctx, sx, yy, swid, bh, 8 * S, Math.max(1, S), withAlpha(0xFFE05656, 0x66));
            RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Reset all HUD elements"), sx, yy, swid, bh, 0xFFE05656, 0.53f * S);
            SHit rh = new SHit(); rh.s = null; rh.kind = 10; rh.x = sx; rh.y = yy; rh.w = swid; rh.h = bh; sHits.add(rh);
            yy += bh;
        }
        if (m instanceof com.lume.client.module.modules.render.CustomHand ch) {
            // --- hand tabs: Right | Left ---
            int tabH = 20 * S, tabGap = 4 * S;
            int tabW = (swid - tabGap) / 2;
            String[] tabNames = { com.lume.client.Lang.tUI("Right"), com.lume.client.Lang.tUI("Left") };
            for (int i = 0; i < 2; i++) {
                int tx = sx + i * (tabW + tabGap);
                boolean sel = ch.hand.index == i;
                RenderUtil.roundedRect(ctx, tx, yy, tabW, tabH, 7 * S, sel ? Theme.accent() : Theme.glassRow());
                RenderUtil.textCentered(ctx, this.textRenderer, tabNames[i], tx, yy, tabW, tabH, sel ? Theme.activeText() : Theme.txtDim(), 0.5f * S);
                SHit th = new SHit(); th.s = ch.hand; th.kind = 15; th.channel = i; th.x = tx; th.y = yy; th.w = tabW; th.h = tabH; sHits.add(th);
            }
            yy += tabH + 4 * S;

            renderBool(ctx, ch.sway, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;

            if (ch.hand.index == 0) {
                renderMode(ctx, ch.style, sx, yy, swid, 15 * S, S);
                yy += 15 * S + 4 * S;
                if (ch.style.index == com.lume.client.module.modules.render.CustomHand.STYLE_CUSTOM) {
                    renderSlider(ctx, ch.rRotX, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
                    renderSlider(ctx, ch.rRotY, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
                    renderSlider(ctx, ch.rRotZ, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
                    yy = renderHandPresets(ctx, ch, sx, yy, swid, S);
                }
            }
            renderMode(ctx, ch.animation, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE
                    || ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_TILT) {
                renderSlider(ctx, ch.swingAngle, sx, yy, swid, 15 * S, S);
                yy += 15 * S + 4 * S;
            }
            if (ch.animation.index == com.lume.client.module.modules.render.CustomHand.ANIM_SIMPLE) {
                renderSlider(ctx, ch.swingPivotY, sx, yy, swid, 15 * S, S); yy += 15 * S + 4 * S;
            }

            renderBool(ctx, ch.outline, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;
            renderMode(ctx, ch.fill, sx, yy, swid, 15 * S, S);
            yy += 15 * S + 4 * S;
            if (ch.outline.value) {
                renderColor(ctx, ch.outlineColor, sx, yy, swid, S, mx, my, dt);
                yy += 15 * S + (ch.outlineColor == openColor ? PAL_H * S : 0) + 4 * S;
            }
            if (ch.fill.index != 0) {
                renderSlider(ctx, ch.fillOpacity, sx, yy, swid, 15 * S, S);
                yy += 15 * S + 4 * S;
            }
            if (ch.fill.index == 1) {
                renderColor(ctx, ch.fillColor, sx, yy, swid, S, mx, my, dt);
                yy += 15 * S + (ch.fillColor == openColor ? PAL_H * S : 0) + 4 * S;
            }

            // --- copy current pose as text ---
            {
                int copyH = 15 * S;
                int copyBtnW = 44 * S;
                int textW = swid - copyBtnW - 4 * S;
                RenderUtil.roundedRect(ctx, sx, yy, textW, copyH, 6 * S, Theme.glassRow());
                String exportStr = ch.exportText();
                RenderUtil.textVCentered(ctx, this.textRenderer, exportStr, sx + 6 * S, yy, copyH, Theme.txtDim(), 0.32f * S);
                int copyBtnX = sx + textW + 4 * S;
                RenderUtil.roundedRect(ctx, copyBtnX, yy, copyBtnW, copyH, 6 * S, Theme.glassHov());
                RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Copy"), copyBtnX, yy, copyBtnW, copyH, Theme.accent(), 0.44f * S);
                SHit ch19 = new SHit(); ch19.s = null; ch19.kind = 19; ch19.x = copyBtnX; ch19.y = yy; ch19.w = copyBtnW; ch19.h = copyH; sHits.add(ch19);
                yy += copyH + 4 * S;
            }

            // --- paste a pose from the clipboard ---
            {
                int pasteH = 22 * S;
                RenderUtil.roundedRect(ctx, sx, yy, swid, pasteH, 8 * S, Theme.glassHov());
                RenderUtil.strokeRoundedRect(ctx, sx, yy, swid, pasteH, 8 * S, Math.max(1, S), Theme.rim());
                RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Paste"), sx, yy, swid, pasteH, Theme.accent(), 0.53f * S);
                SHit ph20 = new SHit(); ph20.s = null; ph20.kind = 20; ph20.x = sx; ph20.y = yy; ph20.w = swid; ph20.h = pasteH; sHits.add(ph20);
                yy += pasteH + 4 * S;
            }

            // --- reset ---
            int bh = 22 * S;
            RenderUtil.roundedRect(ctx, sx, yy, swid, bh, 8 * S, withAlpha(0xFFE05656, 0x33));
            RenderUtil.strokeRoundedRect(ctx, sx, yy, swid, bh, 8 * S, Math.max(1, S), withAlpha(0xFFE05656, 0x66));
            RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Reset to default"), sx, yy, swid, bh, 0xFFE05656, 0.53f * S);
            SHit rh = new SHit(); rh.s = null; rh.kind = 12; rh.x = sx; rh.y = yy; rh.w = swid; rh.h = bh; sHits.add(rh);
            yy += bh;
        }
        if (m instanceof Waypoints) renderWaypointManager(ctx, sx, yy + 4 * S, swid, S);
        if (m instanceof ServerHelper) renderEventList(ctx, sx, yy + 4 * S, swid, S);
        if (m instanceof com.lume.client.module.modules.qol.KeybindManager) renderKeybindManagerButtons(ctx, sx, yy, swid, S);
    }

    /** DrawContext mirror of {@link #renderJvmInfoNvg}. */
    private void renderJvmInfo(DrawContext ctx, int x, int y, int w, int S) {
        RenderUtil.roundedRect(ctx, x - 4 * S, y, w + 8 * S, Math.max(1, S), 0, Theme.border());
        int yy = y + 6 * S;

        String arch = com.lume.client.module.modules.performance.JvmOptimizer.is64bit() ? "64-bit ✓" : "32-bit ✗";
        String jv = "Java " + com.lume.client.module.modules.performance.JvmOptimizer.javaVersion() + "  ·  " + arch;
        RenderUtil.vanillaText(ctx, this.textRenderer, jv, x, yy, Theme.txt(), S);
        yy += 14 * S;

        long used = com.lume.client.module.modules.performance.JvmOptimizer.usedMb();
        long max = com.lume.client.module.modules.performance.JvmOptimizer.maxMb();
        RenderUtil.vanillaText(ctx, this.textRenderer, "RAM: " + used + " MB  /  " + max + " MB", x, yy, Theme.txt(), S);
        yy += 14 * S;

        float frac = max > 0 ? (float) used / max : 0f;
        int barH = 5 * S;
        RenderUtil.roundedRect(ctx, x, yy, w, barH, barH / 2, Theme.pillOff());
        int barCol = frac < 0.6f ? 0xFF6FCF7F : frac < 0.8f ? 0xFFE8C15A : 0xFFE05656;
        int fw = Math.max(barH, Math.round(w * frac));
        RenderUtil.roundedRect(ctx, x, yy, fw, barH, barH / 2, barCol);
        yy += barH + 10 * S;

        RenderUtil.vanillaText(ctx, this.textRenderer, "Флаги JVM (рекомендуемые):", x, yy, Theme.txtDim(), S);
        yy += 14 * S;
        RenderUtil.vanillaText(ctx, this.textRenderer, "-Xmx4G -Xms4G -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions", x, yy, Theme.accent(), S);
        yy += 12 * S;
        RenderUtil.vanillaText(ctx, this.textRenderer, "-XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50", x, yy, Theme.accent(), S);
    }

    /** DrawContext mirror of {@link #renderParticlePickerNvg}. */
    private int renderParticlePicker(DrawContext ctx, String folderKey, String tagPrefix, String selectedFile, int sx, int yy, int swid, int S) {
        RenderUtil.textVCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("My Particles"), sx, yy, 16 * S, Theme.txtDim(), 0.5f * S);
        yy += 16 * S;
        int bh = 22 * S;
        RenderUtil.roundedRect(ctx, sx, yy, swid, bh, 8 * S, Theme.glassHov());
        RenderUtil.strokeRoundedRect(ctx, sx, yy, swid, bh, 8 * S, Math.max(1, S), Theme.rim());
        RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Open Particles Folder"), sx, yy, swid, bh, Theme.txt(), 0.53f * S);
        SHit fh = new SHit(); fh.s = null; fh.kind = 16; fh.tag = folderKey; fh.x = sx; fh.y = yy; fh.w = swid; fh.h = bh; sHits.add(fh);
        yy += bh + 4 * S;

        java.io.File[] files = com.lume.client.fx.ParticleTexture.list(folderKey);
        if (files.length == 0) {
            RenderUtil.textVCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("No files yet — drop a .png above"), sx, yy, 18 * S, Theme.txtDim(), 0.47f * S);
            yy += 18 * S;
        } else {
            for (java.io.File f : files) {
                boolean sel = f.getName().equals(selectedFile) || (selectedFile == null && f == files[0]);
                int rh = 16 * S;
                RenderUtil.roundedRect(ctx, sx, yy, swid, rh, 5 * S, sel ? withAlpha(Theme.accentRgb(), 0x44) : Theme.glassRow());
                RenderUtil.textVCentered(ctx, this.textRenderer, f.getName(), sx + 8 * S, yy, rh, sel ? Theme.accent() : Theme.txt(), 0.47f * S);
                SHit rhit = new SHit(); rhit.s = null; rhit.kind = 17; rhit.tag = tagPrefix + ":" + f.getName();
                rhit.x = sx; rhit.y = yy; rhit.w = swid; rhit.h = rh; sHits.add(rhit);
                yy += rh + 2 * S;
            }
        }
        return yy;
    }

    /** DrawContext mirror of {@link #renderHandPresetsNvg}. */
    private int renderHandPresets(DrawContext ctx, com.lume.client.module.modules.render.CustomHand ch, int sx, int yy, int swid, int S) {
        RenderUtil.textVCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Saved Presets"), sx, yy, 12 * S, Theme.txtDim(), 0.5f * S);
        yy += 12 * S;
        for (com.lume.client.module.modules.render.HandPresets.Preset p : com.lume.client.module.modules.render.HandPresets.list) {
            int rh = 16 * S;
            RenderUtil.roundedRect(ctx, sx, yy, swid, rh, 5 * S, Theme.glassRow());
            RenderUtil.textVCentered(ctx, this.textRenderer, p.name, sx + 8 * S, yy, rh, Theme.txt(), 0.47f * S);
            int dx = sx + swid - 14 * S;
            RenderUtil.textVCentered(ctx, this.textRenderer, "✕", dx, yy, rh, 0xFFE05656, 0.5f * S);
            SHit lh = new SHit(); lh.s = null; lh.kind = 23; lh.tag = p.name; lh.x = sx; lh.y = yy; lh.w = swid - 18 * S; lh.h = rh; sHits.add(lh);
            SHit dh = new SHit(); dh.s = null; dh.kind = 24; dh.tag = p.name; dh.x = dx - 3 * S; dh.y = yy; dh.w = 16 * S; dh.h = rh; sHits.add(dh);
            yy += rh + 2 * S;
        }
        renderString(ctx, ch.presetName, sx, yy, swid, 15 * S, S);
        yy += 15 * S + 4 * S;
        int bh = 18 * S;
        RenderUtil.roundedRect(ctx, sx, yy, swid, bh, 6 * S, Theme.accent());
        RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Save current as…"), sx, yy, swid, bh, Theme.activeText(), 0.5f * S);
        SHit sh = new SHit(); sh.s = null; sh.kind = 25; sh.x = sx; sh.y = yy; sh.w = swid; sh.h = bh; sHits.add(sh);
        yy += bh + 4 * S;
        return yy;
    }

    /** DrawContext mirror of {@link #renderKeybindManagerButtonsNvg} — including the {@link
     *  BindPopup} hook, which this method used to be missing entirely (an invisible-modal bug:
     *  BindPopup would still capture input while never being drawn on this render path). Can't
     *  call {@code BindPopup.render(ctx,...)} directly here — this runs inside the window's own
     *  pan/zoom {@code MatrixStack} transform, and BindPopup's own internal sizing assumes plain
     *  untransformed logical px (it's shared with {@code KeybindManagerScreen}, which has no such
     *  transform) — so like the NanoVG path, the local anchor is stashed and rendered in real
     *  screen space after the transform is popped (see {@link #renderBindPopupIfPending}). */
    private void renderKeybindManagerButtons(DrawContext ctx, int sx, int yy, int swid, int S) {
        int bh = 22 * S, gap = 4 * S;
        boolean capturing = bindingMacroKey;
        RenderUtil.roundedRect(ctx, sx, yy, swid, bh, 6 * S, capturing ? Theme.accent() : Theme.glassHov());
        RenderUtil.textCentered(ctx, this.textRenderer, capturing ? com.lume.client.Lang.tUI("Press a key…") : com.lume.client.Lang.tUI("Bind command"),
                sx, yy, swid, bh, capturing ? Theme.activeText() : Theme.txt(), 0.42f * S);
        SHit bh1 = new SHit(); bh1.s = null; bh1.kind = 21; bh1.x = sx; bh1.y = yy; bh1.w = swid; bh1.h = bh; sHits.add(bh1);
        int bindBtnBottom = yy + bh;
        yy += bh + gap;
        RenderUtil.roundedRect(ctx, sx, yy, swid, bh, 6 * S, Theme.glassHov());
        RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Open manager"), sx, yy, swid, bh, Theme.txt(), 0.42f * S);
        SHit bh2 = new SHit(); bh2.s = null; bh2.kind = 22; bh2.x = sx; bh2.y = yy; bh2.w = swid; bh2.h = bh; sHits.add(bh2);

        if (BindPopup.isActive()) {
            bindPopupPending = true;
            bindPopupLocalX = sx;
            bindPopupLocalY = bindBtnBottom + gap;
        } else {
            bindPopupPending = false;
        }
    }

    /** Converts the window-local anchor {@link #renderKeybindManagerButtons} stashed into real
     *  screen px (same window-local -> framebuffer formula used for the panel rect throughout
     *  this file: {@code winOffX*S + cx + total*(local-cx)}) and draws BindPopup there — call
     *  once per frame, AFTER the window's MatrixStack transform has been popped (or, on the
     *  NanoVG path, after the NanoVG frame itself has closed — see renderNvgMain). Deliberately
     *  NOT zoom-scaled with the window, same as the NanoVG path's version of this bridge. */
    private void renderBindPopupIfPending(DrawContext ctx, double cx, double cy, float total, int S, int mouseX, int mouseY, float dt) {
        if (!bindPopupPending || !BindPopup.isActive()) return;
        double sx0 = winOffX * S + cx + total * (bindPopupLocalX - cx);
        double sy0 = winOffY * S + cy + total * (bindPopupLocalY - cy);
        BindPopup.render(ctx, (int) Math.round(sx0 / S), (int) Math.round(sy0 / S), this.width, this.height, mouseX, mouseY, dt);
    }

    /** Read-only list of configured FT/HW events (with live countdown if active). Cyrillic → vanilla font. */
    private void renderEventList(DrawContext ctx, int sx, int yy, int swid, int S) {
        RenderUtil.vanillaText(ctx, this.textRenderer, "Ивенты (FunTime):", sx, yy, Theme.txtDim(), S);
        int ly = yy + 12 * S;
        for (EventRuleName er : eventRows()) {
            int col = er.left >= 0 ? 0xFF6FCF7F : Theme.txt();
            String line = er.left > 0 ? er.name + " — " + er.left + "с" : er.name;
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
        return "name".equals(id) ? wpName : wpCoords;
    }
    private void bufSet(String id, String v) {
        if ("name".equals(id)) wpName = v; else wpCoords = v;
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
        int coordsW = swid - nameW - addW - 2 * 4 * S;
        int fx = sx;
        field(ctx, "name", fx, yy, nameW, rh, "name", S); fx += nameW + 4 * S;
        field(ctx, "coords", fx, yy, coordsW, rh, "x y z", S);
        int addX = sx + swid - addW;
        RenderUtil.roundedRect(ctx, addX, yy, addW, rh, 5 * S, Theme.accent());
        int aw = RenderUtil.width(this.textRenderer, "Add", 0.42f * S);
        RenderUtil.textVCentered(ctx, this.textRenderer, "Add", addX + (addW - aw) / 2, yy, rh, Theme.activeText(), 0.42f * S);
        wpHits.add(new Object[]{ "add", addX, yy, addW, rh });

        int ly = yy + 18 * S + 6 * S;
        List<Waypoints.WP> vis = Waypoints.visible();
        for (int i = 0; i < vis.size(); i++) {
            Waypoints.WP w = vis.get(i);
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
        String trimmed = wpCoords.trim();
        if (!trimmed.isEmpty()) {
            String[] parts = trimmed.split("[,\\s]+");
            if (parts.length != 3) return;
            try { px = Double.parseDouble(parts[0]); py = Double.parseDouble(parts[1]); pz = Double.parseDouble(parts[2]); }
            catch (Exception e) { return; }
        } else if (this.client.player != null) {
            px = this.client.player.getX(); py = this.client.player.getY(); pz = this.client.player.getZ();
        } else return;
        String name = wpName.isEmpty() ? "WP" + (Waypoints.visible().size() + 1) : wpName;
        Waypoints.add(name, px, py, pz, Waypoints.nextColor());
        wpName = wpCoords = ""; focusedField = null;
        com.lume.client.Config.save();
    }

    private void handleWpHit(String kind) {
        if (kind.equals("add")) { wpAdd(); return; }
        if (kind.startsWith("del:")) {
            int i = Integer.parseInt(kind.substring(4));
            List<Waypoints.WP> vis = Waypoints.visible();
            if (i >= 0 && i < vis.size()) { Waypoints.list.remove(vis.get(i)); com.lume.client.Config.save(); }
            return;
        }
        if (kind.startsWith("color:")) {
            int i = Integer.parseInt(kind.substring(6));
            List<Waypoints.WP> vis = Waypoints.visible();
            if (i >= 0 && i < vis.size()) { vis.get(i).color = Waypoints.nextColor(); com.lume.client.Config.save(); }
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

    private void renderString(DrawContext ctx, StringSetting ts, int x, int y, int w, int h, int S) {
        RenderUtil.textVCentered(ctx, this.textRenderer, ts.name, x, y, h, Theme.txt(), 0.42f * S);
        boolean foc = "customstring".equals(focusedField) && focusedString == ts;
        int bw = Math.round(w * 0.55f), bh = 11 * S, bx = x + w - bw, by = y + (h - bh) / 2;
        RenderUtil.roundedRect(ctx, bx, by, bw, bh, 4 * S, foc ? Theme.glassHov() : Theme.glassRow());
        String shown = ts.value + (foc ? "|" : "");
        RenderUtil.textVCentered(ctx, this.textRenderer, shown, bx + 6 * S, by, bh, Theme.txt(), 0.38f * S);
        SHit hit = new SHit(); hit.s = ts; hit.kind = 18; hit.x = bx; hit.y = y; hit.w = bw; hit.h = h; sHits.add(hit);
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

    /** DrawContext mirror of {@link #renderColorNvg} (name, Accent toggle pill, swatch → opens
     *  the HSV/hue/hex palette below). This used to be a different, simpler design (just an
     *  Accent on/off switch + always-visible R/G/B sliders, no palette) that reused kind=2 for
     *  the switch — but the shared click handler's kind=2 case has always meant "open/close the
     *  colour palette" (see handleSettingClick), not "toggle accent", so that switch silently did
     *  nothing visible when clicked (toggled `openColor` with nothing drawn for it) while real
     *  Accent-toggle kind=13 clicks were unreachable. Rebuilt to match the NanoVG version exactly
     *  — the click/drag handlers for kinds 2/3/6/7/8/13 already exist and are shared, they just
     *  had no matching UI to draw them from on this render path. */
    private void renderColor(DrawContext ctx, ColorSetting cs, int x, int y, int w, int S, int mx, int my, float dt) {
        int row = 15 * S;
        RenderUtil.textVCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI(cs.name), x, y, row, Theme.txt(), 0.42f * S);

        int sw = 30 * S, sh = 11 * S, sxb = x + w - sw, syb = y + (row - sh) / 2;
        int aw = 42 * S, agap = 4 * S, axb = sxb - agap - aw;
        boolean acc = cs.accent;
        RenderUtil.roundedRect(ctx, axb, syb, aw, sh, 4 * S, acc ? withAlpha(Theme.accentRgb(), 0x55) : Theme.glassRow());
        float[] aa = animFor("colacc:" + cs.name);
        aa[0] = approach(aa[0], inside(mx, my, axb, syb, aw, sh) ? 1f : 0f, 14f, dt);
        int accRim = acc ? Theme.accent() : withAlpha(0xFFFFFF, Math.round(0x30 + 0x40 * aa[0]));
        RenderUtil.strokeRoundedRect(ctx, axb, syb, aw, sh, 4 * S, Math.max(1, S), accRim);
        RenderUtil.textCentered(ctx, this.textRenderer, com.lume.client.Lang.tUI("Accent"), axb, syb, aw, sh, acc ? Theme.accent() : Theme.txtDim(), 0.36f * S);
        SHit accHit = new SHit(); accHit.s = cs; accHit.kind = 13; accHit.x = axb; accHit.y = y; accHit.w = aw; accHit.h = row; sHits.add(accHit);

        RenderUtil.roundedRect(ctx, sxb, syb, sw, sh, 4 * S, 0xFF000000 | (acc ? Theme.accentRgb() & 0xFFFFFF : cs.rgb()));
        float[] swa = animFor("colsw:" + cs.name);
        swa[0] = approach(swa[0], inside(mx, my, sxb, syb, sw, sh) ? 1f : 0f, 14f, dt);
        int swRim = cs == openColor ? Theme.accent() : withAlpha(0xFFFFFF, Math.round(0x30 + 0x40 * swa[0]));
        RenderUtil.strokeRoundedRect(ctx, sxb, syb, sw, sh, 4 * S, Math.max(1, S), swRim);
        SHit open = new SHit(); open.s = cs; open.kind = 2; open.x = sxb; open.y = y; open.w = sw; open.h = row; sHits.add(open);

        if (cs != openColor) return;

        // --- HSV picker: saturation/value square + hue bar ---
        int py = y + row + 4 * S;
        int hueW = 12 * S, gap2 = 4 * S;
        int sqW = w - hueW - gap2, sqH = 52 * S;
        int hueColor = 0xFF000000 | hsvToRgb(pickH, 1f, 1f);
        RenderUtil.roundedRect(ctx, x, py, sqW, sqH, 3 * S, hueColor);
        RenderUtil.hGradientRect(ctx, x, py, sqW, sqH, 0xFFFFFFFF, 0x00FFFFFF);
        ctx.fillGradient(x, py, x + sqW, py + sqH, 0x00000000, 0xFF000000);
        RenderUtil.strokeRoundedRect(ctx, x, py, sqW, sqH, 3 * S, Math.max(1, S), Theme.rim());
        float curX = x + pickS * sqW, curY = py + (1f - pickV) * sqH;
        RenderUtil.roundedRect(ctx, Math.round(curX - 3.5f * S), Math.round(curY - 3.5f * S), Math.round(7 * S), Math.round(7 * S), Math.round(3.5f * S), 0xFF000000);
        RenderUtil.roundedRect(ctx, Math.round(curX - 2.5f * S), Math.round(curY - 2.5f * S), Math.round(5 * S), Math.round(5 * S), Math.round(2.5f * S), 0xFFFFFFFF);
        SHit sv = new SHit(); sv.s = cs; sv.kind = 7; sv.x = x; sv.y = py; sv.w = sqW; sv.h = sqH; sHits.add(sv);

        // hue bar (vertical rainbow, 6 segments)
        int hx = x + sqW + gap2;
        int[] hueStops = { 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000 };
        float seg = sqH / 6f;
        for (int i = 0; i < 6; i++) {
            int yA = py + Math.round(i * seg), yB = py + Math.round((i + 1) * seg);
            ctx.fillGradient(hx, yA, hx + hueW, yB, hueStops[i], hueStops[i + 1]);
        }
        RenderUtil.strokeRoundedRect(ctx, hx, py, hueW, sqH, 2 * S, Math.max(1, S), Theme.rim());
        float hueY = py + (pickH / 360f) * sqH;
        RenderUtil.roundedRect(ctx, Math.round(hx - 2 * S), Math.round(hueY - 1.5f * S), Math.round(hueW + 4 * S), Math.round(3 * S), Math.round(1.5f * S), 0xFFFFFFFF);
        SHit hb = new SHit(); hb.s = cs; hb.kind = 8; hb.x = hx; hb.y = py; hb.w = hueW; hb.h = sqH; sHits.add(hb);

        // hex field
        int hy = py + sqH + 4 * S, hh = 14 * S;
        boolean foc = "colorhex".equals(focusedField) && openColor == cs;
        RenderUtil.roundedRect(ctx, x, hy, w, hh, 5 * S, foc ? Theme.glassHov() : Theme.glassRow());
        RenderUtil.strokeRoundedRect(ctx, x, hy, w, hh, 5 * S, Math.max(1, S), foc ? Theme.accent() : Theme.rim());
        String shown = "#" + (foc ? colorHex + "|" : String.format("%06X", cs.rgb() & 0xFFFFFF));
        RenderUtil.textVCentered(ctx, this.textRenderer, shown, x + 8 * S, hy, hh, Theme.txt(), 0.47f * S);
        SHit hex = new SHit(); hex.s = cs; hex.kind = 6; hex.x = x; hex.y = hy; hex.w = w; hex.h = hh; sHits.add(hex);
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
            if (BindPopup.isActive()) {
                // BindPopup now renders in real screen space (see renderNvgMain), not
                // window-local — so hit-testing uses the raw mouse position too, not mlx/mly.
                boolean consumed = BindPopup.mouseClicked((int) mouseX, (int) mouseY);
                if (consumed) return true;
                // click landed outside the popup — let it fall through to close/absorb normally
                // below (matches every other capture-state's "click elsewhere cancels" behaviour).
                BindPopup.close();
            }
            int W = WIN_W * S, H = WIN_H * S;
            int wx = (this.width * S - W) / 2, wy = (this.height * S - H) / 2;
            boolean inWindow = mlx >= wx && mlx <= wx + W && mly >= wy && mly <= wy + H;
            focusedField = null;   // any click defocuses; specific handlers below re-focus

            // top nav bar (above the glass window) — each tab opens its own screen
            if (topNavSegH > 0 && mly >= topNavSegY - 3 * S && mly <= topNavSegY + topNavSegH + 3 * S) {
                for (int i = 0; i < 4; i++) {
                    if (topNavSegX[i] != 0 && inside(mlx, mly, topNavSegX[i], topNavSegY, topNavSegW[i], topNavSegH)) {
                        switch (i) {
                            case 1 -> this.client.setScreen(new EventsScreen(this));
                            case 2 -> this.client.setScreen(new ConfigScreen(this));
                            case 3 -> this.client.setScreen(new FriendsScreen(this));
                        }
                        return true;
                    }
                }
            }

            // config tab clicks
            if (isConfigTab() && inWindow && mly >= lastClipTop && mly <= lastClipBot) {
                for (Object[] h : configHits) {
                    if (!inside(mlx, mly, (int) h[2], (int) h[3], (int) h[4], (int) h[5])) continue;
                    String kind = (String) h[0];
                    String name = (String) h[1];
                    if (kind.equals("load")) {
                        com.lume.client.util.ConfigProfiles.activeProfile = name;
                        if (!com.lume.client.util.ConfigProfiles.loadActive()) {
                            com.lume.client.util.ConfigProfiles.saveActive();
                        }
                    } else if (kind.equals("save")) {
                        com.lume.client.util.ConfigProfiles.saveActive();
                        com.lume.client.Config.save();
                    } else if (kind.equals("new")) {
                        com.lume.client.util.ConfigProfiles.activeProfile = "profile_" + System.currentTimeMillis();
                        com.lume.client.util.ConfigProfiles.saveActive();
                    }
                    return true;
                }
            }

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
                if (inside(mlx, mly, themeBtn[0], themeBtn[1], themeBtn[2], themeBtn[3])) { Theme.toggle(); animFor("_theme")[2] = 1f; ThemeSync.save(); return true; }
                if (inside(mlx, mly, colorsBtn[0], colorsBtn[1], colorsBtn[2], colorsBtn[3])) { animFor("_colors")[2] = 1f; if (client != null) client.setScreen(new ColorsScreen(this)); return true; }
                for (int i = 0; i < segX.length; i++) {
                    if (inside(mlx, mly, segX[i], segY - 3 * S, segW[i], segH + 6 * S)) { selectedCat = i; search = ""; scroll = scrollTarget = 0f; bindingModule = null; bindingSetting = null; bindingQuickCmd = null; bindingMacroKey = false; return true; }
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
                        if (inside(mlx, mly, h.x, h.y, h.w, h.h)) { handleSettingClick(h, mlx, mly); return true; }
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

            // 1) corner resize handle of the selected element (window-style, independent w/h)
            if (selectedHud != null && hudResizeHandle != null
                    && inside(mouseX, mouseY, hudResizeHandle[0] - 3, hudResizeHandle[1] - 3, hudResizeHandle[2] + 6, hudResizeHandle[3] + 6)) {
                List<String> nn = new ArrayList<>();
                List<int[]> rr = hudFrames(this.width, this.height, nn);
                int idx = nn.indexOf(selectedHud);
                int[] cur = idx >= 0 ? rr.get(idx) : new int[]{0, 0, 134, 60};
                hudResizeBase = new int[]{ cur[2], cur[3] };
                dragMode = 6; dragHud = selectedHud; grabMx = mouseX; grabMy = mouseY;
                return true;
            }

            // 2) size slider of the selected element
            if (selectedHud != null && hudSliderTrack != null
                    && inside(mouseX, mouseY, hudSliderTrack[0], hudSliderTrack[1], hudSliderTrack[2], hudSliderTrack[3] + 4)) {
                dragMode = 5;
                updateHudSize(mouseX);
                return true;
            }

            // 3) a HUD element frame: select + (double-click resets) + start move
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

    private void handleSettingClick(SHit h, double mxNative, double myNative) {
        switch (h.kind) {
            case 0 -> {
                BoolSetting bs = (BoolSetting) h.s;
                bs.value = !bs.value;
                Module hudM = LumeClient.MODULES.getByName("HUD");
                if (hudM instanceof com.lume.client.module.modules.visual.Hud hud
                        && (bs == hud.fps || bs == hud.coords || bs == hud.ping || bs == hud.dayCounter
                            || bs == hud.cps || bs == hud.speed || bs == hud.clock)
                        && bs.value && hud.activeLineCount() > 3) {
                    hudWarnUntil = System.currentTimeMillis() + 2500;
                }
            }
            case 2 -> { // open / close the colour palette
                ColorSetting cs = (ColorSetting) h.s;
                if (openColor == cs) { openColor = null; }
                else { openColor = cs; openPicker(cs); }
                if ("colorhex".equals(focusedField)) focusedField = null;
            }
            case 13 -> ((ColorSetting) h.s).accent = !((ColorSetting) h.s).accent;   // "Accent" toggle pill
            case 18 -> { focusedString = (StringSetting) h.s; focusedField = "customstring"; }   // focus a text setting
            case 1, 3 -> { activeSlider = h; updateSlider(h, mxNative); }
            case 4 -> {
                ((ModeSetting) h.s).cycle(mxNative < h.x + h.w / 2.0 ? -1 : 1);
                // Picking a Custom Hand Style also nudges Pos to that style's suggested
                // starting position — a one-time convenience, not a continuous override
                // (the Pos sliders remain the single source of truth afterward).
                Module chM4 = LumeClient.MODULES.getByName("Custom Hand");
                if (chM4 instanceof com.lume.client.module.modules.render.CustomHand ch4 && h.s == ch4.style) {
                    ch4.applyStylePosPreset();
                }
            }
            case 7, 8 -> { // SV square / hue bar drag start
                activePicker = h;
                updatePicker(h, mxNative, myNative);
            }
            case 6 -> { // focus the hex field
                openColor = (ColorSetting) h.s;
                openPicker(openColor);
                focusedField = "colorhex";
                colorHex = String.format("%06X", openColor.rgb() & 0xFFFFFF);
            }
            case 9 -> { // HitSound "Open Sounds Folder"
                java.io.File dir = com.lume.client.audio.CustomAudioPlayer.folder("hitsound").toFile();
                net.minecraft.util.Util.getOperatingSystem().open(dir);
            }
            case 10 -> { // HUD "Reset all HUD elements"
                HudLayout.resetAll();
                com.lume.client.Config.save();
            }
            case 11 -> { // "My Sounds" file row — select which dropped-in file to use
                Module hsM = LumeClient.MODULES.getByName("HitSound");
                if (hsM instanceof com.lume.client.module.modules.render.HitSound hs2) hs2.selectedFile = h.tag;
            }
            case 12 -> { // Custom Hand "Reset to default"
                Module chM = LumeClient.MODULES.getByName("Custom Hand");
                if (chM instanceof com.lume.client.module.modules.render.CustomHand ch) ch.resetToDefaults();
                com.lume.client.Config.save();
            }
            case 19 -> { // Custom Hand "Copy" — current pose as text, to the system clipboard
                Module chM5 = LumeClient.MODULES.getByName("Custom Hand");
                if (chM5 instanceof com.lume.client.module.modules.render.CustomHand ch5) {
                    this.client.keyboard.setClipboard(ch5.exportText());
                }
            }
            case 20 -> { // Custom Hand "Paste" — parse a pose from the clipboard (same format as Copy)
                Module chM6 = LumeClient.MODULES.getByName("Custom Hand");
                if (chM6 instanceof com.lume.client.module.modules.render.CustomHand ch6) {
                    String clip = this.client.keyboard.getClipboard();
                    if (clip != null && ch6.importText(clip)) {
                        com.lume.client.Config.save();
                        Notifications.push("Pose pasted", Theme.accent(), 1500);
                    } else {
                        Notifications.push("Clipboard isn't a valid pose", 0xFFE05656, 2000);
                    }
                }
            }
            case 15 -> ((ModeSetting) h.s).index = h.channel;   // Custom Hand hand tab / preset slot pill
            case 16 -> { // "Open Particles Folder" (World Particles / Hit Particles)
                java.io.File dir = com.lume.client.fx.ParticleTexture.folder(h.tag).toFile();
                net.minecraft.util.Util.getOperatingSystem().open(dir);
            }
            case 17 -> { // "My Particles" file row — tag is "world:name.png" or "hit:name.png"
                int sep = h.tag.indexOf(':');
                String owner = h.tag.substring(0, sep), file = h.tag.substring(sep + 1);
                if (owner.equals("world")) {
                    Module wpM = LumeClient.MODULES.getByName("World Particles");
                    if (wpM instanceof com.lume.client.module.modules.render.WorldParticles wp) wp.selectedFile = file;
                } else {
                    Module hpM = LumeClient.MODULES.getByName("Hit Particles");
                    if (hpM instanceof com.lume.client.module.modules.render.HitParticles hp) hp.selectedFile = file;
                }
            }
            case 21 -> bindingMacroKey = !bindingMacroKey;   // KeybindManager "Bind command" — click again to cancel
            case 22 -> { if (client != null) client.setScreen(new com.lume.client.gui.KeybindManagerScreen(this, null)); }
            case 23 -> {   // Custom Hand — load a saved preset (tag = preset name)
                Module chM7 = LumeClient.MODULES.getByName("Custom Hand");
                if (chM7 instanceof com.lume.client.module.modules.render.CustomHand ch7) {
                    com.lume.client.module.modules.render.HandPresets.Preset p = com.lume.client.module.modules.render.HandPresets.byName(h.tag);
                    if (p != null) { ch7.loadPreset(p); com.lume.client.Config.save(); }
                }
            }
            case 24 -> { com.lume.client.module.modules.render.HandPresets.remove(h.tag); com.lume.client.Config.save(); }   // delete preset
            case 25 -> {   // "Save current as…"
                Module chM8 = LumeClient.MODULES.getByName("Custom Hand");
                if (chM8 instanceof com.lume.client.module.modules.render.CustomHand ch8) ch8.saveCurrentAsPreset();
            }
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
                case 6 -> {
                    if (dragHud != null && hudResizeBase != null) {
                        int w = hudResizeBase[0] + (int) Math.round(mouseX - grabMx);
                        int h = hudResizeBase[1] + (int) Math.round(mouseY - grabMy);
                        HudLayout.setSize(dragHud, w, h);
                    }
                    return true;
                }
            }
            if (activePicker != null) { updatePicker(activePicker, localMx(mouseX), localMy(mouseY)); return true; }
            if (activeSlider != null) { updateSlider(activeSlider, localMx(mouseX)); return true; }
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) { dragMode = 0; dragHud = null; activeSlider = null; activePicker = null; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * 30 * sf();
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (BindPopup.isActive()) { BindPopup.charTyped(chr); return true; }
        if (bindingModule != null || bindingSetting != null || bindingQuickCmd != null || bindingMacroKey) return true;   // consume while capturing a bind
        if (!(chr >= 32 && chr != 127)) return super.charTyped(chr, modifiers);
        if ("colorhex".equals(focusedField)) {
            char up = Character.toUpperCase(chr);
            if (((up >= '0' && up <= '9') || (up >= 'A' && up <= 'F')) && colorHex.length() < 6) {
                colorHex += up; applyHex();
            }
            return true;
        }
        if ("search".equals(focusedField)) { search += chr; scroll = scrollTarget = 0f; return true; }
        if ("customstring".equals(focusedField)) {
            if (focusedString != null) focusedString.value += chr;
            return true;
        }
        if (focusedField != null) {                         // waypoint fields
            boolean coordsField = focusedField.equals("coords");
            boolean ok = focusedField.equals("name")
                    || (chr >= '0' && chr <= '9') || chr == '-' || chr == '.'
                    || (coordsField && (chr == ' ' || chr == ','));
            if (ok) bufSet(focusedField, bufGet(focusedField) + chr);
            return true;
        }
        return super.charTyped(chr, modifiers);             // nothing focused → don't capture
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // BindPopup open (COMMAND typing / FUNCTION search) — let it handle Esc/Backspace/Enter first.
        if (BindPopup.isActive() && !bindingMacroKey) {
            if (BindPopup.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
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
        // KeybindManager "Bind command" — capture the key, then open BindPopup right here
        // (anchored under the button itself, no full-screen takeover — see
        // renderKeybindManagerButtonsNvg).
        if (bindingMacroKey) {
            bindingMacroKey = false;
            if (keyCode != 256) BindPopup.open(keyCode, com.lume.client.gui.KeybindManagerScreen.labelFor(keyCode));   // Esc = cancel
            return true;
        }
        if ("colorhex".equals(focusedField)) {
            if (keyCode == 256) { focusedField = null; return true; }                                    // Esc
            if (keyCode == 257 || keyCode == 335) { applyHex(); focusedField = null; return true; }      // Enter
            if (keyCode == 259 && !colorHex.isEmpty()) { colorHex = colorHex.substring(0, colorHex.length() - 1); applyHex(); return true; }
            if (keyCode == 86 && (modifiers & 0x0002) != 0) {                                             // Ctrl+V paste (GLFW_MOD_CONTROL)
                String clip = this.client.keyboard.getClipboard();
                if (clip != null) {
                    StringBuilder sb = new StringBuilder();
                    for (char c : clip.toUpperCase().toCharArray())
                        if (((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) && sb.length() < 6) sb.append(c);
                    colorHex = sb.toString(); applyHex();
                }
                return true;
            }
            return true;
        }
        if ("search".equals(focusedField)) {
            if (keyCode == 256) { focusedField = null; return true; }                                   // Esc
            if (keyCode == 259 && !search.isEmpty()) { search = search.substring(0, search.length() - 1); scroll = scrollTarget = 0f; }
            return true;
        }
        if ("customstring".equals(focusedField)) {
            if (keyCode == 256 || keyCode == 257 || keyCode == 335) { focusedField = null; com.lume.client.Config.save(); return true; }   // Esc/Enter
            if (focusedString != null) {
                if (keyCode == 259 && !focusedString.value.isEmpty()) focusedString.value = focusedString.value.substring(0, focusedString.value.length() - 1);
                if (keyCode == 86 && (modifiers & 0x0002) != 0) {
                    String clip = this.client.keyboard.getClipboard();
                    if (clip != null) focusedString.value = clip.trim();
                }
            }
            return true;
        }
        if (focusedField != null) {                         // waypoint fields
            if (keyCode == 256) { focusedField = null; return true; }
            if (keyCode == 257 || keyCode == 335) { wpAdd(); return true; }
            if (keyCode == 259) { String c = bufGet(focusedField); if (!c.isEmpty()) bufSet(focusedField, c.substring(0, c.length() - 1)); return true; }
            if (keyCode == 86 && (modifiers & 0x0002) != 0) {   // Ctrl+V — paste "x y z" straight into the coords field
                String clip = this.client.keyboard.getClipboard();
                if (clip != null) bufSet(focusedField, clip.trim());
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}

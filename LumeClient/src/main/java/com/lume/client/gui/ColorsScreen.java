package com.lume.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * In-game "Customize Colors" — the SAME theme.json the LumeLauncher's own Customize
 * Colors screen edits (see ThemeSync), laid out to match it row-for-row: title+subtitle,
 * Light/Dark, Launcher/In-Game Menu preview tabs, a live-animated preview box, Background +
 * Accent/Glow swatch rows, Styles, and Reset to Defaults. A swatch click opens a fully
 * custom NanoVG colour picker (HSV square + hue strip + hex field) — an earlier attempt
 * bridged to Swing's JColorChooser on a background thread, but that never reliably opened
 * in-game (AWT-in-LWJGL is fragile: headless flags, focus/owner issues, no way to debug it
 * without a running client), so this owns the whole picker itself with zero AWT dependency.
 * Dragging in the square/strip or typing a hex value applies live via
 * {@link Theme#setCustomBg}/{@link Theme#setCustomAccent}, same as before.
 */
public class ColorsScreen extends Screen {

    private final Screen parent;
    private boolean editingDark;
    private int previewTab = 1;   // 0 = Launcher, 1 = In-Game Menu
    private long lastFrame = System.currentTimeMillis();
    private final long openTime = System.currentTimeMillis();

    // Preview-only hover animation (visual parity with the real hover glow — these
    // buttons are never clickable, no hit-rects are ever registered for them).
    private final float[] mockCardHover = new float[4];
    private float mockPlayHover = 0f;

    private static final int WIN_W = 300, WIN_H = 572;

    // Window drag — static (like ClickGuiScreen's own winOffX/winOffY) so the position
    // survives closing/reopening Customize Colors within the same session, in GUI px.
    private static int offX = 0, offY = 0;
    private boolean dragging = false;
    private double grabMx, grabMy;
    private int grabOffX, grabOffY;
    private int winX, winY, winW, winH;   // current framebuffer-px window rect, cached each render() for hit-testing

    private int[] closeRect;
    private int[] resetRect;
    private int[][] modeRects = new int[2][];
    private int[][] previewTabRects = new int[2][];
    private int[][] swatchRects = new int[3][];     // 0 = bg, 1 = accent, 2 = button text
    private int[][] styleRects = new int[3][];

    // Custom HSV colour picker popover state
    private int openPicker = -1;     // -1 = closed, 0 = editing bg, 1 = editing accent, 2 = editing button text
    private float pickerHue, pickerSat, pickerVal;
    private String pickerHex = "";
    private boolean pickerHexFocused = false;
    private int draggingWhat = 0;    // 0 = none, 1 = SV square, 2 = hue strip
    private int[] svRect, hueRect, hexRect, pickerRect;

    // Premium Glass tuning sliders (0 = blur, 1 = distortion)
    private int[][] glassSliderRects = new int[2][];
    private int draggingGlassSlider = -1;

    // In-game ClickGUI window-size slider
    private int[] menuSizeSliderRect;
    private boolean draggingMenuSize = false;

    public ColorsScreen(Screen parent) {
        super(Text.literal("Lume — Customize Colors"));
        this.parent = parent;
        this.editingDark = Theme.isDark();
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private int sf() { return (int) Math.max(1, client.getWindow().getScaleFactor()); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Re-render whatever screen was open before us as the backdrop — without this,
        // opening Colors REPLACES the active screen entirely, so at the title screen
        // (no 3D world to fall back on) there's nothing behind our small window but the
        // raw black framebuffer clear, which reads as a hard black ring around the panel.
        // mouseX/mouseY -1 so the backdrop's own hover states don't light up underneath us.
        // Guarded so a parent that fails to render can never take the Colors window down
        // with it (worst case: no backdrop + a log line, not an invisible/broken screen).
        if (parent != null) {
            try {
                parent.render(ctx, -1, -1, delta);
            } catch (Throwable t) {
                System.out.println("[Lume] Colors backdrop render failed: " + t);
            }
        }

        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int S = sf();
        int W = WIN_W, H = WIN_H;
        int x = (width - W) / 2 + offX, y = (height - H) / 2 + offY;
        winX = x; winY = y; winW = W; winH = H;
        float p = openAnim();
        int r = 16;

        if (Theme.getGlassStyle() == 1) {
            com.lume.client.nanovg.GlassRenderer.panel(x * S, y * S, W * S, H * S, r * S, Theme.getGlassBlur(), Theme.getGlassDistort());
        }

        try {
            drawWindow(ctx, x, y, W, H, mouseX, mouseY, dt, p);
        } catch (Throwable t) {
            System.out.println("[Lume] ColorsScreen render failed: " + t);
        }
        // Blur-dissolve open — fade + defocus coming into focus, NO size change (the old
        // version scale-zoomed from 94%). Same pattern as the catalog-switch transition.
        if (p < 1f) com.lume.client.nanovg.GlassRenderer.transitionOverlay(x * S, y * S, W * S, H * S, (1f - p) * 0.8f, 1f - p);
    }

    /** Multiplies an ARGB color's alpha by {@code p} — the DrawContext equivalent of NanoVG's
     *  {@code globalAlpha}, which has no per-call analogue here so each draw bakes it in. */
    private static int fade(int argb, float p) {
        int a = Math.round(((argb >>> 24) & 0xFF) * p);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Eased 0→1 open progress over 180ms (smoothstep-ish ease-out, same family as
     *  LumeSubScreen's own window-open animation). */
    private float openAnim() {
        float p = (System.currentTimeMillis() - openTime) / 180f;
        if (p >= 1f) return 1f;
        if (p <= 0f) return 0f;
        return 1f - (1f - p) * (1f - p);
    }

    private void drawWindow(DrawContext ctx, int x, int y, int W, int H, int mx, int my, float dt, float p) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int r = 16;
        RenderUtil.glow(ctx, x, y, W, H, r, 0x000000, 3);
        RenderUtil.roundedRect(ctx, x, y, W, H, r, fade(Theme.winTop(), p));
        RenderUtil.strokeRoundedRect(ctx, x, y, W, H, r, 1, fade(Theme.rim(), p));

        int pad = 14;
        RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Customize Colors"), x, y + 14, W, 16, fade(Theme.txt(), p), 0.7f);
        RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("background + accent, per theme"), x, y + 30, W, 12, fade(Theme.txtDim(), p), 0.47f);

        int cbs = 22, cbx = x + W - pad - cbs, cby = y + 10;
        boolean closeHov = inside(mx, my, cbx, cby, cbs, cbs);
        RenderUtil.roundedRect(ctx, cbx, cby, cbs, cbs, 7, fade(closeHov ? Theme.glassHov() : Theme.glassRow(), p));
        RenderUtil.textCentered(ctx, tr, "×", cbx, cby, cbs, cbs, fade(Theme.txt(), p), 0.6f);
        closeRect = new int[]{ cbx, cby, cbs, cbs };

        int yy = y + 50;
        int cw = W - pad * 2;

        // Light | Dark
        yy = drawSegRow(ctx, tr, x + pad, yy, cw, modeRects,
                new String[]{ com.lume.client.Lang.tUI("Light"), com.lume.client.Lang.tUI("Dark") },
                i -> (i == 1) == editingDark, mx, my, p);
        yy += 8;

        // Launcher | In-Game Menu
        yy = drawSegRow(ctx, tr, x + pad, yy, cw, previewTabRects,
                new String[]{ com.lume.client.Lang.tUI("Launcher"), com.lume.client.Lang.tUI("In-Game Menu") },
                i -> i == previewTab, mx, my, p);
        yy += 8;

        // Preview box — real ClickGUI/launcher drawing primitives, not a hand-drawn mockup,
        // so it can't visually drift from the actual UI it's previewing. Cards/buttons inside
        // animate on hover the same way the real ones do, but nothing here is clickable.
        int pbH = 114;
        RenderUtil.roundedRect(ctx, x + pad, yy, cw, pbH, 10, fade(Theme.sideBg(), p));
        RenderUtil.strokeRoundedRect(ctx, x + pad, yy, cw, pbH, 10, 1, fade(Theme.rim(), p));
        if (previewTab == 0) drawLauncherMockup(ctx, tr, x + pad, yy, cw, pbH, mx, my, dt, p);
        else drawInGameMockup(ctx, tr, x + pad, yy, cw, pbH, mx, my, dt, p);
        yy += pbH + 12;

        int bg = Theme.getBg(editingDark), accent = Theme.getAccent(editingDark), activeTxt = Theme.getActiveText(editingDark);
        yy = drawColorRow(ctx, tr, x + pad, yy, cw, com.lume.client.Lang.tUI("Background"), bg, 0, mx, my, p);
        yy += 4;
        yy = drawColorRow(ctx, tr, x + pad, yy, cw, com.lume.client.Lang.tUI("Accent / Glow"), accent, 1, mx, my, p);
        yy += 4;
        yy = drawColorRow(ctx, tr, x + pad, yy, cw, com.lume.client.Lang.tUI("Button Text"), activeTxt, 2, mx, my, p);
        yy += 10;

        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Styles").toUpperCase(), x + pad, yy, 12, fade(Theme.txtDim(), p), 0.4f);
        yy += 16;
        String[] styleNames = { com.lume.client.Lang.tUI("Default"), com.lume.client.Lang.tUI("Full Glass"), com.lume.client.Lang.tUI("No Glass") };
        int sGap = 4, sW = (cw - sGap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int sx2 = x + pad + i * (sW + sGap);
            boolean sel = Theme.getGlassStyle() == i;
            RenderUtil.roundedRect(ctx, sx2, yy, sW, 22, 7, fade(sel ? Theme.accent() : Theme.glassRow(), p));
            RenderUtil.textCentered(ctx, tr, styleNames[i], sx2, yy, sW, 22, fade(sel ? Theme.activeText() : Theme.txtDim(), p), 0.4f);
            styleRects[i] = new int[]{ sx2, yy, sW, 22 };
        }
        yy += 22 + 12;

        // Premium Glass tuning — only meaningful (and only shown) for Full Glass; controls
        // GlassRenderer's real backdrop blur/refraction strength.
        if (Theme.getGlassStyle() == 1) {
            RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Premium Glass").toUpperCase(), x + pad, yy, 12, fade(Theme.txtDim(), p), 0.4f);
            yy += 16;
            yy = drawGlassSlider(ctx, tr, x + pad, yy, cw, com.lume.client.Lang.tUI("Blur"), Theme.getGlassBlur(), 0, p);
            yy += 6;
            yy = drawGlassSlider(ctx, tr, x + pad, yy, cw, com.lume.client.Lang.tUI("Distortion"), Theme.getGlassDistort(), 1, p);
            yy += 10;
        }

        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Interface").toUpperCase(), x + pad, yy, 12, fade(Theme.txtDim(), p), 0.4f);
        yy += 16;
        yy = drawMenuSizeSlider(ctx, tr, x + pad, yy, cw, ClickGuiScreen.getWinScale(), p);
        yy += 10;

        // Reset to Defaults — clears custom colours/style for BOTH modes back to the
        // built-in look, not just the one currently being edited.
        boolean resetHov = inside(mx, my, x + pad, yy, cw, 24);
        RenderUtil.roundedRect(ctx, x + pad, yy, cw, 24, 8, fade(resetHov ? Theme.glassHov() : Theme.glassRow(), p));
        RenderUtil.strokeRoundedRect(ctx, x + pad, yy, cw, 24, 8, 1, fade(Theme.rim(), p));
        RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Reset to Defaults"), x, yy, W, 24, fade(Theme.txtDim(), p), 0.47f);
        resetRect = new int[]{ x + pad, yy, cw, 24 };

        // Colour picker popover — floats above everything else, anchored under whichever
        // swatch opened it.
        if (openPicker >= 0) drawPicker(ctx, tr, openPicker, p);
    }

    /** Two-button segmented row (Light/Dark, Launcher/In-Game Menu) — same selected-pill look
     *  as the launcher's own modeRow/previewTabs. Returns the y position right after the row. */
    private int drawSegRow(DrawContext ctx, TextRenderer tr, int sx, int yy, int w, int[][] outRects, String[] labels,
                            java.util.function.IntPredicate selected, int mx, int my, float p) {
        int tabH = 22, tabGap = 4, tabW = (w - tabGap) / 2;
        for (int i = 0; i < 2; i++) {
            int tx = sx + i * (tabW + tabGap);
            boolean sel = selected.test(i);
            RenderUtil.roundedRect(ctx, tx, yy, tabW, tabH, 8, fade(sel ? Theme.accent() : Theme.glassRow(), p));
            RenderUtil.textCentered(ctx, tr, labels[i], tx, yy, tabW, tabH, fade(sel ? Theme.activeText() : Theme.txtDim(), p), 0.47f);
            outRects[i] = new int[]{ tx, yy, tabW, tabH };
        }
        return yy + tabH;
    }

    /** Label left, colour swatch right — click the swatch to open the native colour picker.
     *  Returns the y position right after this row. */
    private int drawColorRow(DrawContext ctx, TextRenderer tr, int sx, int yy, int w, String label, int rgb, int colorIdx, int mx, int my, float p) {
        int rowH = 26;
        RenderUtil.textVCentered(ctx, tr, label, sx, yy, rowH, fade(Theme.txt(), p), 0.47f);
        int swS = 22, swX = sx + w - swS, swY = yy + (rowH - swS) / 2;
        boolean hov = inside(mx, my, swX, swY, swS, swS);
        RenderUtil.roundedRect(ctx, swX, swY, swS, swS, 6, fade(0xFF000000 | rgb, p));
        RenderUtil.strokeRoundedRect(ctx, swX, swY, swS, swS, 6, 1, fade(hov ? Theme.accent() : Theme.rim(), p));
        swatchRects[colorIdx] = new int[]{ swX, swY, swS, swS };
        return yy + rowH;
    }

    /** Percent label + a filled accent track — drag to set 0..1. Returns the y position
     *  right after this row. */
    private int drawGlassSlider(DrawContext ctx, TextRenderer tr, int sx, int yy, int w, String label, float value01, int idx, float p) {
        RenderUtil.text(ctx, tr, label + "  " + Math.round(value01 * 100) + "%", sx, yy, fade(Theme.txt(), p), false, 0.44f);
        yy += 14;
        int trackH = 14;
        RenderUtil.roundedRect(ctx, sx, yy, w, trackH, 6, fade(Theme.glassRow(), p));
        RenderUtil.roundedRect(ctx, sx, yy, Math.max(6, Math.round(w * value01)), trackH, 6, fade(Theme.accent(), p));
        glassSliderRects[idx] = new int[]{ sx, yy, w, trackH };
        return yy + trackH;
    }

    private void updateGlassSliderFromMouse(int idx, int mx) {
        int[] r = glassSliderRects[idx];
        if (r == null) return;
        float v = clamp01((mx - r[0]) / (float) r[2]);
        if (idx == 0) Theme.setGlassBlur(v); else Theme.setGlassDistort(v);
    }

    private static final float MENU_SCALE_MIN = 0.6f, MENU_SCALE_MAX = 1.8f;

    /** Scale readout ("1.2x") + a filled accent track — drag to resize the in-game ClickGUI
     *  window itself (same {@link ClickGuiScreen#setWindow} clamp range as its own corner-drag
     *  resize grip). Returns the y position right after this row. */
    private int drawMenuSizeSlider(DrawContext ctx, TextRenderer tr, int sx, int yy, int w, float scale, float p) {
        RenderUtil.text(ctx, tr, com.lume.client.Lang.tUI("Menu Size") + "  " + String.format("%.1fx", scale),
                sx, yy, fade(Theme.txt(), p), false, 0.44f);
        yy += 14;
        int trackH = 14;
        float frac = clamp01((scale - MENU_SCALE_MIN) / (MENU_SCALE_MAX - MENU_SCALE_MIN));
        RenderUtil.roundedRect(ctx, sx, yy, w, trackH, 6, fade(Theme.glassRow(), p));
        RenderUtil.roundedRect(ctx, sx, yy, Math.max(6, Math.round(w * frac)), trackH, 6, fade(Theme.accent(), p));
        menuSizeSliderRect = new int[]{ sx, yy, w, trackH };
        return yy + trackH;
    }

    private void updateMenuSizeFromMouse(int mx) {
        if (menuSizeSliderRect == null) return;
        float frac = clamp01((mx - menuSizeSliderRect[0]) / (float) menuSizeSliderRect[2]);
        float scale = MENU_SCALE_MIN + frac * (MENU_SCALE_MAX - MENU_SCALE_MIN);
        ClickGuiScreen.setWindow(ClickGuiScreen.getWinOffX(), ClickGuiScreen.getWinOffY(), scale);
    }

    /** Opens the picker for bg (0) or accent (1), seeding it from that colour's current
     *  HSV/hex. Clicking the same swatch again closes it (see {@link #closePicker}). */
    private void togglePicker(int colorIdx) {
        if (openPicker == colorIdx) { closePicker(); return; }
        openPicker = colorIdx;
        pickerHexFocused = false;
        int rgb = colorIdx == 0 ? Theme.getBg(editingDark) : colorIdx == 1 ? Theme.getAccent(editingDark) : Theme.getActiveText(editingDark);
        float[] hsv = rgbToHsv(rgb);
        pickerHue = hsv[0]; pickerSat = hsv[1]; pickerVal = hsv[2];
        pickerHex = String.format("%06X", rgb & 0xFFFFFF);
    }

    /** Persists to theme.json on close — edits already applied live while the picker was open. */
    private void closePicker() {
        if (openPicker >= 0) ThemeSync.save();
        openPicker = -1; pickerHexFocused = false; draggingWhat = 0;
    }

    private void applyPickerColor() {
        int rgb = hsvToRgb(pickerHue, pickerSat, pickerVal);
        pickerHex = String.format("%06X", rgb);
        setPickerTarget(rgb);
    }

    private void applyHex() {
        try {
            int rgb = Integer.parseInt(pickerHex, 16);
            float[] hsv = rgbToHsv(rgb);
            pickerHue = hsv[0]; pickerSat = hsv[1]; pickerVal = hsv[2];
            setPickerTarget(rgb);
        } catch (NumberFormatException ignored) { /* incomplete/invalid hex while typing */ }
    }

    private void setPickerTarget(int rgb) {
        if (openPicker == 0) Theme.setCustomBg(editingDark, rgb);
        else if (openPicker == 1) Theme.setCustomAccent(editingDark, rgb);
        else Theme.setCustomActiveText(editingDark, rgb);
    }

    private void updateSvFromMouse(int mx, int my) {
        if (svRect == null) return;
        pickerSat = clamp01((mx - svRect[0]) / (float) svRect[2]);
        pickerVal = 1f - clamp01((my - svRect[1]) / (float) svRect[3]);
        applyPickerColor();
    }

    private void updateHueFromMouse(int mx) {
        if (hueRect == null) return;
        pickerHue = clamp01((mx - hueRect[0]) / (float) hueRect[2]) * 360f;
        applyPickerColor();
    }

    /** HSV square (drag = saturation/value) + hue strip (drag = hue) + hex field, anchored
     *  under the swatch that opened it. The square is the classic 2-gradient-overlay trick
     *  (white→transparent horizontally for saturation, transparent→black vertically for
     *  value, both layered on a solid pure-hue fill) — {@link RenderUtil#hGradientRect} for
     *  the horizontal one, vanilla {@code DrawContext.fillGradient} (vertical-only) for the
     *  other, same pair used for ClickGuiScreen's own colour picker. */
    private void drawPicker(DrawContext ctx, TextRenderer tr, int colorIdx, float p) {
        int[] anchor = swatchRects[colorIdx];
        int pad = 10, svS = 140, hueH = 14, gap = 8, hexH = 24;
        int pw = svS + pad * 2;
        int ph = pad + svS + gap + hueH + gap + hexH + pad;
        int px = anchor[0] + anchor[2] - pw, py = anchor[1] + anchor[2] + 6;

        RenderUtil.glow(ctx, px, py, pw, ph, 12, 0x000000, 2);
        RenderUtil.roundedRect(ctx, px, py, pw, ph, 12, fade(Theme.winTop(), p));
        RenderUtil.strokeRoundedRect(ctx, px, py, pw, ph, 12, 1, fade(Theme.rim(), p));

        int svX = px + pad, svY = py + pad;
        int hueRgb = 0xFF000000 | hsvToRgb(pickerHue, 1f, 1f);
        RenderUtil.roundedRect(ctx, svX, svY, svS, svS, 8, fade(hueRgb, p));
        RenderUtil.hGradientRect(ctx, svX, svY, svS, svS, fade(0xFFFFFFFF, p), fade(0x00FFFFFF, p));
        ctx.fillGradient(svX, svY, svX + svS, svY + svS, fade(0x00000000, p), fade(0xFF000000, p));
        RenderUtil.strokeRoundedRect(ctx, svX, svY, svS, svS, 8, 1, fade(Theme.rim(), p));
        float curX = svX + pickerSat * svS, curY = svY + (1f - pickerVal) * svS;
        RenderUtil.roundedRect(ctx, Math.round(curX - 5), Math.round(curY - 5), 10, 10, 5, fade(0xFF000000, p));
        RenderUtil.roundedRect(ctx, Math.round(curX - 4), Math.round(curY - 4), 8, 8, 4, fade(0xFFFFFFFF, p));
        svRect = new int[]{ svX, svY, svS, svS };

        int hueY = svY + svS + gap;
        int[] hueStops = { 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000 };
        int segW = svS / 6;
        for (int i = 0; i < 6; i++) {
            int sx2 = svX + i * segW;
            int w2 = i == 5 ? svS - segW * 5 : segW;
            RenderUtil.hGradientRect(ctx, sx2, hueY, w2, hueH, fade(hueStops[i], p), fade(hueStops[i + 1], p));
        }
        RenderUtil.strokeRoundedRect(ctx, svX, hueY, svS, hueH, 6, 1, fade(Theme.rim(), p));
        float hueCurX = svX + (pickerHue / 360f) * svS;
        RenderUtil.roundedRect(ctx, Math.round(hueCurX - 1.5f), hueY - 2, 3, hueH + 4, 1, fade(0xFFFFFFFF, p));
        hueRect = new int[]{ svX, hueY, svS, hueH };

        int hexY = hueY + hueH + gap;
        RenderUtil.roundedRect(ctx, svX, hexY, svS, hexH, 6, fade(pickerHexFocused ? Theme.glassHov() : Theme.glassRow(), p));
        RenderUtil.strokeRoundedRect(ctx, svX, hexY, svS, hexH, 6, 1, fade(pickerHexFocused ? Theme.accent() : Theme.rim(), p));
        RenderUtil.textVCentered(ctx, tr, "#" + pickerHex + (pickerHexFocused ? "_" : ""), svX + 8, hexY, hexH, fade(Theme.txt(), p), 0.53f);
        hexRect = new int[]{ svX, hexY, svS, hexH };

        pickerRect = new int[]{ px, py, pw, ph };
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0) h += 360f;
        float s = max == 0 ? 0 : d / max;
        return new float[]{ h, s, max };
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s, x = c * (1f - Math.abs((h / 60f) % 2f - 1f)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int ri = clampByte(Math.round((r + m) * 255));
        int gi = clampByte(Math.round((g + m) * 255));
        int bi = clampByte(Math.round((b + m) * 255));
        return (ri << 16) | (gi << 8) | bi;
    }

    private static int clampByte(int v) { return Math.max(0, Math.min(255, v)); }

    // ---------------------------------------------------------------------
    // Preview mockups — built from the SAME Theme.*/NanoVgRenderer primitives the real
    // screens use (gradientRoundedRect, bloom, accent/glassRow), so they can't drift from
    // what ClickGUI/the launcher actually look like. Hover glow is purely visual — nothing
    // in here is ever added to a hit-rect, so none of it is clickable.

    private void drawInGameMockup(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my, float dt, float p) {
        int pad = 8;
        RenderUtil.textCentered(ctx, tr, "lume.visuals", x, y + pad, w, 10, fade(Theme.txt(), p), 0.42f);

        // search bar — same flat panel look as ClickGuiScreen's real search box
        int searchY = y + pad + 10 + 5, searchH = 13;
        RenderUtil.roundedRect(ctx, x + pad, searchY, w - pad * 2, searchH, 6, fade(Theme.glassRow(), p));
        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Search modules…"), x + pad + 6, searchY, searchH, fade(Theme.txtDim(), p), 0.3f);

        // category-tab pill row — flat accent pill, matching the real (now non-gradient) selector
        int tabsY = searchY + searchH + 5, tabsH = 13;
        String[] tabs = { "Combat", "Movement", "Visual" };
        int tabGap = 4, tx = x + pad;
        for (int i = 0; i < tabs.length; i++) {
            int tw = RenderUtil.width(tr, tabs[i], 0.3f) + 12;
            if (i == 0) {
                RenderUtil.roundedRect(ctx, tx, tabsY, tw, tabsH, 6, fade(Theme.accent(), p));
                RenderUtil.textCentered(ctx, tr, tabs[i], tx, tabsY, tw, tabsH, fade(Theme.activeText(), p), 0.3f);
            } else {
                RenderUtil.roundedRect(ctx, tx, tabsY, tw, tabsH, 6, fade(Theme.glassRow(), p));
                RenderUtil.textCentered(ctx, tr, tabs[i], tx, tabsY, tw, tabsH, fade(Theme.txtDim(), p), 0.3f);
            }
            tx += tw + tabGap;
        }

        int gridY = tabsY + tabsH + 6;
        int gap = 5, cardW = (w - pad * 2 - gap) / 2, cardH = 22;
        String[] names = { "HUD", "Waypoints", "Custom Hand", "Zoom" };
        for (int i = 0; i < 4; i++) {
            int ccx = x + pad + (i % 2) * (cardW + gap);
            int ccy = gridY + (i / 2) * (cardH + gap);
            boolean hov = inside(mx, my, ccx, ccy, cardW, cardH);
            mockCardHover[i] = approach(mockCardHover[i], hov ? 1f : 0f, 12f, dt);
            if (i == 0) {
                RenderUtil.roundedRect(ctx, ccx, ccy, cardW, cardH, 7, fade(Theme.accent(), p));
                RenderUtil.textCentered(ctx, tr, names[i], ccx, ccy, cardW, cardH, fade(Theme.activeText(), p), 0.32f);
            } else {
                RenderUtil.roundedRect(ctx, ccx, ccy, cardW, cardH, 7, fade(Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), mockCardHover[i]), p));
                RenderUtil.textCentered(ctx, tr, names[i], ccx, ccy, cardW, cardH, fade(Theme.txtDim(), p), 0.32f);
            }
        }
    }

    private void drawLauncherMockup(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, int mx, int my, float dt, float p) {
        int pad = 10, avS = 28;
        RenderUtil.roundedRect(ctx, x + pad, y + pad, avS, avS, 8, fade(Theme.accent(), p));
        RenderUtil.text(ctx, tr, "Nickname", x + pad + avS + 8, y + pad + avS / 2f - 6, fade(Theme.txtDim(), p), false, 0.32f);
        RenderUtil.text(ctx, tr, "Steve", x + pad + avS + 8, y + pad + avS / 2f + 3, fade(Theme.txt(), p), false, 0.42f);

        int playY = y + pad + avS + 10, playH = 24, playW = w - pad * 2;
        boolean hov = inside(mx, my, x + pad, playY, playW, playH);
        mockPlayHover = approach(mockPlayHover, hov ? 1f : 0f, 12f, dt);
        RenderUtil.roundedRect(ctx, x + pad, playY, playW, playH, 9, fade(Theme.colorLerp(Theme.accent(), Theme.glassHov(), mockPlayHover * 0.2f), p));
        RenderUtil.textCentered(ctx, tr, "▶ Play", x + pad, playY, playW, playH, fade(Theme.activeText(), p), 0.42f);
    }

    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * Math.min(1f, rate * dt);
    }

    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    private static boolean inside(int mx, int my, int[] r) { return r != null && inside(mx, my, r[0], r[1], r[2], r[3]); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        if (inside(mx, my, closeRect)) { close(); return true; }
        if (inside(mx, my, resetRect)) { Theme.resetToDefaults(); ThemeSync.save(); return true; }
        for (int i = 0; i < 2; i++) {
            if (inside(mx, my, modeRects[i])) { editingDark = i == 1; Theme.setDark(editingDark); ThemeSync.save(); return true; }
        }
        for (int i = 0; i < 2; i++) {
            if (inside(mx, my, previewTabRects[i])) { previewTab = i; return true; }
        }
        for (int i = 0; i < 3; i++) {
            if (inside(mx, my, styleRects[i])) { Theme.setGlassStyle(i); ThemeSync.save(); return true; }
        }
        if (Theme.getGlassStyle() == 1) {
            for (int i = 0; i < 2; i++) {
                if (inside(mx, my, glassSliderRects[i])) { draggingGlassSlider = i; updateGlassSliderFromMouse(i, mx); return true; }
            }
        }
        if (inside(mx, my, menuSizeSliderRect)) { draggingMenuSize = true; updateMenuSizeFromMouse(mx); return true; }
        if (openPicker >= 0) {
            if (inside(mx, my, hexRect)) { pickerHexFocused = true; return true; }
            pickerHexFocused = false;
            if (inside(mx, my, svRect)) { draggingWhat = 1; updateSvFromMouse(mx, my); return true; }
            if (inside(mx, my, hueRect)) { draggingWhat = 2; updateHueFromMouse(mx); return true; }
            if (pickerRect != null && !inside(mx, my, pickerRect[0], pickerRect[1], pickerRect[2], pickerRect[3])) {
                closePicker(); return true;
            }
            // Clicked somewhere inside the popover's own background/padding (not on hex/sv/hue
            // specifically) — still swallow it here. The popover floats ON TOP of the rest of
            // the window (Reset button, style rows, swatches...), but hit-testing is manual, so
            // without this the click fell through and landed on whatever's underneath it —
            // clicking "through" the picker to the window behind it.
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (inside(mx, my, swatchRects[i])) { togglePicker(i); return true; }
        }
        // Header band (title/subtitle strip, above the Light/Dark row) — click-drag to move
        // the whole window. closeRect is checked above already, so this never steals that click.
        if (inside(mx, my, winX, winY, winW, 50)) {
            dragging = true; grabMx = mouseX; grabMy = mouseY; grabOffX = offX; grabOffY = offY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && dragging) {
            offX = grabOffX + (int) Math.round(mouseX - grabMx);
            offY = grabOffY + (int) Math.round(mouseY - grabMy);
            return true;
        }
        if (button == 0 && draggingMenuSize) {
            updateMenuSizeFromMouse((int) mouseX);
            return true;
        }
        if (button == 0 && (draggingWhat != 0 || draggingGlassSlider >= 0)) {
            int mx = (int) mouseX, my = (int) mouseY;
            if (draggingGlassSlider >= 0) updateGlassSliderFromMouse(draggingGlassSlider, mx);
            else if (draggingWhat == 1) updateSvFromMouse(mx, my);
            else updateHueFromMouse(mx);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) { dragging = false; return true; }
        if (button == 0 && draggingMenuSize) { draggingMenuSize = false; com.lume.client.Config.save(); return true; }
        if (button == 0 && (draggingWhat != 0 || draggingGlassSlider >= 0)) {
            draggingWhat = 0; draggingGlassSlider = -1; ThemeSync.save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (pickerHexFocused) {
            if (isHexChar(chr) && pickerHex.length() < 6) {
                pickerHex += Character.toUpperCase(chr);
                if (pickerHex.length() == 6) applyHex();
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerHexFocused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !pickerHex.isEmpty()) {
                pickerHex = pickerHex.substring(0, pickerHex.length() - 1);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { pickerHexFocused = false; return true; }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                if (pickerHex.length() == 6) applyHex();
                pickerHexFocused = false; return true;
            }
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (openPicker >= 0) { closePicker(); return true; }
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package com.lume.client.gui;

/**
 * Lume glass palette — warm CREAM base with a LAVENDER accent.
 * Light = cream glass; dark = warm dark-cream glass.
 *
 * <p>Switching themes is <b>animated</b>: every colour getter smoothly crossfades
 * from the previous mode to the new one over {@link #DURATION} ms. Because the GUI
 * and HUD re-query these getters every frame, the whole client morphs between
 * themes instead of snapping — a premium, easy-on-the-eyes transition.
 */
public final class Theme {

    private static final long DURATION = 360;

    private static boolean dark = false;
    private static boolean prevDark = false;
    private static long switchAt = -DURATION; // start fully settled

    // --- overrides from the launcher's theme.json (see ThemeSync) — null/‑1 = use the
    // built-in defaults below. Background is a single colour per mode; winTop/winBot
    // (the glass gradient) are derived from it rather than needing two separate configurable
    // values, matching how the launcher itself only ever exposes one "Background" swatch. ---
    private static Integer customBgLight, customBgDark, customAccentLight, customAccentDark;
    /** Text colour drawn on ACTIVE/selected buttons (accent-filled pills, tabs, style
     *  swatches) — every one of those used to hardcode plain white, not customisable. */
    private static Integer customActiveTextLight, customActiveTextDark;
    /** 0 = default, 1 = full glass, 2 = no glass (see setGlassStyle). */
    private static int glassStyle = 0;
    /** Full Glass only: how strong the real backdrop blur/refraction is (see GlassRenderer). */
    private static float glassBlur = 0.5f, glassDistort = 0.35f;

    private Theme() {}

    public static void setCustomBg(boolean d, int rgb) { if (d) customBgDark = rgb; else customBgLight = rgb; }
    public static void setCustomAccent(boolean d, int rgb) { if (d) customAccentDark = rgb; else customAccentLight = rgb; }
    public static void setCustomActiveText(boolean d, int rgb) { if (d) customActiveTextDark = rgb; else customActiveTextLight = rgb; }
    public static void setGlassStyle(int style) { glassStyle = style; }
    public static int getGlassStyle() { return glassStyle; }
    public static void setGlassBlur(float v) { glassBlur = clamp01(v); }
    public static float getGlassBlur() { return glassBlur; }
    public static void setGlassDistort(float v) { glassDistort = clamp01(v); }
    public static float getGlassDistort() { return glassDistort; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    /** Clears every custom bg/accent override (both modes) and resets the glass style —
     *  restores the exact built-in defaults, not just values that happen to match them,
     *  since winTop/winBot/glassRow etc. render differently once a colour is "customised"
     *  vs. left null (see getBg/getAccent and the private per-mode colour getters below). */
    public static void resetToDefaults() {
        customBgLight = null; customBgDark = null;
        customAccentLight = null; customAccentDark = null;
        customActiveTextLight = null; customActiveTextDark = null;
        glassStyle = 0; glassBlur = 0.5f; glassDistort = 0.35f;
    }

    /** Effective (customised-or-default) background/accent/active-text as a plain 0xRRGGBB,
     *  for the in-game Customize Colors screen (ColorsScreen) to seed its sliders from and
     *  for ThemeSync to write back out to the shared theme.json. */
    public static int getBg(boolean d) {
        Integer c = d ? customBgDark : customBgLight;
        return c != null ? c : (d ? 0x221F1A : 0xF2EBDD);
    }
    public static int getAccent(boolean d) {
        Integer c = d ? customAccentDark : customAccentLight;
        return c != null ? c : (d ? 0xB7AAD9 : 0xA99BC7);
    }
    public static int getActiveText(boolean d) {
        Integer c = d ? customActiveTextDark : customActiveTextLight;
        return c != null ? c : 0xFFFFFF;
    }

    // --- state / transition -------------------------------------------------

    public static boolean isDark() { return dark; }

    public static void toggle() { setDark(!dark); }

    public static void setDark(boolean value) {
        if (value == dark) return;
        prevDark = dark;
        dark = value;
        switchAt = System.currentTimeMillis();
    }

    /** Eased 0→1 transition progress (smoothstep). */
    private static float prog() {
        float p = (System.currentTimeMillis() - switchAt) / (float) DURATION;
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        return p * p * (3f - 2f * p);
    }

    /** True while a theme crossfade is still playing. */
    public static boolean isAnimating() {
        return System.currentTimeMillis() - switchAt < DURATION;
    }

    /** ARGB linear interpolation. */
    public static int colorLerp(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t), or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t), ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** Blend the prev-mode value and the current-mode value by the transition. */
    private static int mix(int prevModeColor, int curModeColor) {
        return colorLerp(prevModeColor, curModeColor, prog());
    }

    // --- colours (public = animated blend; private(boolean) = raw per mode) --

    public static int backdrop()  { return com.lume.client.module.modules.cosmetic.NoBgBlur.active() ? 0 : mix(backdrop(prevDark), backdrop(dark)); }
    public static int winTop()    { return mix(winTop(prevDark),    winTop(dark)); }
    public static int winBot()    { return mix(winBot(prevDark),    winBot(dark)); }
    public static int winBg()     { return mix(winBg(prevDark),     winBg(dark)); }
    public static int sideBg()    { return mix(sideBg(prevDark),    sideBg(dark)); }
    public static int glassRow()  { return mix(glassRow(prevDark),  glassRow(dark)); }
    public static int glassHov()  { return mix(glassHov(prevDark),  glassHov(dark)); }
    public static int border()    { return mix(border(prevDark),    border(dark)); }
    public static int rim()       { return mix(rim(prevDark),       rim(dark)); }
    public static int shadow()    { return mix(shadow(prevDark),    shadow(dark)); }
    public static int txt()       { return mix(txt(prevDark),       txt(dark)); }
    public static int txtDim()    { return mix(txtDim(prevDark),    txtDim(dark)); }
    public static int accent()    { return mix(accent(prevDark),    accent(dark)); }
    public static int accent2()   { return mix(accent2(prevDark),   accent2(dark)); }
    public static int pillOff()   { return mix(pillOff(prevDark),   pillOff(dark)); }
    public static int accentRgb() { return accent() & 0xFFFFFF; }
    public static int accent2Rgb() { return accent2() & 0xFFFFFF; }
    /** Text colour for active/selected buttons (accent-filled pills, tabs, style swatches) —
     *  defaults to white, but customisable via Customize Colors like everything else. */
    public static int activeText() { return mix(activeText(prevDark), activeText(dark)); }

    private static int backdrop(boolean d)  { return d ? 0xAA1A1712 : 0x55241E16; }

    /** winTop/winBot are a 2-stop gradient derived from ONE configurable background
     *  colour (a lighten + the base, same idea as the launcher deriving --blue from
     *  --mint). "No glass" forces fully opaque; "Full Glass" is ZERO alpha — no colour
     *  wash at all, purely the real blurred/refracted backdrop from GlassRenderer
     *  showing through (an earlier version still tinted it ~60-70% opaque, which read
     *  as "coloured", not glass). Only touches window/panel fills, never CustomMenu's
     *  own opaque main-menu background. */
    private static int winTop(boolean d) {
        if (glassStyle == 1) return 0;
        Integer c = d ? customBgDark : customBgLight;
        int alpha = glassStyle == 2 ? 0xFF : (d ? 0xE8 : 0xDE);
        return c != null ? (alpha << 24) | (shade(c, 0.08f) & 0xFFFFFF) : (alpha << 24) | (d ? 0x2F2A23 : 0xF8F2E7);
    }
    private static int winBot(boolean d) {
        if (glassStyle == 1) return 0;
        Integer c = d ? customBgDark : customBgLight;
        int alpha = glassStyle == 2 ? 0xFF : (d ? 0xE8 : 0xD4);
        return c != null ? (alpha << 24) | (c & 0xFFFFFF) : (alpha << 24) | (d ? 0x1E1B16 : 0xEADFCB);
    }
    private static int winBg(boolean d) {
        if (glassStyle == 1) return 0;
        Integer c = d ? customBgDark : customBgLight;
        int alpha = glassStyle == 2 ? 0xFF : (d ? 0xE0 : 0xDC);
        return c != null ? (alpha << 24) | (c & 0xFFFFFF) : (alpha << 24) | (d ? 0x272320 : 0xF3ECDD);
    }
    private static int sideBg(boolean d)    { return d ? 0x14FFFFFF : 0x26FFFFFF; }
    private static int glassRow(boolean d) {
        if (glassStyle == 2) return 0xFF000000 | (winBot(d) & 0xFFFFFF);
        int alpha = glassStyle == 1 ? (d ? 0x06 : 0x18) : (d ? 0x18 : 0x5C);
        return (alpha << 24) | 0xFFFFFF;
    }
    private static int glassHov(boolean d) {
        if (glassStyle == 2) return 0xFF000000 | (shade(winBot(d) & 0xFFFFFF, 0.12f) & 0xFFFFFF);
        int alpha = glassStyle == 1 ? (d ? 0x14 : 0x38) : (d ? 0x33 : 0x9E);
        return (alpha << 24) | 0xFFFFFF;
    }
    private static int border(boolean d)    { return d ? 0x42FFFFFF : 0xCCFFFFFF; }
    private static int rim(boolean d)       { return d ? 0x66FFFFFF : 0xAAFFFFFF; }
    private static int shadow(boolean d)    { return d ? 0x66000000 : 0x2E000000; }
    private static int txt(boolean d)       { return d ? 0xFFEDE6D6 : 0xFF4A4133; }
    private static int txtDim(boolean d)    { return d ? 0xFFA99F8C : 0xFF8C8170; }
    private static int accent(boolean d) {
        Integer c = d ? customAccentDark : customAccentLight;
        return c != null ? (0xFF000000 | (c & 0xFFFFFF)) : (d ? 0xFFB7AAD9 : 0xFFA99BC7);
    }
    private static int accent2(boolean d) {
        Integer c = d ? customAccentDark : customAccentLight;
        return c != null ? (0xFF000000 | (shade(c, -0.10f) & 0xFFFFFF)) : (d ? 0xFF9385C4 : 0xFF8E7FC0);
    }
    private static int pillOff(boolean d)   { return d ? 0x3AFFFFFF : 0x33483F33; }
    private static int activeText(boolean d) {
        Integer c = d ? customActiveTextDark : customActiveTextLight;
        return 0xFF000000 | (c != null ? (c & 0xFFFFFF) : 0xFFFFFF);
    }

    /** Darkens (negative pct) or lightens (positive pct) an 0xRRGGBB colour per channel —
     *  same idea as the launcher's own JS shade() helper, kept in sync deliberately. */
    private static int shade(int rgb, float pct) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = clamp255(Math.round(r * (1 + pct)));
        g = clamp255(Math.round(g * (1 + pct)));
        b = clamp255(Math.round(b * (1 + pct)));
        return (r << 16) | (g << 8) | b;
    }
    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }
}

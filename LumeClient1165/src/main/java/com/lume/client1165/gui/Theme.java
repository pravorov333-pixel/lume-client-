package com.lume.client1165.gui;

/**
 * Lume glass palette — warm CREAM base with a LAVENDER accent (1.16.5 build).
 * Light = cream glass; dark = warm dark-cream glass. Switching is animated:
 * every getter crossfades prev→current over {@link #DURATION} ms.
 *
 * <p>Pure Java (no Minecraft API) — identical to the 1.21 build's Theme.
 */
public final class Theme {

    private static final long DURATION = 360;

    private static boolean dark = false;
    private static boolean prevDark = false;
    private static long switchAt = -DURATION;

    private Theme() {}

    public static boolean isDark() { return dark; }

    public static void toggle() { setDark(!dark); }

    public static void setDark(boolean value) {
        if (value == dark) return;
        prevDark = dark;
        dark = value;
        switchAt = System.currentTimeMillis();
    }

    private static float prog() {
        float p = (System.currentTimeMillis() - switchAt) / (float) DURATION;
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        return p * p * (3f - 2f * p);
    }

    public static boolean isAnimating() {
        return System.currentTimeMillis() - switchAt < DURATION;
    }

    public static int colorLerp(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t), or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t), ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private static int mix(int prevModeColor, int curModeColor) {
        return colorLerp(prevModeColor, curModeColor, prog());
    }

    public static int backdrop()  { return mix(backdrop(prevDark),  backdrop(dark)); }
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

    private static int backdrop(boolean d)  { return d ? 0xAA1A1712 : 0x55241E16; }
    private static int winTop(boolean d)    { return d ? 0xE82F2A23 : 0xDEF8F2E7; }
    private static int winBot(boolean d)    { return d ? 0xE81E1B16 : 0xD4EADFCB; }
    private static int winBg(boolean d)     { return d ? 0xE0272320 : 0xDCF3ECDD; }
    private static int sideBg(boolean d)    { return d ? 0x14FFFFFF : 0x26FFFFFF; }
    private static int glassRow(boolean d)  { return d ? 0x18FFFFFF : 0x5CFFFFFF; }
    private static int glassHov(boolean d)  { return d ? 0x33FFFFFF : 0x9EFFFFFF; }
    private static int border(boolean d)    { return d ? 0x42FFFFFF : 0xCCFFFFFF; }
    private static int rim(boolean d)       { return d ? 0x66FFFFFF : 0xAAFFFFFF; }
    private static int shadow(boolean d)    { return d ? 0x66000000 : 0x2E000000; }
    private static int txt(boolean d)       { return d ? 0xFFEDE6D6 : 0xFF4A4133; }
    private static int txtDim(boolean d)    { return d ? 0xFFA99F8C : 0xFF8C8170; }
    private static int accent(boolean d)    { return d ? 0xFFB7AAD9 : 0xFFA99BC7; }
    private static int accent2(boolean d)   { return d ? 0xFF9385C4 : 0xFF8E7FC0; }
    private static int pillOff(boolean d)   { return d ? 0x3AFFFFFF : 0x33483F33; }
}

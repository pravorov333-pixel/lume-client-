package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Repositions / rotates / scales the first-person held item — right and left
 * hand independently. Style picks the right hand's resting ROTATION: 3 named
 * poses, or "Custom" — which reveals 3 free rotation sliders (Rot X/Y/Z,
 * degrees) instead of a fixed lookup. Pos/Scale sliders below always apply
 * on top, for both hands, regardless of which Style is picked.
 */
public class CustomHand extends Module {

    /** Right-hand-only named resting poses (rotation only — left hand is never rotated
     *  by Style). Pos/Scale sliders below still apply on top regardless of which is picked.
     *  "Custom" (last index) uses the free rRot sliders below instead of a fixed lookup. */
    public final ModeSetting style = hide(add(new ModeSetting("Style", 0,
            "Side", "Lay", "Far",
            "Custom")));
    public static final int STYLE_CUSTOM = 3;

    // Rotation in degrees (X, then Y, then Z — Euler, applied in that order) for the named
    // presets only (Custom, last index, uses rRotX/Y/Z instead). These three are the user's own
    // in-game "Copy" dumps pasted back verbatim, so unlike earlier guessed presets they're
    // already known-good poses.
    private static final float[][] STYLE_ROT = {
            { -37.0f, -147.0f, -41.0f },  // Side
            { -24.0f, -89.0f, 30.0f },    // Lay
            { 0.0f, 0.0f, 0.0f },         // Far
    };
    // Starting position for each named style, applied ONCE when you pick it (not a continuous
    // override) — after that, Pos/Scale sliders are the single source of truth. Custom (last
    // index) doesn't get a preset.
    private static final float[][] STYLE_POS = {
            { -0.67f, -0.63f, -1.62f },   // Side
            { -0.25f, -0.41f, -2.00f },   // Lay
            { 0.64f, -0.41f, -1.42f },    // Far
    };
    private static final float[] STYLE_SCALE = {
            0.50f,   // Side
            1.00f,   // Lay
            1.00f,   // Far
    };

    public final SliderSetting rRotX = hide(add(new SliderSetting("Custom Rot X", 0, -180, 180, false)));
    public final SliderSetting rRotY = hide(add(new SliderSetting("Custom Rot Y", 0, -180, 180, false)));
    public final SliderSetting rRotZ = hide(add(new SliderSetting("Custom Rot Z", 0, -180, 180, false)));

    public float[] styleRot() {
        if (style.index == STYLE_CUSTOM) return new float[]{ (float) rRotX.value, (float) rRotY.value, (float) rRotZ.value };
        return STYLE_ROT[style.index];
    }

    /** Nudges the right-hand Pos (and, for styles that set one, Scale) sliders to the picked
     *  style's suggested starting values — call this right after {@code style} changes (see
     *  ClickGuiScreen's mode-cycle handler). No-op for Custom (last index): there's no preset,
     *  it starts from whatever Pos/Scale already is. */
    public void applyStylePosPreset() {
        if (style.index == STYLE_CUSTOM) return;
        float[] p = STYLE_POS[style.index];
        rPosX.value = p[0]; rPosY.value = p[1]; rPosZ.value = p[2];
        float sc = STYLE_SCALE[style.index];
        if (!Float.isNaN(sc)) rScale.value = sc;
    }

    // -2..2 (2x the old -1..1) — the old range was too tight to reach/correct some Style
    // positions (Z presets around -0.9 already ate most of it), leaving no room to nudge further.
    public final SliderSetting rPosX  = add(new SliderSetting("Right Pos X", 0.0, -2.0, 2.0, false));
    public final SliderSetting rPosY  = add(new SliderSetting("Right Pos Y", 0.0, -2.0, 2.0, false));
    public final SliderSetting rPosZ  = add(new SliderSetting("Right Pos Z", 0.0, -2.0, 2.0, false));
    public final SliderSetting rScale = add(new SliderSetting("Right Scale", 1.0, 0.5, 2.0, false));

    public final SliderSetting lPosX  = add(new SliderSetting("Left Pos X", 0.0, -2.0, 2.0, false));
    public final SliderSetting lPosY  = add(new SliderSetting("Left Pos Y", 0.0, -2.0, 2.0, false));
    public final SliderSetting lPosZ  = add(new SliderSetting("Left Pos Z", 0.0, -2.0, 2.0, false));
    public final SliderSetting lScale = add(new SliderSetting("Left Scale", 1.0, 0.5, 2.0, false));

    /** Which hand's Pos/Scale sliders the ClickGUI currently shows. */
    public final ModeSetting hand = hide(add(new ModeSetting("Edit Hand", 0, "Right", "Left")));

    /** The swing/hit animation — applies to BOTH hands, regardless of Style. Every non-Default
     *  choice fully freezes vanilla's own swing/equip bob first (see freezeSwing() /
     *  HeldItemRendererMixin) so only the picked animation's own motion shows — that freeze is
     *  what was missing before and made every style except No Animation look broken (vanilla's
     *  own dip/bounce was still playing underneath, fighting whatever we added on top). */
    public final ModeSetting animation = hide(add(new ModeSetting("Animation", 0,
            "Default", "No Animation", "Simple", "Spin", "Use")));

    public static final int ANIM_DEFAULT = 0, ANIM_NO_ANIMATION = 1, ANIM_SIMPLE = 2, ANIM_SPIN = 3, ANIM_USE = 4;

    /** Continuous idle bob + sprint bob + camera-turn sway, independent of Animation (which is
     *  swing-only) — see HeldItemRendererMixin#applySway for the actual motion. */
    public final BoolSetting sway = hide(add(new BoolSetting("Idle/Sprint Sway", true)));

    /** True when vanilla's own swing/equip bob should be frozen — every choice EXCEPT Default.
     *  Default deliberately keeps vanilla's normal animation (movement and all); the others each
     *  only tilt/spin the item in place around its own mesh pivot, with Use the single exception
     *  that translates (straight down and back, nothing else) — see HeldItemRendererMixin#applyAnimation. */
    public boolean freezeSwing() { return isEnabled() && animation.index != ANIM_DEFAULT; }

    /** Outline: an enlarged flat-coloured copy of the item drawn behind the real one, so a thin
     *  rim of it peeks out past the real silhouette — the closest equivalent to Target ESP's
     *  entity outline that's actually possible here (vanilla's real outline framebuffer only
     *  composites during world rendering, which finishes well before the first-person hand
     *  renders — verified via bytecode, so it can't be reused for held items). */
    public final BoolSetting outline = hide(add(new BoolSetting("Outline", false)));
    /** Fill: re-renders the item under {@link com.mojang.blaze3d.systems.RenderSystem#setShaderColor}
     *  instead of its own texture/shading — the same global colour-multiply vanilla's own dyed
     *  leather armour uses, so it reads as a proper recolour rather than a translucent tint. */
    public final ModeSetting fill = hide(add(new ModeSetting("Fill", 0, "Off", "Color", "Cosmos")));
    public final ColorSetting handColor = hide(add(new ColorSetting("Outline/Fill Color", true, 183, 170, 199)));

    public int outlineRgb() { return handColor.accent ? com.lume.client.gui.Theme.accentRgb() : handColor.rgb(); }

    public int fillRgb() {
        if (fill.index == 2) {
            double t = System.currentTimeMillis() / 1000.0;
            double drift = Math.sin(t / 47.0) * 60.0 + Math.sin(t / 71.0 + 1.7) * 40.0;
            return hsv((float) (260 + drift), 0.75f, 0.95f);
        }
        return handColor.accent ? com.lume.client.gui.Theme.accentRgb() : handColor.rgb();
    }

    private static int hsv(float h, float s, float v) {
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
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    public CustomHand() {
        super("Custom Hand", "Позиция/поворот/размер предмета — отдельно для каждой руки", Category.RENDER, -1);
    }

    /** One-line human-readable dump of the currently-edited hand's pose, for the "copy" field in
     *  the ClickGUI — paste it back to me and I'll turn it into a new named Style preset. */
    public String exportText() {
        boolean right = hand.index == 0;
        double px = right ? rPosX.value : lPosX.value;
        double py = right ? rPosY.value : lPosY.value;
        double pz = right ? rPosZ.value : lPosZ.value;
        double sc = right ? rScale.value : lScale.value;
        StringBuilder sb = new StringBuilder();
        sb.append(right ? "Right" : "Left").append(" | Pos ")
                .append(String.format("%.2f, %.2f, %.2f", px, py, pz))
                .append(" | Scale ").append(String.format("%.2f", sc));
        if (right) {
            float[] rot = styleRot();
            sb.append(" | Rot ").append(String.format("%.0f, %.0f, %.0f", rot[0], rot[1], rot[2]));
        }
        return sb.toString();
    }

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    /** Every number in {@code s}, comma- or dot-decimal (handles both "-0.03" and "-0,03" —
     *  {@link #exportText()} uses the JVM's default locale, which writes commas on a Russian
     *  system), in the order they appear. */
    private static double[] parseNumbers(String s) {
        java.util.regex.Matcher m = NUMBER.matcher(s);
        java.util.List<Double> out = new java.util.ArrayList<>();
        while (m.find()) out.add(Double.parseDouble(m.group().replace(',', '.')));
        double[] arr = new double[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    /** Parses {@link #exportText()}'s own format back into settings — the "Paste" button next to
     *  "Copy" in the ClickGUI, and how the 5 built-in styles above were authored. Accepts
     *  "Right|Left | Pos x, y, z | Scale s [| Rot x, y, z]" (Rot only matters for Right — Style/
     *  Custom rotation is right-hand-only, same as everywhere else in this module); Rot, if
     *  present, always switches {@code style} to Custom so the pasted rotation actually takes
     *  effect (a named Style would otherwise keep using its own fixed rotation). Returns false
     *  (no changes applied) if the text doesn't look like this format at all.
     */
    public boolean importText(String text) {
        if (text == null) return false;
        String[] parts = text.split("\\|");
        if (parts.length < 3) return false;
        String handStr = parts[0].trim();
        boolean right;
        if (handStr.equalsIgnoreCase("right")) right = true;
        else if (handStr.equalsIgnoreCase("left")) right = false;
        else return false;

        Double[] pos = null, sc = null, rot = null;
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            String lower = p.toLowerCase(java.util.Locale.ROOT);
            double[] nums = parseNumbers(p);
            if (lower.startsWith("pos") && nums.length >= 3) pos = new Double[]{ nums[0], nums[1], nums[2] };
            else if (lower.startsWith("scale") && nums.length >= 1) sc = new Double[]{ nums[0] };
            else if (lower.startsWith("rot") && nums.length >= 3) rot = new Double[]{ nums[0], nums[1], nums[2] };
        }
        if (pos == null || sc == null) return false;

        if (right) {
            rPosX.value = pos[0]; rPosY.value = pos[1]; rPosZ.value = pos[2]; rScale.value = sc[0];
            if (rot != null) {
                rRotX.value = rot[0]; rRotY.value = rot[1]; rRotZ.value = rot[2];
                style.index = STYLE_CUSTOM;
            }
        } else {
            lPosX.value = pos[0]; lPosY.value = pos[1]; lPosZ.value = pos[2]; lScale.value = sc[0];
        }
        hand.index = right ? 0 : 1;
        return true;
    }

    /** Back to the untouched vanilla pose (both hands). */
    public void resetToDefaults() {
        style.index = 0;
        rPosX.value = 0; rPosY.value = 0; rPosZ.value = 0; rScale.value = 1;
        lPosX.value = 0; lPosY.value = 0; lPosZ.value = 0; lScale.value = 1;
        rRotX.value = 0; rRotY.value = 0; rRotZ.value = 0;
        hand.index = 0; animation.index = ANIM_DEFAULT; sway.value = true;
        outline.value = false; fill.index = 0;
        applyStylePosPreset();   // style 0 (Showcase) has a pos/scale preset — land on it, not on all-zeros
    }
}

package com.lume.client.module.modules.cosmetic;

import com.lume.client.fx.GlintTexture;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;

/**
 * Enchant Glint — customises the vanilla enchantment shimmer instead of drawing a separate overlay.
 * Two styles:
 * <ul>
 *   <li><b>Normal</b> — the vanilla-style glint, but recoloured, with crank-able saturation and
 *       <b>hardness fixed at maximum</b> (crisp pixels, no blur). Speed controls the shimmer scroll.
 *       Implemented by swapping the vanilla glint sprite ({@link GlintTexture}) + a scroll-speed
 *       mixin (RenderPhaseGlintSpeedMixin) — so it applies on the real item mesh in every view.
 *   <li><b>No Animation</b> — the glint is removed entirely; enchanted items render like plain ones
 *       (LayerRenderStateGlintMixin forces the glint layer off while this style is active).
 * </ul>
 *
 * <p>Colour and Speed are read live by {@link GlintTexture} / the mixins — no restart needed.
 * Scope note: this covers <i>items</i> (held/GUI/dropped/on other players). Armor enchant glint
 * uses a separate render path and is intentionally left untouched.
 */
public class EnchantGlint extends Module {

    public static final int STYLE_NORMAL = 0, STYLE_NONE = 1;

    public final ModeSetting   style      = add(new ModeSetting("Style", 0, "Normal", "No Animation"));
    public final ColorSetting  color      = add(new ColorSetting("Color", false, 180, 90, 255));
    public final SliderSetting saturation = add(new SliderSetting("Saturation", 1.6, 0.2, 2.5, false));
    public final SliderSetting speed       = add(new SliderSetting("Speed", 1.0, 0.0, 3.0, false));

    // Last-applied texture inputs, so we only regenerate/upload the sprite when something changed.
    private int lastRgb = Integer.MIN_VALUE;
    private double lastSat = -1;
    private int lastStyle = -1;

    public EnchantGlint() {
        super("Enchant Glint", "Custom-coloured, sharper enchant shimmer (or none)", Category.COSMETIC, -1);
    }

    /** Current glint colour as 0xRRGGBB (respects the Accent toggle). */
    public int glintRgb() {
        return color.accent ? Theme.accentRgb() : color.rgb();
    }

    @Override
    public void onTick() {
        if (style.index == STYLE_NORMAL) {
            int rgb = glintRgb();
            float sat = (float) saturation.value;
            if (lastStyle != STYLE_NORMAL || rgb != lastRgb || sat != lastSat) {
                GlintTexture.apply(rgb, sat);
                lastRgb = rgb; lastSat = sat; lastStyle = STYLE_NORMAL;
            }
        } else {   // No Animation — glint suppressed by the mixin; make sure the vanilla sprite is back
            if (lastStyle != STYLE_NONE) {
                GlintTexture.restore();
                lastStyle = STYLE_NONE;
            }
        }
    }

    @Override
    public void onDisable() {
        GlintTexture.restore();
        lastStyle = -1; lastRgb = Integer.MIN_VALUE; lastSat = -1;
    }
}

package com.lume.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * Replaces the vanilla enchantment-glint sprites ({@code textures/misc/enchanted_glint_item.png}
 * and {@code ..._entity.png}) at runtime with a generated one, so the game's own glint pass draws
 * OUR pattern on the real item mesh — automatically correct in every view (hand, GUI, dropped,
 * on other players) with no per-item silhouette work.
 *
 * <p>Two knobs, matching Enchant Glint's "Normal" style:
 * <ul>
 *   <li><b>Colour</b> — baked straight into the sprite.
 *   <li><b>Saturation</b> — scales the streak intensity/coverage (higher = stronger, more vivid glint).
 * </ul>
 * <b>Hardness is fixed at maximum</b>: the sprite is drawn with hard-stepped edges and uploaded with
 * nearest-neighbour filtering (no bilinear blur) + REPEAT wrap (so the glint's own UV scroll tiles
 * seamlessly). Scroll <i>speed</i> is a separate concern — see RenderPhaseGlintSpeedMixin.
 *
 * <p>All GL work here must run on the render thread; every caller ({@code EnchantGlint.onTick} /
 * {@code onDisable}) is invoked from the client tick, which is the render thread.
 */
public final class GlintTexture {

    private GlintTexture() {}

    private static final Identifier ITEM = ItemRenderer.ITEM_ENCHANTMENT_GLINT;
    private static final Identifier ENTITY = ItemRenderer.ENTITY_ENCHANTMENT_GLINT;

    // The glint pass samples this texture at a small UV scale (~0.16), so the on-item window is only
    // a fraction of the sheet. The pattern must be FINE (short period) or less than one streak fits in
    // view and it reads as a flat tint indistinguishable from vanilla. TILE must stay a multiple of
    // PERIOD so the diagonal pattern tiles seamlessly under REPEAT wrap.
    private static final int TILE = 64;
    private static final int PERIOD = 8;

    private static boolean overridden = false;

    /** Generate + install the custom glint sprite for {@code rgb} (0xRRGGBB) at the given intensity. */
    public static void apply(int rgb, float intensity) {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(() -> apply(rgb, intensity)); return; }
        installOne(ITEM, rgb, intensity);
        installOne(ENTITY, rgb, intensity);
        overridden = true;
        System.out.printf("[Lume] EnchantGlint: glint sprite replaced (rgb=%06X sat=%.2f)%n", rgb & 0xFFFFFF, intensity);
    }

    /** Restore the vanilla glint sprites (re-registers a fresh resource texture that reloads the PNG). */
    public static void restore() {
        if (!overridden) return;
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(GlintTexture::restore); return; }
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        tm.registerTexture(ITEM, new ResourceTexture(ITEM));
        tm.registerTexture(ENTITY, new ResourceTexture(ENTITY));
        overridden = false;
    }

    private static void installOne(Identifier id, int rgb, float intensity) {
        NativeImage img = new NativeImage(TILE, TILE, false);
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float v = band(Math.floorMod(x + y, PERIOD)) + 0.5f * band(Math.floorMod(x - y, PERIOD));
                v = Math.min(1f, v) * intensity;
                if (v > 1f) v = 1f;
                int a = (int) (v * 255f);
                int r = (int) (cr * v), g = (int) (cg * v), b = (int) (cb * v);
                img.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        tex.setFilter(false, false);   // nearest-neighbour, no mipmap → crisp, no blur ("hardness max")
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        tm.registerTexture(id, tex);
        // The glint pass scrolls UVs past [0,1]; force REPEAT wrap so the pattern tiles instead of
        // clamping/smearing at the edge (vanilla's own glint texture relies on the same).
        tex.bindTexture();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }

    /** Crisp thin diagonal streak within a period: a 2px bright core + 1px halo, then hard dark —
     *  so at the glint's small sample scale you still see distinct moving lines, not a flat wash. */
    private static float band(int d) {
        int m = Math.min(d, PERIOD - d);
        if (m <= 1) return 1f;
        if (m == 2) return 0.35f;
        return 0f;
    }
}

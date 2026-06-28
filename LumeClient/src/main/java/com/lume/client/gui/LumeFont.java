package com.lume.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom font renderer: rasterises the bundled Inter TTF with Java2D
 * antialiasing into a texture atlas and draws glyphs directly — bypassing
 * Minecraft's font system for a clean, launcher-quality look.
 *
 * Fully guarded: if anything fails, {@link #ready} stays false and callers
 * fall back to the vanilla TTF path, so the client never breaks.
 */
public final class LumeFont {

    public static final int FONT_PX = 32;   // rasterisation size (high-res)
    private static final int PAD = 3;

    public static boolean ready = false;
    private static boolean tried = false;

    private static Identifier atlasId;
    private static int atlasW, atlasH, cellW, cellH, ascent;
    // Optical glyph bounds inside a cell (px from cell top), measured from a
    // capital letter — used to vertically centre text inside a box.
    private static int glyphTop, glyphBot;
    private static final Map<Character, int[]> glyphs = new HashMap<>(); // char -> {u, v, advance}

    private LumeFont() {}

    /** ASCII font (default) vs Cyrillic font (Russian). */
    private static String fontPath = "/assets/lume/font/lume.ttf";
    private static boolean loadedRu = false;

    public static void ensure() {
        if (tried) return;
        tried = true;
        try {
            init();
            ready = true;
            System.out.println("[Lume] custom font READY (atlas " + atlasW + "x" + atlasH + ", cell " + cellW + "x" + cellH + ")");
        } catch (Throwable t) {
            ready = false;
            System.out.println("[Lume] custom font FAILED, using fallback: " + t);
            t.printStackTrace();
        }
    }

    /** Swap the HUD font between the default (Poppins, no Cyrillic) and PT Sans (Cyrillic) for RU. */
    public static void ensureLang(boolean ru) {
        ensure();
        if (!ready || ru == loadedRu) return;
        String path = ru ? "/assets/lume/font/ptsans.ttf" : "/assets/lume/font/lume.ttf";
        try {
            fontPath = path;
            glyphs.clear();
            init();
            loadedRu = ru;
        } catch (Throwable t) {
            System.out.println("[Lume] font lang swap failed: " + t);
        }
    }

    private static void init() throws Exception {
        Font font;
        try (InputStream in = LumeFont.class.getResourceAsStream(fontPath)) {
            if (in == null) throw new IllegalStateException("font not found: " + fontPath);
            font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) FONT_PX);
        }

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        ascent = fm.getAscent();
        cellH = fm.getAscent() + fm.getDescent() + PAD * 2;
        int maxAdv = fm.getMaxAdvance() > 0 ? fm.getMaxAdvance() : FONT_PX;
        cellW = maxAdv + PAD * 2;
        pg.dispose();

        java.util.List<Character> chars = new java.util.ArrayList<>();
        for (char c = 32; c <= 126; c++) chars.add(c);          // ASCII
        for (char c = 0x410; c <= 0x451; c++) chars.add(c);     // Cyrillic
        int cols = 16;
        int rows = (chars.size() + cols - 1) / cols;
        atlasW = cols * cellW;
        atlasH = rows * cellH;

        BufferedImage img = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setFont(font);
        g.setColor(Color.WHITE);
        for (int i = 0; i < chars.size(); i++) {
            char c = chars.get(i);
            int col = i % cols, row = i / cols;
            int cx = col * cellW, cy = row * cellH;
            g.drawString(String.valueOf(c), cx + PAD, cy + PAD + ascent);
            glyphs.put(c, new int[]{cx, cy, fm.charWidth(c)});
        }
        g.dispose();

        // Measure the optical top/bottom of a capital 'H' (no ascender/descender
        // overhang) so we can centre text vertically by its visual body, not the
        // padded atlas cell.
        glyphTop = PAD;
        glyphBot = PAD + ascent;
        int[] h = glyphs.get('H');
        if (h != null) {
            int top = -1, bot = -1;
            for (int yy = 0; yy < cellH; yy++) {
                boolean rowHasInk = false;
                for (int xx = 0; xx < cellW; xx++) {
                    if (((img.getRGB(h[0] + xx, h[1] + yy) >>> 24) & 0xFF) > 40) { rowHasInk = true; break; }
                }
                if (rowHasInk) { if (top < 0) top = yy; bot = yy; }
            }
            if (top >= 0) { glyphTop = top; glyphBot = bot + 1; }
        }

        NativeImage ni = new NativeImage(atlasW, atlasH, true);
        for (int yy = 0; yy < atlasH; yy++) {
            for (int xx = 0; xx < atlasW; xx++) {
                ni.setColorArgb(xx, yy, img.getRGB(xx, yy));
            }
        }
        atlasId = Identifier.of("lume", "font_atlas");
        NativeImageBackedTexture tex = new NativeImageBackedTexture(ni);
        tex.setFilter(true, false); // bilinear filtering -> smooth glyph edges, not pixelated
        MinecraftClient.getInstance().getTextureManager().registerTexture(atlasId, tex);
    }

    /** Optical centre of a text body, in atlas px from the cell top (for vertical centring). */
    public static float opticalCenterPx() { return (glyphTop + glyphBot) / 2f; }

    /** Advance width of the string at FONT_PX. */
    public static float advance(String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            int[] gl = glyphs.get(s.charAt(i));
            w += gl != null ? gl[2] : FONT_PX / 2f;
        }
        return w;
    }

    public static void draw(DrawContext ctx, String s, double x, double y, int color, float drawScale) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        if (a == 0f) a = 1f;

        ctx.draw(); // flush prior geometry while shader colour is still white
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);

        var m = ctx.getMatrices();
        m.push();
        m.translate(x, y, 0.0);
        m.scale(drawScale, drawScale, 1.0f);
        int penX = 0;
        for (int i = 0; i < s.length(); i++) {
            int[] gl = glyphs.get(s.charAt(i));
            if (gl != null) {
                ctx.drawTexture(RenderLayer::getGuiTextured, atlasId, penX, 0, (float) gl[0], (float) gl[1], cellW, cellH, atlasW, atlasH);
                penX += gl[2];
            } else {
                penX += FONT_PX / 2;
            }
        }
        m.pop();

        ctx.draw(); // flush glyphs with the tint applied
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}

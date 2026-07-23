package com.lume.client.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * A small, continuously-regenerated procedural texture for Block Outline's animated Fill styles
 * (Cosmos/Swirl/Starfield/Worms) — real per-PIXEL resolution (64×64 = 4096 samples, bilinear-
 * filtered), not per-VERTEX colour (a handful of grid points GPU-interpolated between them, which
 * reads as blocky/blurry no matter how dense the grid gets — that was the "just changing colours"
 * complaint). Same technique {@link GlintTexture} already ships successfully on this exact
 * hardware (generate a {@link NativeImage} on the CPU, upload as a normal
 * {@link NativeImageBackedTexture}) — deliberately NOT a custom GLSL core shader: that was tried
 * once ({@code RoundShader}, now removed) and never loaded in-game on this machine's Intel iGPU.
 * Texture upload is the same standard GL path every vanilla block/item texture already uses, with
 * none of a custom core shader's registration/driver risk.
 */
public final class BlockFillTexture {
    private BlockFillTexture() {}

    public static final Identifier ID = Identifier.of("lume", "block_fill_dynamic");
    // 128×128 = 16384 samples — only ever rendered for the ONE block currently under the
    // crosshair (never dozens at once), and this is plain sine/trig math, not anything expensive
    // — cheap enough to regenerate every frame at 60fps on top of everything else this client
    // already does per frame. Bumped from 64 specifically for smoothness — the earlier size read
    // visibly soft/low-res once actually compared side by side against real reference footage.
    public static final int SIZE = 128;
    private static long lastUpdateNanos = 0;
    // ~60 regenerates/sec — matches typical frame rate, so the animation never visibly steps.
    private static final long UPDATE_INTERVAL_NANOS = 16_000_000L;

    /** Regenerates (throttled) the shared dynamic texture for the given Fill mode and returns its
     *  id to bind. {@code fillIndex} matches BlockOutline's ModeSetting: 2=Cosmos 3=Swirl
     *  4=Starfield 5=Worms. {@code seed} should be stable per-block (see BlockOutline) so the
     *  texture doesn't visibly "pop" between blocks sharing the same instant. */
    public static Identifier get(int fillIndex, double seed, int alpha) {
        long now = System.nanoTime();
        if (now - lastUpdateNanos >= UPDATE_INTERVAL_NANOS) {
            lastUpdateNanos = now;
            regenerate(fillIndex, seed, alpha);
        }
        return ID;
    }

    private static void regenerate(int fillIndex, double seed, int alpha) {
        NativeImage img = new NativeImage(SIZE, SIZE, false);
        double t = System.currentTimeMillis() / 1000.0;
        final double SC = 6.0;
        for (int py = 0; py < SIZE; py++) {
            double v = py / (double) (SIZE - 1);
            for (int px = 0; px < SIZE; px++) {
                double u = px / (double) (SIZE - 1);
                int argb = switch (fillIndex) {
                    case 2 -> ProceduralFill.cosmos(u * SC, v * SC, seed, t, alpha);
                    case 3 -> ProceduralFill.swirl(u * SC, v * SC, seed, t, alpha);
                    case 4 -> ProceduralFill.starfield(u * SC, v * SC, seed, t, alpha);
                    default -> ProceduralFill.worms(u * SC, v * SC, seed, t, alpha);
                };
                img.setColorArgb(px, py, argb);
            }
        }
        NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        tex.setFilter(true, false);   // bilinear, no mipmap — smooth gradients (opposite of Glint's crisp/nearest)
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        tm.registerTexture(ID, tex);   // replaces (and disposes) whatever texture was registered under ID before
        tex.bindTexture();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }
}

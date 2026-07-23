package com.lume.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * A 1x1 solid-white texture, shared by HitColor and Custom Hand Outline/Fill so their forced
 * recolour passes read correctly at ANY chosen colour, including white.
 *
 * <p>Both features draw a second pass through {@link ForcedColorVertexConsumer} on an
 * alpha-blended layer (e.g. {@code RenderLayer.getEntityTranslucentEmissiveNoOutline}) to force
 * a flat colour. That alone isn't enough: the shader still multiplies texture RGB × vertex
 * colour — "emissive" only skips world lighting, not the texture — so forcing vertex colour
 * while still sampling the REAL item/entity texture reproduces the exact bug this was meant to
 * fix: {@code texture × 0xFFFFFF} (white) is the identity multiply, so white silently did
 * nothing. Binding THIS texture instead (uniformly white, any UV) makes texture × colour ==
 * colour exactly, for every colour.
 */
public final class FlatColorTexture {

    private static Identifier id;

    public static Identifier id() {
        if (id == null) {
            NativeImage img = new NativeImage(1, 1, false);
            img.setColorArgb(0, 0, 0xFFFFFFFF);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            id = Identifier.of("lume", "flat_color_white");
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        }
        return id;
    }

    private FlatColorTexture() {}
}

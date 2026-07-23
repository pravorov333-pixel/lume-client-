package com.lume.client.util;

import net.minecraft.client.render.VertexConsumer;

/**
 * Wraps a {@link VertexConsumer}, overriding every {@code color(...)} call — either to a fixed
 * RGBA (flat recolour: Custom Hand's "Color" fill, HitColor) or, via {@link ColorFn}, to a
 * colour computed per-vertex from that vertex's own local mesh-space position (Custom Hand's
 * "Cosmos"/"Swirl" fills — see {@link com.lume.client.fx.ProceduralFill}). The item's model
 * builder always calls {@code vertex(x,y,z)} before {@code color(...)} for each vertex (the
 * standard {@code VertexConsumer} builder order), so tracking the last {@code vertex()} call is
 * enough to know which position the following {@code color()} call belongs to.
 */
public final class ForcedColorVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int r, g, b, a;
    private final ColorFn fn;
    private float lastX, lastY, lastZ;

    /** Computes an ARGB colour from a vertex's local mesh-space position. */
    public interface ColorFn { int argb(float x, float y, float z); }

    public ForcedColorVertexConsumer(VertexConsumer delegate, int argb) {
        this.delegate = delegate;
        this.a = (argb >>> 24) & 0xFF;
        this.r = (argb >> 16) & 0xFF;
        this.g = (argb >> 8) & 0xFF;
        this.b = argb & 0xFF;
        this.fn = null;
    }

    public ForcedColorVertexConsumer(VertexConsumer delegate, ColorFn fn) {
        this.delegate = delegate;
        this.fn = fn;
        this.r = this.g = this.b = this.a = 0;
    }

    @Override public VertexConsumer vertex(float x, float y, float z) {
        lastX = x; lastY = y; lastZ = z;
        return delegate.vertex(x, y, z);
    }

    @Override public VertexConsumer color(int red, int green, int blue, int alpha) {
        if (fn != null) {
            int argb = fn.argb(lastX, lastY, lastZ);
            return delegate.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
        }
        return delegate.color(r, g, b, a);
    }

    @Override public VertexConsumer texture(float u, float v) { return delegate.texture(u, v); }
    @Override public VertexConsumer overlay(int u, int v) { return delegate.overlay(u, v); }
    @Override public VertexConsumer light(int u, int v) { return delegate.light(u, v); }
    @Override public VertexConsumer normal(float x, float y, float z) { return delegate.normal(x, y, z); }
}

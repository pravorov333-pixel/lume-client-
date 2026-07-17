package com.lume.client.util;

import net.minecraft.client.render.VertexConsumer;

/**
 * Wraps a {@link VertexConsumer}, overriding every {@code color(...)} call to a fixed RGBA —
 * used to force a flat recolour (Custom Hand Outline/Fill, HitColor) regardless of whatever
 * per-vertex shading the real model geometry would otherwise submit.
 */
public final class ForcedColorVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int r, g, b, a;

    public ForcedColorVertexConsumer(VertexConsumer delegate, int argb) {
        this.delegate = delegate;
        this.a = (argb >>> 24) & 0xFF;
        this.r = (argb >> 16) & 0xFF;
        this.g = (argb >> 8) & 0xFF;
        this.b = argb & 0xFF;
    }

    @Override public VertexConsumer vertex(float x, float y, float z) { return delegate.vertex(x, y, z); }
    @Override public VertexConsumer color(int red, int green, int blue, int alpha) { return delegate.color(r, g, b, a); }
    @Override public VertexConsumer texture(float u, float v) { return delegate.texture(u, v); }
    @Override public VertexConsumer overlay(int u, int v) { return delegate.overlay(u, v); }
    @Override public VertexConsumer light(int u, int v) { return delegate.light(u, v); }
    @Override public VertexConsumer normal(float x, float y, float z) { return delegate.normal(x, y, z); }
}

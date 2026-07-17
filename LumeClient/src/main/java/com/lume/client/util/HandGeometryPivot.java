package com.lume.client.util;

import com.lume.client.mixin.ItemRenderStateAccessor;
import com.lume.client.mixin.LayerRenderStateAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom Hand's rotation pivot — computed entirely from the held item's own baked
 * mesh, not from any vanilla-authored "how to hold this" hint and not from a manually
 * tuned slider. We read every quad's raw LOCAL vertex positions (before any transform
 * is applied to them — {@link #IDENTITY} is a no-op MatrixStack.Entry, so
 * {@link VertexConsumer#quad} hands back untouched model-space coordinates) and take
 * the centre of their bounding box.
 *
 * <p>Why the raw local-space centre is the mathematically correct pivot: our rotation
 * (applied in HeldItemRendererMixin, BEFORE vanilla's own first-person placement
 * transform runs later in the same method) and the mesh's raw vertices both then pass
 * through that exact same later transform chain on their way to the screen. Putting our
 * pivot at the same point the mesh vertices average out to, in that shared pre-transform
 * space, means the point we rotate around and the mesh's visual centre land on the same
 * screen pixel after everything downstream is applied — so rotating around it never
 * reads as the item swinging or drifting, for any item shape, with no per-item tuning.
 */
public final class HandGeometryPivot {

    private static final Map<Item, Vector3f> CACHE = new HashMap<>();
    private static final Random RANDOM = Random.create();
    private static final MatrixStack.Entry IDENTITY = new MatrixStack().peek();

    private HandGeometryPivot() {}

    public static Vector3f center(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new Vector3f();
        Item item = stack.getItem();
        Vector3f cached = CACHE.get(item);
        if (cached != null) return cached;

        Vector3f result = compute(stack);
        CACHE.put(item, result);
        return result;
    }

    private static Vector3f compute(ItemStack stack) {
        String tag = "[Lume] HandGeometryPivot(" + stack.getItem() + "): ";
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) {
                System.out.println(tag + "no player/world yet, falling back to (0,0,0)");
                return new Vector3f();
            }

            ItemRenderState state = new ItemRenderState();
            mc.getItemModelManager().update(state, stack, ModelTransformationMode.FIXED, mc.world, mc.player, 0);

            ItemRenderState.LayerRenderState layer = ((ItemRenderStateAccessor) state).lume$getFirstLayer();
            if (layer == null) {
                System.out.println(tag + "getFirstLayer() returned null, falling back to (0,0,0)");
                return new Vector3f();
            }
            BakedModel model = ((LayerRenderStateAccessor) layer).lume$getModel();
            if (model == null) {
                System.out.println(tag + "layer.model was null, falling back to (0,0,0)");
                return new Vector3f();
            }

            Bounds b = new Bounds();
            for (Direction dir : Direction.values()) {
                RANDOM.setSeed(42L);
                for (BakedQuad q : model.getQuads(null, dir, RANDOM)) collect(q, b);
            }
            RANDOM.setSeed(42L);
            for (BakedQuad q : model.getQuads(null, null, RANDOM)) collect(q, b);

            if (!b.any) {
                System.out.println(tag + "model had zero quads, falling back to (0,0,0)");
                return new Vector3f();
            }
            Vector3f c = b.center();
            System.out.println(tag + "computed pivot " + c + " from bounds ["
                    + b.minX + "," + b.minY + "," + b.minZ + "] to [" + b.maxX + "," + b.maxY + "," + b.maxZ + "]");
            return c;
        } catch (Throwable e) {
            System.out.println(tag + "EXCEPTION, falling back to (0,0,0): " + e);
            e.printStackTrace();
            return new Vector3f();
        }
    }

    private static void collect(BakedQuad q, VertexConsumer sink) {
        sink.quad(IDENTITY, q, 1f, 1f, 1f, 1f, OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    /** Bare-bones VertexConsumer that only tracks the min/max of every position it
     *  receives — everything else (colour/uv/overlay/light/normal) is a no-op. */
    private static final class Bounds implements VertexConsumer {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean any = false;

        Vector3f center() {
            return any ? new Vector3f((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f) : new Vector3f();
        }

        @Override public VertexConsumer vertex(float x, float y, float z) {
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
            any = true;
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float v) { return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}

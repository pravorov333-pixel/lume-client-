package com.lume.client.util;

import com.lume.client.mixin.ItemRenderStateAccessor;
import com.lume.client.mixin.LayerRenderStateAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;

/**
 * Draws a held item's own REAL, fully-textured render (same model, same texture, same alpha
 * cutout) for Custom Hand's Outline/Fill — only each vertex's COLOUR is intercepted via
 * {@link ForcedColorVertexConsumer}, everything else (geometry, texture, transform, RenderLayer)
 * is exactly what vanilla itself would draw.
 *
 * <p>Two earlier approaches both failed, for two DIFFERENT reasons (see project memory for the
 * full multi-round story):
 * <ol>
 *   <li>Going through {@code self.renderItem()} with a manually-picked {@code RenderLayer} put
 *   the geometry in the wrong place — a huge, tilted, mis-centred plane — because neither the
 *   model's own {@code Transformation} (the FIRST_PERSON_RIGHT_HAND scale/rotate/translate) nor
 *   its {@code -0.5,-0.5,-0.5} centering translate were ever applied; those only happen inside
 *   {@code ItemRenderState.LayerRenderState.render(...)} itself (confirmed via {@code javap}).</li>
 *   <li>After routing through the real {@code render(...)} fixed the position, a raw immediate
 *   {@code POSITION_COLOR} draw of the hand-extracted quads still showed a plain filled
 *   rectangle instead of the item's shape — because it discarded the item's TEXTURE entirely.
 *   A "generated" item model's front/back faces are a full rectangular quad matching the icon's
 *   pixel bounds; the actual sword/tool silhouette only ever came from the texture's ALPHA
 *   channel cutting that rectangle down. Drawing flat colour with no texture at all was always
 *   going to render as a plain rectangle, regardless of position/scale/rotation being correct.</li>
 * </ol>
 *
 * <p>This version keeps the REAL {@code VertexConsumerProvider} (so the real texture — and its
 * alpha cutout — is still sampled by the shader exactly like vanilla's own render) and only
 * swaps each vertex's colour before it reaches the delegate buffer; the final on-screen colour is
 * (our colour) × (the real texture sample), which keeps the item's true silhouette while still
 * recolouring it.
 */
public final class ItemFlatFill {
    private ItemFlatFill() {}

    public static void draw(MatrixStack matrices, LivingEntity entity, ItemStack stack,
                            ModelTransformationMode mode, VertexConsumerProvider realVcp, int light,
                            ForcedColorVertexConsumer.ColorFn fn) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        ItemRenderState.LayerRenderState layer = firstLayer(mc, entity, stack, mode);
        if (layer == null) return;
        // A fresh wrapper per getBuffer() call (never the same instance twice) — enchanted items
        // composite a base + glint layer via VertexConsumers.union(a, b), which throws on a == b
        // by reference (see the "Duplicate delegates" crash this project already hit once with a
        // shared Collector); wrapping each layer's own real delegate individually avoids that
        // entirely, same fix shape, applied here pre-emptively.
        VertexConsumerProvider tintingVcp = renderLayer -> new ForcedColorVertexConsumer(realVcp.getBuffer(renderLayer), fn);
        ((LayerRenderStateAccessor) layer).lume$render(matrices, tintingVcp, light, OverlayTexture.DEFAULT_UV);
    }

    private static ItemRenderState.LayerRenderState firstLayer(MinecraftClient mc, LivingEntity entity, ItemStack stack, ModelTransformationMode mode) {
        try {
            ItemRenderState state = new ItemRenderState();
            mc.getItemModelManager().update(state, stack, mode, mc.world, entity, 0);
            return ((ItemRenderStateAccessor) state).lume$getFirstLayer();
        } catch (Throwable e) {
            System.out.println("[Lume] ItemFlatFill: model lookup failed: " + e);
            return null;
        }
    }
}

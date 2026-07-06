package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

/**
 * Block Outline — recolours the block-selection outline (WorldRendererMixin) and
 * optionally fills the targeted block with a solid colour or an animated
 * "cosmos" gradient (rendered here via WorldRenderEvents).
 */
public class BlockOutline extends Module {

    public final ColorSetting  color     = add(new ColorSetting("Color", false, 169, 155, 199));
    public final ModeSetting   fill      = add(new ModeSetting("Fill", 0, "Off", "Color", "Cosmos"));
    public final SliderSetting fillAlpha = add(new SliderSetting("Fill Opacity", 0.35, 0.0, 0.8, false));

    public BlockOutline() {
        super("Block Outline", "Обводка + заливка блока", Category.COSMETIC, -1);
    }

    /** Outline colour as 0xAARRGGBB, or 0 when off (= keep vanilla). */
    public static int argbOrZero() {
        Module m = LumeClient.MODULES.getByName("Block Outline");
        if (!(m instanceof BlockOutline b) || !b.isEnabled()) return 0;
        return 0xFF000000 | (b.color.rgb() & 0xFFFFFF);
    }

    // corner index bits: x<<2 | y<<1 | z ; faces reference these 4-corner loops
    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}
    };

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void renderFill(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Block Outline");
        if (!(m instanceof BlockOutline b) || !b.isEnabled() || b.fill.index == 0) return;
        if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult bhr)
                || bhr.getType() != HitResult.Type.BLOCK || ctx.camera() == null) return;
        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;
        // tiny outward inflate so the fill sits just past the block's own faces —
        // avoids z-fighting flicker/gaps when the fill is exactly coincident with block geometry.
        Box bb = shape.getBoundingBox().offset(pos).expand(0.002);

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        int alpha = (int) (b.fillAlpha.value * 255);
        boolean cosmos = b.fill.index == 2;
        int solid = (alpha << 24) | (b.color.rgb() & 0xFFFFFF);

        // colour + camera-relative position for the 8 corners
        double[] xs = { bb.minX, bb.maxX }, ys = { bb.minY, bb.maxY }, zs = { bb.minZ, bb.maxZ };
        float[][] p = new float[8][3];
        int[] cc = new int[8];
        for (int i = 0; i < 8; i++) {
            double wx = xs[(i >> 2) & 1], wy = ys[(i >> 1) & 1], wz = zs[i & 1];
            p[i][0] = (float) (wx - cam.x); p[i][1] = (float) (wy - cam.y); p[i][2] = (float) (wz - cam.z);
            cc[i] = cosmos ? cosmos(wx, wy, wz, alpha) : solid;
        }

        Matrix4f mat = ms.peek().getPositionMatrix();
        // getDebugFilledBox() draws TRIANGLE_STRIP — feeding it quad-perimeter-order vertices
        // (and drawing all 6 faces in one un-restarted strip) produced the "half triangle" bug.
        // getDebugQuads() is QUADS draw mode with culling DISABLED (verified from bytecode), so
        // each 4-vertex group in FACES renders as an independent, fully visible quad — correct layer.
        RenderLayer layer = RenderLayer.getDebugQuads();
        VertexConsumer vc = vcp.getBuffer(layer);
        for (int[] f : FACES)
            for (int idx : f)
                vc.vertex(mat, p[idx][0], p[idx][1], p[idx][2]).color(cc[idx]);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    /**
     * Animated deep-space gradient (blue → purple → pink), shifting over time + position.
     * Two incommensurate (non-integer-ratio) sine drifts combine so the pattern doesn't
     * visibly repeat for a very long time — a single fast modulo would loop every ~3s.
     */
    private static int cosmos(double wx, double wy, double wz, int alpha) {
        double t = System.currentTimeMillis() / 1000.0;
        double drift = Math.sin(t / 47.0) * 60.0 + Math.sin(t / 71.0 + 1.7) * 40.0;   // slow, ~55min beat pattern
        float hue = (float) (260 + drift + (wx * 6 + wy * 11 + wz * 6));               // spatial variation stays subtle
        return (alpha << 24) | (hsv(hue, 0.75f, 0.95f) & 0xFFFFFF);
    }

    private static int hsv(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), mm = v - c;
        float r, g, bl;
        switch ((int) (h / 60f) % 6) {
            case 0 -> { r = c; g = x; bl = 0; }
            case 1 -> { r = x; g = c; bl = 0; }
            case 2 -> { r = 0; g = c; bl = x; }
            case 3 -> { r = 0; g = x; bl = c; }
            case 4 -> { r = x; g = 0; bl = c; }
            default -> { r = c; g = 0; bl = x; }
        }
        return (Math.round((r + mm) * 255) << 16) | (Math.round((g + mm) * 255) << 8) | Math.round((bl + mm) * 255);
    }
}

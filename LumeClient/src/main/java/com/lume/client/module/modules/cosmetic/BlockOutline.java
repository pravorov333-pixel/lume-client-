package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * Block Outline — replaces vanilla's block-selection outline (which follows the
 * block's actual voxel shape, is depth-tested, and is cancelled entirely in
 * WorldRendererMixin whenever this module is on) with our own full 1x1x1
 * bounding-box wireframe drawn with depth testing off, so the whole block reads
 * as a complete box and stays visible through other blocks. Optional fill (solid
 * colour or an animated "cosmos" gradient) still follows the real voxel shape.
 */
public class BlockOutline extends Module {

    public final ColorSetting  color     = add(new ColorSetting("Color", false, 169, 155, 199));
    public final SliderSetting lineWidth = add(new SliderSetting("Line Width", 2.0, 1.0, 5.0, false));
    // Defaults to "Color" (was "Off") — with Fill left on Off, the Fill Opacity slider below
    // has zero visible effect no matter what it's dragged to (fill mode gates rendering
    // entirely, independent of opacity), which read as "the opacity slider does nothing".
    public final ModeSetting   fill      = add(new ModeSetting("Fill", 1, "Off", "Color", "Cosmos"));
    public final SliderSetting fillAlpha = add(new SliderSetting("Fill Opacity", 0.35, 0.0, 0.8, false));

    public BlockOutline() {
        super("Block Outline", "Обводка + заливка блока", Category.COSMETIC, -1);
    }

    public static boolean active() {
        Module m = LumeClient.MODULES.getByName("Block Outline");
        return m instanceof BlockOutline b && b.isEnabled();
    }

    /** Outline colour as 0xAARRGGBB, or 0 when off. */
    public static int argbOrZero() {
        Module m = LumeClient.MODULES.getByName("Block Outline");
        if (!(m instanceof BlockOutline b) || !b.isEnabled()) return 0;
        int rgb = b.color.accent ? com.lume.client.gui.Theme.accentRgb() : b.color.rgb();
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). Full-block
     *  no-depth wireframe, replacing vanilla's own (cancelled in WorldRendererMixin). */
    public static void renderOutline(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Block Outline");
        if (!(m instanceof BlockOutline b) || !b.isEnabled()) return;
        if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult bhr)
                || bhr.getType() != HitResult.Type.BLOCK || ctx.camera() == null) return;
        BlockPos pos = bhr.getBlockPos();
        if (mc.world.getBlockState(pos).isAir()) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Vec3d cam = ctx.camera().getPos();
        float x0 = (float) (pos.getX() - cam.x), y0 = (float) (pos.getY() - cam.y), z0 = (float) (pos.getZ() - cam.z);
        int argb = argbOrZero();
        Render3D.boxThroughWalls(ms.peek(), x0, y0, z0, x0 + 1f, y0 + 1f, z0 + 1f, argb, (float) b.lineWidth.value);
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
        if (ms == null) return;
        Vec3d cam = ctx.camera().getPos();
        int alpha = (int) (b.fillAlpha.value * 255);
        boolean cosmos = b.fill.index == 2;
        int solid = (alpha << 24) | ((b.color.accent ? com.lume.client.gui.Theme.accentRgb() : b.color.rgb()) & 0xFFFFFF);

        // colour + camera-relative position for the 8 corners
        double[] xs = { bb.minX, bb.maxX }, ys = { bb.minY, bb.maxY }, zs = { bb.minZ, bb.maxZ };
        float[][] p = new float[8][3];
        int[] cc = new int[8];
        for (int i = 0; i < 8; i++) {
            double wx = xs[(i >> 2) & 1], wy = ys[(i >> 1) & 1], wz = zs[i & 1];
            p[i][0] = (float) (wx - cam.x); p[i][1] = (float) (wy - cam.y); p[i][2] = (float) (wz - cam.z);
            cc[i] = cosmos ? cosmos(wx, wy, wz, alpha) : solid;
        }

        // Raw immediate draw with depth testing off (same technique as Render3D.boxThroughWalls)
        // instead of the batched getDebugQuads() layer — that layer IS depth-tested, so the fill
        // was only visible on faces facing the camera with nothing in front, never "through walls".
        Render3D.fillBoxThroughWalls(ms.peek(), p, cc, FACES);
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

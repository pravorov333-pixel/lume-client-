package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
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

    // Outline stays on by default (existing behaviour unchanged) — off shows Fill alone, with
    // vanilla's own selection outline still cancelled too (that cancellation is tied to the
    // whole module being enabled, in WorldRendererMixin, not to this toggle specifically), so
    // "off" genuinely means no wireframe at all, ours or vanilla's.
    public final BoolSetting   outlineOn = add(new BoolSetting("Outline", true));
    public final ColorSetting  color     = add(new ColorSetting("Color", false, 169, 155, 199));
    public final SliderSetting lineWidth = add(new SliderSetting("Line Width", 2.0, 1.0, 5.0, false));
    // Defaults to "Color" (was "Off") — with Fill left on Off, the Fill Opacity slider below
    // has zero visible effect no matter what it's dragged to (fill mode gates rendering
    // entirely, independent of opacity), which read as "the opacity slider does nothing".
    public final ModeSetting   fill      = add(new ModeSetting("Fill", 1, "Off", "Color", "Cosmos", "Swirl", "Starfield", "Worms", "Rainbow"));
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
        if (!(m instanceof BlockOutline b) || !b.isEnabled() || !b.outlineOn.value) return;
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

    /**
     * WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient) — real box geometry
     * (the block's own AABB, so the fill always hugs the block's exact silhouette from any angle,
     * never floats off it or overshoots past its edges — a screen-space bounding RECT was tried
     * instead and rejected: a cube's projected AABB bounding box is provably bigger than its true
     * on-screen outline at any oblique angle, which is exactly the "sticks out past the block"
     * bug). What used to cause the visible seam at each face boundary wasn't the 3D geometry
     * itself, it was mapping each face independently to its own (0,0)..(1,1) UV — two faces
     * meeting at a shared edge would then sample DIFFERENT texture values right at that edge
     * (each face's own edge-of-image vs the other's), which reads as a seam line no matter how
     * closely the two faces' patterns otherwise match. Fixed here by computing UV per CORNER
     * (screen-projected position of that corner, normalized against the block's own screen-space
     * bounding box) instead of per FACE — every one of the block's 8 corners is shared by up to 3
     * faces, and since they all look up the exact same UV value for that corner, two faces
     * meeting at an edge now sample the identical texture value right at the seam. Continuous
     * across the whole visible silhouette, not just "the same pattern on 6 separate stickers".
     */
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
        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();

        double yaw = Math.toRadians(-camera.getYaw());
        double pitch = Math.toRadians(camera.getPitch());
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw), cpit = Math.cos(pitch), spit = Math.sin(pitch);
        double fx = syaw * cpit, fy = -spit, fz = cyaw * cpit;        // forward
        double rx = -fz, rz = fx;                                     // right = forward × up
        double rl = Math.sqrt(rx * rx + rz * rz); if (rl < 1e-6) { rx = 1; rz = 0; rl = 1; } rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;   // up
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double tanV = Math.tan(Math.toRadians((double) (int) mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / sh;

        double[] xs = { bb.minX, bb.maxX }, ys = { bb.minY, bb.maxY }, zs = { bb.minZ, bb.maxZ };
        float[][] corners = new float[8][3];
        double[] scrX = new double[8], scrY = new double[8];
        double minSx = Double.MAX_VALUE, maxSx = -Double.MAX_VALUE, minSy = Double.MAX_VALUE, maxSy = -Double.MAX_VALUE;
        boolean behind = false;
        for (int i = 0; i < 8; i++) {
            double wx = xs[(i >> 2) & 1], wy = ys[(i >> 1) & 1], wz = zs[i & 1];
            corners[i][0] = (float) (wx - cam.x); corners[i][1] = (float) (wy - cam.y); corners[i][2] = (float) (wz - cam.z);
            double dx = wx - cam.x, dy = wy - cam.y, dz = wz - cam.z;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth <= 0.05) { behind = true; break; }
            double rc = dx * rx + dz * rz, uc = dx * ux + dy * uy + dz * uz;
            double sx = (0.5 + 0.5 * (rc / depth) / (aspect * tanV)) * sw;
            double sy = (0.5 - 0.5 * (uc / depth) / tanV) * sh;
            scrX[i] = sx; scrY[i] = sy;
            if (sx < minSx) minSx = sx; if (sx > maxSx) maxSx = sx;
            if (sy < minSy) minSy = sy; if (sy > maxSy) maxSy = sy;
        }
        // Camera is (almost) inside the block, e.g. standing right up against it — the projection
        // math degenerates here, so skip this frame rather than draw a warped mess.
        if (behind) return;

        int alpha = (int) (b.fillAlpha.value * 255);

        if (b.fill.index == 1) {
            // Flat "Color" — genuinely one colour, a plain 8-corner fill is the right (and
            // cheapest) tool; no projected UV needed at all, no seam possible either way.
            int solid = (alpha << 24) | ((b.color.accent ? com.lume.client.gui.Theme.accentRgb() : b.color.rgb()) & 0xFFFFFF);
            int[] cc = new int[8];
            java.util.Arrays.fill(cc, solid);
            Render3D.fillBoxThroughWalls(ms.peek(), corners, cc, FACES);
            return;
        }

        if (b.fill.index == 6) {
            // Rainbow — a smooth RGB hue-cycle, solid colour (no procedural noise pattern).
            double t = System.currentTimeMillis() / 1000.0;
            float hue = (float) ((t * 70.0) % 360.0);   // one full cycle every ~5.1s
            int rgb = com.lume.client.fx.ProceduralFill.hsv(hue, 0.85f, 1f);
            int[] cc = new int[8];
            java.util.Arrays.fill(cc, (alpha << 24) | (rgb & 0xFFFFFF));
            Render3D.fillBoxThroughWalls(ms.peek(), corners, cc, FACES);
            return;
        }

        // Cosmos/Swirl/Starfield/Worms — real per-pixel texture (BlockFillTexture, same proven
        // CPU-generate/GPU-upload technique Enchant Glint already ships), mapped by re-projecting
        // a FINE GRID of points per face (not just the 4 corners) through the real camera
        // transform. Matching UV at the 4 shared corners (tried first) still showed a visible
        // "crease" at every face boundary: each face bilinearly interpolates UV between only its
        // own 4 corners, so even though the VALUE matches exactly at a shared edge, the pattern's
        // local scale/flow direction (the interpolation's derivative) doesn't — that mismatch in
        // slope, not colour, is what a "grань" actually is. Recomputing the true projection at
        // every grid point instead of interpolating 4 corner values makes each small quad's own
        // approximation error shrink with grid density, converging on the real continuous
        // camera-projected mapping — the same physical effect as a slide projector's image
        // wrapping smoothly (with correct per-face foreshortening, not a seam) around a real box.
        double spanX = Math.max(1e-4, maxSx - minSx), spanY = Math.max(1e-4, maxSy - minSy);
        double bx0 = bb.minX, bx1 = bb.maxX, by0 = bb.minY, by1 = bb.maxY, bz0 = bb.minZ, bz1 = bb.maxZ;
        double[][][] facesWorld = {
                { {bx0,by1,bz0},{bx1,by1,bz0},{bx1,by1,bz1},{bx0,by1,bz1} },   // top   (+Y)
                { {bx0,by0,bz0},{bx1,by0,bz0},{bx1,by0,bz1},{bx0,by0,bz1} },   // bottom(-Y)
                { {bx1,by0,bz0},{bx1,by1,bz0},{bx1,by1,bz1},{bx1,by0,bz1} },   // +X
                { {bx0,by0,bz0},{bx0,by1,bz0},{bx0,by1,bz1},{bx0,by0,bz1} },   // -X
                { {bx0,by0,bz1},{bx1,by0,bz1},{bx1,by1,bz1},{bx0,by1,bz1} },   // +Z
                { {bx0,by0,bz0},{bx1,by0,bz0},{bx1,by1,bz0},{bx0,by1,bz0} },   // -Z
        };
        final int N = 8;
        float[] verts = new float[facesWorld.length * N * N * 4 * 5];
        int o = 0;
        for (double[][] face : facesWorld) {
            double[] p00 = face[0], p10 = face[1], p11 = face[2], p01 = face[3];
            for (int j = 0; j < N; j++) {
                double v0 = j / (double) N, v1 = (j + 1) / (double) N;
                for (int i = 0; i < N; i++) {
                    double u0 = i / (double) N, u1 = (i + 1) / (double) N;
                    o = emitProjectedVertex(verts, o, p00, p10, p11, p01, u0, v0, cam, fx, fy, fz, rx, rz, ux, uy, uz, tanV, aspect, sw, sh, minSx, spanX, minSy, spanY);
                    o = emitProjectedVertex(verts, o, p00, p10, p11, p01, u1, v0, cam, fx, fy, fz, rx, rz, ux, uy, uz, tanV, aspect, sw, sh, minSx, spanX, minSy, spanY);
                    o = emitProjectedVertex(verts, o, p00, p10, p11, p01, u1, v1, cam, fx, fy, fz, rx, rz, ux, uy, uz, tanV, aspect, sw, sh, minSx, spanX, minSy, spanY);
                    o = emitProjectedVertex(verts, o, p00, p10, p11, p01, u0, v1, cam, fx, fy, fz, rx, rz, ux, uy, uz, tanV, aspect, sw, sh, minSx, spanX, minSy, spanY);
                }
            }
        }

        double seed = ((pos.getX() * 928371 + pos.getY() * 123457 + pos.getZ() * 719387) % 1000) / 100.0;
        net.minecraft.util.Identifier tex = com.lume.client.fx.BlockFillTexture.get(b.fill.index, seed, 255);
        int tintArgb = (alpha << 24) | 0xFFFFFF;   // opacity lives in the tint's alpha; colour lives in the texture
        Render3D.fillTexturedQuadsThroughWalls(ms.peek(), verts, tex, tintArgb, false);
    }

    /** One grid vertex for the subdivided fill mesh: bilinearly interpolates the WORLD position
     *  across a (planar, so this is exact) face quad, then re-projects that exact world position
     *  through the real camera transform to get its UV — not an interpolation of 4 corner UVs. */
    private static int emitProjectedVertex(float[] out, int o, double[] p00, double[] p10, double[] p11, double[] p01,
                                            double u, double v, Vec3d cam, double fx, double fy, double fz,
                                            double rx, double rz, double ux, double uy, double uz,
                                            double tanV, double aspect, int sw, int sh,
                                            double minSx, double spanX, double minSy, double spanY) {
        double topX = p00[0] + (p10[0] - p00[0]) * u, botX = p01[0] + (p11[0] - p01[0]) * u;
        double topY = p00[1] + (p10[1] - p00[1]) * u, botY = p01[1] + (p11[1] - p01[1]) * u;
        double topZ = p00[2] + (p10[2] - p00[2]) * u, botZ = p01[2] + (p11[2] - p01[2]) * u;
        double wx = topX + (botX - topX) * v, wy = topY + (botY - topY) * v, wz = topZ + (botZ - topZ) * v;
        double dx = wx - cam.x, dy = wy - cam.y, dz = wz - cam.z;
        double depth = Math.max(0.05, dx * fx + dy * fy + dz * fz);
        double rc = dx * rx + dz * rz, uc = dx * ux + dy * uy + dz * uz;
        double sx = (0.5 + 0.5 * (rc / depth) / (aspect * tanV)) * sw;
        double sy = (0.5 - 0.5 * (uc / depth) / tanV) * sh;
        out[o++] = (float) dx; out[o++] = (float) dy; out[o++] = (float) dz;
        out[o++] = (float) ((sx - minSx) / spanX);
        out[o++] = (float) ((sy - minSy) / spanY);
        return o;
    }
}

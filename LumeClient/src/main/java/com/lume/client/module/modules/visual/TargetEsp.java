package com.lume.client.module.modules.visual;

import com.lume.client.LumeClient;
import com.lume.client.fx.ParticleEngine;
import com.lume.client.gui.Theme;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.util.Render3D;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Target ESP — marks the entity your crosshair is aimed at (legit: crosshair
 * target only, never wall/proximity scan). "Outline" uses MC's native entity
 * outline framebuffer (see MinecraftClientMixin + EntityMixin); Circle is a
 * thin ring line (see {@link Render3D#thickRingXZ} — same technique as
 * {@code JumpFx}'s landing ring) and Square a rounded, gapped glowing outline
 * — both real line/stroke geometry, no particle engine involved at all.
 * Halo/Sparks are true particle effects via {@link ParticleEngine}; Limbs uses
 * {@link ParticleEngine}'s glow rendering but its trail is a hand-rolled
 * ring-buffer of relative offsets (see the Limbs case below), not physics
 * particles either — so it can never drift or "fly off". The "HUD" panel (name/HP/armor, top of
 * screen) absorbs what used to be the separate "Target HUD" module — one
 * toggle governs everything about the crosshair target.
 */
public class TargetEsp extends Module {

    public final ModeSetting  filter    = add(new ModeSetting("Filter", 0, "Players", "Hostiles", "All"));
    public final ModeSetting  style     = add(new ModeSetting("Style", 0, "Outline", "Halo", "Circle", "Square", "Sparks", "Limbs"));
    public final ModeSetting  shape     = add(new ModeSetting("Shape", 0, ParticleEngine.SHAPE_NAMES));
    public final ColorSetting color     = add(new ColorSetting("Color", false, 183, 170, 217));
    public final SliderSetting thickness = add(new SliderSetting("Thickness", 2.0, 1.0, 5.0, false));
    public final SliderSetting size      = add(new SliderSetting("Size", 1.0, 0.3, 1.0, false));

    // Hit Color — same idea as the standalone "HitColor" module (recolours the model on the
    // vanilla hurt flash), but recolours the ESP elements themselves instead.
    public final BoolSetting  hitColor      = add(new BoolSetting("Hit Color", false));
    public final ColorSetting hitColorColor = add(new ColorSetting("Hit Color Color", false, 224, 86, 86));

    // HUD — absorbs the old standalone "Target HUD" module: name/HP/armor panel,
    // top-centre of the screen, for whatever passes the Filter above.
    public final BoolSetting  hud           = add(new BoolSetting("HUD", true));
    public final BoolSetting  hudHead       = add(new BoolSetting("3D Head", true));
    public final BoolSetting  hudHealthBar  = add(new BoolSetting("Health Bar", true));
    public final BoolSetting  hudArmor      = add(new BoolSetting("Armor", true));
    public final BoolSetting  hudAnimate    = add(new BoolSetting("Animate HP", true));

    // Show HP — independent of the HUD panel above: a small HP readout floating
    // right over whatever entity you're hitting/aiming at (screen-space NanoVG
    // pin, projected from the target's world position — see
    // HudRenderer#renderTargetHpPin). Also hides the vanilla "below name"
    // scoreboard HP some servers (e.g. FunTime) push, so only ours shows — see
    // ClientPlayNetworkHandlerMixin#lume$suppressBelowNameHp.
    public final BoolSetting  hpAboveTarget = add(new BoolSetting("Show HP", false));
    public final ModeSetting  hpStyle       = add(new ModeSetting("HP Style", 0, "Hearts", "Bar"));
    public final BoolSetting  hpText        = add(new BoolSetting("HP Text", true));

    public TargetEsp() {
        super("Target ESP", "Marks the entity your crosshair is on", Category.VISUALS, -1);
    }

    /** Shared Filter check (Players/Hostiles/All) — used by both the world ESP styles and the HUD panel. */
    public static boolean passesFilter(TargetEsp esp, LivingEntity le) {
        boolean isPlayer  = le instanceof PlayerEntity;
        boolean isHostile = le instanceof MobEntity mob && mob.getTarget() != null;
        int f = esp.filter.index;
        if (f == 0) return isPlayer;
        if (f == 1) return isHostile;
        return true;
    }

    // Grace-hold + fade — the crosshair target keeps counting as "current" for GRACE_NANOS after
    // you look away, so a brief flick off a PvP target in combat doesn't blink the ESP off and
    // re-fade it back in every time. Re-aiming at the SAME entity within that window just
    // continues (graceId never changed, so the fade-in math below sees "already long past
    // acquisition" and stays at full alpha — no re-appear). World markers only (Circle/Square/
    // Halo/Sparks/Limbs) — fade doesn't apply to Outline (native MC highlight, see isOutlineTarget),
    // only the grace-hold gating does.
    private static final long GRACE_NANOS = 1_000_000_000L;
    private static final float FADE_SEC = 0.22f;
    private static int graceId = -1;
    private static LivingEntity graceEntity = null;
    private static long graceAcquiredNanos = 0;   // when this entity FIRST became the direct target (drives fade-in)
    private static long graceLastSeenNanos = 0;   // last time it was the DIRECT target (drives fade-out once lost)
    private static boolean graceIsDirect = true;

    /** Resolves this frame's effective crosshair target (direct or grace-held) and updates the
     *  shared tracking state above. Returns null if nothing applies, even through the grace window. */
    private static LivingEntity resolveTarget(TargetEsp esp, MinecraftClient mc) {
        Entity raw = mc.targetedEntity;
        if (raw == null && mc.crosshairTarget instanceof EntityHitResult ehr) raw = ehr.getEntity();
        LivingEntity direct = (raw instanceof LivingEntity dle && dle.isAlive() && dle != mc.player && passesFilter(esp, dle)) ? dle : null;

        long now = System.nanoTime();
        if (direct != null) {
            if (direct.getId() != graceId) { graceId = direct.getId(); graceAcquiredNanos = now; }
            graceEntity = direct;
            graceLastSeenNanos = now;
            graceIsDirect = true;
            return direct;
        }
        if (graceId != -1 && graceEntity != null && graceEntity.isAlive() && !graceEntity.isRemoved()
                && (now - graceLastSeenNanos) < GRACE_NANOS) {
            graceIsDirect = false;
            return graceEntity;
        }
        graceId = -1;
        graceEntity = null;
        return null;
    }

    /** 0..1 fade for the CURRENTLY resolved target (see {@link #resolveTarget}) — ramps up over
     *  FADE_SEC once freshly acquired, and (symmetrically) ramps back down over the last FADE_SEC
     *  of the grace window once direct aim is lost, reaching 0 right as the grace period expires. */
    private static float currentFade() {
        long now = System.nanoTime();
        float t;
        if (graceIsDirect) {
            t = Math.min(1f, (now - graceAcquiredNanos) / 1e9f / FADE_SEC);
        } else {
            float remainingSec = (GRACE_NANOS - (now - graceLastSeenNanos)) / 1e9f;
            t = Math.max(0f, Math.min(1f, remainingSec / FADE_SEC));
        }
        return t * t * (3f - 2f * t);   // smoothstep ease
    }

    /** WorldRenderEvents.AFTER_ENTITIES callback (registered in LumeClient). */
    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Target ESP");
        if (!(m instanceof TargetEsp esp) || !esp.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (context.camera() == null) return;

        LivingEntity le = resolveTarget(esp, mc);
        if (le == null) return;

        // interpolate to the render pose so the effect tracks smoothly
        float td = context.tickCounter().getTickDelta(false);
        double ix = MathHelper.lerp(td, le.lastRenderX, le.getX());
        double iy = MathHelper.lerp(td, le.lastRenderY, le.getY());
        double iz = MathHelper.lerp(td, le.lastRenderZ, le.getZ());
        Box hb = le.getBoundingBox().offset(ix - le.getX(), iy - le.getY(), iz - le.getZ());

        // Show HP now renders in screen space (NanoVG) — see HudRenderer#renderTargetHpPin.

        // Outline style — native entity outline framebuffer. Nothing else to draw here.
        if (esp.style.index == 0) return;

        double s = esp.size.value;
        double cx = (hb.minX + hb.maxX) / 2, cz = (hb.minZ + hb.maxZ) / 2;
        float w = (float) (Math.max(hb.maxX - hb.minX, hb.maxZ - hb.minZ) * s);
        int rgb = (esp.hitColor.value && le.hurtTime > 0)
                ? (esp.hitColorColor.accent ? Theme.accentRgb() : esp.hitColorColor.rgb())
                : (esp.color.accent ? Theme.accentRgb() : esp.color.rgb());
        float fade = currentFade();

        switch (esp.style.index) {
            case 1 -> ParticleEngine.halo(context, cx, hb.minY, hb.maxY, cz, w * 0.65f, rgb, esp.shape.index, fade);
            case 2 -> {
                // A flat ring that rides the target's full body height, feet to head and back —
                // sin's rate of change (its derivative, cosine) peaks at the midpoint and hits zero
                // at the top/bottom extremes, so the ring speeds up passing through the MIDDLE of
                // the player and eases to a stop at the feet/head before reversing. See drawCircle
                // for how that same closed-form sine drives the trail (no particles).
                double t = System.currentTimeMillis() / 1000.0;
                drawCircle(context, cx, cz, hb.minY, hb.maxY - hb.minY, w * 0.6, rgb, t, esp.thickness.value, fade);
            }
            case 3 -> drawSpinSquare(context, le, cx, (hb.minY + hb.maxY) / 2, cz, w * 0.55, rgb, fade);
            case 4 -> ParticleEngine.sparksAround(cx, hb.minY, hb.maxY, cz, w * 0.55f, rgb, esp.shape.index, fade);
            case 5 -> {   // Limbs — a small swarm of glowing orbs freely orbiting the whole entity (not
                          // attached to any body part): each orbiter has its own radius/height-band/speed/
                          // spin direction and bobs up and down, so they never bunch together in one spot.
                          // Trail = a small ring-buffer of each orbiter's past RELATIVE-to-entity offsets
                          // (see limbTrail* below) — rendered every frame as anchor.currentPos + storedOffset,
                          // never integrated from a velocity, so there is nothing that can drift: whether
                          // the entity stands still or walks, every trail sample just keeps riding at the
                          // exact spot it was captured relative to the entity, and the trail's shape comes
                          // purely from the orbiters' own circling motion.
                double midY = (hb.minY + hb.maxY) / 2, bh = hb.maxY - hb.minY;
                double t = System.currentTimeMillis() / 1000.0;
                int seed = le.getId();
                int n = 3;
                if (le.getId() != limbTrailOwnerId) {
                    limbTrailOwnerId = le.getId();
                    for (int k = 0; k < n; k++) limbTrailCount[k] = 0;
                }
                for (int i = 0; i < n; i++) {
                    double dir = (i % 2 == 0) ? 1 : -1;                                  // alternating spin direction
                    double speed = (0.55 + 0.4 * (((seed * 31 + i * 17) % 7) / 6.0)) * 1.3;
                    double phase = ((seed * 13 + i * 97) % 360) * Math.PI / 180.0;
                    double radius = w * (0.5 + 0.3 * (((seed + i * 53) % 5) / 4.0));
                    double bobAmp = bh * (0.22 + 0.16 * (((seed + i * 29) % 4) / 3.0));
                    double bobSpeed = 0.45 + 0.35 * (((seed + i * 71) % 5) / 4.0);
                    double a = t * speed * dir + phase;
                    double oy = midY + Math.sin(t * bobSpeed + phase) * bobAmp;
                    double ox = cx + Math.cos(a) * radius, oz = cz + Math.sin(a) * radius;
                    ParticleEngine.limbGlow(context, ox, oy, oz, w * 0.10f, rgb, fade);

                    double offX = ox - le.getX(), offY = oy - le.getY(), offZ = oz - le.getZ();
                    int head = limbTrailHead[i];
                    limbTrailOff[i][head][0] = offX; limbTrailOff[i][head][1] = offY; limbTrailOff[i][head][2] = offZ;
                    limbTrailHead[i] = (head + 1) % TRAIL_LEN;
                    if (limbTrailCount[i] < TRAIL_LEN) limbTrailCount[i]++;

                    int cnt = limbTrailCount[i];
                    for (int k = 1; k < cnt; k++) {
                        int idx = ((limbTrailHead[i] - 1 - k) % TRAIL_LEN + TRAIL_LEN) % TRAIL_LEN;
                        float frac = k / (float) cnt;                       // 0 = most recent, ~1 = oldest
                        float rTrail = w * 0.10f * (1f - frac * 0.6f);
                        float alphaMul = (1f - frac) * fade;
                        ParticleEngine.limbTrailGlow(context, le, limbTrailOff[i][idx][0], limbTrailOff[i][idx][1], limbTrailOff[i][idx][2], rTrail, rgb, alphaMul);
                    }
                }
            }
        }
    }

    // Limbs trail state — per-orbiter ring buffer of relative (entity-local) offsets, see the
    // Limbs case above. 3 orbiters max, TRAIL_LEN samples each; reset when the target changes.
    private static final int TRAIL_LEN = 16;
    private static final double[][][] limbTrailOff = new double[3][TRAIL_LEN][3];
    private static final int[] limbTrailHead = new int[3];
    private static final int[] limbTrailCount = new int[3];
    private static int limbTrailOwnerId = -1;

    /** True while our own "Show HP" is active — used by ClientPlayNetworkHandlerMixin to hide the server's own belowName HP. */
    public static boolean shouldHideServerHp() {
        Module m = LumeClient.MODULES.getByName("Target ESP");
        return m instanceof TargetEsp esp && esp.isEnabled() && esp.hpAboveTarget.value;
    }

    /** Circle — a thin ring LINE (real geometry — an annulus of quads, {@link Render3D#thickRingXZ},
     *  same technique {@code JumpFx} uses for its landing ring; no particle engine involved at
     *  all) that rides up/down the target's body. Thickness is genuine, controllable geometry —
     *  base scales with the Thickness setting, ×1.2 baked into the constant below. The "leaves an
     *  acceleration trail" ask is done with the SAME ring shape, not separate particles: since the
     *  ring's Y position is a plain closed-form sine, a few earlier instants can be re-evaluated
     *  directly (no history buffer needed) and drawn as fainter, thinner echo rings — a pure-
     *  geometry streak that's longest crossing the middle of the player (fastest point) and
     *  vanishes near the feet/head (where the ring is momentarily still). */
    private static void drawCircle(WorldRenderContext ctx, double cx, double cz, double minY, double bh,
                                   double radius, int rgb, double t, double thicknessSetting, float fade) {
        if (fade <= 0.01f) return;
        VertexConsumerProvider vcp = ctx.consumers();
        MatrixStack ms = ctx.matrixStack();
        if (vcp == null || ms == null) return;
        Vec3d cam = ctx.camera().getPos();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        int rgbm = rgb & 0xFFFFFF;
        float fx = (float) (cx - cam.x), fz = (float) (cz - cam.z);
        float halfThick = (float) (0.009 * thicknessSetting) * 1.2f;

        int echoes = 7;
        for (int k = echoes; k >= 1; k--) {
            double et = t - k * 0.014;
            double eY = minY + bh * (0.5 + 0.5 * Math.sin(et * 1.1));
            float frac = 1f - k / (float) (echoes + 1);
            float ey = (float) (eY - cam.y + 0.02);
            int alpha = (int) (frac * frac * 190 * fade);
            float thick = Math.max(0.003f, halfThick * frac);
            Render3D.thickRingXZ(vc, ms.peek(), fx, ey, fz, (float) radius, thick, (alpha << 24) | rgbm, 40);
        }
        double y = minY + bh * (0.5 + 0.5 * Math.sin(t * 1.1));
        float fy = (float) (y - cam.y + 0.02);
        Render3D.thickRingXZ(vc, ms.peek(), fx, fy, fz, (float) radius, halfThick, ((int) (255 * fade) << 24) | rgbm, 40);
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getDebugQuads());
    }

    // Square style state — one spinning marker at a time (single target), so a
    // few static fields are enough: base slow spin, kicks faster on every hit,
    // eases back down to the baseline speed.
    private static int spinTargetId = -1;
    private static float spinAngle = 0f;
    private static float spinVel = 60f;
    private static long spinLastNanos = System.nanoTime();
    private static final float SPIN_BASE = 60f;     // deg/sec at rest
    private static final float SPIN_KICK = 420f;    // deg/sec added per hit

    /** Called from HitEffects.onAttack — speeds the Square marker's spin up on a hit. */
    public static void onHit(int entityId) {
        if (entityId == spinTargetId) spinVel += SPIN_KICK;
    }

    /** Square — a camera-facing, ROUNDED outline that spins in place, speeding up on every hit:
     *  4 rounded-corner "brackets" (each an unbroken, properly mitered glowing stroke — see
     *  {@link #strokeOpenPolyline}, so there are no gaps/seams within a bracket), with a gap sized
     *  1/4 of each straight side left open at that side's midpoint (a common target-lock reticle
     *  look) rather than either a single filled block or a fully closed square.
     *
     *  <p>Drawn depth-test-off (same proven "through walls" technique as
     *  {@link Render3D#boxThroughWalls}/{@code fillBoxThroughWalls} — raw {@code BufferBuilder} +
     *  {@code drawWithGlobalProgram}, NOT {@code vcp.getBuffer(...)}/{@code imm.draw(...)}, since
     *  that path re-applies the layer's own depth-test phase on draw and silently undoes a plain
     *  {@code disableDepthTest()}) so the marker always shows fully — the target's own body model,
     *  walls, or anything else in front of it can never hide it. */
    private static void drawSpinSquare(WorldRenderContext ctx, LivingEntity le, double cx, double cy, double cz, double half, int rgb, float fade) {
        if (fade <= 0.01f) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null || ctx.camera() == null) return;

        long now = System.nanoTime();
        float dt = (float) Math.min(0.1, (now - spinLastNanos) / 1e9);
        spinLastNanos = now;
        if (le.getId() != spinTargetId) { spinTargetId = le.getId(); spinVel = SPIN_BASE; spinAngle = 0f; }
        spinAngle += spinVel * dt;
        spinVel += (SPIN_BASE - spinVel) * Math.min(1f, dt * 2.2f);   // ease back down to the resting speed

        Vec3d cam = ctx.camera().getPos();
        Quaternionf q = ctx.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);

        // rotate the (right, up) billboard basis itself by spinAngle — the whole loop spins in the view plane
        double rad = Math.toRadians(spinAngle);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        Vector3f r2 = new Vector3f(right).mul(cos).add(new Vector3f(up).mul(sin));
        Vector3f u2 = new Vector3f(up).mul(cos).sub(new Vector3f(right).mul(sin));

        float ccx = (float) (cx - cam.x), ccy = (float) (cy - cam.y), ccz = (float) (cz - cam.z);
        int rgbm = rgb & 0xFFFFFF;

        int segPerCorner = 7;
        float h = (float) half, cr = h * 0.4f, strokeW = h * 0.09f;
        float[][] loc = roundedSquareOutline(h, cr, segPerCorner);   // 4*(segPerCorner+1) points, corner-major order
        int per = segPerCorner + 1;

        net.minecraft.client.gl.ShaderProgram prevShader = RenderSystem.getShader();
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        Matrix4f mat = ms.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int c = 0; c < 4; c++) {
            float[] cornerLast = loc[c * per + segPerCorner];
            float[] nextFirst = loc[((c + 1) % 4) * per];
            float[] gapStart = lerp(cornerLast, nextFirst, 0.375f);       // start of the gap on the edge AFTER this corner

            float[] prevLast = loc[((c + 3) % 4) * per + segPerCorner];
            float[] curFirst = loc[c * per];
            float[] gapEnd = lerp(prevLast, curFirst, 0.625f);            // end of the gap on the edge BEFORE this corner

            float[][] piece = new float[per + 2][];
            piece[0] = gapEnd;
            for (int s = 0; s <= segPerCorner; s++) piece[s + 1] = loc[c * per + s];
            piece[per + 1] = gapStart;
            strokeOpenPolyline(buf, mat, ccx, ccy, ccz, r2, u2, piece, strokeW, rgbm, fade);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        if (prevCull) RenderSystem.enableCull();
        RenderSystem.setShader(prevShader);
    }

    /** Perimeter points (billboard-local 2D, centred on origin) of a rounded square: four
     *  quarter-circle corners connected by straight sides, {@code segPerCorner} segments each,
     *  laid out corner-major ({@code (segPerCorner+1)} points per corner, in order). */
    private static float[][] roundedSquareOutline(float half, float cornerR, int segPerCorner) {
        float ix = half - cornerR;
        float[][] centers = { { ix, ix }, { -ix, ix }, { -ix, -ix }, { ix, -ix } };
        float[] startDeg = { 0, 90, 180, 270 };
        float[][] pts = new float[4 * (segPerCorner + 1)][];
        int p = 0;
        for (int c = 0; c < 4; c++) {
            for (int s = 0; s <= segPerCorner; s++) {
                double a = Math.toRadians(startDeg[c] + 90.0 * s / segPerCorner);
                pts[p++] = new float[]{ centers[c][0] + cornerR * (float) Math.cos(a), centers[c][1] + cornerR * (float) Math.sin(a) };
            }
        }
        return pts;
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{ a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t };
    }

    /** Strokes an OPEN polyline (billboard-local 2D points) as one continuous glowing band —
     *  soft halo + bright core. Consecutive quads share their joint vertices exactly (each
     *  interior point's offset uses the AVERAGED normal of its incoming and outgoing edges, a
     *  standard mitered-stroke technique), so there are no gaps/seams at the joints — unlike
     *  offsetting each segment independently, which leaves a visible notch at every vertex where
     *  the direction changes (that was the earlier "torn apart" look). */
    private static void strokeOpenPolyline(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                           Vector3f r2, Vector3f u2, float[][] pts, float coreW, int rgb, float fade) {
        int n = pts.length;
        if (n < 2) return;
        float[][] nrm = new float[n][2];
        for (int i = 0; i < n; i++) {
            float dx, dy;
            if (i == 0) { dx = pts[1][0] - pts[0][0]; dy = pts[1][1] - pts[0][1]; }
            else if (i == n - 1) { dx = pts[n - 1][0] - pts[n - 2][0]; dy = pts[n - 1][1] - pts[n - 2][1]; }
            else {
                float d1x = pts[i][0] - pts[i - 1][0], d1y = pts[i][1] - pts[i - 1][1];
                float l1 = (float) Math.sqrt(d1x * d1x + d1y * d1y); if (l1 > 1e-6f) { d1x /= l1; d1y /= l1; }
                float d2x = pts[i + 1][0] - pts[i][0], d2y = pts[i + 1][1] - pts[i][1];
                float l2 = (float) Math.sqrt(d2x * d2x + d2y * d2y); if (l2 > 1e-6f) { d2x /= l2; d2y /= l2; }
                dx = d1x + d2x; dy = d1y + d2y;
            }
            float l = (float) Math.sqrt(dx * dx + dy * dy); if (l < 1e-6f) l = 1f;
            nrm[i][0] = -dy / l; nrm[i][1] = dx / l;
        }
        strokePass(vc, m, cx, cy, cz, r2, u2, pts, nrm, coreW * 2.2f, rgb, (int) (70 * fade));
        strokePass(vc, m, cx, cy, cz, r2, u2, pts, nrm, coreW, rgb, (int) (235 * fade));
    }

    private static void strokePass(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz,
                                   Vector3f r2, Vector3f u2, float[][] pts, float[][] nrm, float w, int rgb, int alpha) {
        int argb = (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
        for (int i = 0; i < pts.length - 1; i++) {
            float a1x = pts[i][0] + nrm[i][0] * w, a1y = pts[i][1] + nrm[i][1] * w;
            float a2x = pts[i][0] - nrm[i][0] * w, a2y = pts[i][1] - nrm[i][1] * w;
            float b1x = pts[i + 1][0] + nrm[i + 1][0] * w, b1y = pts[i + 1][1] + nrm[i + 1][1] * w;
            float b2x = pts[i + 1][0] - nrm[i + 1][0] * w, b2y = pts[i + 1][1] - nrm[i + 1][1] * w;
            vc.vertex(m, cx + r2.x * a1x + u2.x * a1y, cy + r2.y * a1x + u2.y * a1y, cz + r2.z * a1x + u2.z * a1y).color(argb);
            vc.vertex(m, cx + r2.x * a2x + u2.x * a2y, cy + r2.y * a2x + u2.y * a2y, cz + r2.z * a2x + u2.z * a2y).color(argb);
            vc.vertex(m, cx + r2.x * b2x + u2.x * b2y, cy + r2.y * b2x + u2.y * b2y, cz + r2.z * b2x + u2.z * b2y).color(argb);
            vc.vertex(m, cx + r2.x * b1x + u2.x * b1y, cy + r2.y * b1x + u2.y * b1y, cz + r2.z * b1x + u2.z * b1y).color(argb);
        }
    }

    // Outline's own grace-hold tracking — kept separate from the world-marker grace state above
    // (resolveTarget/graceEntity), since isOutlineTarget is called once per candidate entity
    // during vanilla's entity render pass, which happens BEFORE this frame's
    // WorldRenderEvents.AFTER_ENTITIES (renderWorld) — depending on that state here would read
    // a frame stale by one. Same grace-hold behaviour, no fade (a native highlight can't easily
    // fade — see class doc), fully self-contained.
    private static int outlineGraceId = -1;
    private static LivingEntity outlineGraceEntity = null;
    private static long outlineGraceLastSeenNanos = 0;

    /** True if {@code e} is the crosshair target — or was, within the last second (grace-hold,
     *  same as the world markers) — and the Outline style is active (for the outline mixins). */
    public static boolean isOutlineTarget(Entity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Module m = LumeClient.MODULES.getByName("Target ESP");
        if (!(m instanceof TargetEsp esp) || !esp.isEnabled() || esp.style.index != 0 || mc.player == null) return false;

        Entity raw = mc.targetedEntity;
        if (raw == null && mc.crosshairTarget instanceof EntityHitResult ehr) raw = ehr.getEntity();
        LivingEntity direct = (raw instanceof LivingEntity dle && dle.isAlive() && dle != mc.player && passesFilter(esp, dle)) ? dle : null;

        long now = System.nanoTime();
        if (direct != null) {
            outlineGraceId = direct.getId();
            outlineGraceEntity = direct;
            outlineGraceLastSeenNanos = now;
        } else if (outlineGraceEntity == null || !outlineGraceEntity.isAlive() || outlineGraceEntity.isRemoved()
                || (now - outlineGraceLastSeenNanos) >= GRACE_NANOS) {
            outlineGraceId = -1;
            outlineGraceEntity = null;
        }
        return outlineGraceEntity != null && e.getId() == outlineGraceId;
    }

    /** Outline colour as 0xRRGGBB for the outline mixins — swaps to Hit Color Color while the
     *  currently outlined entity is in its vanilla hurt-flash window, if Hit Color is on. */
    public static int outlineColorRgb() {
        Module m = LumeClient.MODULES.getByName("Target ESP");
        if (!(m instanceof TargetEsp esp)) return 0xFFFFFF;
        if (esp.hitColor.value && outlineGraceEntity != null && outlineGraceEntity.hurtTime > 0) {
            return esp.hitColorColor.accent ? Theme.accentRgb() : esp.hitColorColor.rgb();
        }
        return esp.color.accent ? Theme.accentRgb() : esp.color.rgb();
    }
}

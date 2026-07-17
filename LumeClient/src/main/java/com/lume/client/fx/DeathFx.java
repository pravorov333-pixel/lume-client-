package com.lume.client.fx;

import com.lume.client.LumeClient;
import com.lume.client.module.Module;
import com.lume.client.module.modules.render.DeathAnimations;
import com.lume.client.nanovg.NanoVgRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The actual custom visuals for Death Animations, once vanilla's own death
 * tilt/render has been cancelled (see LivingEntityRendererMixin). One style
 * picker now covers everything (Off/Soul/Anvil/Thunderstrike/Beam) — the
 * old separate "Particles" picker (Bubble Pop/Fog) is gone; Thunderstrike
 * moved in here as just another style, same as the rest.
 *
 * <p>Render states don't carry the owning Entity (no id field in 1.21.4's
 * render-state split) — {@link #trackState} is called from a TAIL hook on
 * {@code LivingEntityRenderer.updateRenderState} (which DOES get both) to
 * remember the mapping, so the death-time HEAD hook can look the real entity
 * back up by its (identity-stable, reused-per-frame) render state object.
 *
 * <p><b>Soul rewritten (2026-07-10):</b> the previous version re-invoked
 * {@code EntityRenderDispatcher.render} to draw an actual floating copy of
 * the dying entity's model — fragile (recurses back through the very mixin
 * that cancels vanilla's death render) and reported broken. Replaced with
 * the same soft-quad billboard technique the rest of this file already uses
 * reliably (Anvil's particle burst, the old Fog style, Beam below) — a pale
 * glowing column that drifts upward and fades, no entity re-render involved.
 */
public final class DeathFx {
    private DeathFx() {}

    private static final Map<LivingEntityRenderState, LivingEntity> STATE_TO_ENTITY = new IdentityHashMap<>();
    private static final Set<LivingEntityRenderState> REGISTERED = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    public static void trackState(LivingEntity entity, LivingEntityRenderState state) {
        STATE_TO_ENTITY.put(state, entity);
    }

    private static final class Dying {
        final LivingEntityRenderState state;
        final double x, y, z;
        final float height;
        final long start;
        final int style;
        boolean impactDone;
        boolean thunderSpawned;
        Dying(LivingEntityRenderState state, double x, double y, double z, float height, int style) {
            this.state = state; this.x = x; this.y = y; this.z = z; this.height = height;
            this.style = style;
            this.start = System.currentTimeMillis();
        }
    }

    private static final List<Dying> LIST = new ArrayList<>();

    /** Called once (per entity) from the death-time HEAD hook — no-op if already registered this death. */
    public static void onDeathFrame(LivingEntityRenderState state, int style) {
        if (REGISTERED.contains(state)) return;
        REGISTERED.add(state);
        LivingEntity e = STATE_TO_ENTITY.get(state);
        if (e == null) return;
        LIST.add(new Dying(state, state.x, state.y, state.z, state.height, style));
    }

    private static boolean soundOn() {
        Module m = LumeClient.MODULES.getByName("Death Animations");
        return !(m instanceof DeathAnimations da) || da.sound.value;
    }

    public static void render(WorldRenderContext ctx) {
        if (LIST.isEmpty() || ctx.camera() == null) return;
        long now = System.currentTimeMillis();
        Iterator<Dying> it = LIST.iterator();
        while (it.hasNext()) {
            Dying d = it.next();
            float t = (now - d.start) / 1000f;
            if (t > 1.8f) { it.remove(); STATE_TO_ENTITY.remove(d.state); REGISTERED.remove(d.state); continue; }
            float tc = Math.min(1f, t);

            switch (d.style) {
                case 1 -> renderSoul(ctx, d, tc);
                case 2 -> renderAnvil(ctx, d, tc);
                case 3 -> renderThunder(d);
                case 4 -> renderBeam(ctx, d, t);
                default -> { }
            }
        }
    }

    // --- Soul — a translucent T-pose Steve model that drifts 2 blocks up out of the player,
    // then evaporates. Built from vanilla's OWN player model class directly (LoadedEntityModels
    // + PlayerEntityModel), never touching EntityRenderDispatcher/the entity's own renderer —
    // the PREVIOUS attempt at "an actual copy of the entity" re-invoked that dispatcher and
    // recursed straight back through the very mixin that cancels the vanilla death render.
    // This is a wholly separate, generic model instance, so that recursion can't happen here.
    private static PlayerEntityModel steveModel;

    private static PlayerEntityModel steveModel() {
        if (steveModel == null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            var models = mc.getBakedModelManager().getEntityModelsSupplier().get();
            var root = models.getModelPart(EntityModelLayers.PLAYER);
            steveModel = new PlayerEntityModel(root, false);
            steveModel.setVisible(true);
            // Static T-pose — arms hang straight down by default; rolling each 90° (opposite
            // signs so they mirror) swings them out level with the shoulders instead.
            steveModel.rightArm.roll = (float) (Math.PI / 2);
            steveModel.leftArm.roll = (float) (-Math.PI / 2);
        }
        return steveModel;
    }

    private static void renderSoul(WorldRenderContext ctx, Dying d, float t) {
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null || ctx.camera() == null) return;
        Vec3d cam = ctx.camera().getPos();

        // Continuous ascent for the ENTIRE lifetime — no plateau, no separate "then fade" stage.
        // Alpha eases down smoothly across the same window the caller removes this Dying at
        // (1.8s, see render()'s `t > 1.8f` cutoff), so it reaches ~0 exactly as it's culled —
        // it never visibly stops rising and never sits static before vanishing.
        float speed = (2.0f / 1.5f) * 1.3f;   // blocks/sec
        float yOff = t * speed;
        float lifetime = 1.8f;
        float alpha = 0.4f * Math.max(0f, 1f - t / lifetime);
        if (alpha <= 0.01f) return;
        int argb = (Math.round(alpha * 255) << 24) | 0xFFFFFF;

        PlayerEntityModel model = steveModel();
        ms.push();
        ms.translate(d.x - cam.x, d.y + yOff - cam.y, d.z - cam.z);
        ms.scale(-1.0f, -1.0f, 1.0f);   // vanilla's own player-model convention: mirrored X, flipped Y
        ms.translate(0.0, -1.501, 0.0); // model origin sits at the hip; shift down so feet line up with the translate point
        RenderLayer layer = RenderLayer.getEntityTranslucent(DefaultSkinHelper.getTexture());
        VertexConsumer vc = vcp.getBuffer(layer);
        model.render(ms, vc, 0xF000F0, OverlayTexture.DEFAULT_UV, argb);
        ms.pop();
        if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(layer);
    }

    // --- Anvil — real vanilla anvil block falls all the way to the ground, then rests there briefly ---
    private static void renderAnvil(WorldRenderContext ctx, Dying d, float t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        MatrixStack ms = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();
        float fallDur = 0.5f;
        float ft = Math.min(1f, t / fallDur);
        double dropFrom = d.y + d.height + 4.0;
        double targetY = d.y;   // lands ON the ground, not stopped mid-air at head height
        double curY = dropFrom - (dropFrom - targetY) * (ft * ft); // ease-in, gravity-like

        if (t >= fallDur && !d.impactDone) {
            d.impactDone = true;
            if (soundOn()) mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ANVIL_LAND, 1.0f, 0.9f));
            ParticleEngine.burst(d.x, targetY + 0.1, d.z, 0xAAAAAA, 16, 1.0f, 0.2f, 0.35f);
        }
        if (t > 1.4f) return; // rests on the ground, then fades out with the rest of the death effects

        float scale = 0.75f; // real block model read as oversized floating alone in mid-air with nothing for scale reference
        ms.push();
        ms.translate(d.x - cam.x - 0.5, curY - cam.y, d.z - cam.z - 0.5);
        ms.translate(0.5, 0.5, 0.5);
        ms.scale(scale, scale, scale);
        ms.translate(-0.5, -0.5, -0.5);
        mc.getBlockRenderManager().renderBlockAsEntity(Blocks.ANVIL.getDefaultState(), ms, ctx.consumers(), 0xF000F0, OverlayTexture.DEFAULT_UV);
        ms.pop();
    }

    // --- Thunderstrike — a REAL vanilla lightning bolt (cosmetic: no damage, no fire) ---
    private static void renderThunder(Dying d) {
        if (d.thunderSpawned) return;
        d.thunderSpawned = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.world instanceof ClientWorld cw)) return;
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(cw, SpawnReason.EVENT);
        if (bolt == null) return;
        bolt.setCosmetic(true);   // visual only — no damage, no block ignition
        if (!soundOn()) bolt.setSilent(true);
        bolt.setPosition(d.x, d.y, d.z);
        cw.addEntity(bolt);
    }

    // --- Beam — a sudden white lightning-bolt strike drawn via NanoVG (screen-space, not a
    // world-space quad): projects the death location to screen coordinates using the same
    // manual camera-basis technique HudRenderer's waypoints already use, then strokes a jagged
    // bolt from off-screen straight down onto it. Strokes, not fills — a jagged/self-crossing
    // path never needs NanoVG's concave-fill stencil trick this way (see logoMark's history:
    // that trick is exactly what crashed the Intel driver earlier this session).
    private static void renderBeam(WorldRenderContext ctx, Dying d, float t) {
        if (t > 0.6f) return;   // sharp, short strike — not a multi-second glow
        Camera cam = ctx.camera();
        if (cam == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        Vec3d cp = cam.getPos();
        double dx = d.x - cp.x, dy = (d.y + d.height * 0.5) - cp.y, dz = d.z - cp.z;

        double yaw = Math.toRadians(-cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw), cpit = Math.cos(pitch), spit = Math.sin(pitch);
        double fx = syaw * cpit, fy = -spit, fz = cyaw * cpit;
        double rx = -fz, rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-6) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.1) return;   // behind the camera
        double rc = dx * rx + dz * rz, uc = dx * ux + dy * uy + dz * uz;

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double tanV = Math.tan(Math.toRadians((double) (int) mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / sh;
        double scrX = (0.5 + 0.5 * (rc / depth) / (aspect * tanV)) * sw;
        double scrY = (0.5 - 0.5 * (uc / depth) / tanV) * sh;
        if (scrX < -100 || scrX > sw + 100 || scrY < -100 || scrY > sh + 100) return;

        float alpha = t < 0.08f ? 1f : Math.max(0f, 1f - (t - 0.08f) / 0.45f);
        if (alpha <= 0.01f) return;

        int S = (int) Math.max(1, mc.getWindow().getScaleFactor());
        float px = (float) (scrX * S), py = (float) (scrY * S);
        float topY = py - 500f * S;
        int a = Math.round(alpha * 255);
        long seed = System.nanoTime() ^ d.state.hashCode();   // reseeded every frame — reads as crackling, not static

        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;
        NanoVgRenderer.frame(vg -> {
            float[] xs = boltX(px, topY, py, S, seed);
            float[] ys = boltY(topY, py, xs.length);
            NanoVgRenderer.strokePolyline(vg, xs, ys, (0x50 * a / 255) << 24 | 0xFFFFFF, 10f * S);   // soft outer glow
            NanoVgRenderer.strokePolyline(vg, xs, ys, (a << 24) | 0xFFFFFF, 2.5f * S);               // crisp bright core
        });
    }

    private static float[] boltX(float x0, float y0, float y1, int S, long seed) {
        int segs = 7;
        float[] xs = new float[segs + 1];
        java.util.Random rnd = new java.util.Random(seed);
        xs[0] = x0;
        for (int i = 1; i < segs; i++) xs[i] = x0 + (rnd.nextFloat() - 0.5f) * 40f * S;
        xs[segs] = x0;
        return xs;
    }

    private static float[] boltY(float y0, float y1, int count) {
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) ys[i] = y0 + (y1 - y0) * (i / (float) (count - 1));
        return ys;
    }
}

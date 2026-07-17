package com.lume.client.gui;

import com.lume.client.Lang;
import com.lume.client.LumeClient;
import com.lume.client.fthw.CurrentAnarchy;
import com.lume.client.fthw.ItemRule;
import com.lume.client.fthw.ItemRules;
import com.lume.client.fthw.ServerType;
import com.lume.client.fthw.TelegramEventNotifier;
import com.lume.client.fthw.TelegramEvents;
import com.lume.client.nanovg.NanoVgRenderer;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomCrosshair;
import com.lume.client.module.modules.fthw.ServerHelper;
import com.lume.client.module.modules.render.CustomHand;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.module.modules.visual.BlockInfo;
import com.lume.client.module.modules.visual.ShiftIndicator;
import com.lume.client.module.modules.visual.TargetEsp;
import com.lume.client.module.setting.Setting;
import com.lume.client.social.Friends;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.util.ClickTracker;
import com.lume.client.util.SpeedTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * Glass HUD. Text/panels render at NATIVE resolution (crisp); item overlays
 * (armor, inventory, totem) render in GUI space. Each element has its own toggle.
 */
public final class HudRenderer {

    private HudRenderer() {}

    private static boolean on(String name) {
        Module m = LumeClient.MODULES.getByName(name);
        return m != null && m.isEnabled();
    }

    /**
     * Effective scale for an element. While the "HUD Scale" module is ON, EVERY
     * element uses that one slider value (uniform). Otherwise each element uses
     * its own size from the on-screen editor (HudLayout).
     */
    private static float sizeOf(String elementName) {
        Module m = LumeClient.MODULES.getByName("HUD Scale");
        if (m != null && m.isEnabled()) {
            for (Setting s : m.getSettings())
                if (s instanceof SliderSetting ss && ss.name.equals("Scale")) return (float) ss.value;
        }
        return HudLayout.getScale(elementName);
    }

    /**
     * Render an element scaled by {@code es} around its anchor AND shifted by its
     * saved HUD-editor offset. {@code unit} = S for native-space elements, 1 for
     * GUI-space item overlays (offset is stored in GUI px).
     */
    private static void transform(DrawContext ctx, String name, float es, double ax, double ay, int unit, Runnable r) {
        int[] off = HudLayout.get(name);
        boolean moved = off[0] != 0 || off[1] != 0;
        boolean scaledNeeded = Math.abs(es - 1f) >= 0.001f;
        if (!moved && !scaledNeeded) { r.run(); return; }
        var m = ctx.getMatrices();
        m.push();
        m.translate((double) off[0] * unit, (double) off[1] * unit, 0);
        if (scaledNeeded) {
            m.translate(ax, ay, 0);
            m.scale(es, es, 1f);
            m.translate(-ax, -ay, 0);
        }
        r.run();
        m.pop();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int S = (int) Math.max(1, mc.getWindow().getScaleFactor());
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();

        // Keep the Telegram events feed warm (self-throttled) even when the Events
        // screen isn't open — EventLocator/waypoint linking still needs live data.
        TelegramEvents.load();
        // TelegramEventNotifier.tick();  // event toasts disabled for now (user request)

        TargetEsp espMod = (TargetEsp) LumeClient.MODULES.getByName("Target ESP");
        boolean espActive = espMod != null && espMod.isEnabled() && (espMod.hud.value || espMod.hpAboveTarget.value);
        LivingEntity target = espActive ? findTarget(mc, espMod) : null;

        // --- GUI-space item overlays (offset + scaled around their anchor) ---
        if (on("Inventory HUD"))
            transform(ctx, "Inventory HUD", sizeOf("Inventory HUD"), sw / 2.0, sh, 1, () -> renderInventory(ctx, mc, tr));
        if (on("Armor HUD"))
            transform(ctx, "Armor HUD", sizeOf("Armor HUD"), sw / 2.0 + 95, sh, 1, () -> renderArmor(ctx, mc, tr));
        if (on("Totem Counter"))
            transform(ctx, "Totem Counter", sizeOf("Totem Counter"), sw / 2.0 - 132, sh, 1, () -> renderTotem(ctx, mc, tr));
        renderPvpHighlight(ctx, mc);

        // --- Native-resolution text / panels ---
        var m = ctx.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        int nsw = sw * S, nsh = sh * S;
        if (on("HUD")) transform(ctx, "HUD", sizeOf("HUD"), 6 * S, 6 * S, S, () -> renderInfo(ctx, mc, tr, S));
        if (on("Potion HUD")) transform(ctx, "Potion HUD", sizeOf("Potion HUD"), nsw, 6 * S, S, () -> renderPotions(ctx, mc, tr, S));
        if (on("Keystrokes")) transform(ctx, "Keystrokes", sizeOf("Keystrokes"), 12 * S, nsh, S, () -> renderKeystrokes(ctx, mc, tr, S));
        if (on("Custom Crosshair")) renderCrosshair(ctx, mc, S);
        if (on("Server Helper")) {
            ServerHelper shm = (ServerHelper) LumeClient.MODULES.getByName("Server Helper");
            if (shm == null || shm.showServer.value) transform(ctx, "FT Events", sizeOf("FT Events"), 6 * S, 150 * S, S, () -> renderServerHelper(ctx, mc, tr, S));
            // Anarchy Event HUD panel disabled for now (user request) — event info lives in the Events tab instead.
            if (shm == null || shm.itemHelper.value) transform(ctx, "Item Helper", sizeOf("Item Helper"), nsw / 2.0, nsh, S, () -> renderItemHelper(ctx, mc, tr, S));
            if (shm == null || shm.effects.value) transform(ctx, "Effects", sizeOf("Effects"), 6 * S, nsh / 2.0, S, () -> renderEffects(ctx, mc, tr, S));
            if (shm == null || shm.quickCmds.value) transform(ctx, "Quick Commands", sizeOf("Quick Commands"), nsw, nsh / 2.0, S, () -> renderQuickBar(ctx, mc, tr, S));
        }
        Notifications.render(ctx, tr, S, nsw);
        if (on("Module List")) transform(ctx, "Module List", sizeOf("Module List"), nsw, 6 * S, S, () -> renderArrayList(ctx, mc, tr, S));
        if (on("Crit Helper")) transform(ctx, "Crit Helper", sizeOf("Crit Helper"), nsw / 2.0, nsh / 2.0 + 16 * S, S, () -> renderShiftIndicator(ctx, mc, tr, S));
        // RAM bar HUD (System Info module)
        com.lume.client.module.modules.performance.JvmOptimizer jvmMod =
                (com.lume.client.module.modules.performance.JvmOptimizer) LumeClient.MODULES.getByName("System Info");
        if (jvmMod != null && jvmMod.isEnabled() && jvmMod.showRam.value)
            transform(ctx, "RAM Bar", com.lume.client.gui.HudLayout.getScale("RAM Bar"), 6 * S, nsh - 10 * S, S,
                    () -> renderRamBar(ctx, tr, S));
        if (target != null && espMod.hud.value) {
            float es = sizeOf("Target HUD");
            LivingEntity ft = target;
            transform(ctx, "Target HUD", es, nsw / 2.0, 10 * S, S, () -> renderTargetPanel(ctx, mc, tr, ft, S));
        }

        // --- Block Info (own HUD, like Target HUD) ---
        BlockHitResult blockHit = (on("Block Info") && mc.world != null
                && mc.crosshairTarget instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK)
                ? (BlockHitResult) mc.crosshairTarget : null;
        BlockInfo biMod = (BlockInfo) LumeClient.MODULES.getByName("Block Info");
        boolean biSimple = biMod != null && biMod.simple.value;
        if (blockHit != null) {
            float es = sizeOf("Block Info");
            BlockHitResult bh = blockHit;
            if (biSimple) transform(ctx, "Block Info", es, nsw / 2.0, nsh / 2.0, S, () -> renderBlockSimple(ctx, mc, tr, bh, S));
            else transform(ctx, "Block Info", es, nsw / 2.0, 10 * S, S, () -> renderBlockPanel(ctx, mc, tr, bh, S));
        }

        m.pop();

        // Heads / item icons drawn last (GUI space) so they sit ON TOP of the panel bg.
        if (target != null && espMod.hud.value) {
            renderTargetHead(ctx, mc, target, S, sizeOf("Target HUD"));
            renderTargetArmor(ctx, mc, tr, target, S, sizeOf("Target HUD"));
        }
        if (blockHit != null && !biSimple)
            renderBlockIcon(ctx, mc, blockHit, S, sizeOf("Block Info"));

        // Both NanoVG consumers (target HP pin + waypoint pins) go dead last, back to back, with
        // NOTHING DrawContext-based after either — NanoVgRenderer.frame() is documented "draw it
        // last": any ctx.fill/drawText/drawTexture call queued into DrawContext's own BufferBuilder
        // AFTER an nvg frame (even something as small as the block-info item icon above, which used
        // to sit BETWEEN the two nvg calls) flushes with leftover NanoVG GL/shader state and
        // intermittently paints solid black — this is exactly what happened whenever the Target HP
        // pin was showing (i.e. whenever you're looking at a target). Own matrix push/scale for
        // Waypoints since it now runs outside the shared native-res block above.
        if (target != null && espMod.hpAboveTarget.value) renderTargetHpPin(ctx, mc, target, espMod);
        // Fast Waypoint pings render through the same pass as personal Waypoints (both are "pins"),
        // but they're a SEPARATE module — gating this on just "Waypoints" meant a ping never showed
        // at all (not even to yourself) unless that unrelated module also happened to be on.
        if (on("Waypoints") || !Friends.activePings.isEmpty()) {
            var m2 = ctx.getMatrices();
            m2.push();
            m2.scale(1f / S, 1f / S, 1f);
            renderWaypoints(ctx, mc, tr, S);
            m2.pop();
        }

        if (mc.currentScreen instanceof ClickGuiScreen) renderHandOffscreenHint(ctx, mc, tr, S);
    }

    /**
     * Custom Hand editing aid, only while the ClickGUI is open: if the picked Pos offset has
     * pushed the held item far enough that it's likely off-screen, point an edge arrow (same
     * technique as the Waypoints one) toward it with a label, instead of leaving you hunting for
     * it. Pos X/Y are treated directly as a screen-space direction (right/up) — the item's Pos
     * offset is applied in camera-relative viewmodel space, not world space, so there's no real
     * "project to screen" math to do here, just this direction heuristic.
     */
    private static void renderHandOffscreenHint(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        Module m = LumeClient.MODULES.getByName("Custom Hand");
        if (!(m instanceof CustomHand ch) || !ch.isEnabled() || mc.player == null) return;
        boolean right = ch.hand.index == 0;
        double px = right ? ch.rPosX.value : ch.lPosX.value;
        double py = right ? ch.rPosY.value : ch.lPosY.value;
        double mag = Math.hypot(px, py);
        if (mag < 0.45) return;   // still roughly on-screen — no hint needed

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int cx = sw / 2, cy = sh / 2;
        double dirX = px, dirY = -py;   // screen up = -Y in pixel space
        int accent = Theme.accent();
        drawEdgeArrow(ctx, cx, cy, sw, sh, dirX, dirY, accent, S);

        double nx = dirX / mag, ny = dirY / mag;
        ItemStack held = right ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
        String label = held.getName().getString() + " " + Lang.tUI("is this way");
        int lx = cx + (int) Math.round(nx * 46 * S) - RenderUtil.width(tr, label, 0.4f * S) / 2;
        int ly = cy + (int) Math.round(ny * 46 * S);
        RenderUtil.text(ctx, tr, label, lx, ly, accent, true, 0.4f * S);
    }

    /** PvP Helper — pulsing green highlight over the hotbar slot with the best food to eat. */
    private static void renderPvpHighlight(DrawContext ctx, MinecraftClient mc) {
        if (mc.currentScreen != null) return;   // inventory highlight is handled in HandledScreenMixin instead
        Module m = LumeClient.MODULES.getByName("PvP Helper");
        if (!(m instanceof com.lume.client.module.modules.qol.PvpHelper ph) || !ph.isEnabled()) return;
        int slot = ph.targetSlot();
        if (slot < 0 || slot > 8) return;   // only the hotbar portion is drawn here
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int x = sw / 2 - 91 + slot * 20, y = sh - 22;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
        int a = Math.round(90 + 120 * pulse);
        RenderUtil.roundedRect(ctx, x + 1, y + 1, 18, 18, 3, (a << 24) | 0x00FF66);
    }

    /** The living entity we can hit: vanilla targeted entity (reach + wall checked), not invisible, passes the Target ESP filter. */
    private static LivingEntity findTarget(MinecraftClient mc, TargetEsp esp) {
        if (mc.player == null) return null;
        Entity e = mc.targetedEntity;
        if (e instanceof LivingEntity le && le.isAlive() && !le.isSpectator()
                && !le.isInvisible() && le != mc.player && TargetEsp.passesFilter(esp, le)) {
            return le;
        }
        return null;
    }

    // Show HP pin — smooth HP follow, independent of the Target HUD panel's own animation state
    // (so it still animates even when the panel itself is off).
    private static int hpPinId = -1;
    private static float hpPinDisp = 0f, hpPinGhost = 0f;
    private static long hpPinNanos = System.nanoTime();

    /** "Show HP" — a NanoVG pin floating above the crosshair target (hearts or bar), projected from its world position. */
    private static void renderTargetHpPin(DrawContext ctx, MinecraftClient mc, LivingEntity t, TargetEsp esp) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(-cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw), cpit = Math.cos(pitch), spit = Math.sin(pitch);
        double fx = syaw * cpit, fy = -spit, fz = cyaw * cpit;
        double rx = -fz, rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz); if (rl < 1e-6) { rx = 1; rz = 0; rl = 1; } rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double tanV = Math.tan(Math.toRadians((double) (int) mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / sh;

        double wx = t.getX(), wy = t.getY() + t.getHeight() + 0.3, wz = t.getZ();
        double dx = wx - cp.x, dy = wy - cp.y, dz = wz - cp.z;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth <= 0.1) return;
        double rc = dx * rx + dz * rz, uc = dx * ux + dy * uy + dz * uz;
        double scrX = (0.5 + 0.5 * (rc / depth) / (aspect * tanV)) * sw;
        double scrY = (0.5 - 0.5 * (uc / depth) / tanV) * sh;
        if (scrX < 0 || scrX > sw || scrY < 0 || scrY > sh) return;

        int S = (int) Math.max(1, mc.getWindow().getScaleFactor());
        float nx = (float) (scrX * S), ny = (float) (scrY * S);

        float max = Math.max(1f, t.getMaxHealth());
        float real = Math.max(0f, Math.min(max, t.getHealth()));
        long now = System.nanoTime();
        float dt = (float) Math.min(0.1, (now - hpPinNanos) / 1e9);
        hpPinNanos = now;
        if (t.getId() != hpPinId) { hpPinId = t.getId(); hpPinDisp = real; hpPinGhost = real; }
        hpPinDisp += (real - hpPinDisp) * Math.min(1f, dt * 16f);
        if (real >= hpPinGhost) hpPinGhost = real; else hpPinGhost += (real - hpPinGhost) * Math.min(1f, dt * 3.5f);

        boolean hearts = esp.hpStyle.index == 0;
        boolean showText = esp.hpText.value;
        float ratioMain = hpPinDisp / max, ratioGhost = hpPinGhost / max;
        String hpStr = (int) Math.ceil(real) + " / " + (int) max;

        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;
        ctx.draw();   // flush DrawContext's own queued geometry (e.g. the Target HUD panel's text) before raw-GL NanoVG draws
        NanoVgRenderer.frame(vg -> {
            if (hearts) drawHpHearts(vg, nx, ny, S, ratioMain);
            else drawHpBar(vg, nx, ny, S, ratioMain, ratioGhost);
            if (showText) {
                float ty = ny - (hearts ? 17 : 13) * S;
                NanoVgRenderer.text(vg, nx, ty, 9 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, hpStr);
            }
        });
    }

    /** Row of up to 10 hearts (vanilla-style, health scaled to a 20-unit display), half-heart precision via a clipped overlay. */
    private static void drawHpHearts(long vg, float nx, float ny, int S, float ratioMain) {
        int totalHearts = 10;
        float heartSize = 8 * S, gap = 1.5f * S;
        float rowW = totalHearts * heartSize + (totalHearts - 1) * gap;
        float startX = nx - rowW / 2f + heartSize / 2f;
        float y = ny - 6 * S;
        float filledUnits = Math.max(0f, ratioMain) * totalHearts * 2;
        for (int i = 0; i < totalHearts; i++) {
            float cx = startX + i * (heartSize + gap);
            float need = (i + 1) * 2;
            boolean full = filledUnits >= need;
            boolean half = !full && filledUnits >= need - 1;
            drawHeart(vg, cx, y, heartSize, 0x33FFFFFF);
            if (full) {
                drawHeart(vg, cx, y, heartSize, 0xFFE05656);
            } else if (half) {
                NanoVgRenderer.save(vg);
                NanoVgRenderer.scissor(vg, cx - heartSize, y - heartSize, heartSize, heartSize * 2);
                drawHeart(vg, cx, y, heartSize, 0xFFE05656);
                NanoVgRenderer.restore(vg);
            }
        }
    }

    private static void drawHeart(long vg, float cx, float cy, float size, int argb) {
        float r = size * 0.28f;
        NanoVgRenderer.circle(vg, cx - r * 0.95f, cy - r * 0.35f, r, argb);
        NanoVgRenderer.circle(vg, cx + r * 0.95f, cy - r * 0.35f, r, argb);
        NanoVgRenderer.triangle(vg, cx - r * 1.85f, cy - r * 0.15f, cx + r * 1.85f, cy - r * 0.15f, cx, cy + r * 1.7f, argb);
    }

    /** Health bar (main fill + a slower-trailing "ghost" showing recent damage). */
    private static void drawHpBar(long vg, float nx, float ny, int S, float ratioMain, float ratioGhost) {
        float barW = 56 * S, barH = 5 * S;
        float x = nx - barW / 2f, y = ny - 10 * S - barH;
        NanoVgRenderer.roundedRect(vg, x, y, barW, barH, barH / 2f, 0x99000000);
        float gw = barW * Math.max(0f, Math.min(1f, ratioGhost));
        if (gw > 0) NanoVgRenderer.roundedRect(vg, x, y, gw, barH, barH / 2f, 0xFFD98C8C);
        int mainCol = ratioMain > 0.5f ? 0xFF6FCF7F : ratioMain > 0.25f ? 0xFFE8C15A : 0xFFE05656;
        float mw = barW * Math.max(0f, Math.min(1f, ratioMain));
        if (mw > 0) NanoVgRenderer.roundedRect(vg, x, y, mw, barH, barH / 2f, mainCol);
    }

    /** Geometry for the target panel (native px). Indices documented inline. */
    private static int[] targetLayout(MinecraftClient mc, TextRenderer tr, LivingEntity t, int S, boolean showHead, boolean showArmor) {
        int pad = 8 * S, gap = 5 * S;
        int nameH = 11 * S, barH = 6 * S, hpH = 9 * S, barW = 120 * S, armorH = 16 * S;
        float nameScale = 0.5f * S;
        int textBlockH = nameH + gap + barH + gap + hpH + (showArmor ? gap + armorH : 0);
        int headSize = showHead ? textBlockH : 0;
        int leftPad = showHead ? headSize + 8 * S : 0;
        int textBlockW = Math.max(barW, RenderUtil.width(tr, t.getDisplayName().getString(), nameScale));
        int pw = pad + leftPad + textBlockW + pad;
        int ph = pad * 2 + textBlockH;
        int sw = mc.getWindow().getScaledWidth() * S;
        int x = sw / 2 - pw / 2, y = 10 * S;
        int textX = x + pad + leftPad;
        int barY = y + pad + nameH + gap;
        int hpY = barY + barH + gap;
        return new int[]{
                x, y, pw, ph,                    // 0..3 panel
                x + pad, y + pad, headSize,      // 4..6 head x,y,size
                textX, barY, barW,               // 7..9 textX, barY, barW
                y + pad, hpY,                    // 10 nameY, 11 hpY
                nameH, hpH, barH,                // 12 nameH, 13 hpH, 14 barH
                hpY + hpH + gap, armorH           // 15 armorY, 16 armorH
        };
    }

    // Smooth health animation state (one target shown at a time).
    private static int hpId = -1;
    private static float hpDisp = 0f, hpGhost = 0f;
    private static long hpTime = 0L;

    /** Glass panel (top-centre): name + animated HP bar. Head + armor drawn separately (GUI space). */
    private static void renderTargetPanel(DrawContext ctx, MinecraftClient mc, TextRenderer tr, LivingEntity t, int S) {
        TargetEsp mod = (TargetEsp) LumeClient.MODULES.getByName("Target ESP");
        boolean showHead = true;   // always reserved — hudHead now only switches 3D model vs 2D face icon, see renderTargetHead
        boolean showBar = mod == null || mod.hudHealthBar.value;
        boolean showArmor = mod != null && mod.hudArmor.value;
        boolean animate = mod == null || mod.hudAnimate.value;

        String name = t.getDisplayName().getString();
        float max = Math.max(1f, t.getMaxHealth());
        float real = Math.max(0f, Math.min(max, t.getHealth()));

        // ease the displayed + ghost (damage trail) health
        if (animate) {
            long now = System.currentTimeMillis();
            float dt = hpTime == 0 ? 0f : Math.min(0.1f, (now - hpTime) / 1000f);
            hpTime = now;
            if (t.getId() != hpId) { hpId = t.getId(); hpDisp = real; hpGhost = real; }
            hpDisp += (real - hpDisp) * Math.min(1f, dt * 16f);
            if (real >= hpGhost) hpGhost = real;
            else hpGhost += (real - hpGhost) * Math.min(1f, dt * 3.5f);
        } else {
            hpId = t.getId(); hpDisp = real; hpGhost = real;
        }

        int[] L = targetLayout(mc, tr, t, S, showHead, showArmor);
        int x = L[0], y = L[1], pw = L[2], ph = L[3];
        float nameScale = 0.5f * S, hpScale = 0.42f * S;
        int accentRgb = (mod != null && !mod.color.accent) ? mod.color.rgb() : Theme.accentRgb();

        RenderUtil.glow(ctx, x, y, pw, ph, 9 * S, accentRgb, 3);
        RenderUtil.roundedRect(ctx, x, y, pw, ph, 9 * S, Theme.winBg());

        if (showHead) // subtle inset behind the head
            RenderUtil.roundedRect(ctx, L[4], L[5], L[6], L[6], 6 * S, 0x33000000);

        RenderUtil.textVCentered(ctx, tr, name, L[7], L[10], L[12], Theme.txt(), nameScale);

        if (showBar) {
            int barX = L[7], barY = L[8], barW = L[9], barH = L[14];
            RenderUtil.roundedRect(ctx, barX, barY, barW, barH, barH / 2, 0x66000000);
            float ratioGhost = hpGhost / max, ratioMain = hpDisp / max;
            int gw = Math.round(barW * Math.max(0f, Math.min(1f, ratioGhost)));
            if (gw > 0) RenderUtil.roundedRect(ctx, barX, barY, Math.max(barH, gw), barH, barH / 2, 0xFFD98C8C);
            int mainCol = ratioMain > 0.5f ? 0xFF6FCF7F : ratioMain > 0.25f ? 0xFFE8C15A : 0xFFE05656;
            int mw = Math.round(barW * Math.max(0f, Math.min(1f, ratioMain)));
            if (mw > 0) RenderUtil.roundedRect(ctx, barX, barY, Math.max(barH, mw), barH, barH / 2, mainCol);
        }

        String hpStr = (int) Math.ceil(real) + " / " + (int) max;
        RenderUtil.textVCentered(ctx, tr, hpStr, L[7], L[11], L[13], Theme.txtDim(), hpScale);
    }

    /**
     * The target's head, matched to the (possibly scaled) panel's left box.
     * "3D Head" on → full 3D model preview (as before). Off → a flat 2D face
     * icon instead (player skin face+hat layer, Minecraft's classic "head
     * icon" look) — for non-player entities there's no vanilla 2D head
     * texture to draw, so those keep the 3D model regardless of the setting.
     */
    private static void renderTargetHead(DrawContext ctx, MinecraftClient mc, LivingEntity t, int S, float es) {
        TargetEsp mod = (TargetEsp) LumeClient.MODULES.getByName("Target ESP");
        boolean showArmor = mod != null && mod.hudArmor.value;
        int[] L = targetLayout(mc, mc.textRenderer, t, S, true, showArmor);
        int hs = L[6];
        if (hs <= 0) return;
        // mirror the panel's scale-around-anchor so the head lines up
        double ax = mc.getWindow().getScaledWidth() * S / 2.0, ay = 10 * S;
        double hx = ax + (L[4] - ax) * es, hy = ay + (L[5] - ay) * es;
        double hsz = hs * es;
        int[] off = HudLayout.get("Target HUD");
        int x1 = (int) (hx / S) + off[0], y1 = (int) (hy / S) + off[1];
        int x2 = (int) ((hx + hsz) / S) + off[0], y2 = (int) ((hy + hsz) / S) + off[1];

        boolean use3d = mod == null || mod.hudHead.value || !(t instanceof net.minecraft.client.network.AbstractClientPlayerEntity);
        if (use3d) {
            int entSize = Math.max(8, Math.round((y2 - y1) * 0.5f));
            float cxp = (x1 + x2) / 2f, cyp = (y1 + y2) / 2f;
            try {
                InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, entSize, 0.0625f, cxp, cyp, t);
            } catch (Throwable ignored) {
                // some modded/edge entities can't render in a GUI — skip the head, keep the panel
            }
        } else {
            net.minecraft.client.network.AbstractClientPlayerEntity acpe = (net.minecraft.client.network.AbstractClientPlayerEntity) t;
            var skin = acpe.getSkinTextures();
            int size = x2 - x1;
            ctx.drawTexture(RenderLayer::getGuiTextured, skin.texture(), x1, y1, 8f, 8f, size, size, 64, 64);   // base face
            ctx.drawTexture(RenderLayer::getGuiTextured, skin.texture(), x1, y1, 40f, 8f, size, size, 64, 64);  // hat overlay layer
        }
    }

    private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET };

    /** Target's equipped armor — a row of icons under the HP bar, matched to the panel's scale-around-anchor. */
    private static void renderTargetArmor(DrawContext ctx, MinecraftClient mc, TextRenderer tr, LivingEntity t, int S, float es) {
        TargetEsp mod = (TargetEsp) LumeClient.MODULES.getByName("Target ESP");
        if (mod != null && !mod.hudArmor.value) return;
        int[] L = targetLayout(mc, tr, t, S, true, true);
        int cell = L[16];
        double ax = mc.getWindow().getScaledWidth() * S / 2.0, ay = 10 * S;
        double rowX = ax + (L[7] - ax) * es, rowY = ay + (L[15] - ay) * es;
        int[] off = HudLayout.get("Target HUD");
        int gx = (int) (rowX / S) + off[0], gy = (int) (rowY / S) + off[1];
        float scaledCell = cell / (float) S * es;
        for (net.minecraft.entity.EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack st = t.getEquippedStack(slot);
            if (st.isEmpty()) { gx += Math.round(scaledCell); continue; }
            var mstack = ctx.getMatrices();
            mstack.push();
            mstack.translate(gx, gy, 0);
            mstack.scale(scaledCell / 16f, scaledCell / 16f, 1f);
            ctx.drawItem(st, 0, 0);
            ctx.drawStackOverlay(tr, st, 0, 0);
            mstack.pop();
            gx += Math.round(scaledCell);
        }
    }

    // ---- Block Info (own HUD) ----

    private static String blockName(MinecraftClient mc, BlockHitResult bhr) {
        return mc.world.getBlockState(bhr.getBlockPos()).getBlock().getName().getString();
    }

    private static ItemStack blockStack(MinecraftClient mc, BlockHitResult bhr) {
        return new ItemStack(mc.world.getBlockState(bhr.getBlockPos()).getBlock());
    }

    /** Geometry (native px): {x,y,pw,ph, iconX, iconY, iconN, nameX, nameH}. */
    private static int[] blockLayout(MinecraftClient mc, TextRenderer tr, BlockHitResult bhr, int S, boolean showIcon) {
        int pad = 8 * S, nameH = 11 * S, iconN = showIcon ? 16 * S : 0, gap = showIcon ? 6 * S : 0;
        int nameW = RenderUtil.width(tr, blockName(mc, bhr), 0.5f * S);
        int pw = pad + iconN + gap + nameW + pad;
        int ph = pad * 2 + Math.max(nameH, iconN);
        int sw = mc.getWindow().getScaledWidth() * S;
        int x = sw / 2 - pw / 2, y = 10 * S;
        return new int[]{x, y, pw, ph, x + pad, y + (ph - iconN) / 2, iconN, x + pad + iconN + gap, nameH};
    }

    private static void renderBlockPanel(DrawContext ctx, MinecraftClient mc, TextRenderer tr, BlockHitResult bhr, int S) {
        int[] L = blockLayout(mc, tr, bhr, S, true);
        int x = L[0], y = L[1], pw = L[2], ph = L[3];
        RenderUtil.glow(ctx, x, y, pw, ph, 9 * S, Theme.accentRgb(), 3);
        RenderUtil.roundedRect(ctx, x, y, pw, ph, 9 * S, Theme.winBg());
        RenderUtil.textVCentered(ctx, tr, blockName(mc, bhr), L[7], y + (ph - L[8]) / 2, L[8], Theme.txt(), 0.5f * S);
    }

    /** Block's 3D item icon, GUI space, mirroring the panel's scale-around-anchor. */
    private static void renderBlockIcon(DrawContext ctx, MinecraftClient mc, BlockHitResult bhr, int S, float es) {
        ItemStack stack = blockStack(mc, bhr);
        if (stack.isEmpty()) return;
        int[] L = blockLayout(mc, mc.textRenderer, bhr, S, true);
        double ax = mc.getWindow().getScaledWidth() * S / 2.0, ay = 10 * S;
        double ix = ax + (L[4] - ax) * es, iy = ay + (L[5] - ay) * es;
        int[] off = HudLayout.get("Block Info");
        var m = ctx.getMatrices();
        m.push();
        m.translate(ix / S + off[0], iy / S + off[1], 0);
        m.scale(es, es, 1f);
        ctx.drawItem(stack, 0, 0);
        m.pop();
    }

    /** Simple mode: just the block name next to the crosshair. */
    private static void renderBlockSimple(DrawContext ctx, MinecraftClient mc, TextRenderer tr, BlockHitResult bhr, int S) {
        String name = blockName(mc, bhr);
        int cx = mc.getWindow().getScaledWidth() * S / 2;
        int cy = mc.getWindow().getScaledHeight() * S / 2;
        int w = RenderUtil.width(tr, name, 0.5f * S);
        int px = cx + 10 * S, py = cy + 6 * S;
        RenderUtil.roundedRect(ctx, px - 4 * S, py, w + 8 * S, 14 * S, 5 * S, Theme.winBg());
        RenderUtil.textVCentered(ctx, tr, name, px, py, 14 * S, Theme.txt(), 0.5f * S);
    }

    /** In-game crosshair (first-person only). Delegates to {@link #drawCrosshair}. */
    private static void renderCrosshair(DrawContext ctx, MinecraftClient mc, int S) {
        if (mc.currentScreen != null) return;
        if (mc.options == null || !mc.options.getPerspective().isFirstPerson()) return;
        if (mc.player == null || mc.player.isSpectator()) return;
        int cx = mc.getWindow().getScaledWidth() * S / 2;
        int cy = mc.getWindow().getScaledHeight() * S / 2;
        drawCrosshair(ctx, cx, cy, S);
    }

    /**
     * Draws the configured crosshair centred at (cx,cy) with {@code unit} px per
     * setting-step. Public so the ClickGUI can show a live preview.
     */
    public static void drawCrosshair(DrawContext ctx, int cx, int cy, int unit) {
        CustomCrosshair m = (CustomCrosshair) LumeClient.MODULES.getByName("Custom Crosshair");
        int len = (m != null ? m.size.getInt() : 5) * unit;
        int th = Math.max(1, (m != null ? m.thickness.getInt() : 1) * unit);
        int gap = (m != null ? m.gap.getInt() : 3) * unit;
        boolean outline = m == null || m.outline.value;
        boolean dot = m == null || m.dot.value;
        int color = (m != null && !m.color.accent) ? (0xFF000000 | m.color.rgb()) : Theme.accent();

        RenderUtil.glow(ctx, cx - len - gap, cy - th, (len + gap) * 2, th * 2, th, color & 0xFFFFFF, 2);
        if (outline) arm(ctx, cx, cy, gap, len, th, 0xAA000000, unit, true);
        arm(ctx, cx, cy, gap, len, th, color, unit, false);
        if (dot) {
            int d = Math.max(1, th / 2 + (unit - 1));
            if (outline) ctx.fill(cx - d - unit, cy - d - unit, cx + d + unit, cy + d + unit, 0xAA000000);
            ctx.fill(cx - d, cy - d, cx + d, cy + d, color);
        }
    }

    /** Draws the four crosshair arms; {@code outlinePass} inflates them by 1px for a dark edge. */
    private static void arm(DrawContext ctx, int cx, int cy, int gap, int len, int th, int color, int S, boolean outlinePass) {
        int e = outlinePass ? S : 0;
        ctx.fill(cx - gap - len - e, cy - th / 2 - e, cx - gap + e, cy - th / 2 + th + e, color); // left
        ctx.fill(cx + gap - e, cy - th / 2 - e, cx + gap + len + e, cy - th / 2 + th + e, color); // right
        ctx.fill(cx - th / 2 - e, cy - gap - len - e, cx - th / 2 + th + e, cy - gap + e, color); // top
        ctx.fill(cx - th / 2 - e, cy + gap - e, cx - th / 2 + th + e, cy + gap + len + e, color); // bottom
    }

    /** Minimum lines the main HUD panel must always fit — title row + one horizontal metrics row. */
    private static final int HUD_MIN_LINES = 2;

    /**
     * Main info panel: title row + a SINGLE horizontal metrics row (FPS + whichever
     * optional fields are on, joined side by side) — never stacks extra fields as new
     * rows, so the panel stays exactly 2 lines tall no matter how many are enabled
     * (that's what lets it be resized down in Y) and everything reads left-to-right.
     */
    private static void renderInfo(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        int x = 6 * S, y = 6 * S, pad = 6 * S;
        com.lume.client.module.modules.visual.Hud hudMod =
                (com.lume.client.module.modules.visual.Hud) LumeClient.MODULES.getByName("HUD");
        int accentCol = (hudMod != null && !hudMod.color.accent) ? (0xFF000000 | hudMod.color.rgb()) : Theme.accent();
        int accentRgb = accentCol & 0xFFFFFF;
        int lineH = 11 * S;

        boolean fps = hudMod == null || hudMod.fps.value;   // default on for a fresh install (no module yet)
        boolean coords = hudMod != null && hudMod.coords.value && mc.player != null;
        boolean ping = hudMod != null && hudMod.ping.value && mc.player != null && mc.getNetworkHandler() != null;
        boolean day = hudMod != null && hudMod.dayCounter.value && mc.world != null;
        boolean cps = hudMod != null && hudMod.cps.value;
        boolean speed = hudMod != null && hudMod.speed.value && mc.player != null;
        boolean clock = hudMod != null && hudMod.clock.value && mc.world != null;

        List<String> parts = new ArrayList<>();
        if (fps) parts.add("FPS " + mc.getCurrentFps());
        if (coords) parts.add(String.format("%.0f %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        if (ping) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            // Always English here, never Lang.t() — the in-game HUD renders through our own
            // Lume/Poppins font (no Cyrillic glyphs at all), so a Russian label would silently
            // fall back to vanilla's blocky font for the whole line. Language only translates
            // menu/settings text, never the HUD overlay itself.
            if (e != null) parts.add(e.getLatency() + "ms");
        }
        if (day) parts.add("Day " + (mc.world.getTimeOfDay() / 24000L));
        if (cps) parts.add(ClickTracker.left() + "|" + ClickTracker.right());
        if (speed) parts.add(String.format("%.1f ", SpeedTracker.get()) + "b/s");
        if (clock) {
            long tod = mc.world.getTimeOfDay() % 24000L;
            if (tod < 0) tod += 24000L;
            int hh = (int) ((tod / 1000L + 6L) % 24L);     // tick 0 = 06:00
            int mm = (int) ((tod % 1000L) * 60L / 1000L);
            parts.add(String.format("%02d:%02d", hh, mm));
        }
        String metricsStr = String.join("  ·  ", parts);

        int[] sizeOverride = HudLayout.getSize("HUD");   // user-resized (window-style) — else auto-fit to content
        int autoW = Math.max(134, RenderUtil.width(tr, metricsStr, 0.46f * S) + pad * 2 + 4 * S);
        int pw = (sizeOverride != null ? sizeOverride[0] * S : autoW);
        int autoH = HUD_MIN_LINES * lineH + pad * 2 - 2 * S;
        int h = sizeOverride != null ? Math.max(sizeOverride[1] * S, autoH) : autoH;

        RenderUtil.glow(ctx, x, y, pw, h, 9 * S, accentRgb, 3 * S);
        RenderUtil.roundedRect(ctx, x, y, pw, h, 9 * S, Theme.winBg());
        ctx.enableScissor(x, y, x + pw, y + h);   // clip long metrics rows / small resizes, don't spill

        int ty = y + pad;
        centerLine(ctx, tr, "Lume Visuals", x, pw, ty, lineH, accentCol, 0.5f * S); ty += lineH;
        centerLine(ctx, tr, metricsStr, x, pw, ty, lineH, Theme.txt(), 0.46f * S);
        ctx.disableScissor();
    }

    /** Hide every waypoint marker within this many blocks of the world spawn (the anarchy hub) — guessed radius, tune if wrong. */
    private static final double SPAWN_HIDE_RADIUS = 150.0;

    private static boolean nearSpawn(MinecraftClient mc) {
        BlockPos sp = mc.world.getSpawnPos();
        if (sp == null) return false;
        double dx = mc.player.getX() - sp.getX(), dz = mc.player.getZ() - sp.getZ();
        return dx * dx + dz * dz <= SPAWN_HIDE_RADIUS * SPAWN_HIDE_RADIUS;
    }

    /** One on-screen pin queued for the batched NanoVG pass at the end of {@link #renderWaypoints}.
     *  {@code scale} is 1.0 for friend/ping pins — only real Waypoints honour the module's own Size slider. */
    private record PinJob(float nx, float ny, int color, String name, String sub, int subColor, float scale) {}

    /** Draws saved waypoints as NanoVG "map pin" markers (rect tapering down to the exact spot, custom font)
     *  + edge arrows when off-screen. Only the current server/anarchy's own set — see {@link Waypoints#visible()}. */
    private static void renderWaypoints(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        if (mc.world == null || mc.player == null) return;
        // Personal waypoints stay gated on their own module even though this function is now also
        // reachable purely for Fast Waypoint pings — see the call site's comment.
        List<Waypoints.WP> visible = on("Waypoints") ? Waypoints.visible() : java.util.Collections.emptyList();
        List<Friends.Point> friendPts = Friends.pointsHere();
        if ((visible.isEmpty() && friendPts.isEmpty()) || nearSpawn(mc)) return;
        Waypoints mod = (Waypoints) LumeClient.MODULES.getByName("Waypoints");
        boolean arrows = mod == null || mod.arrows.value;
        int arrowCol = (mod != null && !mod.color.accent) ? (0xFF000000 | mod.color.rgb()) : Theme.accent();
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();

        // camera basis from yaw/pitch (no quaternion convention guessing)
        double yaw = Math.toRadians(-cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw), cpit = Math.cos(pitch), spit = Math.sin(pitch);
        double fx = syaw * cpit, fy = -spit, fz = cyaw * cpit;        // forward
        double rx = -fz, rz = fx;                                     // right = forward × up
        double rl = Math.sqrt(rx * rx + rz * rz); if (rl < 1e-6) { rx = 1; rz = 0; rl = 1; } rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;   // up

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double tanV = Math.tan(Math.toRadians((double) (int) mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / sh;
        int cx = sw / 2, cy = sh / 2;
        Vec3d ppos = mc.player.getPos();
        String curAnarchy = CurrentAnarchy.get();

        List<PinJob> pins = new ArrayList<>();

        for (Waypoints.WP w : visible) {
            double dx = w.x - cp.x, dy = w.y - cp.y, dz = w.z - cp.z;
            double depth = dx * fx + dy * fy + dz * fz;
            double rc = dx * rx + dz * rz;
            double uc = dx * ux + dy * uy + dz * uz;
            int dist = (int) Math.round(ppos.distanceTo(new Vec3d(w.x, w.y, w.z)));

            boolean front = depth > 0.1;
            double scrX = 0, scrY = 0;
            boolean onScreen = false;
            if (front) {
                scrX = (0.5 + 0.5 * (rc / depth) / (aspect * tanV)) * sw;
                scrY = (0.5 - 0.5 * (uc / depth) / tanV) * sh;
                onScreen = scrX >= 0 && scrX <= sw && scrY >= 0 && scrY <= sh;
            }

            if (onScreen) {
                TelegramEvents.Ev linked = null;
                if (curAnarchy != null && TelegramEvents.available()) {
                    for (TelegramEvents.Ev e : TelegramEvents.events()) {
                        if (e.anarchy.equals(curAnarchy) && e.name.equalsIgnoreCase(w.name)) { linked = e; break; }
                    }
                }
                String sub; int subColor;
                if (linked == null) {
                    sub = dist + "m"; subColor = Theme.txtDim();
                } else if (linked.isActive()) {
                    sub = dist + "m  ·  " + (linked.isVolcano() ? "Извергается" : "Уже открылся");
                    subColor = 0xFF6FCF7F;
                } else {
                    int s = linked.liveSecondsLeft();
                    String verb = linked.isOpening() ? (linked.isVolcano() ? "Извержение" : "Откроется") : "Появится";
                    sub = s > 0 ? dist + "m  ·  " + verb + " через " + linked.liveTimeText() : dist + "m  ·  " + verb;
                    subColor = s > 0 ? 0xFFE8C15A : Theme.txtDim();
                }
                float pinScale = mod != null ? (float) mod.size.value : 1f;
                pins.add(new PinJob((float) (scrX * S), (float) (scrY * S), w.color, w.name, sub, subColor, pinScale));
            } else if (arrows) {
                double dirX = front ? (scrX - cx) : rc;
                double dirY = front ? (scrY - cy) : -uc;
                drawEdgeArrow(ctx, cx, cy, sw, sh, dirX, dirY, arrowCol, S);
            }
        }

        int friendCol = 0xFF6F9CE0, pingCol = 0xFFE8C15A;
        long now = System.currentTimeMillis();
        for (Friends.Point p : friendPts) {
            boolean isPing = Friends.PING_NAME.equals(p.name);
            if (isPing) {
                Long expire = Friends.activePings.get(p.id);
                if (expire == null || now >= expire) continue;   // ping pins are transient-only — hide once expired
            }

            double dx = p.x - cp.x, dy = p.y - cp.y, dz = p.z - cp.z;
            double depth = dx * fx + dy * fy + dz * fz;
            double rc = dx * rx + dz * rz;
            double uc = dx * ux + dy * uy + dz * uz;
            int dist = (int) Math.round(ppos.distanceTo(new Vec3d(p.x, p.y, p.z)));

            boolean front = depth > 0.1;
            double scrX = 0, scrY = 0;
            boolean onScreen = false;
            if (front) {
                scrX = (0.5 + 0.5 * (rc / depth) / (aspect * tanV)) * sw;
                scrY = (0.5 - 0.5 * (uc / depth) / tanV) * sh;
                onScreen = scrX >= 0 && scrX <= sw && scrY >= 0 && scrY <= sh;
            }

            if (onScreen) {
                if (isPing) {
                    String coords = Math.round(p.x) + ", " + Math.round(p.y) + ", " + Math.round(p.z);
                    pins.add(new PinJob((float) (scrX * S), (float) (scrY * S), pingCol,
                            "⚡ " + p.from, dist + "m  ·  " + coords, pingCol, 1f));
                } else {
                    pins.add(new PinJob((float) (scrX * S), (float) (scrY * S), friendCol,
                            p.name, dist + "m  ·  " + p.from, Theme.txtDim(), 1f));
                }
            } else if (arrows) {
                double dirX = front ? (scrX - cx) : rc;
                double dirY = front ? (scrY - cy) : -uc;
                drawEdgeArrow(ctx, cx, cy, sw, sh, dirX, dirY, isPing ? pingCol : friendCol, S);
            }
        }

        if (!pins.isEmpty()) {
            NanoVgRenderer.ensureInit();
            if (NanoVgRenderer.ready()) {
                ctx.draw();   // flush DrawContext's own queued geometry before raw-GL NanoVG draws
                NanoVgRenderer.frame(vg -> { for (PinJob p : pins) drawPin(vg, p, S); });
            }
        }
    }

    /** One waypoint pin: rounded-rect body + a downward triangular tail tapering to the exact world point. */
    private static void drawPin(long vg, PinJob p, int S) {
        float sc = p.scale() * S;
        float fName = 9.5f * sc, fSub = 8.5f * sc, lineH = 12 * sc;
        float padX = 8 * sc, padY = 5 * sc;
        float nameW = NanoVgRenderer.textWidth(vg, fName, p.name());
        float subW = NanoVgRenderer.textWidth(vg, fSub, p.sub());
        float bodyW = Math.max(Math.max(nameW, subW) + padX * 2, 46 * sc);
        float bodyH = padY * 2 + lineH * 2;
        float tailW = 9 * sc, tailH = 6 * sc;
        float bodyBottom = p.ny() - tailH;
        float bodyTop = bodyBottom - bodyH;
        float bodyX = p.nx() - bodyW / 2f;

        NanoVgRenderer.shadow(vg, bodyX, bodyTop, bodyW, bodyH, 7 * sc, 8 * sc, 0x50000000);
        NanoVgRenderer.triangle(vg, p.nx() - tailW / 2f, bodyBottom, p.nx() + tailW / 2f, bodyBottom, p.nx(), p.ny(), Theme.winBg());
        NanoVgRenderer.roundedRect(vg, bodyX, bodyTop, bodyW, bodyH, 7 * sc, Theme.winBg());
        NanoVgRenderer.strokeRoundedRect(vg, bodyX + 0.5f * S, bodyTop + 0.5f * S, bodyW - S, bodyH - S, 7 * sc, S, p.color());
        NanoVgRenderer.text(vg, p.nx(), bodyTop + padY + lineH / 2f, fName, p.color(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, p.name());
        NanoVgRenderer.text(vg, p.nx(), bodyTop + padY + lineH + lineH / 2f, fSub, p.subColor(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, p.sub());
    }

    /** A small triangle pinned to the screen edge, pointing toward an off-screen waypoint. */
    private static void drawEdgeArrow(DrawContext ctx, int cx, int cy, int sw, int sh, double dirX, double dirY, int color, int S) {
        double len = Math.hypot(dirX, dirY);
        if (len < 1e-4) return;
        double nx = dirX / len, ny = dirY / len;
        int margin = 18;
        double t = Double.MAX_VALUE;
        if (Math.abs(nx) > 1e-6) { double tt = ((nx > 0 ? sw - margin : margin) - cx) / nx; if (tt > 0) t = Math.min(t, tt); }
        if (Math.abs(ny) > 1e-6) { double tt = ((ny > 0 ? sh - margin : margin) - cy) / ny; if (tt > 0) t = Math.min(t, tt); }
        if (t == Double.MAX_VALUE) return;
        int ex = (int) ((cx + nx * t) * S), ey = (int) ((cy + ny * t) * S);

        var m = ctx.getMatrices();
        m.push();
        m.translate(ex, ey, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.toDegrees(Math.atan2(ny, nx))));
        int aw = 7 * S, ah = 9 * S;   // arrow points toward +x (the waypoint)
        for (int xx = 0; xx < aw; xx++) {
            int half = Math.round((1f - (float) xx / aw) * ah / 2f);
            ctx.fill(xx, -half, xx + 1, half, color);
        }
        m.pop();
    }

    /**
     * Small standalone HUD: the Telegram event(s) on the anarchy you're CURRENTLY
     * on, and only that anarchy — shows NOTHING until {@link CurrentAnarchy} can
     * read a number off the scoreboard (i.e. you're actually on a recognised
     * anarchy, not just connected to the server's hub/lobby), and even then only
     * if there's an event for that specific anarchy number. Gated by the
     * "Server Helper" module + its eventsHud sub-toggle (see call site).
     */
    private static void renderAnarchyEventHud(DrawContext ctx, TextRenderer tr, int S, int nsw) {
        String current = CurrentAnarchy.get();
        if (current == null || !TelegramEvents.available()) return;
        java.util.List<TelegramEvents.Ev> mine = new java.util.ArrayList<>();
        for (TelegramEvents.Ev e : TelegramEvents.events()) if (e.anarchy.equals(current)) mine.add(e);
        if (mine.isEmpty()) return;

        int pad = 6 * S, lineH = 11 * S;
        int h = pad * 2 + mine.size() * lineH;
        int pw = 0;
        for (TelegramEvents.Ev e : mine) {
            String l = e.name + "  ·  " + e.statusText();
            pw = Math.max(pw, RenderUtil.vanillaWidth(tr, l, S));
        }
        pw += pad * 2 + 4 * S;
        int x = nsw / 2 - pw / 2, y = 6 * S;   // centred, same anchor y passed to transform()
        RenderUtil.roundedRect(ctx, x, y, pw, h, 7 * S, Theme.winBg());
        int ty = y + pad;
        for (TelegramEvents.Ev e : mine) {
            int col = e.isActive() ? 0xFF6FCF7F : e.isVoting() ? Theme.accent() : (e.liveSecondsLeft() > 0 ? 0xFFE8C15A : Theme.txtDim());
            RenderUtil.roundedRect(ctx, x, ty - 1, 3 * S, lineH - 2 * S, 2 * S, col);
            RenderUtil.vanillaText(ctx, tr, e.name + "  ·  " + e.statusText(), x + pad + 3 * S, ty, col, S);
            ty += lineH;
        }
    }

    /** FT/HW helper HUD: just the detected-server line (events moved to {@link #renderAnarchyEventHud}, own toggle). */
    private static void renderServerHelper(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        String l = "Сервер: " + ServerType.current().display();
        int x = 6 * S, y = 150 * S, pad = 6 * S, lineH = 11 * S;
        int pw = RenderUtil.vanillaWidth(tr, l, S) + pad * 2 + 4 * S;
        int h = lineH + pad * 2;
        RenderUtil.roundedRect(ctx, x, y, pw, h, 7 * S, Theme.winBg());
        RenderUtil.roundedRect(ctx, x, y, 3 * S, h, 2 * S, Theme.accent());
        RenderUtil.vanillaText(ctx, tr, l, x + pad + 3 * S, y + pad, Theme.txt(), S);
    }

    /** FT/HW item helper: when holding a known custom item, show its name/radius/cooldown + a ground ring. */
    private static void renderItemHelper(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandStack();
        ItemRule rule = held.isEmpty() ? null : ItemRules.find(held.getName().getString());
        if (rule == null) {
            held = mc.player.getOffHandStack();
            rule = held.isEmpty() ? null : ItemRules.find(held.getName().getString());
        }
        if (rule == null) return;

        if (rule.radius > 0) drawGroundRing(ctx, mc, rule.radius, rule.color, S);

        String name = held.getName().getString();
        float cd = 0f;
        try { cd = mc.player.getItemCooldownManager().getCooldownProgress(held, 0f); } catch (Exception ignored) {}

        int sw = mc.getWindow().getScaledWidth() * S, sh = mc.getWindow().getScaledHeight() * S;
        // title line: name + radius + (live cooldown seconds when on cooldown, else "готов")
        StringBuilder lb = new StringBuilder(name);
        if (rule.radius > 0) lb.append("  ·  R").append((int) rule.radius);
        if (rule.cooldownSec > 0) {
            if (cd > 0f) lb.append("  ·  ").append((int) Math.ceil(cd * rule.cooldownSec)).append("с");
            else lb.append("  ·  готов");
        }
        String line = lb.toString();
        int pw = Math.max(RenderUtil.vanillaWidth(tr, line, S), RenderUtil.vanillaWidth(tr, rule.note, S)) + 20 * S;
        int ph = 30 * S;
        int x = sw / 2 - pw / 2, y = sh - 64 * S;
        RenderUtil.roundedRect(ctx, x, y, pw, ph, 7 * S, Theme.winBg());
        RenderUtil.roundedRect(ctx, x, y, 3 * S, ph, 2 * S, rule.color);
        RenderUtil.vanillaText(ctx, tr, line, x + 10 * S, y + 5 * S, cd > 0f ? Theme.txtDim() : rule.color, S);
        RenderUtil.vanillaText(ctx, tr, rule.note, x + 10 * S, y + 17 * S, Theme.txtDim(), S);
        if (cd > 0f) {   // cooldown bar (cd: 1 = just used → 0 = ready)
            int bx = x + 10 * S, by = y + ph - 4 * S, bw = pw - 20 * S;
            RenderUtil.roundedRect(ctx, bx, by, bw, 2 * S, S, 0x66000000);
            RenderUtil.roundedRect(ctx, bx, by, Math.round(bw * cd), 2 * S, S, rule.color);
        }
    }

    /**
     * "Effects on you" — your own active status effects with live countdowns,
     * negative effects (the ones FT items inflict) highlighted. Legit: reads only
     * your own potion effects. Left-middle of the screen, vanilla font (Cyrillic).
     */
    private static void renderEffects(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        if (mc.player == null) return;
        var fx = mc.player.getStatusEffects();
        if (fx.isEmpty()) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<Integer> cols = new java.util.ArrayList<>();
        for (StatusEffectInstance e : fx) {
            String nm = e.getEffectType().value().getName().getString();
            int amp = e.getAmplifier() + 1;
            int d = e.getDuration();
            String dur = d >= 32767 ? "∞" : fmtTicks(d);
            lines.add(nm + (amp > 1 ? " " + amp : "") + "  " + dur);
            boolean bad = e.getEffectType().value().getCategory()
                    == net.minecraft.entity.effect.StatusEffectCategory.HARMFUL;
            cols.add(bad ? 0xFFE06666 : 0xFF7FD08A);
        }

        int x = 6 * S, y = mc.getWindow().getScaledHeight() * S / 2 - lines.size() * 6 * S, pad = 6 * S, lineH = 11 * S;
        int pw = 0;
        for (String l : lines) pw = Math.max(pw, RenderUtil.vanillaWidth(tr, l, S));
        pw += pad * 2 + 4 * S;
        int h = lines.size() * lineH + pad * 2;
        RenderUtil.roundedRect(ctx, x, y, pw, h, 7 * S, Theme.winBg());
        RenderUtil.roundedRect(ctx, x, y, 3 * S, h, 2 * S, Theme.accent());
        int ty = y + pad;
        for (int i = 0; i < lines.size(); i++) {
            RenderUtil.vanillaText(ctx, tr, lines.get(i), x + pad + 3 * S, ty, cols.get(i), S);
            ty += lineH;
        }
    }

    /** Format a duration given in ticks as m:ss or s. */
    private static String fmtTicks(int ticks) {
        int sec = ticks / 20;
        if (sec >= 60) return (sec / 60) + ":" + String.format("%02d", sec % 60);
        return sec + "с";
    }

    private static String keyName(int code) {
        if (code < 0) return "—";
        try { return net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString(); }
        catch (Exception e) { return "?"; }
    }

    /** FT/HW quick-command HUD bar (right side): each command + its bound key. */
    private static void renderQuickBar(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        java.util.List<com.lume.client.fthw.QuickCommands.Cmd> list = com.lume.client.fthw.QuickCommands.list;
        if (list.isEmpty()) return;
        java.util.List<String> lines = new java.util.ArrayList<>();
        int pad = 6 * S, lineH = 12 * S, pw = 0;
        for (com.lume.client.fthw.QuickCommands.Cmd c : list) {
            String line = c.label + "  [" + keyName(c.key) + "]";
            lines.add(line);
            pw = Math.max(pw, RenderUtil.vanillaWidth(tr, line, S));
        }
        pw += pad * 2 + 4 * S;
        int h = lines.size() * lineH + pad * 2;
        int x = mc.getWindow().getScaledWidth() * S - pw - 6 * S;
        int y = mc.getWindow().getScaledHeight() * S / 2 - h / 2;
        RenderUtil.roundedRect(ctx, x, y, pw, h, 7 * S, Theme.winBg());
        RenderUtil.roundedRect(ctx, x + pw - 3 * S, y, 3 * S, h, 2 * S, Theme.accent());
        int ty = y + pad;
        for (String l : lines) {
            RenderUtil.vanillaText(ctx, tr, l, x + pad, ty, Theme.txt(), S);
            ty += lineH;
        }
    }

    /** Dotted ring on the ground (radius blocks) around the player, projected to screen. */
    private static void drawGroundRing(DrawContext ctx, MinecraftClient mc, double radius, int color, int S) {
        net.minecraft.client.render.Camera cam = mc.gameRenderer.getCamera();
        net.minecraft.util.math.Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(-cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double cyaw = Math.cos(yaw), syaw = Math.sin(yaw), cpit = Math.cos(pitch), spit = Math.sin(pitch);
        double fx = syaw * cpit, fy = -spit, fz = cyaw * cpit;
        double rx = -fz, rz = fx;
        double rl = Math.sqrt(rx * rx + rz * rz); if (rl < 1e-6) { rx = 1; rz = 0; rl = 1; } rx /= rl; rz /= rl;
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double tanV = Math.tan(Math.toRadians((double) (int) mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / sh;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();

        int n = 48;
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n;
            double wx = px + radius * Math.cos(ang), wz = pz + radius * Math.sin(ang);
            double dx = wx - cp.x, dy = py - cp.y, dz = wz - cp.z;
            double depth = dx * fx + dy * fy + dz * fz;
            if (depth <= 0.1) continue;
            double sX = (0.5 + 0.5 * ((dx * rx + dz * rz) / depth) / (aspect * tanV)) * sw;
            double sY = (0.5 - 0.5 * ((dx * ux + dy * uy + dz * uz) / depth) / tanV) * sh;
            if (sX < 0 || sX > sw || sY < 0 || sY > sh) continue;
            int d = Math.max(1, S);
            ctx.fill((int) (sX * S) - d, (int) (sY * S) - d, (int) (sX * S) + d, (int) (sY * S) + d, color);
        }
    }

    /**
     * Crit Helper — draggable/resizable HUD element, defaulting just under the
     * crosshair: either a filling Bar or an accumulating 1..100 Percent readout.
     * Both re-colour red→yellow→green as the attack cooldown recovers, then glow
     * once a crit is ready.
     */
    private static void renderShiftIndicator(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        if (mc.player == null) return;
        boolean crit = ShiftIndicator.canCrit(mc);
        float cooldown = mc.player.getAttackCooldownProgress(1f); // 0.0 to 1.0
        boolean canHit = cooldown >= 0.99f;

        ShiftIndicator mod = (ShiftIndicator) LumeClient.MODULES.getByName("Crit Helper");
        int critCol = (mod != null && !mod.color.accent) ? (0xFF000000 | mod.color.rgb()) : 0xFFE8C15A;
        float sizeVal = mod != null ? (float) mod.size.value : 16f;
        boolean percentStyle = mod != null && mod.style.index == 1;
        int barCol = crit ? critCol : canHit ? 0xFF6FCF7F : cooldown >= 0.5f ? 0xFFE8C15A : 0xFFE05656;

        int cx = mc.getWindow().getScaledWidth() * S / 2;
        int cy = mc.getWindow().getScaledHeight() * S / 2 + 16 * S;   // default: just below the crosshair

        if (percentStyle) {
            int pct = Math.round(cooldown * 100);
            String label = pct + "%";
            float scale = sizeVal / 16f * 0.5f * S;
            int tw = RenderUtil.width(tr, label, scale);
            int th = Math.round(scale * 16);
            int x = cx - tw / 2, y = cy - th / 2;
            if (crit) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 150.0);
                RenderUtil.glow(ctx, x, y, tw, th, th / 2, critCol & 0xFFFFFF, Math.round(3 * pulse));
            }
            RenderUtil.textVCentered(ctx, tr, label, x, y, th, barCol, scale);
        } else {
            int barW = Math.round(sizeVal * 3 * S);
            int barH = Math.max(2, Math.round(3f * S));
            int x = cx - barW / 2, y = cy - barH / 2;
            if (crit) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(System.currentTimeMillis() / 150.0);
                RenderUtil.glow(ctx, x, y, barW, barH, barH / 2, critCol & 0xFFFFFF, Math.round(3 * pulse));
            }
            RenderUtil.roundedRect(ctx, x, y, barW, barH, barH / 2, Theme.pillOff());
            int filledW = Math.max(barH, Math.round(barW * cooldown));
            RenderUtil.roundedRect(ctx, x, y, filledW, barH, barH / 2, barCol);
        }
    }

    /** Compact RAM bar drawn in HUD (System Info module → "RAM Bar HUD" enabled). */
    private static void renderRamBar(DrawContext ctx, TextRenderer tr, int S) {
        long used = com.lume.client.module.modules.performance.JvmOptimizer.usedMb();
        long max = com.lume.client.module.modules.performance.JvmOptimizer.maxMb();
        if (max <= 0) return;
        float frac = (float) used / max;
        int barW = 90 * S, barH = 5 * S;
        int col = frac < 0.6f ? 0xFF6FCF7F : frac < 0.8f ? 0xFFE8C15A : 0xFFE05656;
        RenderUtil.roundedRect(ctx, 0, 0, barW, barH, barH / 2, Theme.pillOff());
        RenderUtil.roundedRect(ctx, 0, 0, Math.max(barH, Math.round(barW * frac)), barH, barH / 2, col);
        String label = "RAM " + used + "/" + max + " MB";
        RenderUtil.vanillaText(ctx, tr, label, 0, barH + 2 * S, Theme.txtDim(), S);
    }

    /** Right-side ArrayList of enabled modules, staircase-sorted by name width. */
    private static void renderArrayList(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        int sw = mc.getWindow().getScaledWidth() * S;
        float sc = 0.46f * S;
        List<Module> en = new ArrayList<>();
        for (Module mod : LumeClient.MODULES.getModules()) if (mod.isEnabled()) en.add(mod);
        en.sort((a, b) -> RenderUtil.width(tr, b.getName(), sc) - RenderUtil.width(tr, a.getName(), sc));

        com.lume.client.module.modules.visual.ModuleList mlMod =
                (com.lume.client.module.modules.visual.ModuleList) LumeClient.MODULES.getByName("Module List");
        int accentCol = (mlMod != null && !mlMod.color.accent) ? (0xFF000000 | mlMod.color.rgb()) : Theme.accent();

        int y = 6 * S, rowH = 13 * S, right = sw - 4 * S;
        for (Module mod : en) {
            String name = mod.getName();
            int w = RenderUtil.width(tr, name, sc);
            int x1 = right - w - 12 * S;
            RenderUtil.roundedRect(ctx, x1, y, w + 12 * S, rowH, 0, Theme.winBg());
            RenderUtil.roundedRect(ctx, right - 2 * S, y, 2 * S, rowH, 0, accentCol);
            RenderUtil.textVCentered(ctx, tr, name, x1 + 6 * S, y, rowH, Theme.txt(), sc);
            y += rowH + 1 * S;
        }
    }

    /** One info-panel line: horizontally centred in the panel, vertically centred in its row. */
    private static void centerLine(DrawContext ctx, TextRenderer tr, String s, int panelX, int panelW, int rowY, int rowH, int color, float scale) {
        RenderUtil.textCentered(ctx, tr, s, panelX, rowY, panelW, rowH, color, scale);
    }

    private static void renderPotions(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        if (mc.player == null || mc.player.getStatusEffects().isEmpty()) return;
        int sw = mc.getWindow().getScaledWidth() * S;
        int y = 6 * S;
        com.lume.client.module.modules.visual.PotionHud phMod =
                (com.lume.client.module.modules.visual.PotionHud) LumeClient.MODULES.getByName("Potion HUD");
        int accentRgb = (phMod != null && !phMod.color.accent) ? phMod.color.rgb() : Theme.accentRgb();
        int accentCol = 0xFF000000 | accentRgb;
        for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
            String name = inst.getEffectType().value().getName().getString();
            int dur = inst.getDuration();
            String time = dur >= 32767 ? "∞" : (dur / 20 / 60) + ":" + String.format("%02d", (dur / 20) % 60);
            String label = name + " " + (inst.getAmplifier() + 1) + "  " + time;
            int w = RenderUtil.width(tr, label, 0.46f * S);
            int ax = sw - w - 18 * S;
            RenderUtil.glow(ctx, ax - 8 * S, y, w + 16 * S, 16 * S, 7 * S, accentRgb, 2 * S);
            RenderUtil.roundedRect(ctx, ax - 8 * S, y, w + 16 * S, 16 * S, 7 * S, Theme.winBg());
            RenderUtil.roundedRect(ctx, sw - 8 * S, y + 3 * S, 3 * S, 10 * S, 1 * S, accentCol);
            RenderUtil.textVCentered(ctx, tr, label, ax, y, 16 * S, Theme.txt(), 0.46f * S);
            y += 20 * S;
        }
    }

    private static void renderKeystrokes(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        if (mc.options == null) return;
        int sh = mc.getWindow().getScaledHeight() * S;
        int box = 20 * S, gap = 3 * S;
        int bx = 12 * S;
        int by = sh - (box * 2 + gap + 10 * S) - 44 * S;

        boolean w = mc.options.forwardKey.isPressed();
        boolean a = mc.options.leftKey.isPressed();
        boolean s = mc.options.backKey.isPressed();
        boolean d = mc.options.rightKey.isPressed();
        boolean space = mc.options.jumpKey.isPressed();

        com.lume.client.module.modules.visual.Keystrokes ksMod =
                (com.lume.client.module.modules.visual.Keystrokes) LumeClient.MODULES.getByName("Keystrokes");
        int accentRgb = (ksMod != null && !ksMod.color.accent) ? ksMod.color.rgb() : Theme.accentRgb();
        int accentCol = 0xFF000000 | accentRgb;

        key(ctx, tr, bx + box + gap, by, box, box, "W", w, S, accentRgb, accentCol);
        key(ctx, tr, bx, by + box + gap, box, box, "A", a, S, accentRgb, accentCol);
        key(ctx, tr, bx + box + gap, by + box + gap, box, box, "S", s, S, accentRgb, accentCol);
        key(ctx, tr, bx + (box + gap) * 2, by + box + gap, box, box, "D", d, S, accentRgb, accentCol);
        int spaceW = box * 3 + gap * 2;
        int spaceY = by + (box + gap) * 2;
        if (space) RenderUtil.glow(ctx, bx, spaceY, spaceW, 9 * S, 5 * S, accentRgb, 2 * S);
        RenderUtil.roundedRect(ctx, bx, spaceY, spaceW, 9 * S, 5 * S, space ? accentCol : Theme.glassRow());
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean pressed, int S, int accentRgb, int accentCol) {
        if (pressed) RenderUtil.glow(ctx, x, y, w, h, 6 * S, accentRgb, 2 * S);
        RenderUtil.roundedRect(ctx, x, y, w, h, 6 * S, pressed ? accentCol : Theme.glassRow());
        RenderUtil.textCentered(ctx, tr, label, x, y, w, h, pressed ? 0xFF04342C : Theme.txt(), 0.5f * S);
    }

    // --- GUI-space item overlays ---

    private static void renderArmor(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        com.lume.client.module.modules.visual.ArmorHud am =
                (com.lume.client.module.modules.visual.ArmorHud) LumeClient.MODULES.getByName("Armor HUD");
        boolean vert = am != null && am.flipY.value;
        int accentCol = (am != null && !am.color.accent) ? (0xFF000000 | am.color.rgb()) : Theme.accent();
        int x = sw / 2 + 95;            // right of the hotbar
        int y = vert ? sh - 19 - 3 * 18 : sh - 19;   // vertical: 4 items stacked above the anchor
        for (int i = 3; i >= 0; i--) {  // helmet -> boots
            ItemStack st = mc.player.getInventory().armor.get(i);
            if (st.isEmpty()) { if (vert) y += 18; else x += 18; continue; }
            ctx.drawItem(st, x, y);
            ctx.drawStackOverlay(tr, st, x, y);
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                int pct = (st.getMaxDamage() - st.getDamage()) * 100 / st.getMaxDamage();
                int col = pct > 50 ? accentCol : pct > 20 ? 0xFFE8C15A : 0xFFE05656;
                String s = String.valueOf(pct);   // number only, no "%"
                int tw = RenderUtil.width(tr, s, 0.4f);
                RenderUtil.text(ctx, tr, s, x + 8 - tw / 2, y - 7, col, true, 0.4f);
            }
            if (vert) y += 18; else x += 18;
        }
    }

    private static void renderInventory(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int cell = 18, cols = 9, rows = 3;
        int gw = cols * cell;
        int x0 = sw / 2 - gw / 2;
        int y0 = sh - 22 - rows * cell - 6;
        // faint glass backing
        RenderUtil.roundedRect(ctx, x0 - 4, y0 - 4, gw + 8, rows * cell + 8, 6, 0x40101018);
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack st = mc.player.getInventory().main.get(slot);
            if (st.isEmpty()) continue;
            int idx = slot - 9;
            int ix = x0 + (idx % cols) * cell + 1;
            int iy = y0 + (idx / cols) * cell + 1;
            ctx.drawItem(st, ix, iy);
            ctx.drawStackOverlay(tr, st, ix, iy);
        }
    }

    private static void renderTotem(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
        if (mc.player == null) return;
        int count = 0;
        for (ItemStack st : mc.player.getInventory().main) if (st.isOf(Items.TOTEM_OF_UNDYING)) count += st.getCount();
        for (ItemStack st : mc.player.getInventory().offHand) if (st.isOf(Items.TOTEM_OF_UNDYING)) count += st.getCount();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x = sw / 2 - 134, y = sh - 19;   // further left so it never overlaps the hotbar
        ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x, y);
        RenderUtil.text(ctx, tr, "x" + count, x + 19, y + 5, count > 0 ? Theme.accent() : 0xFFE05656, true, 0.42f);
    }
}

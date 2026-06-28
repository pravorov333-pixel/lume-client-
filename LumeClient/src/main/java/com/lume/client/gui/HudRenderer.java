package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.fthw.EventManager;
import com.lume.client.fthw.ItemRule;
import com.lume.client.fthw.ItemRules;
import com.lume.client.fthw.ServerType;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomCrosshair;
import com.lume.client.module.modules.fthw.ServerHelper;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.module.modules.visual.BlockInfo;
import com.lume.client.module.modules.visual.TargetHud;
import com.lume.client.module.setting.Setting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.util.ClickTracker;
import com.lume.client.util.SpeedTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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

        LivingEntity target = on("Target HUD") ? findTarget(mc) : null;

        // --- GUI-space item overlays (offset + scaled around their anchor) ---
        if (on("Inventory HUD"))
            transform(ctx, "Inventory HUD", sizeOf("Inventory HUD"), sw / 2.0, sh, 1, () -> renderInventory(ctx, mc, tr));
        if (on("Armor HUD"))
            transform(ctx, "Armor HUD", sizeOf("Armor HUD"), sw / 2.0 + 95, sh, 1, () -> renderArmor(ctx, mc, tr));
        if (on("Totem Counter"))
            transform(ctx, "Totem Counter", sizeOf("Totem Counter"), sw / 2.0 - 132, sh, 1, () -> renderTotem(ctx, mc, tr));

        // --- Native-resolution text / panels ---
        var m = ctx.getMatrices();
        m.push();
        m.scale(1f / S, 1f / S, 1f);
        int nsw = sw * S, nsh = sh * S;
        if (on("HUD")) transform(ctx, "HUD", sizeOf("HUD"), 6 * S, 6 * S, S, () -> renderInfo(ctx, mc, tr, S));
        if (on("Potion HUD")) transform(ctx, "Potion HUD", sizeOf("Potion HUD"), nsw, 6 * S, S, () -> renderPotions(ctx, mc, tr, S));
        if (on("Keystrokes")) transform(ctx, "Keystrokes", sizeOf("Keystrokes"), 12 * S, nsh, S, () -> renderKeystrokes(ctx, mc, tr, S));
        if (on("Custom Crosshair")) renderCrosshair(ctx, mc, S);
        if (on("Waypoints")) renderWaypoints(ctx, mc, tr, S);
        if (on("Server Helper")) {
            ServerHelper shm = (ServerHelper) LumeClient.MODULES.getByName("Server Helper");
            if (shm == null || shm.eventsHud.value) renderServerHelper(ctx, mc, tr, S);
            if (shm == null || shm.itemHelper.value) renderItemHelper(ctx, mc, tr, S);
            if (shm == null || shm.effects.value) renderEffects(ctx, mc, tr, S);
        }
        Notifications.render(ctx, tr, S, nsw);
        if (on("Module List")) transform(ctx, "Module List", sizeOf("Module List"), nsw, 6 * S, S, () -> renderArrayList(ctx, mc, tr, S));
        if (target != null) {
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
        if (target != null) renderTargetHead(ctx, mc, target, S, sizeOf("Target HUD"));
        if (blockHit != null && !biSimple)
            renderBlockIcon(ctx, mc, blockHit, S, sizeOf("Block Info"));
    }

    /** The living entity we can hit: vanilla targeted entity (reach + wall checked), not invisible. */
    private static LivingEntity findTarget(MinecraftClient mc) {
        if (mc.player == null) return null;
        Entity e = mc.targetedEntity;
        if (e instanceof LivingEntity le && le.isAlive() && !le.isSpectator()
                && !le.isInvisible() && le != mc.player) {
            return le;
        }
        return null;
    }

    /** Geometry for the target panel (native px). Indices documented inline. */
    private static int[] targetLayout(MinecraftClient mc, TextRenderer tr, LivingEntity t, int S, boolean showHead) {
        int pad = 8 * S, gap = 5 * S;
        int nameH = 11 * S, barH = 6 * S, hpH = 9 * S, barW = 120 * S;
        float nameScale = 0.5f * S;
        int textBlockH = nameH + gap + barH + gap + hpH;
        int headSize = showHead ? textBlockH : 0;
        int leftPad = showHead ? headSize + 8 * S : 0;
        int textBlockW = Math.max(barW, RenderUtil.width(tr, t.getDisplayName().getString(), nameScale));
        int pw = pad + leftPad + textBlockW + pad;
        int ph = pad * 2 + textBlockH;
        int sw = mc.getWindow().getScaledWidth() * S;
        int x = sw / 2 - pw / 2, y = 10 * S;
        int textX = x + pad + leftPad;
        int barY = y + pad + nameH + gap;
        return new int[]{
                x, y, pw, ph,                    // 0..3 panel
                x + pad, y + pad, headSize,      // 4..6 head x,y,size
                textX, barY, barW,               // 7..9 textX, barY, barW
                y + pad, barY + barH + gap,      // 10 nameY, 11 hpY
                nameH, hpH, barH                 // 12 nameH, 13 hpH, 14 barH
        };
    }

    // Smooth health animation state (one target shown at a time).
    private static int hpId = -1;
    private static float hpDisp = 0f, hpGhost = 0f;
    private static long hpTime = 0L;

    /** Glass panel (top-centre): name + animated HP bar. Head drawn separately. */
    private static void renderTargetPanel(DrawContext ctx, MinecraftClient mc, TextRenderer tr, LivingEntity t, int S) {
        TargetHud mod = (TargetHud) LumeClient.MODULES.getByName("Target HUD");
        boolean showHead = mod == null || mod.head.value;
        boolean showBar = mod == null || mod.healthBar.value;
        boolean animate = mod == null || mod.animate.value;

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

        int[] L = targetLayout(mc, tr, t, S, showHead);
        int x = L[0], y = L[1], pw = L[2], ph = L[3];
        float nameScale = 0.5f * S, hpScale = 0.42f * S;

        RenderUtil.glow(ctx, x, y, pw, ph, 9 * S, Theme.accentRgb(), 3);
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

    /** 3D head/model of the target, matched to the (possibly scaled) panel's left box. */
    private static void renderTargetHead(DrawContext ctx, MinecraftClient mc, LivingEntity t, int S, float es) {
        TargetHud mod = (TargetHud) LumeClient.MODULES.getByName("Target HUD");
        if (mod != null && !mod.head.value) return;
        int[] L = targetLayout(mc, mc.textRenderer, t, S, true);
        int hs = L[6];
        if (hs <= 0) return;
        // mirror the panel's scale-around-anchor so the head lines up
        double ax = mc.getWindow().getScaledWidth() * S / 2.0, ay = 10 * S;
        double hx = ax + (L[4] - ax) * es, hy = ay + (L[5] - ay) * es;
        double hsz = hs * es;
        int[] off = HudLayout.get("Target HUD");
        int x1 = (int) (hx / S) + off[0], y1 = (int) (hy / S) + off[1];
        int x2 = (int) ((hx + hsz) / S) + off[0], y2 = (int) ((hy + hsz) / S) + off[1];
        int entSize = Math.max(8, Math.round((y2 - y1) * 0.5f));
        float cxp = (x1 + x2) / 2f, cyp = (y1 + y2) / 2f;
        try {
            InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, entSize, 0.0625f, cxp, cyp, t);
        } catch (Throwable ignored) {
            // some modded/edge entities can't render in a GUI — skip the head, keep the panel
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

    private static void renderInfo(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        int x = 6 * S, y = 6 * S, pad = 6 * S;
        int pw = 134 * S;             // fixed width — panel never jumps from big numbers
        int lineH = 11 * S;
        boolean coords = on("Coords") && mc.player != null;
        boolean ping = on("Ping") && mc.player != null && mc.getNetworkHandler() != null;
        boolean day = on("Day Counter") && mc.world != null;
        boolean cps = on("CPS");
        boolean speed = on("Speed") && mc.player != null;
        boolean clock = on("Clock") && mc.world != null;
        int extra = (coords ? 1 : 0) + (ping ? 1 : 0) + (day ? 1 : 0) + (cps ? 1 : 0) + (speed ? 1 : 0) + (clock ? 1 : 0);
        int n = 2 + extra;
        int h = n * lineH + pad * 2 - 2 * S;

        RenderUtil.glow(ctx, x, y, pw, h, 7 * S, Theme.accentRgb(), 3 * S);
        RenderUtil.roundedRect(ctx, x, y, pw, h, 7 * S, Theme.winBg());
        RenderUtil.roundedRect(ctx, x, y, 3 * S, h, 2 * S, Theme.accent());

        int ty = y + pad;
        centerLine(ctx, tr, "Lume Client", x, pw, ty, lineH, Theme.accent(), 0.5f * S); ty += lineH;
        centerLine(ctx, tr, "FPS " + mc.getCurrentFps(), x, pw, ty, lineH, Theme.txt(), 0.46f * S); ty += lineH;
        if (coords) {
            String c = String.format("XYZ %.0f  %.0f  %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            centerLine(ctx, tr, c, x, pw, ty, lineH, Theme.txt(), 0.4f * S); ty += lineH;
        }
        if (ping) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) { centerLine(ctx, tr, com.lume.client.Lang.t("Ping") + " " + e.getLatency() + com.lume.client.Lang.t("ms"), x, pw, ty, lineH, Theme.txt(), 0.46f * S); }
            ty += lineH;
        }
        if (day) {
            centerLine(ctx, tr, com.lume.client.Lang.t("Day") + " " + (mc.world.getTimeOfDay() / 24000L), x, pw, ty, lineH, Theme.txt(), 0.46f * S); ty += lineH;
        }
        if (cps) {
            centerLine(ctx, tr, "CPS " + ClickTracker.left() + " | " + ClickTracker.right(), x, pw, ty, lineH, Theme.txt(), 0.46f * S); ty += lineH;
        }
        if (speed) {
            centerLine(ctx, tr, com.lume.client.Lang.t("Speed") + String.format(" %.1f ", SpeedTracker.get()) + com.lume.client.Lang.t("b/s"), x, pw, ty, lineH, Theme.txt(), 0.46f * S); ty += lineH;
        }
        if (clock) {
            long tod = mc.world.getTimeOfDay() % 24000L;
            if (tod < 0) tod += 24000L;
            int hh = (int) ((tod / 1000L + 6L) % 24L);     // tick 0 = 06:00
            int mm = (int) ((tod % 1000L) * 60L / 1000L);
            centerLine(ctx, tr, String.format("%02d:%02d", hh, mm), x, pw, ty, lineH, Theme.txt(), 0.46f * S); ty += lineH;
        }
    }

    /** Draws saved waypoints as 2D markers (name + distance) + edge arrows when off-screen. */
    private static void renderWaypoints(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        if (mc.world == null || mc.player == null || Waypoints.list.isEmpty()) return;
        Waypoints mod = (Waypoints) LumeClient.MODULES.getByName("Waypoints");
        boolean arrows = mod == null || mod.arrows.value;
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

        for (Waypoints.WP w : Waypoints.list) {
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
                String label = w.name + "  " + dist + "m";
                int nx = (int) (scrX * S), ny = (int) (scrY * S);
                int tw = RenderUtil.width(tr, label, 0.46f * S);
                RenderUtil.roundedRect(ctx, nx - 2 * S, ny - 2 * S, 4 * S, 4 * S, 2 * S, w.color);
                RenderUtil.roundedRect(ctx, nx - tw / 2 - 4 * S, ny - 17 * S, tw + 8 * S, 13 * S, 3 * S, Theme.winBg());
                RenderUtil.textVCentered(ctx, tr, label, nx - tw / 2, ny - 17 * S, 13 * S, w.color, 0.46f * S);
            } else if (arrows) {
                double dirX = front ? (scrX - cx) : rc;
                double dirY = front ? (scrY - cy) : -uc;
                drawEdgeArrow(ctx, cx, cy, sw, sh, dirX, dirY, w.color, S);
            }
        }
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

    /** FT/HW helper HUD: detected server + active events with countdown (vanilla font → Cyrillic). */
    private static void renderServerHelper(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        ServerHelper mod = (ServerHelper) LumeClient.MODULES.getByName("Server Helper");
        boolean showServer = mod == null || mod.showServer.value;
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (showServer) lines.add("Сервер: " + ServerType.current().display());
        for (EventManager.Active a : EventManager.active) lines.add(a.rule.name + " — " + a.secondsLeft() + "с");
        if (lines.isEmpty()) return;

        int x = 6 * S, y = 150 * S, pad = 6 * S, lineH = 11 * S;
        int pw = 0;
        for (String l : lines) pw = Math.max(pw, RenderUtil.vanillaWidth(tr, l, S));
        pw += pad * 2 + 4 * S;
        int h = lines.size() * lineH + pad * 2;
        RenderUtil.roundedRect(ctx, x, y, pw, h, 7 * S, Theme.winBg());
        RenderUtil.roundedRect(ctx, x, y, 3 * S, h, 2 * S, Theme.accent());
        int ty = y + pad;
        for (String l : lines) {
            RenderUtil.vanillaText(ctx, tr, l, x + pad + 3 * S, ty, Theme.txt(), S);
            ty += lineH;
        }
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

    /** Right-side ArrayList of enabled modules, staircase-sorted by name width. */
    private static void renderArrayList(DrawContext ctx, MinecraftClient mc, TextRenderer tr, int S) {
        int sw = mc.getWindow().getScaledWidth() * S;
        float sc = 0.46f * S;
        List<Module> en = new ArrayList<>();
        for (Module mod : LumeClient.MODULES.getModules()) if (mod.isEnabled()) en.add(mod);
        en.sort((a, b) -> RenderUtil.width(tr, b.getName(), sc) - RenderUtil.width(tr, a.getName(), sc));

        int y = 6 * S, rowH = 13 * S, right = sw - 4 * S;
        for (Module mod : en) {
            String name = mod.getName();
            int w = RenderUtil.width(tr, name, sc);
            int x1 = right - w - 12 * S;
            RenderUtil.roundedRect(ctx, x1, y, w + 12 * S, rowH, 0, Theme.winBg());
            RenderUtil.roundedRect(ctx, right - 2 * S, y, 2 * S, rowH, 0, Theme.accent());
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
        for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
            String name = inst.getEffectType().value().getName().getString();
            int dur = inst.getDuration();
            String time = dur >= 32767 ? "∞" : (dur / 20 / 60) + ":" + String.format("%02d", (dur / 20) % 60);
            String label = name + " " + (inst.getAmplifier() + 1) + "  " + time;
            int w = RenderUtil.width(tr, label, 0.46f * S);
            int ax = sw - w - 18 * S;
            RenderUtil.glow(ctx, ax - 8 * S, y, w + 16 * S, 16 * S, 7 * S, Theme.accentRgb(), 2 * S);
            RenderUtil.roundedRect(ctx, ax - 8 * S, y, w + 16 * S, 16 * S, 7 * S, Theme.winBg());
            RenderUtil.roundedRect(ctx, sw - 8 * S, y + 3 * S, 3 * S, 10 * S, 1 * S, Theme.accent());
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

        key(ctx, tr, bx + box + gap, by, box, box, "W", w, S);
        key(ctx, tr, bx, by + box + gap, box, box, "A", a, S);
        key(ctx, tr, bx + box + gap, by + box + gap, box, box, "S", s, S);
        key(ctx, tr, bx + (box + gap) * 2, by + box + gap, box, box, "D", d, S);
        int spaceW = box * 3 + gap * 2;
        int spaceY = by + (box + gap) * 2;
        if (space) RenderUtil.glow(ctx, bx, spaceY, spaceW, 9 * S, 5 * S, Theme.accentRgb(), 2 * S);
        RenderUtil.roundedRect(ctx, bx, spaceY, spaceW, 9 * S, 5 * S, space ? Theme.accent() : Theme.glassRow());
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean pressed, int S) {
        if (pressed) RenderUtil.glow(ctx, x, y, w, h, 6 * S, Theme.accentRgb(), 2 * S);
        RenderUtil.roundedRect(ctx, x, y, w, h, 6 * S, pressed ? Theme.accent() : Theme.glassRow());
        RenderUtil.textCentered(ctx, tr, label, x, y, w, h, pressed ? 0xFF04342C : Theme.txt(), 0.5f * S);
    }

    // --- GUI-space item overlays ---

    private static void renderArmor(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
        if (mc.player == null) return;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x = sw / 2 + 95;            // right of the hotbar
        int y = sh - 19;
        for (int i = 3; i >= 0; i--) {  // helmet -> boots
            ItemStack st = mc.player.getInventory().armor.get(i);
            if (st.isEmpty()) continue;
            ctx.drawItem(st, x, y);
            ctx.drawStackOverlay(tr, st, x, y);
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                int pct = (st.getMaxDamage() - st.getDamage()) * 100 / st.getMaxDamage();
                int col = pct > 50 ? Theme.accent() : pct > 20 ? 0xFFE8C15A : 0xFFE05656;
                String s = String.valueOf(pct);   // number only, no "%"
                int tw = RenderUtil.width(tr, s, 0.4f);
                RenderUtil.text(ctx, tr, s, x + 8 - tw / 2, y - 7, col, true, 0.4f);
            }
            x += 18;
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

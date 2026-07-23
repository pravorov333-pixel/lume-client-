package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lume.client.nanovg.NanoVgRenderer.*;

/**
 * Small overlay on top of the otherwise fully vanilla title screen: 4 corner buttons — Account
 * Manager (top-left), Settings (top-right), Fast Connect (bottom-left), Friends (bottom-right).
 * Vanilla renders its own background, panorama, logo, splash text, and Singleplayer/Multiplayer/
 * Options/Language/Quit buttons completely untouched (see {@code TitleScreenMixin}, which no
 * longer strips vanilla's own widgets at all) — this class only draws on top of them.
 *
 * <p>The 4 corner buttons themselves are baked-image DrawContext blits (same {@code MenuAssets}
 * glass-icon look the very first version of this menu used — PILL/ACCOUNT/IC_GEAR — restored
 * after a NanoVG-pill redesign read as noticeably different/plainer). Panel CONTENT (the
 * dropdowns each button opens) stays NanoVG, same split the original file always used.
 *
 * <p>Text is always drawn via {@code ALIGN_CENTER_MIDDLE}/{@code ALIGN_MIDDLE} anchored at the
 * box's own centre/left-middle point — NanoVG's {@code ALIGN_LEFT} anchors to the TOP of the
 * glyph, not the middle, so mixing it with a hand-tuned offset drifts text off-centre.
 */
public final class LumeTitleMenu {
    private LumeTitleMenu() {}

    private static long lastFrame = System.currentTimeMillis();
    private static final Map<String, float[]> anim = new HashMap<>(); // {hover, press}
    private static final List<Object[]> hits = new ArrayList<>();     // {id, x, y, w, h, ...}

    /** Which corner panel is open, or null. Only Settings still uses this inline dropdown —
     *  Account/Fast Connect/Friends all open full screens now (see the click cases below). */
    private static String openPanel = null;

    private static float[] a(String id) { return anim.computeIfAbsent(id, k -> new float[2]); }

    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * Math.min(1f, rate * dt);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ---------------------------------------------------------------------
    // Render

    private static final int TH = 28;          // Settings stays this square
    private static final int PILL_W = 108;      // Fast Connect/Friends pill width (sharp corners now, see MenuAssets)
    private static final int ACCT_W = 132;       // Account chip width (head + 2 lines of text)
    private static final int MARGIN = 10;

    public static void render(DrawContext ctx, TitleScreen screen, int mouseX, int mouseY) {
        NanoVgRenderer.ensureInit();
        if (!CustomMenu.active()) return;
        int width = screen.width, height = screen.height;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        boolean showAcct = CustomMenu.showAccount();
        int gearX = width - MARGIN - TH, gearY = MARGIN;
        int fcX = MARGIN, fcY = height - MARGIN - TH;
        int frX = width - MARGIN - PILL_W, frY = height - MARGIN - TH;

        // Baked glass icon buttons — DrawContext, matching the original menu's look (see class
        // doc). Rendered BEFORE the NanoVG frame below, same order the original file always used.
        if (showAcct) renderAccountButton(ctx, MARGIN, MARGIN, mouseX, mouseY, dt);
        if (!bakedIcon(ctx, "settings", MenuAssets.IC_GEAR, gearX, gearY, TH, mouseX, mouseY, dt, "settings".equals(openPanel))) {
            RenderUtil.roundedRect(ctx, gearX, gearY, TH, TH, (int) (TH * 0.28f), Theme.glassRow());
        }
        hits.add(new Object[]{"settings", gearX, gearY, TH, TH});
        if (CustomMenu.showFastConnect()) {
            pillButton(ctx, "fastconnect", "Fast Connect", fcX, fcY, mouseX, mouseY, dt);
            hits.add(new Object[]{"fastconnect", fcX, fcY, PILL_W, TH});
        }
        if (CustomMenu.showFriends()) {
            pillButton(ctx, "friends", "Friends", frX, frY, mouseX, mouseY, dt);
            hits.add(new Object[]{"friends", frX, frY, PILL_W, TH});
        }

        if (!NanoVgRenderer.ready()) return;
        try {
            float S = NanoVgRenderer.pxScale();
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                if ("settings".equals(openPanel)) renderSettingsPanel(vg, mouseX, mouseY, gearX - (190 - TH), gearY + TH + 6, dt, S);
            });
        } catch (Throwable t) {
            System.out.println("[Lume] LumeTitleMenu render failed: " + t);
        }
    }

    /** Wide glass chip: baked {@code ACCOUNT} frame + real player head + nickname + license
     *  sub-label — the original Custom Menu look, sharp corners now (see {@code tools/gen_menu.py}
     *  — the baked PNG itself carries the rounding, a Java-side radius param never affected it).
     *  Click opens {@link AccountManagerScreen} (a real window with every saved nickname + full
     *  details), not an inline dropdown. */
    private static void renderAccountButton(DrawContext ctx, int x, int y, int mouseX, int mouseY, float dt) {
        boolean hov = inside(mouseX, mouseY, x, y, ACCT_W, TH);
        float[] st = a("account");
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        bakedFrame(ctx, MenuAssets.ACCOUNT, x, y, ACCT_W, TH, 0, st[0]);

        MinecraftClient mc = MinecraftClient.getInstance();
        var session = mc.getSession();
        net.minecraft.client.util.SkinTextures skin = mc.player != null
                ? mc.player.getSkinTextures()
                : net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(session != null ? session.getUuidOrNull() : null);
        int hs = TH - 8;
        net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx, skin, x + 4, y + 4, hs);

        String nick = session != null ? session.getUsername() : "Player";
        String subLabel = switch (com.lume.client.social.License.status()) {
            case "valid" -> { long d = com.lume.client.social.License.daysRemaining(); yield d >= 0 ? (d + "d left") : "Active"; }
            case "checking" -> "…";
            case "unreachable" -> "server offline";
            case "invalid" -> "invalid key";
            default -> "no key";
        };
        int textX = x + TH + 4;
        var tr = mc.textRenderer;
        RenderUtil.textVCentered(ctx, tr, nick, textX, y + TH / 2 - 7, 7, Theme.txt(), 0.43f);
        RenderUtil.textVCentered(ctx, tr, subLabel, textX, y + TH / 2, 7, Theme.txtDim(), 0.33f);
        hits.add(new Object[]{"account", x, y, ACCT_W, TH});
    }

    /** Wide glass pill with a full text label (Fast Connect / Friends) — same {@code PILL} asset
     *  the original toggle pill used, sharp corners now (see {@link #renderAccountButton}'s doc). */
    private static void pillButton(DrawContext ctx, String id, String label, int x, int y, int mouseX, int mouseY, float dt) {
        boolean open = id.equals(openPanel);
        boolean hov = inside(mouseX, mouseY, x, y, PILL_W, TH) || open;
        float[] st = a(id);
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        bakedFrame(ctx, MenuAssets.PILL, x, y, PILL_W, TH, 0, st[0]);
        RenderUtil.textCentered(ctx, MinecraftClient.getInstance().textRenderer, label, x, y, PILL_W, TH,
                open ? Theme.accent() : Theme.txt(), 0.5f);
    }

    /** Baked frame + hover lighten for a rectangular glass surface (pill/account). Draws the
     *  frame image if present, else a plain glass rect at the given radius (0 = sharp). Live
     *  content is drawn by the caller on top either way. */
    private static void bakedFrame(DrawContext ctx, String asset, int x, int y, int w, int h, int radius, float hov) {
        boolean img = MenuAssets.blit(ctx, asset, x, y, w, h);
        if (img) {
            if (hov > 0.02f) RenderUtil.roundedRect(ctx, x, y, w, h, radius, (Math.round(hov * 44) << 24) | 0xFFFFFF);
        } else {
            RenderUtil.roundedRect(ctx, x, y, w, h, radius, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), hov));
        }
    }

    /** Blit a baked glass icon-button image (current theme×style set) + soft hover glow/lighten.
     *  {@code forceOn} keeps it lit while its panel is open. @return false if the image is
     *  missing (caller draws its own fallback). */
    private static boolean bakedIcon(DrawContext ctx, String id, String asset, int x, int y, int size,
                                     int mouseX, int mouseY, float dt, boolean forceOn) {
        if (!MenuAssets.blit(ctx, asset, x, y, size, size)) return false;
        float[] st = a(id);
        boolean hov = inside(mouseX, mouseY, x, y, size, size) || forceOn;
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        if (st[0] > 0.02f) {
            MenuAssets.blitGlow(ctx, x, y, size, size, 8, (Math.round(st[0] * 90) << 24) | Theme.accentRgb());
            RenderUtil.roundedRect(ctx, x, y, size, size, Math.round(size * 0.3f), (Math.round(st[0] * 40) << 24) | 0xFFFFFF);
        }
        return true;
    }

    // ---------------------------------------------------------------------
    // Settings panel — Wallpaper / Background Dim / Theme / show-hide toggles. Everything the
    // old "Menu settings" gear dropdown had, minus the background-style cycling (Particles/
    // Geometry) which no longer exists — vanilla owns the background again unless a wallpaper is
    // explicitly turned on. Background Dim applies either way (vanilla panorama or wallpaper).

    private static void renderSettingsPanel(long vg, int mouseX, int mouseY, int anchorX, int anchorY, float dt, float S) {
        record Row(String label, String kind, java.util.function.Supplier<String> value) {}
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Wallpaper", "wallpaperToggle", () -> isWallpaperOn() ? "On" : "Off"));
        if (isWallpaperOn()) {
            rows.add(new Row("Image", "wallpaperCycle", () -> {
                String w = CustomMenu.wallpaper();
                return (w == null || w.isBlank()) ? "None" : w;
            }));
            rows.add(new Row("Open Folder", "wallpaperOpen", () -> ""));
        }
        rows.add(new Row("Background Dim", "bgDimCycle", () -> CustomMenu.bgDim() + "%"));
        rows.add(new Row("Theme", "themeToggle", () -> Theme.isDark() ? "Dark" : "Light"));
        rows.add(new Row("Show Account", "accountToggle", () -> CustomMenu.showAccount() ? "On" : "Off"));
        rows.add(new Row("Show Fast Connect", "fastConnectToggle", () -> CustomMenu.showFastConnect() ? "On" : "Off"));
        rows.add(new Row("Show Friends", "friendsToggle", () -> CustomMenu.showFriends() ? "On" : "Off"));

        int pw = 190, srowH = 18, headerH = 22, rowStep = 24;
        int ph = headerH + rows.size() * rowStep + 6;
        int px = anchorX, py = anchorY;
        int rowW = 84, rowX = px + pw - 12 - rowW;

        panelChrome(vg, px, py, pw, ph, S, "Menu settings");
        int ry = py + headerH;
        for (Row r : rows) {
            text(vg, (px + 12) * S, (ry + srowH / 2f) * S, 9 * S, Theme.txt(), ALIGN_MIDDLE, r.label());
            cyclerNvg(vg, rowX, ry, rowW, srowH, r.value().get(), S);
            hits.add(new Object[]{r.kind(), rowX, ry, rowW, srowH});
            ry += rowStep;
        }
        hits.add(new Object[]{"panel", px, py, pw, ph});
    }

    private static boolean isWallpaperOn() {
        var m = com.lume.client.LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c && c.wallpaperOn.value;
    }

    private static void cyclerNvg(long vg, int x, int y, int w, int h, String value, float S) {
        roundedRect(vg, x * S, y * S, w * S, h * S, 6 * S, Theme.glassRow());
        text(vg, (x + 8) * S, (y + h / 2f) * S, 9 * S, Theme.txtDim(), ALIGN_MIDDLE, "‹");
        text(vg, (x + w - 14) * S, (y + h / 2f) * S, 9 * S, Theme.txtDim(), ALIGN_MIDDLE, "›");
        text(vg, (x + w / 2f) * S, (y + h / 2f) * S, 9 * S, Theme.txt(), ALIGN_CENTER_MIDDLE, value);
    }

    /** Shared drop-shadow + glass window + title, used by every panel below. */
    private static void panelChrome(long vg, int px, int py, int pw, int ph, float S, String title) {
        shadow(vg, px * S, py * S, pw * S, ph * S, 10 * S, 14 * S, 0x55000000);
        roundedRect(vg, px * S, py * S, pw * S, ph * S, 10 * S, Theme.winBg());
        strokeRoundedRect(vg, (px + 0.5f) * S, (py + 0.5f) * S, pw * S - S, ph * S - S, 10 * S, S, Theme.rim());
        if (title != null) text(vg, (px + 12) * S, (py + 14) * S, 9.5f * S, Theme.txtDim(), ALIGN_MIDDLE, title);
    }

    // ---------------------------------------------------------------------
    // Input

    public static boolean mouseClicked(TitleScreen screen, double mouseX, double mouseY) {
        if (!CustomMenu.active()) return false;
        for (Object[] h : hits) {
            String kind = (String) h[0];
            int x = (int) h[1], y = (int) h[2], w = (int) h[3], hh = (int) h[4];
            if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + hh) continue;
            a(kind)[1] = 1f;
            MinecraftClient mc = MinecraftClient.getInstance();
            switch (kind) {
                case "account" -> { if (mc != null) mc.setScreen(new AccountManagerScreen(screen)); }
                case "fastconnect" -> { if (mc != null) mc.setScreen(new FastConnectScreen(screen)); }
                case "friends" -> { if (mc != null) mc.setScreen(new FriendsScreen(screen)); }
                case "settings" -> togglePanel("settings");
                case "panel" -> { /* absorb click, keep panel open */ }
                case "wallpaperToggle" -> { CustomMenu.toggleWallpaperOn(); Config.save(); }
                case "wallpaperCycle" -> { CustomMenu.cycleWallpaper(mouseX > x + w / 2.0 ? 1 : -1); Config.save(); }
                case "wallpaperOpen" -> Wallpapers.openFolder();
                case "bgDimCycle" -> { CustomMenu.cycleBgDim(mouseX > x + w / 2.0 ? 1 : -1); Config.save(); }
                case "themeToggle" -> { Theme.toggle(); com.lume.client.gui.ThemeSync.save(); }
                case "accountToggle" -> { CustomMenu.toggleShowAccount(); Config.save(); }
                case "fastConnectToggle" -> { CustomMenu.toggleShowFastConnect(); Config.save(); }
                case "friendsToggle" -> { CustomMenu.toggleShowFriends(); Config.save(); }
                default -> { }
            }
            return true;
        }
        if (openPanel != null) openPanel = null;
        return false;
    }

    private static void togglePanel(String id) {
        openPanel = id.equals(openPanel) ? null : id;
    }

    /** Settings' cycler rows never take text input — nothing left to type into on this screen
     *  any more (Fast Connect/Friends/Account's own text fields all moved into their own
     *  screens), but the signature stays for {@code ScreenMixin}'s existing call site. */
    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}

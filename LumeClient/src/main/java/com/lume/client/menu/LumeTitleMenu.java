package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Overlay on top of the otherwise fully vanilla title screen: Account Manager / Theme / Colors /
 * Settings (top), Fast Connect / Friends (bottom corners), Options+Language / Quit (bottom
 * centre). Every button's BACKGROUND renders live through {@link RenderUtil#premiumBg} (raw-GL
 * SDF fill + contour-hugging glow + a slight lift on hover, see {@code SdfRenderer}) instead of
 * a baked PNG — crisp at any GUI scale, glow follows the button's actual rounded shape rather
 * than a rectangular halo. Icon glyphs (gear/globe/sun-moon/dots/×) are still small baked PNGs
 * ({@code MenuAssets}, now glyph-only/transparent-bg — see {@code tools/gen_menu.py}) blitted on
 * top of the live SDF background. No NanoVG anywhere in this class.
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
    private static final int PILL_W = 108;      // Fast Connect/Friends pill width
    private static final int ACCT_W = 132;       // Account chip width (head + 2 lines of text)
    private static final int MARGIN = 10;
    private static final int GAP = 6;           // spacing between corner-cluster icons
    private static final int RADIUS = 7;        // baked-in radius of PILL/ACCOUNT assets (see gen_menu.py)

    public static void render(DrawContext ctx, TitleScreen screen, int mouseX, int mouseY) {
        if (!CustomMenu.active()) return;
        int width = screen.width, height = screen.height;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        boolean showAcct = CustomMenu.showAccount();
        // Top-right cluster — Theme / Colors / Settings, per the reference "Menu widgets" row
        // (was Settings alone). Laid out right-to-left from the margin so Settings keeps its
        // exact old position (gearX unchanged) and the two new icons sit to its left.
        int gearX = width - MARGIN - TH, gearY = MARGIN;
        int colorsX = gearX - GAP - TH;
        int themeX = colorsX - GAP - TH;
        int fcX = MARGIN, fcY = height - MARGIN - TH;
        int frX = width - MARGIN - PILL_W, frY = height - MARGIN - TH;

        // Live SDF backgrounds + baked glyphs on top (see class doc).
        if (showAcct) renderAccountButton(ctx, MARGIN, MARGIN, mouseX, mouseY, dt);
        iconButton(ctx, "theme", MenuAssets.IC_THEME, themeX, gearY, TH, mouseX, mouseY, dt, false);
        hits.add(new Object[]{"theme", themeX, gearY, TH, TH});
        iconButton(ctx, "colors", MenuAssets.IC_COLORS, colorsX, gearY, TH, mouseX, mouseY, dt, false);
        hits.add(new Object[]{"colors", colorsX, gearY, TH, TH});
        iconButton(ctx, "settings", MenuAssets.IC_GEAR, gearX, gearY, TH, mouseX, mouseY, dt, "settings".equals(openPanel));
        hits.add(new Object[]{"settings", gearX, gearY, TH, TH});
        if (CustomMenu.showFastConnect()) {
            pillButton(ctx, "fastconnect", "Fast Connect", fcX, fcY, mouseX, mouseY, dt);
            hits.add(new Object[]{"fastconnect", fcX, fcY, PILL_W, TH});
        }
        if (CustomMenu.showFriends()) {
            pillButton(ctx, "friends", "Friends", frX, frY, mouseX, mouseY, dt);
            hits.add(new Object[]{"friends", frX, frY, PILL_W, TH});
        }

        renderBottomBar(ctx, width, height, mouseX, mouseY, dt);

        if ("settings".equals(openPanel)) {
            try {
                renderSettingsPanel(ctx, mouseX, mouseY, gearX - (190 - TH), gearY + TH + 6, dt);
            } catch (Throwable t) {
                System.out.println("[Lume] LumeTitleMenu render failed: " + t);
            }
        }
    }

    /** Wide glass chip: live SDF background + real player head + nickname + license sub-label.
     *  Click opens {@link AccountManagerScreen} (a real window with every saved nickname + full
     *  details), not an inline dropdown. */
    private static void renderAccountButton(DrawContext ctx, int x, int y, int mouseX, int mouseY, float dt) {
        boolean hov = inside(mouseX, mouseY, x, y, ACCT_W, TH);
        float[] st = a("account");
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        RenderUtil.premiumBg(ctx, x, y, ACCT_W, TH, RADIUS, st[0], Theme.winBg(), Theme.rim(), Theme.accentRgb());
        int ly = y - RenderUtil.premiumLift(st[0]);

        MinecraftClient mc = MinecraftClient.getInstance();
        var session = mc.getSession();
        net.minecraft.client.util.SkinTextures skin = mc.player != null
                ? mc.player.getSkinTextures()
                : net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(session != null ? session.getUuidOrNull() : null);
        int hs = TH - 8;
        net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx, skin, x + 4, ly + 4, hs);

        String nick = session != null ? session.getUsername() : "Player";
        String subLabel = switch (com.lume.client.social.License.status()) {
            case "valid" -> {
                String tier = com.lume.client.social.License.tierLabel();
                long d = com.lume.client.social.License.daysRemaining();
                String life = d >= 0 ? (d + "d left") : "Active";
                yield tier != null ? (tier + " · " + life) : life;
            }
            case "checking" -> "…";
            case "unreachable" -> "server offline";
            case "invalid" -> "invalid key";
            default -> "no key";
        };
        int textX = x + TH + 4;
        var tr = mc.textRenderer;
        RenderUtil.textVCentered(ctx, tr, nick, textX, ly + TH / 2 - 7, 7, Theme.txt(), 0.43f);
        RenderUtil.textVCentered(ctx, tr, subLabel, textX, ly + TH / 2, 7, Theme.txtDim(), 0.33f);
        hits.add(new Object[]{"account", x, y, ACCT_W, TH});
    }

    /** Wide glass pill with a full text label (Fast Connect / Friends) — live SDF background,
     *  same look/feel as {@link #renderAccountButton}. */
    private static void pillButton(DrawContext ctx, String id, String label, int x, int y, int mouseX, int mouseY, float dt) {
        boolean open = id.equals(openPanel);
        boolean hov = inside(mouseX, mouseY, x, y, PILL_W, TH) || open;
        float[] st = a(id);
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        RenderUtil.premiumBg(ctx, x, y, PILL_W, TH, RADIUS, st[0], Theme.winBg(), Theme.rim(), Theme.accentRgb());
        int ly = y - RenderUtil.premiumLift(st[0]);
        RenderUtil.textCentered(ctx, MinecraftClient.getInstance().textRenderer, label, x, ly, PILL_W, TH,
                open ? Theme.accent() : Theme.txt(), 0.5f);
    }

    /** Square icon button: live SDF background + a small baked glyph (gear/sun-moon/dots — glyph-
     *  only PNGs now, see {@code gen_menu.py}) centred on top. {@code forceOn} keeps the hover
     *  state lit while its panel is open. */
    private static void iconButton(DrawContext ctx, String id, String glyph, int x, int y, int size,
                                    int mouseX, int mouseY, float dt, boolean forceOn) {
        boolean hov = inside(mouseX, mouseY, x, y, size, size) || forceOn;
        float[] st = a(id);
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        RenderUtil.premiumBg(ctx, x, y, size, size, Math.round(size * 0.28f), st[0], Theme.winBg(), Theme.rim(), Theme.accentRgb());
        int ly = y - RenderUtil.premiumLift(st[0]);
        int gs = Math.round(size * 0.5f);
        MenuAssets.blit(ctx, glyph, x + (size - gs) / 2, ly + (size - gs) / 2, gs, gs);
    }

    // ---------------------------------------------------------------------
    // Bottom-center bar — Options+Language combo pill / Quit — additive overlay buttons (vanilla
    // has neither in this exact combined form), matching the reference's "Bottom actions" row.
    // Sits on the same row as the Fast Connect/Friends corner pills, centred between them.

    private static final int OPTLANG_W = 98, OPTLANG_H = 20, QUIT_W = 44, QUIT_H = 20;

    private static void renderBottomBar(DrawContext ctx, int width, int height, int mouseX, int mouseY, float dt) {
        int rowY = height - MARGIN - TH;
        int totalW = OPTLANG_W + GAP + QUIT_W;
        int barX = (width - totalW) / 2;
        int olX = barX, olY = rowY + (TH - OPTLANG_H) / 2;
        int qX = barX + OPTLANG_W + GAP, qY = rowY + (TH - QUIT_H) / 2;

        boolean olHov = inside(mouseX, mouseY, olX, olY, OPTLANG_W, OPTLANG_H);
        float[] olSt = a("optlang");
        olSt[0] = approach(olSt[0], olHov ? 1f : 0f, 12f, dt);
        RenderUtil.premiumBg(ctx, olX, olY, OPTLANG_W, OPTLANG_H, RADIUS, olSt[0], Theme.winBg(), Theme.rim(), Theme.accentRgb());
        int olLy = olY - RenderUtil.premiumLift(olSt[0]);
        int gs = 12;
        MenuAssets.blit(ctx, MenuAssets.IC_GEAR, olX + OPTLANG_W / 4 - gs / 2, olLy + (OPTLANG_H - gs) / 2, gs, gs);
        MenuAssets.blit(ctx, MenuAssets.IC_GLOBE, olX + OPTLANG_W * 3 / 4 - gs / 2, olLy + (OPTLANG_H - gs) / 2, gs, gs);
        RenderUtil.roundedRect(ctx, olX + OPTLANG_W / 2 - 1, olLy + 4, 1, OPTLANG_H - 8, 0, Theme.rim());
        hits.add(new Object[]{"options", olX, olY, OPTLANG_W / 2, OPTLANG_H});
        hits.add(new Object[]{"language", olX + OPTLANG_W / 2, olY, OPTLANG_W - OPTLANG_W / 2, OPTLANG_H});

        boolean qHov = inside(mouseX, mouseY, qX, qY, QUIT_W, QUIT_H);
        float[] qSt = a("quit");
        qSt[0] = approach(qSt[0], qHov ? 1f : 0f, 12f, dt);
        RenderUtil.premiumBg(ctx, qX, qY, QUIT_W, QUIT_H, RADIUS, qSt[0], Theme.winBg(), Theme.rim(), Theme.accentRgb());
        int qLy = qY - RenderUtil.premiumLift(qSt[0]);
        MenuAssets.blit(ctx, MenuAssets.IC_X, qX + (QUIT_W - gs) / 2, qLy + (QUIT_H - gs) / 2, gs, gs);
        hits.add(new Object[]{"quit", qX, qY, QUIT_W, QUIT_H});
    }

    // ---------------------------------------------------------------------
    // Settings panel — Wallpaper / Background Dim / Theme / show-hide toggles. Everything the
    // old "Menu settings" gear dropdown had, minus the background-style cycling (Particles/
    // Geometry) which no longer exists — vanilla owns the background again unless a wallpaper is
    // explicitly turned on. Background Dim applies either way (vanilla panorama or wallpaper).

    private static void renderSettingsPanel(DrawContext ctx, int mouseX, int mouseY, int anchorX, int anchorY, float dt) {
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

        panelChrome(ctx, px, py, pw, ph, "Menu settings");
        var tr = MinecraftClient.getInstance().textRenderer;
        int ry = py + headerH;
        for (Row r : rows) {
            RenderUtil.textVCentered(ctx, tr, r.label(), px + 12, ry, srowH, Theme.txt(), 0.45f);
            cyclerVanilla(ctx, rowX, ry, rowW, srowH, r.value().get());
            hits.add(new Object[]{r.kind(), rowX, ry, rowW, srowH});
            ry += rowStep;
        }
        hits.add(new Object[]{"panel", px, py, pw, ph});
    }

    private static boolean isWallpaperOn() {
        var m = com.lume.client.LumeClient.MODULES.getByName("Custom Menu");
        return m instanceof CustomMenu c && c.wallpaperOn.value;
    }

    private static void cyclerVanilla(DrawContext ctx, int x, int y, int w, int h, String value) {
        var tr = MinecraftClient.getInstance().textRenderer;
        RenderUtil.roundedRect(ctx, x, y, w, h, 6, Theme.glassRow());
        RenderUtil.textVCentered(ctx, tr, "‹", x + 6, y, h, Theme.txtDim(), 0.45f);
        RenderUtil.textVCentered(ctx, tr, "›", x + w - 12, y, h, Theme.txtDim(), 0.45f);
        RenderUtil.textCentered(ctx, tr, value, x, y, w, h, Theme.txt(), 0.45f);
    }

    /** Shared drop-shadow + glass window + title, used by every panel below. */
    private static void panelChrome(DrawContext ctx, int px, int py, int pw, int ph, String title) {
        var tr = MinecraftClient.getInstance().textRenderer;
        RenderUtil.glow(ctx, px, py, pw, ph, 10, 0x000000, 4);
        RenderUtil.roundedRect(ctx, px, py, pw, ph, 10, Theme.winBg());
        RenderUtil.strokeRoundedRect(ctx, px, py, pw, ph, 10, 1, Theme.rim());
        if (title != null) RenderUtil.textVCentered(ctx, tr, title, px + 12, py, 22, Theme.txtDim(), 0.48f);
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
                case "theme" -> { Theme.toggle(); com.lume.client.gui.ThemeSync.save(); }
                case "colors" -> { if (mc != null) mc.setScreen(new com.lume.client.gui.ColorsScreen(screen)); }
                case "options" -> { if (mc != null) mc.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(screen, mc.options)); }
                case "language" -> { if (mc != null) mc.setScreen(new net.minecraft.client.gui.screen.option.LanguageOptionsScreen(screen, mc.options, mc.getLanguageManager())); }
                case "quit" -> { if (mc != null) mc.scheduleStop(); }
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

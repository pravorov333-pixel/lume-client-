package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete replacement for the title screen's own widgets while Custom Menu is on — vanilla's
 * Singleplayer/Multiplayer/Realms/Options/etc. buttons are all hidden AND deactivated (see
 * {@code TitleScreenMixin}'s {@code init} injection), Lume draws and hit-tests everything itself
 * instead: a centred logo lockup, Singleplayer/Multiplayer, an Options+Language/Quit bar directly
 * under Multiplayer, corner icon widgets (Account Manager / Theme / Colors / Settings, top), and
 * Fast Connect / Friends as ALWAYS-EXPANDED panels pinned to the bottom corners (not click-to-
 * open — per the reference's "Menu widgets" gallery, they're persistent widgets showing their
 * server/friend rows directly, same as everything else laid out to match the reference rather
 * than vanilla's own spacing). Every button's BACKGROUND renders live through {@link
 * RenderUtil#premiumBg} (raw-GL SDF fill + contour-hugging glow + a slight lift on hover, see
 * {@code SdfRenderer}) instead of a baked PNG — crisp at any GUI scale, glow follows the button's
 * actual rounded shape rather than a rectangular halo. Icon glyphs (gear/globe/sun-moon/dots/×)
 * are still small baked PNGs ({@code MenuAssets}, glyph-only/transparent-bg — see {@code
 * tools/gen_menu.py}), and the logo lockup itself is a baked PNG too (matches the reference's
 * exact Montserrat styling — no in-game font renderer for that weight exists). No NanoVG
 * anywhere in this class.
 */
public final class LumeTitleMenu {
    private LumeTitleMenu() {}

    private static long lastFrame = System.currentTimeMillis();
    private static final Map<String, float[]> anim = new HashMap<>(); // {hover, press}
    private static final List<Object[]> hits = new ArrayList<>();     // {id, x, y, w, h, ...}

    /** Which click-to-open corner panel is open, or null — only Settings uses this now. Account
     *  opens a real full screen (needs the room for a whole account grid); Fast Connect/Friends
     *  are always-expanded panels with no open/closed state at all (see {@link #render}). */
    private static String openPanel = null;

    // Fast Connect / Friends panel state — see #renderFastConnectPanel / #renderFriendsPanel.
    private static boolean fcFormOpen = false;
    private static String fcName = "", fcAddr = "";
    private static String frAddName = "";
    /** Which text field currently has keyboard focus: "fc:name" / "fc:addr" / "fr:add" / null. */
    private static String panelFocused = null;

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
    private static final int ACCT_W = 132;       // Account chip width (head + 2 lines of text)
    private static final int MARGIN = 10;
    private static final int GAP = 6;           // spacing between corner-cluster icons
    private static final int RADIUS = TH / 2;   // true pill/stadium shape — was a mild 7px round,
                                                 // bumped so the contour glow visibly reads as
                                                 // hugging the button's actual rounded shape
                                                 // instead of looking near-rectangular.
    private static final int MAIN_BTN_W = 200, MAIN_BTN_H = 24;   // Singleplayer/Multiplayer

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

        // Central lockup — logo, Singleplayer, Multiplayer, then the Options+Language/Quit bar
        // directly under Multiplayer — all clustered together (see class doc), matching the
        // reference's "Full menu preview" composition instead of vanilla's own wide spacing.
        int logoW = 200, logoH = Math.round(logoW * 100f / 240f);
        int logoX = width / 2 - logoW / 2, logoY = 40;
        MenuAssets.blit(ctx, MenuAssets.LOGO, logoX, logoY, logoW, logoH);

        int spY = logoY + logoH + 16;
        int mpY = spY + MAIN_BTN_H + 8;
        int mainBtnX = width / 2 - MAIN_BTN_W / 2;
        mainButton(ctx, "singleplayer", Text.translatable("menu.singleplayer").getString(), mainBtnX, spY, mouseX, mouseY, dt);
        mainButton(ctx, "multiplayer", Text.translatable("menu.multiplayer").getString(), mainBtnX, mpY, mouseX, mouseY, dt);

        renderBottomBar(ctx, width, mpY + MAIN_BTN_H + 10, mouseX, mouseY, dt);

        // Live SDF backgrounds + baked glyphs on top (see class doc).
        if (showAcct) renderAccountButton(ctx, MARGIN, MARGIN, mouseX, mouseY, dt);
        iconButton(ctx, "theme", MenuAssets.IC_THEME, themeX, gearY, TH, mouseX, mouseY, dt, false);
        hits.add(new Object[]{"theme", themeX, gearY, TH, TH});
        iconButton(ctx, "colors", MenuAssets.IC_COLORS, colorsX, gearY, TH, mouseX, mouseY, dt, false);
        hits.add(new Object[]{"colors", colorsX, gearY, TH, TH});
        iconButton(ctx, "settings", MenuAssets.IC_GEAR, gearX, gearY, TH, mouseX, mouseY, dt, "settings".equals(openPanel));
        hits.add(new Object[]{"settings", gearX, gearY, TH, TH});

        // Fast Connect / Friends — ALWAYS expanded on screen (not click-to-open), per the
        // reference's "Menu widgets" gallery: bottom-left/bottom-right persistent panels, same
        // panelChrome look as everything else.
        if (CustomMenu.showFastConnect()) renderFastConnectPanel(ctx, mouseX, mouseY, MARGIN, height - MARGIN, dt);
        if (CustomMenu.showFriends()) renderFriendsPanel(ctx, mouseX, mouseY, width - MARGIN - 220, height - MARGIN, dt);

        if ("settings".equals(openPanel)) {
            try {
                renderSettingsPanel(ctx, mouseX, mouseY, gearX - (190 - TH), gearY + TH + 6, dt);
            } catch (Throwable t) {
                System.out.println("[Lume] LumeTitleMenu render failed: " + t);
            }
        }
    }

    /** Singleplayer/Multiplayer — fully custom now (vanilla's own buttons are hidden, see
     *  {@code TitleScreenMixin}): a true pill via {@link RenderUtil#premiumBg} + the vanilla
     *  label text (translation-key-looked-up, so it's correct under any locale even though
     *  nothing here is vanilla's own widget any more). */
    private static void mainButton(DrawContext ctx, String id, String label, int x, int y, int mouseX, int mouseY, float dt) {
        boolean hov = inside(mouseX, mouseY, x, y, MAIN_BTN_W, MAIN_BTN_H);
        float[] st = a(id);
        st[0] = approach(st[0], hov ? 1f : 0f, 8f, dt);
        RenderUtil.premiumBg(ctx, x, y, MAIN_BTN_W, MAIN_BTN_H, MAIN_BTN_H / 2, st[0], Theme.winBg(), Theme.rim(), Theme.accentRgb());
        int ly = y - RenderUtil.premiumLift(st[0]);
        RenderUtil.textCentered(ctx, MinecraftClient.getInstance().textRenderer, label, x, ly, MAIN_BTN_W, MAIN_BTN_H, Theme.txt(), 0.5f);
        hits.add(new Object[]{id, x, y, MAIN_BTN_W, MAIN_BTN_H});
    }

    /** Wide glass chip: live SDF background + real player head + nickname + license sub-label.
     *  Click opens {@link AccountManagerScreen} (a real window with every saved nickname + full
     *  details), not an inline dropdown. */
    private static void renderAccountButton(DrawContext ctx, int x, int y, int mouseX, int mouseY, float dt) {
        boolean hov = inside(mouseX, mouseY, x, y, ACCT_W, TH);
        float[] st = a("account");
        st[0] = approach(st[0], hov ? 1f : 0f, 8f, dt);
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

    /** Square icon button: live SDF background + a small baked glyph (gear/sun-moon/dots — glyph-
     *  only PNGs now, see {@code gen_menu.py}) centred on top. {@code forceOn} keeps the hover
     *  state lit while its panel is open. */
    private static void iconButton(DrawContext ctx, String id, String glyph, int x, int y, int size,
                                    int mouseX, int mouseY, float dt, boolean forceOn) {
        boolean hov = inside(mouseX, mouseY, x, y, size, size) || forceOn;
        float[] st = a(id);
        st[0] = approach(st[0], hov ? 1f : 0f, 8f, dt);
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

    private static void renderBottomBar(DrawContext ctx, int width, int rowY, int mouseX, int mouseY, float dt) {
        int totalW = OPTLANG_W + GAP + QUIT_W;
        int barX = (width - totalW) / 2;
        int olX = barX, olY = rowY + (TH - OPTLANG_H) / 2;
        int qX = barX + OPTLANG_W + GAP, qY = rowY + (TH - QUIT_H) / 2;

        boolean olHov = inside(mouseX, mouseY, olX, olY, OPTLANG_W, OPTLANG_H);
        float[] olSt = a("optlang");
        olSt[0] = approach(olSt[0], olHov ? 1f : 0f, 8f, dt);
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
        qSt[0] = approach(qSt[0], qHov ? 1f : 0f, 8f, dt);
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
    // Fast Connect / Friends — ALWAYS-EXPANDED panels pinned to the bottom corners (not a
    // click-to-open dropdown — the user explicitly wants these visible on screen at all times,
    // matching the reference's "Menu widgets" gallery), same panelChrome look as the Settings
    // dropdown. Same server-list/friend-list data (FastConnect.list, Friends.friendList) and
    // add/connect/delete actions {@code FastConnectScreen}/{@code FriendsScreen} used, just laid
    // out as a persistent widget instead of a page or a dropdown.

    private static void renderFastConnectPanel(DrawContext ctx, int mouseX, int mouseY, int anchorX, int bottomY, float dt) {
        List<FastConnect.Entry> list = FastConnect.list;
        int pw = 220, rowH = 22, rowGap = 5, headerH = 22;
        int rowsH = list.size() * (rowH + rowGap);
        int formH = fcFormOpen ? (2 * 23 + 22 + rowGap) : (rowH + rowGap);
        int ph = headerH + rowsH + formH + 8;
        int px = Math.max(4, anchorX), py = bottomY - ph;

        panelChrome(ctx, px, py, pw, ph, "Fast Connect");
        var tr = MinecraftClient.getInstance().textRenderer;
        int rowX = px + 10, rowW = pw - 20, ry = py + headerH;
        for (int i = 0; i < list.size(); i++) {
            FastConnect.Entry e = list.get(i);
            int delW = 16, delX = rowX + rowW - delW - 2;
            float[] rh = a("fcrow:" + i);
            rh[0] = approach(rh[0], inside(mouseX, mouseY, rowX, ry, rowW, rowH) ? 1f : 0f, 10f, dt);
            RenderUtil.roundedRect(ctx, rowX, ry, rowW, rowH, 6, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), rh[0]));
            RenderUtil.textVCentered(ctx, tr, e.name, rowX + 8, ry, rowH, Theme.txt(), 0.44f);
            RenderUtil.textVCentered(ctx, tr, e.address, rowX + rowW - 44, ry, rowH, Theme.txtDim(), 0.36f);
            RenderUtil.textCentered(ctx, tr, "✕", delX, ry, delW, rowH, Theme.txtDim(), 0.42f);
            hits.add(new Object[]{"fcConnect", rowX, ry, rowW - delW - 4, rowH, i});
            hits.add(new Object[]{"fcDelete", delX, ry, delW, rowH, i});
            ry += rowH + rowGap;
        }
        if (!fcFormOpen) {
            float[] ah = a("fcAddRow");
            ah[0] = approach(ah[0], inside(mouseX, mouseY, rowX, ry, rowW, rowH) ? 1f : 0f, 10f, dt);
            RenderUtil.roundedRect(ctx, rowX, ry, rowW, rowH, 6, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ah[0]));
            RenderUtil.textCentered(ctx, tr, "+ " + com.lume.client.Lang.tUI("Add server"), rowX, ry, rowW, rowH, Theme.accent(), 0.44f);
            hits.add(new Object[]{"fcOpenForm", rowX, ry, rowW, rowH});
        } else {
            panelField(ctx, tr, "fc:name", rowX, ry, rowW, 20, "server name", fcName);
            ry += 23;
            panelField(ctx, tr, "fc:addr", rowX, ry, rowW, 20, "ip:port", fcAddr);
            ry += 25;
            int halfW = (rowW - 6) / 2;
            RenderUtil.roundedRect(ctx, rowX, ry, halfW, 22, 6, Theme.accent());
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Add"), rowX, ry, halfW, 22, Theme.activeText(), 0.44f);
            RenderUtil.roundedRect(ctx, rowX + halfW + 6, ry, halfW, 22, 6, Theme.glassRow());
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Cancel"), rowX + halfW + 6, ry, halfW, 22, Theme.txt(), 0.44f);
            hits.add(new Object[]{"fcSave", rowX, ry, halfW, 22});
            hits.add(new Object[]{"fcCancel", rowX + halfW + 6, ry, halfW, 22});
        }
        hits.add(new Object[]{"panel", px, py, pw, ph});
    }

    private static void renderFriendsPanel(DrawContext ctx, int mouseX, int mouseY, int anchorX, int bottomY, float dt) {
        List<String> friends = new ArrayList<>(com.lume.client.social.Friends.friendList);
        friends.sort((a, b) -> Boolean.compare(com.lume.client.social.Friends.isOnline(b), com.lume.client.social.Friends.isOnline(a)));

        int pw = 220, rowH = 22, rowGap = 5, headerH = 22, fieldH = 24;
        int ph = headerH + friends.size() * (rowH + rowGap) + fieldH + 8;
        int px = Math.max(4, anchorX), py = bottomY - ph;

        panelChrome(ctx, px, py, pw, ph, "Friends");
        var tr = MinecraftClient.getInstance().textRenderer;
        int rowX = px + 10, rowW = pw - 20, ry = py + headerH;
        for (String name : friends) {
            boolean online = com.lume.client.social.Friends.isOnline(name);
            int delW = 16, delX = rowX + rowW - delW - 2;
            float[] rh = a("frrow:" + name);
            rh[0] = approach(rh[0], inside(mouseX, mouseY, rowX, ry, rowW, rowH) ? 1f : 0f, 10f, dt);
            RenderUtil.roundedRect(ctx, rowX, ry, rowW, rowH, 6, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), rh[0]));
            RenderUtil.roundedRect(ctx, rowX + 6, ry + rowH / 2 - 3, 5, 5, 3, online ? 0xFF6FCF7F : Theme.txtDim());
            RenderUtil.textVCentered(ctx, tr, name, rowX + 16, ry, rowH, Theme.txt(), 0.44f);
            RenderUtil.textCentered(ctx, tr, "✕", delX, ry, delW, rowH, Theme.txtDim(), 0.42f);
            hits.add(new Object[]{"frConnect", rowX, ry, rowW - delW - 4, rowH, name});
            hits.add(new Object[]{"frDelete", delX, ry, delW, rowH, name});
            ry += rowH + rowGap;
        }
        boolean foc = "fr:add".equals(panelFocused);
        int addW = 56, fieldW = rowW - addW - 6;
        RenderUtil.roundedRect(ctx, rowX, ry, fieldW, fieldH, 6, foc ? Theme.glassHov() : Theme.glassRow());
        if (foc) RenderUtil.roundedRect(ctx, rowX, ry + fieldH - 1, fieldW, 1, 1, Theme.accent());
        String show = frAddName.isEmpty() && !foc ? com.lume.client.Lang.tUI("friend name") : frAddName + (foc ? "_" : "");
        RenderUtil.textVCentered(ctx, tr, show, rowX + 6, ry, fieldH, frAddName.isEmpty() && !foc ? Theme.txtDim() : Theme.txt(), 0.42f);
        int addX = rowX + fieldW + 6;
        RenderUtil.roundedRect(ctx, addX, ry, addW, fieldH, 6, Theme.accent());
        RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Add"), addX, ry, addW, fieldH, Theme.activeText(), 0.42f);
        hits.add(new Object[]{"field:fr:add", rowX, ry, fieldW, fieldH});
        hits.add(new Object[]{"frAdd", addX, ry, addW, fieldH});
        hits.add(new Object[]{"panel", px, py, pw, ph});
    }

    private static void panelField(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, String id,
                                    int x, int y, int w, int h, String placeholder, String value) {
        boolean foc = id.equals(panelFocused);
        String show = value.isEmpty() && !foc ? com.lume.client.Lang.tUI(placeholder) : value + (foc ? "_" : "");
        int color = value.isEmpty() && !foc ? Theme.txtDim() : Theme.txt();
        RenderUtil.roundedRect(ctx, x, y, w, h, 5, foc ? Theme.glassHov() : Theme.glassRow());
        if (foc) RenderUtil.roundedRect(ctx, x, y + h - 1, w, 1, 1, Theme.accent());
        RenderUtil.textVCentered(ctx, tr, show, x + 6, y, h, color, 0.4f);
        hits.add(new Object[]{"field:" + id, x, y, w, h});
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
                case "settings" -> togglePanel("settings");
                case "singleplayer" -> { if (mc != null) mc.setScreen(new SelectWorldScreen(screen)); }
                case "multiplayer" -> { if (mc != null) mc.setScreen(new MultiplayerScreen(screen)); }
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
                case "fcOpenForm" -> { fcFormOpen = true; fcName = ""; fcAddr = ""; panelFocused = "fc:name"; }
                case "fcCancel" -> { fcFormOpen = false; panelFocused = null; }
                case "fcSave" -> { FastConnect.add(fcName, fcAddr); Config.save(); fcFormOpen = false; panelFocused = null; }
                case "fcConnect" -> {
                    int i = (int) h[5];
                    if (i >= 0 && i < FastConnect.list.size()) {
                        FastConnect.Entry e = FastConnect.list.get(i);
                        var info = new net.minecraft.client.network.ServerInfo(e.name, e.address, net.minecraft.client.network.ServerInfo.ServerType.OTHER);
                        net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(screen, mc,
                                net.minecraft.client.network.ServerAddress.parse(e.address), info, false, null);
                    }
                }
                case "fcDelete" -> {
                    int i = (int) h[5];
                    if (i >= 0 && i < FastConnect.list.size()) { FastConnect.remove(FastConnect.list.get(i)); Config.save(); }
                }
                case "frConnect" -> {
                    String n = (String) h[5];
                    var st = com.lume.client.social.Friends.statusOf(n);
                    if (st != null && st.online && st.server != null) com.lume.client.social.Friends.connectTo(st.server);
                }
                case "frDelete" -> com.lume.client.social.Friends.removeFriend((String) h[5]);
                case "frAdd" -> { com.lume.client.social.Friends.addFriend(frAddName.trim()); frAddName = ""; }
                case "field:fc:name" -> panelFocused = "fc:name";
                case "field:fc:addr" -> panelFocused = "fc:addr";
                case "field:fr:add" -> panelFocused = "fr:add";
                default -> { }
            }
            return true;
        }
        if (openPanel != null) { openPanel = null; panelFocused = null; }
        return false;
    }

    private static void togglePanel(String id) {
        openPanel = id.equals(openPanel) ? null : id;
        panelFocused = null;
    }

    /** Fast Connect/Friends text fields have no real {@code charTyped} to hook (TitleScreen never
     *  overrides it — see the old doc note this replaced), so characters are derived straight
     *  from the key code instead: covers what server names/addresses/nicknames actually need
     *  (letters, digits, {@code . - _ : /} and space). Not full Unicode input, but this is the
     *  same constraint every previous round of this exact overlay has worked within. */
    private static char charForKey(int keyCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            char c = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
            return shift ? Character.toUpperCase(c) : c;
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        if (keyCode == GLFW.GLFW_KEY_PERIOD) return '.';
        if (keyCode == GLFW.GLFW_KEY_MINUS) return shift ? '_' : '-';
        if (keyCode == GLFW.GLFW_KEY_SPACE) return ' ';
        if (keyCode == GLFW.GLFW_KEY_SEMICOLON) return shift ? ':' : ';';
        if (keyCode == GLFW.GLFW_KEY_SLASH) return '/';
        return 0;
    }

    private static String focusedValue() {
        return switch (panelFocused) {
            case "fc:name" -> fcName;
            case "fc:addr" -> fcAddr;
            case "fr:add" -> frAddName;
            default -> "";
        };
    }

    private static void setFocusedValue(String v) {
        switch (panelFocused) {
            case "fc:name" -> fcName = v;
            case "fc:addr" -> fcAddr = v;
            case "fr:add" -> frAddName = v;
            default -> { }
        }
    }

    /** Routes typed characters into whichever Fast Connect/Friends field has focus (see
     *  {@link #charForKey}); Settings' cycler rows never take text input. */
    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (panelFocused == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { panelFocused = null; return true; }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String v = focusedValue();
            if (!v.isEmpty()) setFocusedValue(v.substring(0, v.length() - 1));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            switch (panelFocused) {
                case "fc:name" -> panelFocused = "fc:addr";
                case "fc:addr" -> { FastConnect.add(fcName, fcAddr); Config.save(); fcFormOpen = false; panelFocused = null; }
                case "fr:add" -> { com.lume.client.social.Friends.addFriend(frAddName.trim()); frAddName = ""; }
            }
            return true;
        }
        char c = charForKey(keyCode, modifiers);
        if (c != 0) setFocusedValue(focusedValue() + c);
        return true;
    }
}

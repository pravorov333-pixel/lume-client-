package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import com.lume.client.social.Friends;
import com.lume.client.social.License;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lume.client.nanovg.NanoVgRenderer.*;

/**
 * Fully custom-rendered Lume title screen — own NanoVG glass UI (not vanilla
 * ButtonWidgets reskinned), used by {@code TitleScreenMixin} while Custom Menu
 * is on. Vanilla children are cleared; Singleplayer/Multiplayer/Options/
 * Language/Quit are re-triggered directly by us. Realms and Accessibility are
 * dropped entirely (not needed). Own top-right cluster: open full mod menu,
 * theme/background gear, and the Lume/Vanilla toggle pill (always visible,
 * even outside Custom Menu, so you can always get back).
 *
 * <p>Text is always drawn via {@code ALIGN_CENTER_MIDDLE}/{@code ALIGN_MIDDLE}
 * anchored at the box's own centre/left-middle point — NanoVG's {@code
 * ALIGN_LEFT} anchors to the TOP of the glyph, not the middle, so mixing it
 * with a "box centre + half line-height" offset (an earlier version of this
 * file did that) drifts the text off-centre. Always prefer the *_MIDDLE aligns
 * and pass the box's actual centre/edge point, never a hand-tuned offset.
 */
public final class LumeTitleMenu {
    private LumeTitleMenu() {}

    private static long lastFrame = System.currentTimeMillis();
    private static final Map<String, float[]> anim = new HashMap<>(); // {hover, press}
    private static final List<Object[]> hits = new ArrayList<>();     // {id, x, y, w, h, ...}

    private static boolean settingsOpen = false;
    private static boolean formOpen = false;
    private static String fcName = "", fcAddr = "";
    private static String focused = null;

    private static boolean accountOpen = false;
    private static String acctNewName = "";

    private static String friendAddName = "";

    private static float[] a(String id) { return anim.computeIfAbsent(id, k -> new float[2]); }

    /** Y of the Singleplayer button — same formula vanilla itself used (height/4 + 48), kept
     *  as one shared method so {@link com.lume.client.mixin.LogoDrawerMixin}'s wordmark can
     *  anchor a fixed gap above it instead of vanilla's unrelated, much-higher logo position. */
    public static int buttonRowY(int height) { return height / 4 + 48; }

    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * Math.min(1f, rate * dt);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ---------------------------------------------------------------------
    // Lifecycle hooks called from TitleScreenMixin

    /** Called at the tail of TitleScreen#init — strip vanilla widgets if Custom Menu is on. */
    public static void onInit(TitleScreen screen) {
        if (CustomMenu.active()) ((com.lume.client.mixin.ScreenAccessor) screen).lume$clearChildren();
    }

    /** Toggle pill clicked: flip the module, then rebuild (vanilla) and re-strip if turning on. */
    public static void onToggle(TitleScreen screen) {
        CustomMenu.toggleActive();
        Config.save();
        screen.init(MinecraftClient.getInstance(), screen.width, screen.height);
        if (CustomMenu.active()) ((com.lume.client.mixin.ScreenAccessor) screen).lume$clearChildren();
    }

    // ---------------------------------------------------------------------
    // Render

    public static void render(DrawContext ctx, TitleScreen screen, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = screen.width, height = screen.height;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        boolean active = CustomMenu.active();

        // --- top-right cluster: account widget, open-menu (logo), theme toggle, colors,
        // gear, toggle pill --- theme/colors are the exact same icon buttons ClickGuiScreen
        // and the launcher use (via ThemeIcons), always visible regardless of Custom Menu
        // on/off (theme affects every Lume screen, not just this custom background).
        int th = 24, iconTh = 22, gap = 8, pillW = 96;
        int pillX = width - pillW - 10, pillY = 10;
        int iconY = pillY + (th - iconTh) / 2;
        int gearX = pillX - gap - th;
        int themeX = gearX - gap - iconTh;
        int colorsX = themeX - gap - iconTh;
        int openX = colorsX - gap - th;
        int acctW = 108;
        int acctX = openX - gap - acctW;

        NanoVgRenderer.ensureInit();
        boolean nvg = NanoVgRenderer.ready();

        boolean showAcct = CustomMenu.showAccount();

        if (nvg) {
            float S = NanoVgRenderer.pxScale();
            final int fPillX = pillX, fPillY = pillY, fGearX = gearX, fOpenX = openX, fTh = th, fAcctX = acctX;
            final int fThemeX = themeX, fColorsX = colorsX, fIconY = iconY;
            final boolean fActive = active;
            boolean themeHov = inside(mouseX, mouseY, themeX, iconY, iconTh, iconTh);
            boolean colorsHov = inside(mouseX, mouseY, colorsX, iconY, iconTh, iconTh);
            float[] tha = a("mainTheme");
            tha[0] = approach(tha[0], themeHov ? 1f : 0f, 12f, dt);
            float[] cla = a("mainColors");
            cla[0] = approach(cla[0], colorsHov ? 1f : 0f, 12f, dt);
            boolean acctHov = showAcct && inside(mouseX, mouseY, acctX, pillY, acctW, th);
            float[] aa = a("account");
            aa[0] = approach(aa[0], acctHov || accountOpen ? 1f : 0f, 12f, dt);
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                // account widget background pill (head icon + text drawn after, outside this frame)
                if (showAcct) {
                    roundedRect(vg, fAcctX * S, fPillY * S, acctW * S, fTh * S, 8 * S,
                            Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), aa[0]));
                }

                // toggle pill
                float[] ta = a("toggle");
                boolean hov = inside(mouseX, mouseY, fPillX, fPillY, pillW, fTh);
                ta[0] = approach(ta[0], hov ? 1f : 0f, 12f, dt);
                roundedRect(vg, fPillX * S, fPillY * S, pillW * S, fTh * S, 8 * S,
                        Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), ta[0]));
                if (fActive) circle(vg, (fPillX + 10) * S, (fPillY + fTh / 2f) * S, 3 * S, Theme.accent());
                String label = fActive ? "Lume menu" : "Vanilla menu";
                text(vg, (fPillX + (fActive ? 18 : 10)) * S, (fPillY + fTh / 2f) * S, 10.5f * S,
                        fActive ? Theme.accent() : Theme.txt(), ALIGN_MIDDLE, label);

                // theme toggle + Customize Colors — identical icon buttons to ClickGuiScreen's
                // header and the launcher's own corner cluster (drawn via shared ThemeIcons).
                com.lume.client.gui.ThemeIcons.drawTheme(vg, fThemeX * S, fIconY * S, S, tha[0]);
                com.lume.client.gui.ThemeIcons.drawColors(vg, fColorsX * S, fIconY * S, S, cla[0]);

                // gear (background/menu settings)
                drawIconButton(vg, "gear", fGearX, fPillY, fTh, fTh, mouseX, mouseY, dt, settingsOpen);
                gearIcon(vg, fGearX + fTh / 2f, fPillY + fTh / 2f, fTh * 0.30f, S);

                // open full mod menu (logo mark)
                drawIconButton(vg, "openmenu", fOpenX, fPillY, fTh, fTh, mouseX, mouseY, dt, false);
                float ls = fTh * 0.44f;
                logoMark(vg, (fOpenX + fTh / 2f) * S - ls * S / 2f, (fPillY + fTh / 2f) * S - ls * S / 2f, ls * S);
            });

            if (showAcct) {
                // player head — real skin texture, can only be drawn via DrawContext, not
                // inside the NanoVG frame above, so it's queued right after that frame flushes.
                // mc.player is null on the title screen (no world joined yet) — fall back to
                // DefaultSkinHelper (sync, no network) keyed by the session UUID so a head
                // always shows instead of nothing.
                MinecraftClient mcHead = MinecraftClient.getInstance();
                var session = mcHead.getSession();
                net.minecraft.client.util.SkinTextures skin = mcHead.player != null
                        ? mcHead.player.getSkinTextures()
                        : net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(session != null ? session.getUuidOrNull() : null);
                int hs = th - 8;
                // was a manual drawTexture sampling a 16x16 texel block starting at UV(8,8) —
                // bigger than the 8x8 face region, so it bled in neighbouring skin parts
                // (looked like "the whole skin" instead of a clean head). PlayerSkinDrawer
                // crops face+hat-overlay correctly and scales to the requested size.
                net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx, skin, acctX + 4, pillY + 4, hs);
                ctx.draw();

                String nick = session != null ? session.getUsername() : "Player";
                String subLabel = switch (License.status()) {
                    case "valid" -> { long d = License.daysRemaining(); yield d >= 0 ? (d + "d left") : "Active"; }
                    case "checking" -> "…";
                    case "unreachable" -> "server offline";
                    case "invalid" -> "invalid key";
                    default -> "no key";
                };
                final String fNick = nick, fSub = subLabel;
                boolean licenseActive = "valid".equals(License.status());
                // Text used to start right at the head's own edge (zero gap) and the two lines
                // were slightly off-centre around the pill's middle (-5/+6). Proper gap after
                // the head + symmetric +-6 offsets so the 2-line block centres cleanly. A small
                // green dot left of the nick signals an active license, same convention as the
                // online dot in Friends rows.
                int dotX = fAcctX + fTh + 4;
                int textX = dotX + (licenseActive ? 8 : 0);
                NanoVgRenderer.frame(vg -> {
                    if (licenseActive) circle(vg, (dotX + 2) * S, (fPillY + fTh / 2f) * S, 2.2f * S, 0xFF6FCF7F);
                    text(vg, textX * S, (fPillY + fTh / 2f - 6) * S, 9f * S, Theme.txt(), ALIGN_MIDDLE, fNick);
                    text(vg, textX * S, (fPillY + fTh / 2f + 6) * S, 7f * S, Theme.txtDim(), ALIGN_MIDDLE, fSub);
                });
            }
        }
        if (showAcct) hits.add(new Object[]{"account", acctX, pillY, acctW, th});
        hits.add(new Object[]{"toggle", pillX, pillY, pillW, th});
        hits.add(new Object[]{"gear", gearX, pillY, th, th});
        hits.add(new Object[]{"mainTheme", themeX, iconY, iconTh, iconTh});
        hits.add(new Object[]{"mainColors", colorsX, iconY, iconTh, iconTh});
        hits.add(new Object[]{"openmenu", openX, pillY, th, th});

        // Vanilla's own bottom-left "Minecraft x.xx.x" text is genuinely CANCELLED now (not
        // covered) via a @Redirect on the exact drawTextWithShadow call in TitleScreenMixin,
        // gated on CustomMenu.showVersion() — no separate label of ours replaces it.

        // Layout for the centre Singleplayer/Multiplayer + Options/Quit row (pure arithmetic,
        // computed unconditionally so panels below can anchor off it even when Custom Menu
        // isn't replacing the actual vanilla buttons this frame).
        int bw = 200, bh = 20;
        int bx = width / 2 - bw / 2;
        int by1 = buttonRowY(height);
        int by2 = by1 + 24;
        int rowGap = 4;
        int rowH = bh;
        int leftW = (bw - rowGap) / 2, rightW = bw - rowGap - leftW;
        int leftX = bx, rightX = leftX + leftW + rowGap;
        int halfW = leftW / 2;
        int rowY = by2 + 24;

        // Panels below (Fast Connect / Friends / Account manager) sit in the SAME place
        // whether Custom Menu fully replaced the title screen or not — anchored off the
        // top-right cluster, not the vanilla-button-replacement layout.
        int panelTop = pillY + th + 10;

        // --- settings popover: Background/Cursor Glow + the HUD-element toggles + Version
        // when Custom Menu owns the screen; in vanilla menu, background settings don't apply
        // to anything (we're not painting our own background), so only the HUD-element
        // on/off toggles show. Theme + Customize Colors have their own dedicated icon
        // buttons in the cluster now (always visible, not buried in here). ---
        float[] settingsAnim = a("settingsPopover");
        settingsAnim[0] = approach(settingsAnim[0], settingsOpen ? 1f : 0f, 10f, dt);
        if (settingsAnim[0] > 0.01f && nvg) {
            record Row(String label, String kind, java.util.function.Supplier<String> value) {}
            List<Row> rows = new ArrayList<>();
            if (active) {
                rows.add(new Row("Background", "bgCycle", () -> new String[]{"Glow", "Geometry", "Default"}[CustomMenu.bgStyle()]));
                rows.add(new Row("Cursor Glow", "glowToggle", () -> CustomMenu.cursorGlowOn() ? "On" : "Off"));
            }
            rows.add(new Row("Fast Connect", "fastConnectToggle", () -> CustomMenu.showFastConnect() ? "On" : "Off"));
            rows.add(new Row("Friends", "friendsToggle", () -> CustomMenu.showFriends() ? "On" : "Off"));
            rows.add(new Row("Account", "accountToggle", () -> CustomMenu.showAccount() ? "On" : "Off"));
            if (active) rows.add(new Row("Version", "versionToggle", () -> CustomMenu.showVersion() ? "On" : "Off"));

            int pw = 190, srowH = 18, headerH = 26, rowStep = 26;
            int ph = headerH + rows.size() * rowStep + 6;
            int px = gearX + th - pw, py = pillY + th + 6;
            int rowW = 80, rowX = px + pw - 12 - rowW;
            final int fpx = px, fpy = py, fpw = pw, fph = ph, frowX = rowX, frowW = rowW, fHeaderH = headerH, fRowStep = rowStep;
            float S = NanoVgRenderer.pxScale();
            // Fade + slight downward-slide-in/out instead of an instant pop, matching the
            // easing already used for hover states elsewhere (approach()).
            float prog = settingsAnim[0];
            float slide = (1f - prog) * 6f;
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                save(vg);
                globalAlpha(vg, prog);
                translate(vg, 0, slide * S);
                shadow(vg, fpx * S, fpy * S, fpw * S, fph * S, 10 * S, 14 * S, 0x55000000);
                roundedRect(vg, fpx * S, fpy * S, fpw * S, fph * S, 10 * S, Theme.winBg());
                text(vg, (fpx + 12) * S, (fpy + 14) * S, 9.5f * S, Theme.txtDim(), ALIGN_MIDDLE, "Menu settings");
                int ry = fpy + fHeaderH;
                for (Row r : rows) {
                    text(vg, (fpx + 12) * S, (ry + srowH / 2f) * S, 10 * S, Theme.txt(), ALIGN_MIDDLE, r.label());
                    cyclerNvg(vg, frowX, ry, frowW, srowH, r.value().get(), S);
                    ry += fRowStep;
                }
                restore(vg);
            });
            if (settingsOpen) {   // only clickable while actually open, not mid fade-out
                int ry = py + headerH;
                for (Row r : rows) {
                    hits.add(new Object[]{r.kind(), rowX, ry, rowW, srowH});
                    ry += rowStep;
                }
                hits.add(new Object[]{"settingsPanel", px, py, pw, ph});
            }
        }

        float[] acctPanelAnim = a("acctPopover");
        acctPanelAnim[0] = approach(acctPanelAnim[0], accountOpen ? 1f : 0f, 10f, dt);
        if (acctPanelAnim[0] > 0.01f && nvg && CustomMenu.showAccount())
            renderAccountPanel(ctx, mouseX, mouseY, acctX, pillY + th + 6, acctW, acctPanelAnim[0], accountOpen, dt);

        // --- Fast Connect panel (right side) — hidden while the settings popover is open,
        // or permanently via Menu settings → Fast Connect. Works whether Custom Menu fully
        // replaced the title screen or not (per user request: available in vanilla menu too). ---
        if (!settingsOpen && CustomMenu.showFastConnect()) renderFastConnect(ctx, mouseX, mouseY, width, panelTop, dt);

        // --- Friends panel (right, vertically centred) — online friends prioritized above
        // offline ones; works in vanilla menu too, same as Fast Connect. ---
        if (!settingsOpen && CustomMenu.showFriends()) renderFriendsPanel(ctx, mouseX, mouseY, width, height, dt);

        if (!active) return;

        // --- centre: Singleplayer / Multiplayer — same size/position as vanilla's own buttons ---
        if (nvg) {
            float S = NanoVgRenderer.pxScale();
            final int fbx = bx, fby1 = by1, fby2 = by2, fbw = bw, fbh = bh;
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                bigButton(vg, "sp", fbx, fby1, fbw, fbh, "Singleplayer", mouseX, mouseY, dt, S);
                bigButton(vg, "mp", fbx, fby2, fbw, fbh, "Multiplayer", mouseX, mouseY, dt, S);
            });
        }
        hits.add(new Object[]{"singleplayer", bx, by1, bw, bh});
        hits.add(new Object[]{"multiplayer", bx, by2, bw, bh});

        // --- bottom row: [Options | Language] combined box + Quit box ---
        // Matches vanilla's OWN Options/Quit proportions exactly (98 wide each,
        // 4px gap, together spanning bx..bx+bw) — Options and Language share the
        // left 98-wide box (split by a thin divider, own independent hover each)
        // since Language is icon-only and doesn't need a full box of its own;
        // Quit gets the whole right 98-wide box to itself (bigger click target
        // and a bigger icon).
        if (nvg) {
            float S = NanoVgRenderer.pxScale();
            float[] stO = a("options"), stL = a("language");
            boolean hovO = inside(mouseX, mouseY, leftX, rowY, halfW, rowH);
            boolean hovL = inside(mouseX, mouseY, leftX + halfW, rowY, leftW - halfW, rowH);
            stO[0] = approach(stO[0], hovO ? 1f : 0f, 12f, dt);
            stL[0] = approach(stL[0], hovL ? 1f : 0f, 12f, dt);
            final int fLeftX = leftX, fRightX = rightX, fLeftW = leftW, fRightW = rightW, fHalfW = halfW, fRowY = rowY, fRowH = rowH;
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                // combined Options+Language box, divided by a thin line
                roundedRect(vg, fLeftX * S, fRowY * S, fLeftW * S, fRowH * S, 6 * S, Theme.glassRow());
                if (stO[0] > 0.02f) roundedRect(vg, fLeftX * S, fRowY * S, fHalfW * S, fRowH * S, 6 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), stO[0]));
                if (stL[0] > 0.02f) roundedRect(vg, (fLeftX + fHalfW) * S, fRowY * S, (fLeftW - fHalfW) * S, fRowH * S, 6 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), stL[0]));
                cursorGlow(vg, fLeftX, fRowY, fHalfW, fRowH, mouseX, mouseY, S, stO[0]);
                cursorGlow(vg, fLeftX + fHalfW, fRowY, fLeftW - fHalfW, fRowH, mouseX, mouseY, S, stL[0]);
                roundedRect(vg, (fLeftX + fHalfW - 0.5f) * S, (fRowY + 3) * S, 1 * S, (fRowH - 6) * S, 0, Theme.border());
                gearIcon(vg, fLeftX + fHalfW / 2f, fRowY + fRowH / 2f, fRowH * 0.32f, S);
                // language glyph drawn separately below via vanilla font (CJK glyph coverage)

                drawIconButton(vg, "quit", fRightX, fRowY, fRightW, fRowH, mouseX, mouseY, dt, false);
                quitIcon(vg, fRightX + fRightW / 2f, fRowY + fRowH / 2f, fRowH * 0.42f, S);
            });
        }
        hits.add(new Object[]{"options", leftX, rowY, halfW, rowH});
        hits.add(new Object[]{"language", leftX + halfW, rowY, leftW - halfW, rowH});
        hits.add(new Object[]{"quit", rightX, rowY, rightW, rowH});

        // Language icon: literal CJK glyph via vanilla TextRenderer (Lume/Poppins font has no CJK coverage,
        // vanilla's bundled Unifont fallback does) — drawn on top, outside the nvg frame, scaled+centred
        // properly (fontHeight for vertical centring, not a hand-tuned magic offset).
        TextRenderer tr = mc.textRenderer;
        String glyph = "文";
        float targetPx = rowH * 0.7f;
        float glyphW = tr.getWidth(glyph), glyphH = tr.fontHeight;
        float gscale = targetPx / glyphH;
        float langCx = leftX + halfW + (leftW - halfW) / 2f;
        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(langCx - (glyphW * gscale) / 2f, rowY + rowH / 2f - (glyphH * gscale) / 2f, 0);
        ms.scale(gscale, gscale, 1f);
        ctx.drawText(tr, glyph, 0, 0, Theme.txt(), false);
        ms.pop();

    }

    /** Whole bar is one click target: left half = previous, right half = next (matches ClickGUI's own ModeSetting). */
    private static void cyclerNvg(long vg, int x, int y, int w, int h, String value, float S) {
        roundedRect(vg, x * S, y * S, w * S, h * S, 6 * S, Theme.glassRow());
        text(vg, (x + 8) * S, (y + h / 2f) * S, 9 * S, Theme.txtDim(), ALIGN_MIDDLE, "‹");
        text(vg, (x + w - 14) * S, (y + h / 2f) * S, 9 * S, Theme.txtDim(), ALIGN_MIDDLE, "›");
        text(vg, (x + w / 2f) * S, (y + h / 2f) * S, 9 * S, Theme.txt(), ALIGN_CENTER_MIDDLE, value);
    }

    private static void drawIconButton(long vg, String id, int x, int y, int w, int h, int mouseX, int mouseY, float dt, boolean forceOn) {
        float S = NanoVgRenderer.pxScale();
        float[] st = a(id);
        boolean hov = inside(mouseX, mouseY, x, y, w, h) || forceOn;
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        float r = Math.min(w, h) * 0.28f;
        if (st[0] > 0.02f) neonGlow(vg, x * S, y * S, w * S, h * S, r * S, 8 * S, (int) (st[0] * 60) << 24 | (Theme.accentRgb() & 0xFFFFFF));
        roundedRect(vg, x * S, y * S, w * S, h * S, r * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), st[0]));
        cursorGlow(vg, x, y, w, h, mouseX, mouseY, S, st[0]);
    }

    private static void bigButton(long vg, String id, int x, int y, int w, int h, String label, int mouseX, int mouseY, float dt, float S) {
        float[] st = a(id);
        boolean hov = inside(mouseX, mouseY, x, y, w, h);
        st[0] = approach(st[0], hov ? 1f : 0f, 12f, dt);
        if (st[0] > 0.02f) neonGlow(vg, x * S, y * S, w * S, h * S, 10 * S, 10 * S, (int) (st[0] * 50) << 24 | (Theme.accentRgb() & 0xFFFFFF));
        roundedRect(vg, x * S, y * S, w * S, h * S, 6 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), st[0]));
        cursorGlow(vg, x, y, w, h, mouseX, mouseY, S, st[0]);
        text(vg, x * S + w * S / 2f, y * S + h * S / 2f, 10 * S,
                Theme.colorLerp(Theme.txt(), Theme.accent(), st[0]), ALIGN_CENTER_MIDDLE, label);
    }

    /** Soft light that follows the cursor within the hovered element, clipped to its bounds — toggleable (Menu settings → Cursor Glow). */
    private static void cursorGlow(long vg, int x, int y, int w, int h, int mouseX, int mouseY, float S, float intensity) {
        if (!CustomMenu.cursorGlowOn() || intensity <= 0.02f) return;
        scissor(vg, x * S, y * S, w * S, h * S);
        int rgb = Theme.colorLerp(0xFFFFFF, Theme.accentRgb(), 0.4f) & 0xFFFFFF;
        float r = Math.max(w, h) * S;
        for (int i = 6; i >= 1; i--) {
            float rr = r * i / 6f;
            int a = (int) (intensity * (7 - i) * 6);
            circle(vg, mouseX * S, mouseY * S, rr, (Math.min(160, a) << 24) | rgb);
        }
        resetScissor(vg);
    }

    /** Simple vector gear: hub ring + 8 radial teeth. */
    private static void gearIcon(long vg, float cx, float cy, float r, float S) {
        int rgb = Theme.txt();
        save(vg);
        translate(vg, cx * S, cy * S);
        for (int i = 0; i < 8; i++) {
            save(vg);
            rotate(vg, (float) (i * Math.PI / 4));
            roundedRect(vg, -r * 0.14f * S, -r * 1.15f * S, r * 0.28f * S, r * 0.5f * S, 1f * S, rgb);
            restore(vg);
        }
        restore(vg);
        circle(vg, cx * S, cy * S, r * 0.85f * S, rgb);
        circle(vg, cx * S, cy * S, r * 0.40f * S, Theme.winBg());
    }

    /** "X" quit glyph made of two crossed bars. */
    private static void quitIcon(long vg, float cx, float cy, float r, float S) {
        int rgb = Theme.txt();
        save(vg);
        translate(vg, cx * S, cy * S);
        rotate(vg, (float) (Math.PI / 4));
        roundedRect(vg, -r * S, -0.16f * r * S, 2 * r * S, 0.32f * r * S, 0.16f * r * S, rgb);
        roundedRect(vg, -0.16f * r * S, -r * S, 0.32f * r * S, 2 * r * S, 0.16f * r * S, rgb);
        restore(vg);
    }

    // ---------------------------------------------------------------------
    // Fast Connect (unchanged data/logic, now nvg-rendered)

    private static void renderFastConnect(DrawContext ctx, int mouseX, int mouseY, int width, int startY, float dt) {
        if (!NanoVgRenderer.ready()) return;
        float S = NanoVgRenderer.pxScale();
        int panelW = 150, rowH = 20, gap = 4;
        int panelX = width - panelW - 10, panelY = startY;
        List<FastConnect.Entry> list = FastConnect.list;

        final int fpx = panelX, fpy = panelY, fpw = panelW;
        ctx.draw();
        NanoVgRenderer.frame(vg -> text(vg, (fpx + 2) * S, (fpy + 6) * S, 9f * S, Theme.txtDim(), ALIGN_MIDDLE, "Fast Connect"));

        int y = panelY + 14;
        for (int i = 0; i < list.size(); i++) {
            FastConnect.Entry e = list.get(i);
            boolean rowHov = inside(mouseX, mouseY, panelX, y, panelW, rowH);
            int delW = 14, delX = panelX + panelW - delW - 4;
            boolean delHov = inside(mouseX, mouseY, delX, y, delW, rowH);
            float[] rh = a("fcRow:" + i);
            rh[0] = approach(rh[0], rowHov ? 1f : 0f, 14f, dt);
            float[] dh = a("fcDel:" + i);
            dh[0] = approach(dh[0], delHov ? 1f : 0f, 14f, dt);
            final int fy = y, fdelX = delX;
            final float frh = rh[0], fdelHov = dh[0];
            NanoVgRenderer.frame(vg -> {
                roundedRect(vg, fpx * S, fy * S, fpw * S, rowH * S, 6 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), frh));
                text(vg, (fpx + 8) * S, (fy + rowH / 2f) * S, 8.5f * S, Theme.txt(), ALIGN_MIDDLE, e.name);
                text(vg, (fdelX + delW / 2f) * S, (fy + rowH / 2f) * S, 8.5f * S, Theme.colorLerp(Theme.txtDim(), 0xFFE06868, fdelHov), ALIGN_CENTER_MIDDLE, "x");
            });
            hits.add(new Object[]{"connect", panelX, y, panelW - delW - 6, rowH, i});
            hits.add(new Object[]{"delete", delX, y, delW, rowH, i});
            y += rowH + gap;
        }

        final int fy2 = y;
        if (!formOpen) {
            boolean addHov = inside(mouseX, mouseY, panelX, y, panelW, rowH);
            float[] ah = a("fcAddRow");
            ah[0] = approach(ah[0], addHov ? 1f : 0f, 14f, dt);
            final float fah = ah[0];
            NanoVgRenderer.frame(vg -> {
                roundedRect(vg, fpx * S, fy2 * S, fpw * S, rowH * S, 6 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), fah));
                text(vg, fpx * S + fpw * S / 2f, (fy2 + rowH / 2f) * S, 8.5f * S, Theme.accent(), ALIGN_CENTER_MIDDLE, "+ Add server");
            });
            hits.add(new Object[]{"openForm", panelX, y, panelW, rowH});
        } else {
            int fh = 18;
            field(ctx, "name", panelX, y, panelW, fh, "server name", S);
            y += fh + 3;
            field(ctx, "addr", panelX, y, panelW, fh, "ip:port", S);
            y += fh + 5;
            int halfW = (panelW - 4) / 2;
            final int fy3 = y, fhalfW = halfW;
            NanoVgRenderer.frame(vg -> {
                roundedRect(vg, fpx * S, fy3 * S, fhalfW * S, rowH * S, 6 * S, Theme.accent());
                text(vg, fpx * S + fhalfW * S / 2f, (fy3 + rowH / 2f) * S, 8.5f * S, Theme.activeText(), ALIGN_CENTER_MIDDLE, "Add");
                roundedRect(vg, (fpx + fhalfW + 4) * S, fy3 * S, fhalfW * S, rowH * S, 6 * S, Theme.glassRow());
                text(vg, (fpx + fhalfW + 4) * S + fhalfW * S / 2f, (fy3 + rowH / 2f) * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, "Cancel");
            });
            hits.add(new Object[]{"saveForm", panelX, y, halfW, rowH});
            hits.add(new Object[]{"cancelForm", panelX + halfW + 4, y, halfW, rowH});
        }
    }

    // ---------------------------------------------------------------------
    // Friends panel — right side, vertically centred. Online friends sorted
    // above offline ones; status + server/IP shown as a small label above
    // each name, with a Connect button for online friends (reuses the same
    // ConnectScreen flow FastConnect/AutoReconnect already use).

    /** Same row/panel footprint as {@link #renderFastConnect} (150px wide, 20px rows) instead
     *  of the old bigger 2-line cards, so the two panels read as one consistent family. Always
     *  shows the Add-friend field (even with zero friends), same pattern as FastConnect's own
     *  always-visible "+ Add server" row. */
    private static void renderFriendsPanel(DrawContext ctx, int mouseX, int mouseY, int width, int height, float dt) {
        if (!NanoVgRenderer.ready()) return;
        List<String> friends = new ArrayList<>(Friends.friendList);
        friends.sort((a, b) -> Boolean.compare(Friends.isOnline(b), Friends.isOnline(a)));   // online first

        float S = NanoVgRenderer.pxScale();
        int panelW = 150, rowH = 20, gap = 4, header = 14, fieldH = 18;
        int totalH = header + friends.size() * (rowH + gap) + fieldH;
        int panelX = width - panelW - 10;
        int panelY = Math.max(60, height / 2 - totalH / 2);

        final int fpx = panelX, fpy = panelY, fpw = panelW;
        ctx.draw();
        NanoVgRenderer.frame(vg -> text(vg, (fpx + 2) * S, (fpy + 6) * S, 9f * S, Theme.txtDim(), ALIGN_MIDDLE, "Friends"));

        int y = panelY + header;
        for (String name : friends) {
            boolean online = Friends.isOnline(name);
            boolean rowHov = inside(mouseX, mouseY, panelX, y, panelW, rowH);
            int delW = 14, delX = panelX + panelW - delW - 4;
            boolean delHov = inside(mouseX, mouseY, delX, y, delW, rowH);
            float[] rh = a("friendRow:" + name);
            rh[0] = approach(rh[0], rowHov ? 1f : 0f, 14f, dt);
            float[] dh = a("friendDel:" + name);
            dh[0] = approach(dh[0], delHov ? 1f : 0f, 14f, dt);
            final int fy = y, fdelX = delX;
            final boolean fonline = online;
            final float frowHov = rh[0], fdelHov = dh[0];
            NanoVgRenderer.frame(vg -> {
                roundedRect(vg, fpx * S, fy * S, fpw * S, rowH * S, 6 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), frowHov));
                int dotCol = fonline ? 0xFF6FCF7F : Theme.txtDim();
                circle(vg, (fpx + 8) * S, (fy + rowH / 2f) * S, 2.2f * S, dotCol);
                text(vg, (fpx + 14) * S, (fy + rowH / 2f) * S, 8.5f * S, Theme.txt(), ALIGN_MIDDLE, name);
                text(vg, (fdelX + delW / 2f) * S, (fy + rowH / 2f) * S, 8.5f * S, Theme.colorLerp(Theme.txtDim(), 0xFFE06868, fdelHov), ALIGN_CENTER_MIDDLE, "x");
            });
            hits.add(new Object[]{"friendConnect", panelX, y, panelW - delW - 6, rowH, name});
            hits.add(new Object[]{"friendDelete", delX, y, delW, rowH, name});
            y += rowH + gap;
        }

        field(ctx, "friendAdd", panelX, y, panelW - 38, fieldH, "friend name", S);
        int addX = panelX + panelW - 34;
        final int faddX = addX, fy2 = y;
        NanoVgRenderer.frame(vg -> {
            roundedRect(vg, faddX * S, fy2 * S, 34 * S, fieldH * S, 6 * S, Theme.accent());
            text(vg, (faddX + 17) * S, (fy2 + fieldH / 2f) * S, 8 * S, Theme.activeText(), ALIGN_CENTER_MIDDLE, "Add");
        });
        hits.add(new Object[]{"friendAdd", addX, y, 34, fieldH});
    }

    // ---------------------------------------------------------------------
    // Account manager dropdown — current identity + subscription (from
    // social.License) + a list of saved offline nicknames. Picking a saved
    // nickname only marks it "preferred" for the LAUNCHER to read on next
    // login — it can't change the identity of the game instance already
    // running (Minecraft's session name is fixed at process start).

    private static void renderAccountPanel(DrawContext ctx, int mouseX, int mouseY, int anchorX, int anchorY, int minW,
                                            float progress, boolean fullyOpen, float dt) {
        if (!NanoVgRenderer.ready()) return;
        float S = NanoVgRenderer.pxScale();
        int pw = Math.max(minW, 190), rowH = 18, gap = 4, fieldH = 18;
        int px = anchorX, py = anchorY;
        List<String> accounts = Config.savedAccounts;
        int listH = accounts.size() * (rowH + gap);
        int ph = 48 + listH + fieldH + 10;
        final int fpx = px, fpy = py, fpw = pw, fph = ph;
        float slide = (1f - progress) * 6f;

        MinecraftClient mc = MinecraftClient.getInstance();
        String nick = mc.getSession() != null ? mc.getSession().getUsername() : "Player";
        String subLabel = switch (License.status()) {
            case "valid" -> { long d = License.daysRemaining(); yield d >= 0 ? ("Active — " + d + " days left") : "Active — lifetime"; }
            case "checking" -> "Checking subscription…";
            case "unreachable" -> "License server unreachable";
            case "invalid" -> "Invalid or expired key";
            default -> "No license key on file";
        };
        final String fNick = nick, fSub = subLabel;

        ctx.draw();
        NanoVgRenderer.frame(vg -> {
            save(vg);
            globalAlpha(vg, progress);
            translate(vg, 0, slide * S);
            shadow(vg, fpx * S, fpy * S, fpw * S, fph * S, 10 * S, 14 * S, 0x55000000);
            roundedRect(vg, fpx * S, fpy * S, fpw * S, fph * S, 10 * S, Theme.winBg());
            text(vg, (fpx + 12) * S, (fpy + 14) * S, 10 * S, Theme.txt(), ALIGN_MIDDLE, fNick);
            text(vg, (fpx + 12) * S, (fpy + 28) * S, 8 * S, Theme.txtDim(), ALIGN_MIDDLE, fSub);
            text(vg, (fpx + 12) * S, (fpy + 42) * S, 8.5f * S, Theme.txtDim(), ALIGN_MIDDLE, "Saved nicknames");
            restore(vg);
        });

        int y = py + 48;
        for (String name : accounts) {
            boolean preferred = name.equals(Config.preferredAccount);
            int delW = 14, delX = px + pw - delW - 4;
            boolean rowHov = inside(mouseX, mouseY, px, y, pw - delW - 6, rowH);
            boolean delHov = inside(mouseX, mouseY, delX, y, delW, rowH);
            float[] rh = a("acctRow:" + name);
            rh[0] = approach(rh[0], rowHov ? 1f : 0f, 14f, dt);
            float[] dh = a("acctDel:" + name);
            dh[0] = approach(dh[0], delHov ? 1f : 0f, 14f, dt);
            final int fy = y, fdelX = delX;
            final boolean fpref = preferred;
            final float frowHov = rh[0], fdelHov = dh[0];
            NanoVgRenderer.frame(vg -> {
                save(vg);
                globalAlpha(vg, progress);
                translate(vg, 0, slide * S);
                roundedRect(vg, fpx * S, fy * S, (fpw - 6) * S, rowH * S, 5 * S, Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), frowHov));
                if (fpref) circle(vg, (fpx + 8) * S, (fy + rowH / 2f) * S, 2.2f * S, Theme.accent());
                text(vg, (fpx + 16) * S, (fy + rowH / 2f) * S, 8.5f * S, fpref ? Theme.accent() : Theme.txt(), ALIGN_MIDDLE, name);
                text(vg, (fdelX + delW / 2f) * S, (fy + rowH / 2f) * S, 8.5f * S, Theme.colorLerp(Theme.txtDim(), 0xFFE06868, fdelHov), ALIGN_CENTER_MIDDLE, "x");
                restore(vg);
            });
            if (fullyOpen) {
                hits.add(new Object[]{"acctSelect", px, y, pw - delW - 6, rowH, name});
                hits.add(new Object[]{"acctDelete", delX, y, delW, rowH, name});
            }
            y += rowH + gap;
        }

        field(ctx, "acctNew", px, y, pw - 42, fieldH, "nickname", S);
        int addX = px + pw - 38;
        final int faddX = addX, fy2 = y;
        NanoVgRenderer.frame(vg -> {
            roundedRect(vg, faddX * S, fy2 * S, 34 * S, fieldH * S, 5 * S, Theme.accent());
            text(vg, (faddX + 17) * S, (fy2 + fieldH / 2f) * S, 8 * S, Theme.activeText(), ALIGN_CENTER_MIDDLE, "Add");
        });
        if (fullyOpen) {
            hits.add(new Object[]{"acctAdd", addX, y, 34, fieldH});
            hits.add(new Object[]{"accountPanel", px, py, pw, ph});
        }
    }

    private static String textFor(String id) {
        if ("name".equals(id)) return fcName;
        if ("acctNew".equals(id)) return acctNewName;
        if ("friendAdd".equals(id)) return friendAddName;
        return fcAddr;
    }

    private static void field(DrawContext ctx, String id, int x, int y, int w, int h, String placeholder, float S) {
        if (!NanoVgRenderer.ready()) return;
        boolean foc = id.equals(focused);
        String txt = textFor(id);
        String show = txt.isEmpty() && !foc ? placeholder : txt + (foc ? "_" : "");
        int color = txt.isEmpty() && !foc ? Theme.txtDim() : Theme.txt();
        ctx.draw();
        NanoVgRenderer.frame(vg -> {
            roundedRect(vg, x * S, y * S, w * S, h * S, 5 * S, foc ? Theme.glassHov() : Theme.glassRow());
            if (foc) roundedRect(vg, x * S, (y + h - 1) * S, w * S, 1 * S, 1 * S, Theme.accent());
            text(vg, (x + 5) * S, (y + h / 2f) * S, 8.5f * S, color, ALIGN_MIDDLE, show);
        });
        hits.add(new Object[]{"field:" + id, x, y, w, h});
    }

    // ---------------------------------------------------------------------
    // Input

    public static boolean mouseClicked(TitleScreen screen, double mouseX, double mouseY) {
        for (Object[] h : hits) {
            String kind = (String) h[0];
            int x = (int) h[1], y = (int) h[2], w = (int) h[3], hh = (int) h[4];
            if (mouseX < x || mouseX > x + w || mouseY < y || mouseY > y + hh) continue;
            a(kind)[1] = 1f;
            switch (kind) {
                case "toggle" -> onToggle(screen);
                case "gear" -> { settingsOpen = !settingsOpen; accountOpen = false; formOpen = false; }
                case "mainTheme" -> { Theme.toggle(); com.lume.client.gui.ThemeSync.save(); }
                case "mainColors" -> MinecraftClient.getInstance().setScreen(new com.lume.client.gui.ColorsScreen(screen));
                case "account" -> { accountOpen = !accountOpen; settingsOpen = false; }
                case "acctSelect" -> { Config.preferredAccount = (String) h[5]; Config.save(); }
                case "acctDelete" -> { Config.savedAccounts.remove((String) h[5]); Config.save(); }
                case "acctAdd" -> {
                    String n = acctNewName.trim();
                    if (!n.isEmpty() && !Config.savedAccounts.contains(n)) {
                        Config.savedAccounts.add(n);
                        Config.preferredAccount = n;
                        Config.save();
                    }
                    acctNewName = "";
                }
                case "openmenu" -> MinecraftClient.getInstance().setScreen(new com.lume.client.gui.ClickGuiScreen());
                case "bgCycle" -> {
                    int dir = mouseX > x + w / 2.0 ? 1 : -1;
                    CustomMenu.cycleBgStyle(dir);
                    Config.save();
                }
                case "glowToggle" -> { CustomMenu.toggleCursorGlow(); Config.save(); }
                case "fastConnectToggle" -> { CustomMenu.toggleShowFastConnect(); Config.save(); }
                case "friendsToggle" -> { CustomMenu.toggleShowFriends(); Config.save(); }
                case "accountToggle" -> { CustomMenu.toggleShowAccount(); Config.save(); }
                case "versionToggle" -> { CustomMenu.toggleShowVersion(); Config.save(); }
                case "settingsPanel" -> { /* absorb click, keep panel open */ }
                case "accountPanel" -> { /* absorb click, keep panel open */ }
                case "singleplayer" -> MinecraftClient.getInstance().setScreen(new SelectWorldScreen(screen));
                case "multiplayer" -> MinecraftClient.getInstance().setScreen(new MultiplayerScreen(screen));
                case "options" -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.setScreen(new OptionsScreen(screen, mc.options));
                }
                case "language" -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.setScreen(new LanguageOptionsScreen(screen, mc.options, mc.getLanguageManager()));
                }
                case "quit" -> MinecraftClient.getInstance().scheduleStop();
                case "openForm" -> { formOpen = true; fcName = ""; fcAddr = ""; focused = "name"; }
                case "cancelForm" -> { formOpen = false; focused = null; }
                case "saveForm" -> { FastConnect.add(fcName, fcAddr); Config.save(); formOpen = false; focused = null; }
                case "connect" -> {
                    int i = (int) h[5];
                    if (i >= 0 && i < FastConnect.list.size()) {
                        FastConnect.Entry e = FastConnect.list.get(i);
                        MinecraftClient mc = MinecraftClient.getInstance();
                        ServerInfo info = new ServerInfo(e.name, e.address, ServerInfo.ServerType.OTHER);
                        ConnectScreen.connect(screen, mc, ServerAddress.parse(e.address), info, false, null);
                    }
                }
                case "delete" -> {
                    int i = (int) h[5];
                    if (i >= 0 && i < FastConnect.list.size()) { FastConnect.remove(FastConnect.list.get(i)); Config.save(); }
                }
                case "friendConnect" -> {
                    String friendName = (String) h[5];
                    Friends.Status st = Friends.statusOf(friendName);
                    if (st != null && st.online && st.server != null) Friends.connectTo(st.server);
                }
                case "friendDelete" -> Friends.removeFriend((String) h[5]);
                case "friendAdd" -> { Friends.addFriend(friendAddName.trim()); friendAddName = ""; }
                case "field:name" -> focused = "name";
                case "field:addr" -> focused = "addr";
                case "field:acctNew" -> focused = "acctNew";
                case "field:friendAdd" -> focused = "friendAdd";
                default -> { }
            }
            return true;
        }
        if (settingsOpen) settingsOpen = false;
        if (accountOpen) accountOpen = false;
        if (focused != null) focused = null;
        return false;
    }

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focused == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { focused = null; return true; }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String v = fieldValue();
            if (!v.isEmpty()) v = v.substring(0, v.length() - 1);
            setFieldValue(v);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if ("name".equals(focused)) focused = "addr";
            else if ("addr".equals(focused)) { FastConnect.add(fcName, fcAddr); Config.save(); formOpen = false; focused = null; }
            else if ("acctNew".equals(focused)) {
                String n = acctNewName.trim();
                if (!n.isEmpty() && !Config.savedAccounts.contains(n)) {
                    Config.savedAccounts.add(n);
                    Config.preferredAccount = n;
                    Config.save();
                }
                acctNewName = "";
            }
            else if ("friendAdd".equals(focused)) { Friends.addFriend(friendAddName); friendAddName = ""; focused = null; }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) { appendChar(' '); return true; }
        String keyName = GLFW.glfwGetKeyName(keyCode, scanCode);
        if (keyName != null && keyName.length() == 1) {
            char c = keyName.charAt(0);
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 && Character.isLetter(c)) c = Character.toUpperCase(c);
            appendChar(c);
            return true;
        }
        return false;
    }

    private static String fieldValue() { return textFor(focused); }

    private static void setFieldValue(String v) {
        if ("name".equals(focused)) fcName = v;
        else if ("acctNew".equals(focused)) acctNewName = v;
        else if ("friendAdd".equals(focused)) friendAddName = v;
        else fcAddr = v;
    }

    private static void appendChar(char c) { setFieldValue(fieldValue() + c); }
}

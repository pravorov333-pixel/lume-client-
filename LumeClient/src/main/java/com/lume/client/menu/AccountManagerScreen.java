package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import com.lume.client.util.AltService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.lume.client.nanovg.NanoVgRenderer.*;

/**
 * Full-page Accounts manager (fills the whole screen, no floating bordered window — matches the
 * reference layout) — a grid of saved-nickname cards (real player head, name, edit pencil,
 * delete ✕), a Create field/button, and a Home button back to the title screen. Picking a
 * different card switches the active identity INSTANTLY via {@link AltService} (in-place
 * {@code MinecraftClient} session swap, no restart) — takes effect for whatever connects next
 * (a new world/server); it can't rename the player entity in an already-loaded world, same as
 * any offline-mode client.
 *
 * <p>Each account can optionally have a bound server address (set via its pencil/edit action) —
 * see {@link Config#accountServers} and {@code mixin.ConnectScreenMixin}, which switches to the
 * bound nickname automatically right before connecting to that server (Fast Connect or vanilla's
 * own Multiplayer list, both funnel through the same {@code ConnectScreen.connect(...)}).
 */
public class AccountManagerScreen extends Screen {

    private long lastFrame = System.currentTimeMillis();
    private final long openTime = System.currentTimeMillis();

    private static final int COLS = 3;
    private static final int CARD_W = 128, CARD_H = 34, CARD_GAP_X = 8, CARD_GAP_Y = 8;

    private final List<Object[]> hits = new ArrayList<>();   // {kind, x, y, w, h, name}
    private String focusedField = null;   // "add", "server", or null
    private String addName = "";
    private String editServer = "";
    private String editing = null;   // non-null = renaming/binding this saved nickname (Create button becomes Save)

    public AccountManagerScreen(Screen parent) {
        super(Text.literal("Lume — Accounts"));
    }

    @Override
    protected void init() {
        // The nickname you actually launched with should always be right here, even before it's
        // ever been explicitly "saved" — auto-add it once per screen open rather than making the
        // user type in the name they're already playing as.
        var session = MinecraftClient.getInstance().getSession();
        if (session != null && !session.getUsername().isBlank() && !Config.savedAccounts.contains(session.getUsername())) {
            Config.savedAccounts.add(0, session.getUsername());
            if (Config.preferredAccount == null) Config.preferredAccount = session.getUsername();
            Config.save();
        }
    }

    @Override public void close() { if (client != null) client.setScreen(new TitleScreen()); }
    @Override public boolean shouldPause() { return false; }

    private int sf() { return (int) Math.max(1, client.getWindow().getScaleFactor()); }
    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    /** Same deterministic offline-mode UUID the launcher's own offline auth uses (see
     *  {@code LumeLauncher/src/launcher.js}'s {@code offlineAuth} — md5("OfflinePlayer:"+name)),
     *  so a saved nickname's head icon shows the SAME default Steve/Alex skin variant it would
     *  actually get when you play as it. */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private record Card(String name, boolean active, int x, int y) {}

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Plain background (vanilla panorama/dirt) + Background Dim, filling the WHOLE screen —
        // no bordered floating window on top of it. Our own screens are excluded from the
        // mixin-driven "everywhere" dim (to avoid double-dimming under screens that paint their
        // own background), so it's applied directly here instead.
        this.renderBackground(ctx, mouseX, mouseY, delta);
        CustomMenu.drawDimOverlay(ctx, width, height);
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        int S = sf();
        List<String> accounts = Config.savedAccounts;
        int rows = (accounts.size() + COLS - 1) / COLS;
        int gridW = COLS * CARD_W + (COLS - 1) * CARD_GAP_X;
        int gridH = Math.max(1, rows) * CARD_H + Math.max(0, rows - 1) * CARD_GAP_Y;
        int mx = mouseX * S, my = mouseY * S;
        float p = openAnim();

        int gridX = (width - gridW) / 2;
        int gridY = Math.max(90, (height - gridH) / 2 - 40);
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < accounts.size(); i++) {
            int col = i % COLS, row = i / COLS;
            cards.add(new Card(accounts.get(i), accounts.get(i).equals(Config.preferredAccount),
                    gridX + col * (CARD_W + CARD_GAP_X), gridY + row * (CARD_H + CARD_GAP_Y)));
        }
        int fieldH = 24;
        int fy = gridY + gridH + 22;
        int fieldW = 220, addW = 90, formX = (width - fieldW - addW - 8) / 2;
        int serverY = fy + fieldH + 8;
        int homeW = 90, homeH = 26;
        int homeY = (editing != null ? serverY + fieldH : fy + fieldH) + 20;
        int homeX = (width - homeW) / 2;

        // Pass 1: card backgrounds + real player-head icons — DrawContext (a real GL skin
        // texture can't be sampled from inside a raw NanoVG paint call), flushed before the
        // NanoVG frame below draws text on TOP of this same footprint.
        for (Card c : cards) {
            boolean hov = mx >= c.x() * S && mx <= (c.x() + CARD_W) * S && my >= c.y() * S && my <= (c.y() + CARD_H) * S;
            float[] st = animFor("card:" + c.name());
            st[0] = approach(st[0], hov ? 1f : 0f, 14f, dt);
            if (!MenuAssets.blit(ctx, MenuAssets.ACCOUNT, c.x(), c.y(), CARD_W, CARD_H)) {
                int bg = c.active() ? withAlpha(Theme.accentRgb(), 0x33) : Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), st[0]);
                com.lume.client.gui.RenderUtil.roundedRect(ctx, c.x(), c.y(), CARD_W, CARD_H, 0, bg);
            } else if (st[0] > 0.02f || c.active()) {
                int tint = c.active() ? withAlpha(Theme.accentRgb(), 0x33) : (Math.round(st[0] * 44) << 24) | 0xFFFFFF;
                com.lume.client.gui.RenderUtil.roundedRect(ctx, c.x(), c.y(), CARD_W, CARD_H, 0, tint);
            }

            var session = MinecraftClient.getInstance().getSession();
            boolean isActiveSession = session != null && c.name().equals(session.getUsername());
            SkinTextures skin = isActiveSession && MinecraftClient.getInstance().player != null
                    ? MinecraftClient.getInstance().player.getSkinTextures()
                    : DefaultSkinHelper.getSkinTextures(offlineUuid(c.name()));
            int hs = CARD_H - 8;
            net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx, skin, c.x() + 4, c.y() + 4, hs);

            drawPencilIcon(ctx, c.x() + CARD_W - 30, c.y() + CARD_H / 2, 4);
        }

        try {
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                save(vg);
                globalAlpha(vg, p);

                drawPeopleIcon(vg, width / 2f * S, 30 * S, 8 * S);
                text(vg, width / 2f * S, 50 * S, 12 * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Accounts"));
                text(vg, width / 2f * S, 63 * S, 8 * S, Theme.txtDim(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Create or select an existing account"));

                for (Card c : cards) drawCardText(vg, c, mx, my, S, dt);

                boolean foc = "add".equals(focusedField);
                roundedRect(vg, formX * S, fy * S, fieldW * S, fieldH * S, 0, foc ? Theme.glassHov() : Theme.glassRow());
                if (foc) roundedRect(vg, formX * S, (fy + fieldH - 1) * S, fieldW * S, S, 1, Theme.accent());
                String placeholder = editing != null ? editing : com.lume.client.Lang.tUI("nickname");
                String show = addName.isEmpty() && !foc ? placeholder : addName + (foc ? "_" : "");
                text(vg, (formX + 10) * S, (fy + fieldH / 2f) * S, 8.5f * S, addName.isEmpty() && !foc ? Theme.txtDim() : Theme.txt(), ALIGN_MIDDLE, show);
                int addX = formX + fieldW + 8;
                roundedRect(vg, addX * S, fy * S, addW * S, fieldH * S, 0, Theme.accent());
                text(vg, (addX + addW / 2f) * S, (fy + fieldH / 2f) * S, 8.5f * S, Theme.activeText(), ALIGN_CENTER_MIDDLE,
                        editing != null ? com.lume.client.Lang.tUI("Save") : com.lume.client.Lang.tUI("Create"));
                hits.add(new Object[]{"field", formX, fy, fieldW, fieldH, null});
                hits.add(new Object[]{"submit", addX, fy, addW, fieldH, null});

                if (editing != null) {
                    boolean sFoc = "server".equals(focusedField);
                    roundedRect(vg, formX * S, serverY * S, (fieldW + addW + 8) * S, fieldH * S, 0, sFoc ? Theme.glassHov() : Theme.glassRow());
                    if (sFoc) roundedRect(vg, formX * S, (serverY + fieldH - 1) * S, (fieldW + addW + 8) * S, S, 1, Theme.accent());
                    String sShow = editServer.isEmpty() && !sFoc ? com.lume.client.Lang.tUI("bind to server ip:port (optional)") : editServer + (sFoc ? "_" : "");
                    text(vg, (formX + 10) * S, (serverY + fieldH / 2f) * S, 8.5f * S, editServer.isEmpty() && !sFoc ? Theme.txtDim() : Theme.txt(), ALIGN_MIDDLE, sShow);
                    hits.add(new Object[]{"serverField", formX, serverY, fieldW + addW + 8, fieldH, null});
                }

                roundedRect(vg, homeX * S, homeY * S, homeW * S, homeH * S, 0, Theme.glassRow());
                text(vg, (homeX + homeW / 2f) * S, (homeY + homeH / 2f) * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Home"));
                hits.add(new Object[]{"home", homeX, homeY, homeW, homeH, null});

                restore(vg);
            });
        } catch (Throwable t) {
            System.out.println("[Lume] AccountManagerScreen render failed: " + t);
        }
    }

    private float openAnim() {
        float pr = (System.currentTimeMillis() - openTime) / 160f;
        if (pr >= 1f) return 1f;
        if (pr <= 0f) return 0f;
        return 1f - (1f - pr) * (1f - pr);
    }

    /** Name text + delete ✕ (the card background/head/pencil icon were already drawn via
     *  DrawContext in the pass before this NanoVG frame started — see {@link #render}). A small
     *  server-link glyph shows under the name when this account has a bound server. */
    private void drawCardText(long vg, Card c, int mx, int my, int S, float dt) {
        int textX = c.x() + CARD_H;
        String bound = Config.accountServers.get(c.name());
        text(vg, textX * S, (c.y() + (bound != null ? CARD_H / 2f - 6 : CARD_H / 2f)) * S, 8.5f * S, c.active() ? Theme.accent() : Theme.txt(), ALIGN_MIDDLE, c.name());
        if (bound != null) text(vg, textX * S, (c.y() + CARD_H / 2f + 6) * S, 6.5f * S, Theme.txtDim(), ALIGN_MIDDLE, "→ " + bound);
        int delW = 16, delX = c.x() + CARD_W - delW - 4;
        text(vg, (delX + delW / 2f) * S, (c.y() + CARD_H / 2f) * S, 8.5f * S, Theme.txtDim(), ALIGN_CENTER_MIDDLE, "✕");
        hits.add(new Object[]{"select", c.x(), c.y(), CARD_W - 46, CARD_H, c.name()});
        hits.add(new Object[]{"edit", c.x() + CARD_W - 34, c.y(), 16, CARD_H, c.name()});
        hits.add(new Object[]{"delete", delX, c.y(), delW, CARD_H, c.name()});
    }

    /** Simple diagonal-bar pencil glyph — same rotate+roundedRect technique the old vector
     *  gear/quit icons in this client already used, avoids depending on any specific font glyph. */
    private void drawPencilIcon(DrawContext ctx, int cx, int cy, int r) {
        var ms = ctx.getMatrices();
        ms.push();
        ms.translate(cx, cy, 0);
        ms.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(45f));
        com.lume.client.gui.RenderUtil.roundedRect(ctx, -r, -1, 2 * r, 2, 1, Theme.txtDim());
        com.lume.client.gui.RenderUtil.roundedRect(ctx, r - 1, -1, 2, 2, 1, Theme.accent());
        ms.pop();
    }

    /** Two overlapping head-ish circles — the "Accounts" header glyph from the reference. */
    private void drawPeopleIcon(long vg, float cx, float cy, float r) {
        circle(vg, cx - r * 0.35f, cy, r * 0.55f, Theme.txtDim());
        circle(vg, cx + r * 0.35f, cy, r * 0.55f, Theme.txt());
    }

    private final Map<String, float[]> anim = new HashMap<>();
    private float[] animFor(String id) { return anim.computeIfAbsent(id, k -> new float[1]); }
    private static float approach(float cur, float target, float rate, float dt) { return cur + (target - cur) * Math.min(1f, rate * dt); }

    // ---------------------------------------------------------------------
    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int S = sf();
        int mx = (int) (mouseX * S), my = (int) (mouseY * S);
        for (Object[] h : hits) {
            String kind = (String) h[0];
            int x = (int) h[1], y = (int) h[2], w = (int) h[3], hh = (int) h[4];
            if (mx < x * S || mx > (x + w) * S || my < y * S || my > (y + hh) * S) continue;
            switch (kind) {
                case "select" -> {
                    String name = (String) h[5];
                    Config.preferredAccount = name;
                    Config.save();
                    AltService.changeName(name);
                }
                case "edit" -> {
                    editing = (String) h[5];
                    addName = "";
                    editServer = Config.accountServers.getOrDefault(editing, "");
                    focusedField = "add";
                }
                case "delete" -> {
                    String name = (String) h[5];
                    Config.savedAccounts.remove(name);
                    Config.accountServers.remove(name);
                    if (name.equals(Config.preferredAccount)) Config.preferredAccount = null;
                    Config.save();
                }
                case "field" -> focusedField = "add";
                case "serverField" -> focusedField = "server";
                case "submit" -> submit();
                case "home" -> close();
                default -> { }
            }
            return true;
        }
        focusedField = null;
        return true;
    }

    private void submit() {
        if (editing != null) {
            String n = addName.trim().isEmpty() ? editing : addName.trim();
            int idx = Config.savedAccounts.indexOf(editing);
            if (idx >= 0 && (n.equals(editing) || !Config.savedAccounts.contains(n))) {
                Config.savedAccounts.set(idx, n);
                if (editing.equals(Config.preferredAccount)) Config.preferredAccount = n;
                String srv = editServer.trim();
                Config.accountServers.remove(editing);
                if (!srv.isEmpty()) Config.accountServers.put(n, srv);
            }
            editing = null;
            editServer = "";
        } else {
            String n = addName.trim();
            if (n.isEmpty()) return;
            if (!Config.savedAccounts.contains(n)) {
                Config.savedAccounts.add(n);
                if (Config.preferredAccount == null) Config.preferredAccount = n;
            }
        }
        Config.save();
        addName = "";
        focusedField = null;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr < 32 || chr == 127) return super.charTyped(chr, modifiers);
        if ("add".equals(focusedField)) { addName += chr; return true; }
        if ("server".equals(focusedField)) { editServer += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editing != null) { editing = null; addName = ""; editServer = ""; focusedField = null; return true; }
            if (focusedField != null) { focusedField = null; return true; }
            close();
            return true;
        }
        if ("add".equals(focusedField) || "server".equals(focusedField)) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if ("add".equals(focusedField) && !addName.isEmpty()) addName = addName.substring(0, addName.length() - 1);
                else if ("server".equals(focusedField) && !editServer.isEmpty()) editServer = editServer.substring(0, editServer.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if ("add".equals(focusedField) && editing != null) focusedField = "server";
                else submit();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

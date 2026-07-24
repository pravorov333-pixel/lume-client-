package com.lume.client.menu;

import com.lume.client.Config;
import com.lume.client.gui.RenderUtil;
import com.lume.client.gui.Theme;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.util.AltService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-page Accounts manager (fills the whole screen, no floating bordered window — matches the
 * reference layout) — a grid of saved-nickname cards (real player head, name, edit action shown
 * as a globe/planet glyph — matches the Language button's icon in the main menu, same rename +
 * server-bind function underneath, delete ✕), a Create field/button, and a Home button back to
 * the title screen. Picking a
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

    private record Card(String name, boolean active, int x, int y) {}

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Plain background (vanilla panorama/dirt) + Background Dim, filling the WHOLE screen —
        // no bordered floating window on top of it. Our own screens are excluded from the
        // mixin-driven "everywhere" dim (to avoid double-dimming under screens that paint their
        // own background), so it's applied directly here instead.
        this.renderBackground(ctx, mouseX, mouseY, delta);
        CustomMenu.drawDimOverlay(ctx, width, height);
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();

        List<String> accounts = Config.savedAccounts;
        int rows = (accounts.size() + COLS - 1) / COLS;
        int gridW = COLS * CARD_W + (COLS - 1) * CARD_GAP_X;
        int gridH = Math.max(1, rows) * CARD_H + Math.max(0, rows - 1) * CARD_GAP_Y;
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

        try {
            var tr = MinecraftClient.getInstance().textRenderer;

            // Card backgrounds + real player-head icons + pencil, then name/delete text — all
            // plain DrawContext now, no separate NanoVG pass needed.
            for (Card c : cards) {
                boolean hov = mouseX >= c.x() && mouseX <= c.x() + CARD_W && mouseY >= c.y() && mouseY <= c.y() + CARD_H;
                float[] st = animFor("card:" + c.name());
                st[0] = approach(st[0], hov ? 1f : 0f, 9f, dt);
                int fill = c.active() ? withAlpha(Theme.accentRgb(), 0x33) : Theme.colorLerp(Theme.glassRow(), Theme.glassHov(), st[0] * 0.5f);
                RenderUtil.premiumBg(ctx, c.x(), c.y(), CARD_W, CARD_H, 6, st[0], fade(fill, p), fade(Theme.rim(), p), Theme.accentRgb());
                int ly = c.y() - RenderUtil.premiumLift(st[0]);

                int hs = CARD_H - 8;
                RenderUtil.drawFace(ctx, c.x() + 4, ly + 4, hs);

                // Edit action (rename + server-bind) — rendered as a globe/planet glyph, same as
                // the Language button in the main menu, per the user's explicit ask; still the
                // same "edit" action underneath.
                MenuAssets.blit(ctx, MenuAssets.IC_GLOBE, c.x() + CARD_W - 34, ly + CARD_H / 2 - 6, 12, 12);
                drawCardText(ctx, tr, c, p, ly);
            }

            int markSize = 22;
            RenderUtil.drawLogo(ctx, Math.round(width / 2f - markSize / 2f), 16, markSize);
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Accounts"), 0, 44, width, 12, fade(Theme.txt(), p), 0.67f);
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Create or select an existing account"), 0, 59, width, 8, fade(Theme.txtDim(), p), 0.44f);

            boolean foc = "add".equals(focusedField);
            RenderUtil.roundedRect(ctx, formX, fy, fieldW, fieldH, 0, fade(foc ? Theme.glassHov() : Theme.glassRow(), p));
            if (foc) RenderUtil.roundedRect(ctx, formX, fy + fieldH - 1, fieldW, 1, 1, fade(Theme.accent(), p));
            String placeholder = editing != null ? editing : com.lume.client.Lang.tUI("nickname");
            String show = addName.isEmpty() && !foc ? placeholder : addName + (foc ? "_" : "");
            RenderUtil.textVCentered(ctx, tr, show, formX + 10, fy, fieldH, fade(addName.isEmpty() && !foc ? Theme.txtDim() : Theme.txt(), p), 0.47f);
            int addX = formX + fieldW + 8;
            RenderUtil.roundedRect(ctx, addX, fy, addW, fieldH, 0, fade(Theme.accent(), p));
            RenderUtil.textCentered(ctx, tr, editing != null ? com.lume.client.Lang.tUI("Save") : com.lume.client.Lang.tUI("Create"),
                    addX, fy, addW, fieldH, fade(Theme.activeText(), p), 0.47f);
            hits.add(new Object[]{"field", formX, fy, fieldW, fieldH, null});
            hits.add(new Object[]{"submit", addX, fy, addW, fieldH, null});

            if (editing != null) {
                boolean sFoc = "server".equals(focusedField);
                RenderUtil.roundedRect(ctx, formX, serverY, fieldW + addW + 8, fieldH, 0, fade(sFoc ? Theme.glassHov() : Theme.glassRow(), p));
                if (sFoc) RenderUtil.roundedRect(ctx, formX, serverY + fieldH - 1, fieldW + addW + 8, 1, 1, fade(Theme.accent(), p));
                String sShow = editServer.isEmpty() && !sFoc ? com.lume.client.Lang.tUI("bind to server ip:port (optional)") : editServer + (sFoc ? "_" : "");
                RenderUtil.textVCentered(ctx, tr, sShow, formX + 10, serverY, fieldH, fade(editServer.isEmpty() && !sFoc ? Theme.txtDim() : Theme.txt(), p), 0.47f);
                hits.add(new Object[]{"serverField", formX, serverY, fieldW + addW + 8, fieldH, null});
            }

            RenderUtil.roundedRect(ctx, homeX, homeY, homeW, homeH, 0, fade(Theme.glassRow(), p));
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Home"), homeX, homeY, homeW, homeH, fade(Theme.txt(), p), 0.47f);
            hits.add(new Object[]{"home", homeX, homeY, homeW, homeH, null});
        } catch (Throwable t) {
            System.out.println("[Lume] AccountManagerScreen render failed: " + t);
        }
    }

    /** Multiplies an ARGB color's alpha by {@code p} — the DrawContext equivalent of NanoVG's
     *  {@code globalAlpha}, which has no per-call analogue here so each draw bakes it in. */
    private static int fade(int argb, float p) {
        int a = Math.round(((argb >>> 24) & 0xFF) * p);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private float openAnim() {
        float pr = (System.currentTimeMillis() - openTime) / 160f;
        if (pr >= 1f) return 1f;
        if (pr <= 0f) return 0f;
        return 1f - (1f - pr) * (1f - pr);
    }

    /** Name text + delete ✕ (the card background/head/globe icon were already drawn just before
     *  this call — see {@link #render}). {@code ly} is the card's lifted-on-hover draw position;
     *  hit zones below intentionally stay keyed to the true {@code c.y()} so clicking doesn't
     *  shift with the cosmetic lift. A small server-link glyph shows under the name when this
     *  account has a bound server. */
    private void drawCardText(DrawContext ctx, net.minecraft.client.font.TextRenderer tr, Card c, float p, int ly) {
        int textX = c.x() + CARD_H;
        String bound = Config.accountServers.get(c.name());
        int nameY = bound != null ? ly + CARD_H / 2 - 6 : ly + CARD_H / 2;
        RenderUtil.text(ctx, tr, c.name(), textX, nameY - 3, fade(c.active() ? Theme.accent() : Theme.txt(), p), false, 0.47f);
        if (bound != null) RenderUtil.text(ctx, tr, "→ " + bound, textX, ly + CARD_H / 2 + 3, fade(Theme.txtDim(), p), false, 0.36f);
        int delW = 16, delX = c.x() + CARD_W - delW - 4;
        RenderUtil.textCentered(ctx, tr, "✕", delX, ly, delW, CARD_H, fade(Theme.txtDim(), p), 0.47f);
        hits.add(new Object[]{"select", c.x(), c.y(), CARD_W - 46, CARD_H, c.name()});
        hits.add(new Object[]{"edit", c.x() + CARD_W - 34, c.y(), 16, CARD_H, c.name()});
        hits.add(new Object[]{"delete", delX, c.y(), delW, CARD_H, c.name()});
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

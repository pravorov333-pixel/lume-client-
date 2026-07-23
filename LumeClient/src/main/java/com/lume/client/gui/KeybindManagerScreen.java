package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.command.MacroManager;
import com.lume.client.fthw.QuickCommands;
import com.lume.client.module.Module;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.lume.client.nanovg.NanoVgRenderer.*;

/**
 * Full visual keyboard overview of every keybind in the client — module toggles (bindable
 * modules), FT/HW quick commands, and free-form command macros (see {@link MacroManager}) — all
 * three sources lit up on one keyboard so nothing is hidden in a separate tab. A lit key shows
 * what it's bound to; clicking it opens {@link BindPopup} (unbind/edit if already bound, or the
 * Command-vs-Function bind menu if not).
 *
 * <p>Layout matches a real 65%-class compact keyboard (Keychron K6-style, per the user's own
 * reference): 4 main rows with NO function row, a narrow Del/Page Up/Page Down column down the
 * right edge of rows 1-3, Up on row 4's right edge, and Left/Down/Right inline at the end of the
 * bottom row — not a detached arrow-key cluster off to the side.
 */
public class KeybindManagerScreen extends Screen {

    private record Key(String label, int code, float w) {}

    // 4 main rows (no F-row) + a narrow right-side Del/PgUp/PgDn/Up column + inline bottom-row
    // arrows — a real 65%-class compact layout, not a full-size board with a detached arrow pad.
    private static final Key[][] ROWS = {
        { new Key("Esc", GLFW.GLFW_KEY_ESCAPE, 1), new Key("1", GLFW.GLFW_KEY_1, 1), new Key("2", GLFW.GLFW_KEY_2, 1),
          new Key("3", GLFW.GLFW_KEY_3, 1), new Key("4", GLFW.GLFW_KEY_4, 1), new Key("5", GLFW.GLFW_KEY_5, 1),
          new Key("6", GLFW.GLFW_KEY_6, 1), new Key("7", GLFW.GLFW_KEY_7, 1), new Key("8", GLFW.GLFW_KEY_8, 1),
          new Key("9", GLFW.GLFW_KEY_9, 1), new Key("0", GLFW.GLFW_KEY_0, 1), new Key("-", GLFW.GLFW_KEY_MINUS, 1),
          new Key("=", GLFW.GLFW_KEY_EQUAL, 1), new Key("Backspace", GLFW.GLFW_KEY_BACKSPACE, 2) },
        { new Key("Tab", GLFW.GLFW_KEY_TAB, 1.5f), new Key("Q", GLFW.GLFW_KEY_Q, 1), new Key("W", GLFW.GLFW_KEY_W, 1),
          new Key("E", GLFW.GLFW_KEY_E, 1), new Key("R", GLFW.GLFW_KEY_R, 1), new Key("T", GLFW.GLFW_KEY_T, 1),
          new Key("Y", GLFW.GLFW_KEY_Y, 1), new Key("U", GLFW.GLFW_KEY_U, 1), new Key("I", GLFW.GLFW_KEY_I, 1),
          new Key("O", GLFW.GLFW_KEY_O, 1), new Key("P", GLFW.GLFW_KEY_P, 1), new Key("[", GLFW.GLFW_KEY_LEFT_BRACKET, 1),
          new Key("]", GLFW.GLFW_KEY_RIGHT_BRACKET, 1), new Key("\\", GLFW.GLFW_KEY_BACKSLASH, 1.5f) },
        { new Key("Caps", GLFW.GLFW_KEY_CAPS_LOCK, 1.75f), new Key("A", GLFW.GLFW_KEY_A, 1), new Key("S", GLFW.GLFW_KEY_S, 1),
          new Key("D", GLFW.GLFW_KEY_D, 1), new Key("F", GLFW.GLFW_KEY_F, 1), new Key("G", GLFW.GLFW_KEY_G, 1),
          new Key("H", GLFW.GLFW_KEY_H, 1), new Key("J", GLFW.GLFW_KEY_J, 1), new Key("K", GLFW.GLFW_KEY_K, 1),
          new Key("L", GLFW.GLFW_KEY_L, 1), new Key(";", GLFW.GLFW_KEY_SEMICOLON, 1), new Key("'", GLFW.GLFW_KEY_APOSTROPHE, 1),
          new Key("Enter", GLFW.GLFW_KEY_ENTER, 2.25f) },
        { new Key("Shift", GLFW.GLFW_KEY_LEFT_SHIFT, 2.25f), new Key("Z", GLFW.GLFW_KEY_Z, 1), new Key("X", GLFW.GLFW_KEY_X, 1),
          new Key("C", GLFW.GLFW_KEY_C, 1), new Key("V", GLFW.GLFW_KEY_V, 1), new Key("B", GLFW.GLFW_KEY_B, 1),
          new Key("N", GLFW.GLFW_KEY_N, 1), new Key("M", GLFW.GLFW_KEY_M, 1), new Key(",", GLFW.GLFW_KEY_COMMA, 1),
          new Key(".", GLFW.GLFW_KEY_PERIOD, 1), new Key("/", GLFW.GLFW_KEY_SLASH, 1), new Key("Shift", GLFW.GLFW_KEY_RIGHT_SHIFT, 1.75f) },
        { new Key("Ctrl", GLFW.GLFW_KEY_LEFT_CONTROL, 1.25f), new Key("Win", GLFW.GLFW_KEY_LEFT_SUPER, 1.25f),
          new Key("Alt", GLFW.GLFW_KEY_LEFT_ALT, 1.25f), new Key("Space", GLFW.GLFW_KEY_SPACE, 6.25f),
          new Key("Alt", GLFW.GLFW_KEY_RIGHT_ALT, 1.25f), new Key("Ctrl", GLFW.GLFW_KEY_RIGHT_CONTROL, 1.25f) },
    };

    /** Narrow right-edge column, aligned with rows 1-3 (Del/PgUp/PgDn) — the 65%-layout
     *  navigation cluster, not a detached block. */
    private static final Key[] SIDE_COL = {
        new Key("Del", GLFW.GLFW_KEY_DELETE, 1), new Key("PgUp", GLFW.GLFW_KEY_PAGE_UP, 1), new Key("PgDn", GLFW.GLFW_KEY_PAGE_DOWN, 1),
    };
    private static final Key UP_KEY = new Key("↑", GLFW.GLFW_KEY_UP, 1);
    private static final Key[] BOTTOM_ARROWS = { new Key("←", GLFW.GLFW_KEY_LEFT, 1), new Key("↓", GLFW.GLFW_KEY_DOWN, 1), new Key("→", GLFW.GLFW_KEY_RIGHT, 1) };

    private final Screen parent;
    private long lastFrame = System.currentTimeMillis();
    private final long openTime = System.currentTimeMillis();

    private static final int UNIT = 34, KEY_GAP = 4, ROW_GAP = 4;
    private static final int MAIN_ROWS_W = 15;   // widest main row, in units — where the side column starts

    private final List<Object[]> keyHits = new ArrayList<>();   // {Key, x, y, w, h}

    public KeybindManagerScreen(Screen parent, Integer prefillKey) {
        super(Text.literal("Lume — Keybind Manager"));
        this.parent = parent;
        if (prefillKey != null) {
            for (Key k : allKeys()) if (k.code() == prefillKey) { BindPopup.open(k.code(), k.label()); break; }
        }
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private int sf() { return (int) Math.max(1, client.getWindow().getScaleFactor()); }
    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    private static List<Key> allKeys() {
        List<Key> out = new ArrayList<>();
        for (Key[] row : ROWS) for (Key k : row) out.add(k);
        out.addAll(List.of(SIDE_COL));
        out.add(UP_KEY);
        out.addAll(List.of(BOTTOM_ARROWS));
        return out;
    }

    private static List<Module> allModulesBound(int code) {
        List<Module> out = new ArrayList<>();
        for (Module m : LumeClient.MODULES.getModules()) if (m.isBindable() && m.getKey() == code) out.add(m);
        return out;
    }

    /** Display label for a GLFW key code, e.g. for {@link BindPopup}'s header when opened from
     *  somewhere other than clicking an actual key on this keyboard (see ClickGuiScreen's "Bind
     *  command" button, which captures a key press directly). */
    public static String labelFor(int code) {
        for (Key k : allKeys()) if (k.code() == code) return k.label();
        return "Key " + code;
    }

    private static boolean isBound(int code) {
        if (!allModulesBound(code).isEmpty()) return true;
        for (QuickCommands.Cmd c : QuickCommands.list) if (c.key == code) return true;
        for (MacroManager.Macro mac : MacroManager.macros) if (mac.key == code) return true;
        return false;
    }

    // ---------------------------------------------------------------------
    // Render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Plain background + Background Dim, no parent screen rendered behind us — a genuinely
        // separate page, not an overlay on top of ClickGUI.
        this.renderBackground(ctx, mouseX, mouseY, delta);
        CustomMenu.drawDimOverlay(ctx, width, height);
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        keyHits.clear();

        int S = sf();
        int mx = mouseX * S, my = mouseY * S;
        float p = openAnim();

        int exitW = 70, exitH = 24, exitX = 12, exitY = 12;

        int kbUnitsW = MAIN_ROWS_W + 1;   // + side column
        int winW = 32 + kbUnitsW * UNIT, winH = 300;
        int W = winW * S, H = winH * S;
        int x = (width * S - W) / 2, y = (height * S - H) / 2;
        int r = 16 * S;

        try {
            ctx.draw();
            NanoVgRenderer.frame(vg -> {
                save(vg);
                globalAlpha(vg, p);

                roundedRect(vg, exitX * S, exitY * S, exitW * S, exitH * S, 6 * S, Theme.glassRow());
                text(vg, (exitX + exitW / 2f) * S, (exitY + exitH / 2f) * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("← Exit"));

                shadow(vg, x, y, W, H, r, 22 * S, 0x70000000);
                shadow(vg, x, y, W, H, r, 30 * S, withAlpha(Theme.accentRgb(), 0x33));
                gradientRoundedRect(vg, x, y, W, H, r, Theme.winTop(), Theme.winBot());
                strokeRoundedRect(vg, x + 0.5f * S, y + 0.5f * S, W - S, H - S, r, S, Theme.rim());
                text(vg, x + W / 2f, y + 20 * S, 11.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Keybind Manager"));
                text(vg, x + W / 2f, y + 34 * S, 8 * S, Theme.txtDim(), ALIGN_CENTER_MIDDLE,
                        com.lume.client.Lang.tUI("Lit keys are bound — click to inspect. Click an empty key to bind."));

                int kbX = x + 16 * S, kbY = y + 46 * S;
                drawKeyboard(vg, kbX, kbY, S, mx, my);

                if (BindPopup.isActive()) {
                    int akX = 0, akY = 0, akH = 0;
                    for (Object[] h : keyHits) if (((Key) h[0]).code() == BindPopup.keyCode()) { akX = (int) h[1]; akY = (int) h[2]; akH = (int) h[4]; }
                    BindPopup.render(vg, akX, akY + akH + 4 * S, width * S, height * S, S, mx, my, dt);
                }

                restore(vg);
            });
        } catch (Throwable t) {
            System.out.println("[Lume] KeybindManagerScreen render failed: " + t);
        }
    }

    private float openAnim() {
        float pr = (System.currentTimeMillis() - openTime) / 160f;
        if (pr >= 1f) return 1f;
        if (pr <= 0f) return 0f;
        return 1f - (1f - pr) * (1f - pr);
    }

    private void drawKeyboard(long vg, int kbX, int kbY, int S, int mx, int my) {
        int sideX = kbX + MAIN_ROWS_W * UNIT * S + 8 * S;
        int yy = kbY;
        int bottomRowEndX = kbX, bottomRowY = kbY;
        for (int r = 0; r < ROWS.length; r++) {
            int xx = kbX;
            for (Key k : ROWS[r]) {
                int w = Math.round(k.w() * UNIT * S) - KEY_GAP * S;
                int h = UNIT * S - ROW_GAP * S;
                drawKey(vg, k, xx, yy, w, h, S, mx, my);
                xx += Math.round(k.w() * UNIT * S);
            }
            if (r == ROWS.length - 1) { bottomRowEndX = xx; bottomRowY = yy; }   // real accumulated width, not a hand-summed guess
            // side column: Del/PgUp/PgDn next to rows 1-3, Up next to row 4
            int sw = UNIT * S - KEY_GAP * S, sh = UNIT * S - ROW_GAP * S;
            if (r < SIDE_COL.length) drawKey(vg, SIDE_COL[r], sideX, yy, sw, sh, S, mx, my);
            else if (r == 3) drawKey(vg, UP_KEY, sideX, yy, sw, sh, S, mx, my);
            yy += UNIT * S;
        }
        // arrows go inline right after the bottom row's own last key
        int bx = bottomRowEndX + 8 * S;
        int aw = UNIT * S - KEY_GAP * S, ah = UNIT * S - ROW_GAP * S;
        for (Key k : BOTTOM_ARROWS) { drawKey(vg, k, bx, bottomRowY, aw, ah, S, mx, my); bx += UNIT * S; }
    }

    private void drawKey(long vg, Key k, int x, int y, int w, int h, int S, int mx, int my) {
        keyHits.add(new Object[]{k, x, y, w, h});
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        boolean bound = isBound(k.code());
        boolean open = BindPopup.isActive() && BindPopup.keyCode() == k.code();
        int fill = bound ? withAlpha(Theme.accentRgb(), open ? 0xCC : (hov ? 0xAA : 0x88))
                          : (hov ? Theme.glassHov() : Theme.glassRow());
        if (bound) neonGlow(vg, x, y, w, h, 6 * S, 8 * S, withAlpha(Theme.accentRgb(), hov ? 0x55 : 0x30));
        roundedRect(vg, x, y, w, h, 5 * S, fill);
        text(vg, x + w / 2f, y + h / 2f, 7.5f * S, bound ? Theme.activeText() : Theme.txt(), ALIGN_CENTER_MIDDLE, k.label());
    }

    // ---------------------------------------------------------------------
    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int S = sf();
        int mx = (int) (mouseX * S), my = (int) (mouseY * S);

        int exitW = 70, exitH = 24, exitX = 12, exitY = 12;
        if (mx >= exitX * S && mx <= (exitX + exitW) * S && my >= exitY * S && my <= (exitY + exitH) * S) { close(); return true; }

        if (BindPopup.isActive() && BindPopup.mouseClicked(mx, my, S)) return true;

        for (Object[] h : keyHits) {
            Key k = (Key) h[0];
            int x = (int) h[1], y = (int) h[2], w = (int) h[3], hh = (int) h[4];
            if (mx >= x && mx <= x + w && my >= y && my <= y + hh) {
                BindPopup.open(k.code(), k.label());
                return true;
            }
        }
        BindPopup.close();
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (BindPopup.isActive() && BindPopup.charTyped(chr)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BindPopup.isActive()) {
            if (BindPopup.keyPressed(keyCode, scanCode, modifiers)) return true;
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

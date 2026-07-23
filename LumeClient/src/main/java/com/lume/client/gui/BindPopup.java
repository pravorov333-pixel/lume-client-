package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.command.MacroManager;
import com.lume.client.fthw.QuickCommands;
import com.lume.client.module.Module;
import com.lume.client.nanovg.NanoVgRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.lume.client.nanovg.NanoVgRenderer.*;

/**
 * "What should this key do" popup — Command (a macro, see {@link MacroManager}, always sent as a
 * chat command) or Function (any module with {@code isBindable()} true, searched live). A single
 * reusable widget: renders anchored near whatever point the caller gives it each frame, clamped
 * inside the screen, instead of being its own full-screen {@code Screen}. Used by both {@code
 * KeybindManagerScreen} (anchored near a keyboard key) and {@code ClickGuiScreen}'s "Bind
 * command" button (anchored near the button itself — the whole point: no full-screen takeover for
 * a quick single-key bind). Only one instance is ever open at a time (a static singleton), since
 * only one bind action can be in flight at once — the caller checks {@link #isActive()} and
 * routes input to {@link #mouseClicked}/{@link #charTyped}/{@link #keyPressed} while it is.
 */
public final class BindPopup {
    private BindPopup() {}

    private enum Source { MODULE, QUICK, MACRO }
    private record BindInfo(Source source, String text, Object ref) {}
    private enum Mode { INFO, CHOOSE, COMMAND, FUNCTION }

    private static boolean active = false;
    private static int keyCode = -1;
    private static String keyLabel = "";
    private static Mode mode = Mode.INFO;
    private static String editText = "";
    private static String search = "";
    private static boolean searchFocused = false;
    private static long openTime, modeTime;

    // Brief confirmation flash on binding a Function/Command, before the popup actually closes —
    // otherwise a pick reads as the popup just vanishing with no acknowledgment.
    private static boolean selecting = false;
    private static long selectTime;
    private static String selectedLabel = "";
    private static final long FLASH_MS = 260;

    private static void completeAndFlash(String label) {
        selecting = true;
        selectTime = System.currentTimeMillis();
        selectedLabel = label;
    }

    private static final List<Object[]> hits = new ArrayList<>();   // {kind, x, y, w, h}
    private static int lastPx, lastPy, lastPw, lastPh;

    public static boolean isActive() { return active; }
    public static int keyCode() { return keyCode; }

    /** Opens for a given GLFW key code; {@code label} is just the display name shown in the
     *  popup header (a keyboard key glyph, or the raw key name for a captured "Bind command"
     *  press — the caller decides). */
    public static void open(int code, String label) {
        active = true;
        keyCode = code;
        keyLabel = label;
        search = "";
        searchFocused = false;
        editText = "";
        selecting = false;
        mode = bindsFor(code).isEmpty() ? Mode.CHOOSE : Mode.INFO;
        long now = System.currentTimeMillis();
        openTime = now;
        modeTime = now;
    }

    public static void close() { active = false; }

    private static void setMode(Mode m) { mode = m; modeTime = System.currentTimeMillis(); }

    private static List<BindInfo> bindsFor(int code) {
        List<BindInfo> out = new ArrayList<>();
        for (Module m : LumeClient.MODULES.getModules()) {
            if (m.isBindable() && m.getKey() == code) out.add(new BindInfo(Source.MODULE, m.getName(), m));
        }
        for (QuickCommands.Cmd c : QuickCommands.list) {
            if (c.key == code) out.add(new BindInfo(Source.QUICK, c.command, c));
        }
        for (MacroManager.Macro mac : MacroManager.macros) {
            if (mac.key == code) out.add(new BindInfo(Source.MACRO, mac.text, mac));
        }
        return out;
    }

    /** Every module opted into being bindable — queried live, never hand-maintained. */
    private static List<Module> filteredModules() {
        List<Module> out = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (Module m : LumeClient.MODULES.getModules()) {
            if (!m.isBindable()) continue;
            if (q.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        return out;
    }

    private static float ease(long since, float ms) {
        float pr = (System.currentTimeMillis() - since) / ms;
        if (pr >= 1f) return 1f;
        if (pr <= 0f) return 0f;
        return 1f - (1f - pr) * (1f - pr);
    }

    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    public static int height(int S) {
        int rowH = 16 * S;
        return switch (mode) {
            case CHOOSE -> 26 * S + 22 * S + 6 * S + 14 * S + 8 * S;
            case COMMAND -> 26 * S + rowH + 24 * S + 8 * S;
            case FUNCTION -> {
                int n = Math.max(1, filteredModules().size());
                yield 26 * S + (n + 1) * (rowH + 4 * S) + 8 * S;
            }
            case INFO -> {
                List<BindInfo> binds = bindsFor(keyCode);
                int n = Math.max(1, binds.size());
                yield 26 * S + n * (rowH + 4 * S) + (binds.stream().anyMatch(b -> b.source() == Source.MACRO) ? 14 * S : 0) + 8 * S;
            }
        };
    }

    /** Draws near {@code (anchorX, anchorBelowY)}, sliding up to stay inside the screen if it
     *  doesn't fit below. Fades + slides in on open, and again on every mode switch (Command vs
     *  Function, etc.) — matches the same ease-out easing every other Lume popup/window uses. */
    public static void render(long vg, int anchorX, int anchorBelowY, int screenW, int screenH, int S, int mx, int my, float dt) {
        if (!active) return;
        hits.clear();
        int pw = 220 * S, ph = height(S);
        int px = Math.max(8 * S, Math.min(anchorX, screenW - pw - 8 * S));
        int py = Math.max(8 * S, Math.min(anchorBelowY, screenH - ph - 8 * S));
        lastPx = px; lastPy = py; lastPw = pw; lastPh = ph;

        float p = ease(openTime, 140f);
        save(vg);
        globalAlpha(vg, p);
        translate(vg, 0, -(1f - p) * 6f * S);

        shadow(vg, px, py, pw, ph, 10 * S, 14 * S, 0x55000000);
        roundedRect(vg, px, py, pw, ph, 10 * S, Theme.winBg());
        strokeRoundedRect(vg, px + 0.5f * S, py + 0.5f * S, pw - S, ph - S, 10 * S, S, Theme.rim());
        List<BindInfo> binds = bindsFor(keyCode);
        text(vg, px + 10 * S, py + 14 * S, 9 * S, Theme.txtDim(), ALIGN_MIDDLE, keyLabel + (binds.isEmpty() ? " — " + com.lume.client.Lang.tUI("unbound") : ""));

        // Mode content fades/slides in on its OWN timer, separate from the popup's own open
        // animation, so switching Command<->Function reads as a deliberate transition too.
        float mp = ease(modeTime, 120f);
        save(vg);
        globalAlpha(vg, mp);
        translate(vg, 0, -(1f - mp) * 4f * S);
        int ry = py + 26 * S, rowH = 16 * S;
        switch (mode) {
            case CHOOSE -> {
                int halfW = (pw - 24 * S) / 2;
                roundedRect(vg, px + 10 * S, ry, halfW, 22 * S, 7 * S, Theme.accent());
                text(vg, px + 10 * S + halfW / 2f, ry + 11 * S, 8.5f * S, Theme.activeText(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Command"));
                roundedRect(vg, px + 14 * S + halfW, ry, halfW, 22 * S, 7 * S, Theme.glassRow());
                text(vg, px + 14 * S + halfW + halfW / 2f, ry + 11 * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Function"));
                ry += 22 * S + 6 * S;
                text(vg, px + 10 * S, ry, 7 * S, Theme.txtDim(), ALIGN_MIDDLE, com.lume.client.Lang.tUI("Command: sent to chat. Function: toggles a module."));
            }
            case COMMAND -> {
                roundedRect(vg, px + 10 * S, ry, pw - 20 * S, rowH, 5 * S, Theme.glassHov());
                roundedRect(vg, px + 10 * S, ry + rowH - Math.max(1, S), pw - 20 * S, Math.max(1, S), 1, Theme.accent());
                String show = editText.isEmpty() ? com.lume.client.Lang.tUI("/command…") : editText + "_";
                text(vg, px + 15 * S, ry + rowH / 2f, 8 * S, editText.isEmpty() ? Theme.txtDim() : Theme.txt(), ALIGN_MIDDLE, show);
                ry += rowH + 6 * S;
                int halfW = (pw - 24 * S) / 2;
                roundedRect(vg, px + 10 * S, ry, halfW, 18 * S, 6 * S, Theme.accent());
                text(vg, px + 10 * S + halfW / 2f, ry + 9 * S, 8.5f * S, Theme.activeText(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Save"));
                roundedRect(vg, px + 14 * S + halfW, ry, halfW, 18 * S, 6 * S, Theme.glassRow());
                text(vg, px + 14 * S + halfW + halfW / 2f, ry + 9 * S, 8.5f * S, Theme.txt(), ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Cancel"));
            }
            case FUNCTION -> {
                roundedRect(vg, px + 10 * S, ry, pw - 20 * S, rowH, 5 * S, searchFocused ? Theme.glassHov() : Theme.glassRow());
                String show = search.isEmpty() && !searchFocused ? com.lume.client.Lang.tUI("search functions…") : search + (searchFocused ? "_" : "");
                text(vg, px + 15 * S, ry + rowH / 2f, 8 * S, search.isEmpty() && !searchFocused ? Theme.txtDim() : Theme.txt(), ALIGN_MIDDLE, show);
                ry += rowH + 4 * S;
                List<Module> list = filteredModules();
                for (Module m : list) {
                    roundedRect(vg, px + 10 * S, ry, pw - 20 * S, rowH, 5 * S, Theme.glassRow());
                    text(vg, px + 15 * S, ry + rowH / 2f, 8 * S, Theme.txt(), ALIGN_MIDDLE, m.getName());
                    ry += rowH + 4 * S;
                }
                if (list.isEmpty()) text(vg, px + 10 * S, ry + 4 * S, 7.5f * S, Theme.txtDim(), ALIGN_MIDDLE, com.lume.client.Lang.tUI("no match"));
            }
            case INFO -> {
                for (BindInfo b : binds) {
                    String badge = switch (b.source()) { case MODULE -> "Module"; case QUICK -> "Quick"; case MACRO -> "Macro"; };
                    roundedRect(vg, px + 10 * S, ry, pw - 20 * S, rowH, 5 * S, Theme.glassRow());
                    text(vg, px + 14 * S, ry + rowH / 2f, 7.5f * S, Theme.accent(), ALIGN_MIDDLE, badge);
                    text(vg, px + 52 * S, ry + rowH / 2f, 8 * S, Theme.txt(), ALIGN_MIDDLE, b.text());
                    int xW = 14 * S;
                    text(vg, px + pw - 10 * S - xW / 2f, ry + rowH / 2f, 8.5f * S, 0xFFE06868, ALIGN_CENTER_MIDDLE, "✕");
                    ry += rowH + 4 * S;
                }
                if (binds.stream().anyMatch(b -> b.source() == Source.MACRO)) {
                    text(vg, px + 10 * S, ry + 4 * S, 7.5f * S, Theme.txtDim(), ALIGN_MIDDLE, com.lume.client.Lang.tUI("click to unbind, or the text to edit"));
                }
            }
        }
        restore(vg);
        restore(vg);

        if (selecting) {
            long elapsed = System.currentTimeMillis() - selectTime;
            float inP = ease(selectTime, 120f);
            save(vg);
            globalAlpha(vg, inP * p);
            roundedRect(vg, px, py, pw, ph, 10 * S, withAlpha(Theme.accent(), 235));
            text(vg, px + pw / 2f, py + ph / 2f, 9 * S, Theme.activeText(), ALIGN_CENTER_MIDDLE,
                    "✓ " + com.lume.client.Lang.tUI("Bound") + ": " + selectedLabel);
            restore(vg);
            if (elapsed > FLASH_MS) { selecting = false; close(); }
        }
    }

    /** @return true if the click was consumed (inside the popup bounds, whether or not it hit a
     *  specific control) — the caller should NOT also treat it as a click on whatever's behind. */
    public static boolean mouseClicked(int mx, int my, int S) {
        if (!active) return false;
        if (selecting) return true;   // ignore clicks during the confirmation flash
        int px = lastPx, py = lastPy, pw = lastPw, ph = lastPh;
        if (mx < px || mx > px + pw || my < py || my > py + ph) return false;   // outside — caller decides what happens

        int ry = py + 26 * S, rowH = 16 * S;
        switch (mode) {
            case CHOOSE -> {
                int halfW = (pw - 24 * S) / 2;
                if (my >= ry && my <= ry + 22 * S) {
                    if (mx >= px + 10 * S && mx <= px + 10 * S + halfW) setMode(Mode.COMMAND);
                    else if (mx >= px + 14 * S + halfW) { setMode(Mode.FUNCTION); search = ""; searchFocused = true; }
                }
            }
            case COMMAND -> {
                ry += rowH + 6 * S;
                int halfW = (pw - 24 * S) / 2;
                if (my >= ry && my <= ry + 18 * S) {
                    if (mx >= px + 10 * S && mx <= px + 10 * S + halfW) {
                        if (!editText.isBlank()) {
                            MacroManager.add(keyCode, editText.trim());
                            com.lume.client.Config.save();
                            completeAndFlash(editText.trim());
                        } else close();
                    } else if (mx >= px + 14 * S + halfW) {
                        close();
                    }
                }
            }
            case FUNCTION -> {
                if (my >= ry && my <= ry + rowH) { searchFocused = true; return true; }
                ry += rowH + 4 * S;
                for (Module m : filteredModules()) {
                    if (my >= ry && my <= ry + rowH) {
                        m.setKey(keyCode);
                        com.lume.client.Config.save();
                        completeAndFlash(m.getName());
                        return true;
                    }
                    ry += rowH + 4 * S;
                }
            }
            case INFO -> {
                List<BindInfo> binds = bindsFor(keyCode);
                for (BindInfo b : binds) {
                    if (my >= ry && my <= ry + rowH) {
                        int xW = 14 * S;
                        if (mx >= px + pw - 10 * S - xW) {
                            switch (b.source()) {
                                case MODULE -> ((Module) b.ref()).setKey(-1);
                                case QUICK -> ((QuickCommands.Cmd) b.ref()).key = -1;
                                case MACRO -> MacroManager.remove(keyCode);
                            }
                            com.lume.client.Config.save();
                            close();
                        } else if (b.source() == Source.MACRO) {
                            editText = b.text();
                            setMode(Mode.COMMAND);
                        }
                        return true;
                    }
                    ry += rowH + 4 * S;
                }
            }
        }
        return true;   // inside popup bounds — always consume, even on empty space
    }

    public static boolean charTyped(char chr) {
        if (!active || selecting || chr < 32 || chr == 127) return false;
        if (mode == Mode.COMMAND) { editText += chr; return true; }
        if (mode == Mode.FUNCTION && searchFocused) { search += chr; return true; }
        return false;
    }

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active) return false;
        if (selecting) return true;   // ignore keys during the confirmation flash
        if (mode == Mode.COMMAND) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!editText.isEmpty()) editText = editText.substring(0, editText.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!editText.isBlank()) {
                    MacroManager.add(BindPopup.keyCode, editText.trim());
                    com.lume.client.Config.save();
                    completeAndFlash(editText.trim());
                } else close();
                return true;
            }
            return true;
        }
        if (mode == Mode.FUNCTION && searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return false;
    }
}

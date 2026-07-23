package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.command.MacroManager;
import com.lume.client.fthw.QuickCommands;
import com.lume.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 *
 * <p>Plain {@code DrawContext}/{@link RenderUtil} rendering — no NanoVG. Coordinates passed in
 * and out (anchor, hit bounds) are logical GUI px, same space every vanilla {@code Screen} uses.
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

    /** Multiplies an ARGB color's alpha by {@code p} — the DrawContext equivalent of NanoVG's
     *  {@code globalAlpha}, which has no per-call analogue here so each draw bakes it in. */
    private static int fade(int argb, float p) {
        int a = Math.round(((argb >>> 24) & 0xFF) * p);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int height() {
        int rowH = 16;
        return switch (mode) {
            case CHOOSE -> 26 + 22 + 6 + 14 + 8;
            case COMMAND -> 26 + rowH + 24 + 8;
            case FUNCTION -> {
                int n = Math.max(1, filteredModules().size());
                yield 26 + (n + 1) * (rowH + 4) + 8;
            }
            case INFO -> {
                List<BindInfo> binds = bindsFor(keyCode);
                int n = Math.max(1, binds.size());
                yield 26 + n * (rowH + 4) + (binds.stream().anyMatch(b -> b.source() == Source.MACRO) ? 14 : 0) + 8;
            }
        };
    }

    /** Draws near {@code (anchorX, anchorBelowY)}, sliding up to stay inside the screen if it
     *  doesn't fit below. Fades + slides in on open, and again on every mode switch (Command vs
     *  Function, etc.) — matches the same ease-out easing every other Lume popup/window uses. */
    public static void render(DrawContext ctx, int anchorX, int anchorBelowY, int screenW, int screenH, int mx, int my, float dt) {
        if (!active) return;
        hits.clear();
        int pw = 220, ph = height();
        int px = Math.max(8, Math.min(anchorX, screenW - pw - 8));
        int py = Math.max(8, Math.min(anchorBelowY, screenH - ph - 8));
        lastPx = px; lastPy = py; lastPw = pw; lastPh = ph;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float p = ease(openTime, 140f);
        int slide = Math.round((1f - p) * 6f);
        py -= slide;   // fold the open-slide into the draw position (no matrix-translate group here)

        RenderUtil.glow(ctx, px, py, pw, ph, 10, 0x000000, 3);
        RenderUtil.roundedRect(ctx, px, py, pw, ph, 10, fade(Theme.winBg(), p));
        RenderUtil.strokeRoundedRect(ctx, px, py, pw, ph, 10, 1, fade(Theme.rim(), p));
        List<BindInfo> binds = bindsFor(keyCode);
        RenderUtil.textVCentered(ctx, tr, keyLabel + (binds.isEmpty() ? " — " + com.lume.client.Lang.tUI("unbound") : ""),
                px + 10, py, 20, fade(Theme.txtDim(), p), 0.5f);

        // Mode content fades/slides in on its OWN timer, separate from the popup's own open
        // animation, so switching Command<->Function reads as a deliberate transition too.
        float mp = ease(modeTime, 120f);
        float combined = p * mp;
        int mSlide = Math.round((1f - mp) * 4f);
        int ry = py + 26 - mSlide, rowH = 16;
        switch (mode) {
            case CHOOSE -> {
                int halfW = (pw - 24) / 2;
                RenderUtil.roundedRect(ctx, px + 10, ry, halfW, 22, 7, fade(Theme.accent(), combined));
                RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Command"), px + 10, ry, halfW, 22, fade(Theme.activeText(), combined), 0.47f);
                RenderUtil.roundedRect(ctx, px + 14 + halfW, ry, halfW, 22, 7, fade(Theme.glassRow(), combined));
                RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Function"), px + 14 + halfW, ry, halfW, 22, fade(Theme.txt(), combined), 0.47f);
                ry += 22 + 6;
                RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Command: sent to chat. Function: toggles a module."), px + 10, ry - 7, 14, fade(Theme.txtDim(), combined), 0.39f);
            }
            case COMMAND -> {
                RenderUtil.roundedRect(ctx, px + 10, ry, pw - 20, rowH, 5, fade(Theme.glassHov(), combined));
                RenderUtil.roundedRect(ctx, px + 10, ry + rowH - 1, pw - 20, 1, 1, fade(Theme.accent(), combined));
                String show = editText.isEmpty() ? com.lume.client.Lang.tUI("/command…") : editText + "_";
                RenderUtil.textVCentered(ctx, tr, show, px + 15, ry, rowH, fade(editText.isEmpty() ? Theme.txtDim() : Theme.txt(), combined), 0.44f);
                ry += rowH + 6;
                int halfW = (pw - 24) / 2;
                RenderUtil.roundedRect(ctx, px + 10, ry, halfW, 18, 6, fade(Theme.accent(), combined));
                RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Save"), px + 10, ry, halfW, 18, fade(Theme.activeText(), combined), 0.47f);
                RenderUtil.roundedRect(ctx, px + 14 + halfW, ry, halfW, 18, 6, fade(Theme.glassRow(), combined));
                RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Cancel"), px + 14 + halfW, ry, halfW, 18, fade(Theme.txt(), combined), 0.47f);
            }
            case FUNCTION -> {
                RenderUtil.roundedRect(ctx, px + 10, ry, pw - 20, rowH, 5, fade(searchFocused ? Theme.glassHov() : Theme.glassRow(), combined));
                String show = search.isEmpty() && !searchFocused ? com.lume.client.Lang.tUI("search functions…") : search + (searchFocused ? "_" : "");
                RenderUtil.textVCentered(ctx, tr, show, px + 15, ry, rowH, fade(search.isEmpty() && !searchFocused ? Theme.txtDim() : Theme.txt(), combined), 0.44f);
                ry += rowH + 4;
                List<Module> list = filteredModules();
                for (Module m : list) {
                    RenderUtil.roundedRect(ctx, px + 10, ry, pw - 20, rowH, 5, fade(Theme.glassRow(), combined));
                    RenderUtil.textVCentered(ctx, tr, m.getName(), px + 15, ry, rowH, fade(Theme.txt(), combined), 0.44f);
                    ry += rowH + 4;
                }
                if (list.isEmpty()) RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("no match"), px + 10, ry, rowH, fade(Theme.txtDim(), combined), 0.42f);
            }
            case INFO -> {
                for (BindInfo b : binds) {
                    String badge = switch (b.source()) { case MODULE -> "Module"; case QUICK -> "Quick"; case MACRO -> "Macro"; };
                    RenderUtil.roundedRect(ctx, px + 10, ry, pw - 20, rowH, 5, fade(Theme.glassRow(), combined));
                    RenderUtil.textVCentered(ctx, tr, badge, px + 14, ry, rowH, fade(Theme.accent(), combined), 0.42f);
                    RenderUtil.textVCentered(ctx, tr, b.text(), px + 52, ry, rowH, fade(Theme.txt(), combined), 0.44f);
                    int xW = 14;
                    RenderUtil.textCentered(ctx, tr, "✕", px + pw - 10 - xW, ry, xW, rowH, fade(0xFFE06868, combined), 0.47f);
                    ry += rowH + 4;
                }
                if (binds.stream().anyMatch(b -> b.source() == Source.MACRO)) {
                    RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("click to unbind, or the text to edit"), px + 10, ry, 14, fade(Theme.txtDim(), combined), 0.39f);
                }
            }
        }

        if (selecting) {
            long elapsed = System.currentTimeMillis() - selectTime;
            float inP = ease(selectTime, 120f);
            RenderUtil.roundedRect(ctx, px, py, pw, ph, 10, fade(withAlpha(Theme.accent(), 235), inP * p));
            RenderUtil.textCentered(ctx, tr, "✓ " + com.lume.client.Lang.tUI("Bound") + ": " + selectedLabel,
                    px, py, pw, ph, fade(Theme.activeText(), inP * p), 0.5f);
            if (elapsed > FLASH_MS) { selecting = false; close(); }
        }
    }

    /** @return true if the click was consumed (inside the popup bounds, whether or not it hit a
     *  specific control) — the caller should NOT also treat it as a click on whatever's behind. */
    public static boolean mouseClicked(int mx, int my) {
        if (!active) return false;
        if (selecting) return true;   // ignore clicks during the confirmation flash
        int px = lastPx, py = lastPy, pw = lastPw, ph = lastPh;
        if (mx < px || mx > px + pw || my < py || my > py + ph) return false;   // outside — caller decides what happens

        int ry = py + 26, rowH = 16;
        switch (mode) {
            case CHOOSE -> {
                int halfW = (pw - 24) / 2;
                if (my >= ry && my <= ry + 22) {
                    if (mx >= px + 10 && mx <= px + 10 + halfW) setMode(Mode.COMMAND);
                    else if (mx >= px + 14 + halfW) { setMode(Mode.FUNCTION); search = ""; searchFocused = true; }
                }
            }
            case COMMAND -> {
                ry += rowH + 6;
                int halfW = (pw - 24) / 2;
                if (my >= ry && my <= ry + 18) {
                    if (mx >= px + 10 && mx <= px + 10 + halfW) {
                        if (!editText.isBlank()) {
                            MacroManager.add(keyCode, editText.trim());
                            com.lume.client.Config.save();
                            completeAndFlash(editText.trim());
                        } else close();
                    } else if (mx >= px + 14 + halfW) {
                        close();
                    }
                }
            }
            case FUNCTION -> {
                if (my >= ry && my <= ry + rowH) { searchFocused = true; return true; }
                ry += rowH + 4;
                for (Module m : filteredModules()) {
                    if (my >= ry && my <= ry + rowH) {
                        m.setKey(keyCode);
                        com.lume.client.Config.save();
                        completeAndFlash(m.getName());
                        return true;
                    }
                    ry += rowH + 4;
                }
            }
            case INFO -> {
                List<BindInfo> binds = bindsFor(keyCode);
                for (BindInfo b : binds) {
                    if (my >= ry && my <= ry + rowH) {
                        int xW = 14;
                        if (mx >= px + pw - 10 - xW) {
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
                    ry += rowH + 4;
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

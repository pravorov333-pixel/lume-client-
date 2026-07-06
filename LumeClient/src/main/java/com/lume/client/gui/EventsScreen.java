package com.lume.client.gui;

import com.lume.client.fthw.EventManager;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Full-screen Events tab — shows Telegram events or FT/HW local schedule. */
public class EventsScreen extends LumeSubScreen {

    private static final int WIN_W = 520, WIN_H = 356;

    private float scroll = 0, scrollTarget = 0;
    private long lastFrame = System.currentTimeMillis();

    public EventsScreen(Screen parent) {
        super(Text.literal("Events – Lume"), parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);   // same background blur as the main menu
        ctx.fill(0, 0, width, height, Theme.backdrop());
        drawHudEditor(ctx, mouseX, mouseY);          // draggable HUD frames stay visible here
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int sw = width * S, sh = height * S;
        int W = WIN_W * S, H = WIN_H * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        computeTotal(WIN_W, WIN_H);
        int mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        // Pre-compute events data outside lambda (no lambdas-capturing mutable locals)
        com.lume.client.fthw.TelegramEvents.load();
        boolean tg = com.lume.client.fthw.TelegramEvents.available();
        String hdr;
        final List<com.lume.client.fthw.TelegramEvents.Ev> evs;
        final List<com.lume.client.fthw.EventRule> localRules;
        if (tg) {
            long age = com.lume.client.fthw.TelegramEvents.ageSec();
            hdr = "Telegram · все анархии" + (age >= 0 ? " · " + (age < 60 ? age + "с" : (age / 60) + "м") + " назад" : "");
            List<com.lume.client.fthw.TelegramEvents.Ev> tmp = new ArrayList<>(com.lume.client.fthw.TelegramEvents.events());
            tmp.sort((a, b) -> a.anarchy.length() != b.anarchy.length() ? a.anarchy.length() - b.anarchy.length() : a.anarchy.compareTo(b.anarchy));
            evs = tmp; localRules = null;
        } else {
            hdr = "Подключи Telegram в лаунчере для ивентов всех анархий";
            evs = null; localRules = new ArrayList<>(EventManager.rules);
        }
        int n = tg ? evs.size() : (localRules == null ? 0 : localRules.size());
        int rowH = 38 * S, gapr = 8 * S;
        int margin = 24 * S;
        int gy = y + 42 * S;
        int clipTop = gy - 2 * S, clipBot = y + H - 12 * S, visH = clipBot - gy;
        int contentH = 22 * S + n * (rowH + gapr);
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = (float) Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        final int scrollI = Math.round(scroll);
        final int fMaxScroll = maxScroll, fContentH = contentH;
        final String fHdr = hdr;
        final int fRowH = rowH, fGapr = gapr, fN = n;
        final int sx = x + margin, ew = W - margin * 2;

        NanoVgRenderer.frame(vg -> {
            applyTransform(vg, S, sw, sh);
            drawWindowFrame(vg, x, y, W, H, S, mx, my, 1);

            NanoVgRenderer.save(vg);
            NanoVgRenderer.scissor(vg, x, clipTop, W, visH);

            NanoVgRenderer.text(vg, sx + 2 * S, gy - scrollI + 8 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, fHdr);
            int cur = 22 * S;

            if (tg && evs != null) {
                for (com.lume.client.fthw.TelegramEvents.Ev e : evs) {
                    int ry = gy + cur - scrollI;
                    if (ry + fRowH >= clipTop && ry <= clipBot) {
                        boolean active = !e.phase.isEmpty() && !e.phase.toLowerCase().contains("ожидан");
                        int dotCol = active ? 0xFF6FCF7F : 0xFFE8C15A;
                        NanoVgRenderer.roundedRect(vg, sx, ry, ew, fRowH, 10 * S, Theme.glassRow());
                        NanoVgRenderer.roundedRect(vg, sx, ry, 3 * S, fRowH, 2 * S, dotCol);
                        NanoVgRenderer.text(vg, sx + 14 * S, ry + 15 * S, 13 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE,
                                "Анархия " + e.anarchy + "  ·  " + e.name);
                        NanoVgRenderer.text(vg, sx + 14 * S, ry + 29 * S, 10 * S,
                                active ? 0xFF6FCF7F : Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                                e.phase + (e.rarity.isEmpty() ? "" : "  ·  " + e.rarity));
                        if (!e.time.isEmpty() && !e.time.toLowerCase().contains("загруз")) {
                            float tw = NanoVgRenderer.textWidth(vg, 14 * S, e.time);
                            NanoVgRenderer.text(vg, sx + ew - tw - 14 * S, ry + fRowH / 2f, 14 * S, dotCol, NanoVgRenderer.ALIGN_MIDDLE, e.time);
                        }
                    }
                    cur += fRowH + fGapr;
                }
            } else if (localRules != null) {
                for (com.lume.client.fthw.EventRule er : localRules) {
                    int ry = gy + cur - scrollI;
                    if (ry + fRowH >= clipTop && ry <= clipBot) {
                        int left = -1;
                        for (EventManager.Active a : EventManager.active) if (a.rule == er) { left = a.secondsLeft(); break; }
                        long eta = er.etaSec(), ago = er.agoSec();
                        int dotCol; String status; int statusCol;
                        if (left >= 0) { dotCol = 0xFF6FCF7F; status = "идёт сейчас"; statusCol = 0xFF6FCF7F; }
                        else if (eta > 0) { dotCol = 0xFFE8C15A; status = "≈ через " + fmtDur(eta); statusCol = 0xFFE8C15A; }
                        else if (ago >= 0) { dotCol = Theme.txtDim(); status = "был " + fmtDur(ago) + " назад"; statusCol = Theme.txtDim(); }
                        else { dotCol = Theme.pillOff(); status = "ещё не видел"; statusCol = Theme.txtDim(); }
                        NanoVgRenderer.roundedRect(vg, sx, ry, ew, fRowH, 10 * S, Theme.glassRow());
                        NanoVgRenderer.roundedRect(vg, sx, ry, 3 * S, fRowH, 2 * S, dotCol);
                        NanoVgRenderer.text(vg, sx + 14 * S, ry + 15 * S, 13 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, er.name);
                        NanoVgRenderer.text(vg, sx + 14 * S, ry + 29 * S, 10 * S, statusCol, NanoVgRenderer.ALIGN_MIDDLE, status);
                        String big = left >= 0 ? left + "с" : (eta > 0 ? fmtDur(eta) : "");
                        if (!big.isEmpty()) {
                            float tw = NanoVgRenderer.textWidth(vg, 18 * S, big);
                            NanoVgRenderer.text(vg, sx + ew - tw - 14 * S, ry + fRowH / 2f, 18 * S,
                                    left >= 0 ? 0xFF6FCF7F : 0xFFE8C15A, NanoVgRenderer.ALIGN_MIDDLE, big);
                        }
                    }
                    cur += fRowH + fGapr;
                }
            }

            NanoVgRenderer.restore(vg);

            if (fMaxScroll > 0) {
                int sbW = 3 * S, sbX = x + W - 14 * S;
                NanoVgRenderer.roundedRect(vg, sbX, gy, sbW, visH, sbW / 2f, Theme.glassRow());
                int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) fContentH)));
                int thumbY = gy + Math.round((visH - thumbH) * (scroll / fMaxScroll));
                NanoVgRenderer.roundedRect(vg, sbX, thumbY, sbW, thumbH, sbW / 2f, Theme.accent());
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tryNavClick(mouseX, mouseY)) return true;
        if (button == 0 && tryHudDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollTarget -= (float) v * 30 * (int) Math.max(1, client.getWindow().getScaleFactor());
        return true;
    }
}

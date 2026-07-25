package com.lume.client.gui;

import com.lume.client.fthw.CurrentAnarchy;
import com.lume.client.fthw.EventManager;
import com.lume.client.fthw.EventRule;
import com.lume.client.fthw.TelegramEvents;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.nanovg.NanoVgRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen Events tab — a 3-per-row grid of anarchy cards (Telegram feed),
 * or the local FT/HW learned schedule as a fallback list when Telegram isn't
 * configured. One card per anarchy, listing EVERY event currently running or
 * pending on it (an anarchy with 2 events shows both in the same card, not
 * just the "best" one) — sorted: your own anarchy first, then by ascending
 * time-to-next-thing.
 */
public class EventsScreen extends LumeSubScreen {

    private static final int WIN_W = 520, WIN_H = 356;
    private static final int COLS = 3;

    private float scroll = 0, scrollTarget = 0;
    private long lastFrame = System.currentTimeMillis();
    private final List<Object[]> titleHits = new ArrayList<>();   // {command, x, y, w, h}, rebuilt every render — click to copy

    public EventsScreen(Screen parent) {
        super(Text.literal("Events – Lume"), parent);
    }

    /** One card = one anarchy; holds EVERY event currently reported for it. */
    private static final class Card {
        final String anarchy;
        final List<TelegramEvents.Ev> events;
        Card(String anarchy, List<TelegramEvents.Ev> events) { this.anarchy = anarchy; this.events = events; }
        TelegramEvents.Ev primary() { return events.get(0); }   // events are pre-sorted by priority — see buildCards
    }

    /**
     * Active (incl. just-opened/erupting, see {@link TelegramEvents.Ev#justOpened()}) > voting >
     * "opens in" (already appeared, counting down to open) > "appears in" (not spawned yet).
     * Opening beats appearing because it's the more actionable state — you can already be on
     * your way to it. A just-opened event is bumped to the same top tier as active — its
     * countdown ran out, so it's presumably open/erupting right now, same urgency as "active".
     */
    private static int priorityRank(TelegramEvents.Ev e) {
        if (e.isActive() || e.justOpened()) return 0;
        if (e.isVoting()) return 1;
        if (e.isWaiting() && e.isOpening()) return 2;
        return 3;
    }

    /** Rarity + a known location (auto-placed via EventLocator/"Координаты ивента") — the fully "actionable" events. */
    private static boolean resolved(TelegramEvents.Ev e) {
        return !e.rarity.isEmpty() && hasCoords(e);
    }

    private static boolean hasCoords(TelegramEvents.Ev e) {
        for (Waypoints.WP w : Waypoints.list) {
            if (e.anarchy.equals(w.anarchy) && w.name.equalsIgnoreCase(e.name)) return true;
        }
        return false;
    }

    /** The "big" event types — bumped to the front whenever they're relevant (mainly matters while opening/counting down). */
    private static final String[] PRIORITY_NAMES = { "вулкан", "маяк", "метеоритный дождь" };

    private static boolean isPriorityEvent(TelegramEvents.Ev e) {
        String n = e.name.toLowerCase();
        for (String p : PRIORITY_NAMES) if (n.contains(p)) return true;
        return false;
    }

    /** Вулкан/Маяк/Метеоритный дождь always first — above everything else, any tier. Then priority tier, then resolved, then soonest time. */
    private static int compareEv(TelegramEvents.Ev a, TelegramEvents.Ev b) {
        boolean pa = isPriorityEvent(a), pb = isPriorityEvent(b);
        if (pa != pb) return pa ? -1 : 1;
        int r = priorityRank(a) - priorityRank(b);
        if (r != 0) return r;
        boolean ra = resolved(a), rb = resolved(b);
        if (ra != rb) return ra ? -1 : 1;
        int sa = a.liveSecondsLeft(), sb = b.liveSecondsLeft();
        if (sa < 0 && sb < 0) return 0;
        if (sa < 0) return 1;
        if (sb < 0) return -1;
        return sa - sb;
    }

    private static final int APPEAR_SOON_SEC = 600;   // 10 minutes

    private static List<Card> buildCards(List<TelegramEvents.Ev> all, String curAnarchy) {
        Map<String, List<TelegramEvents.Ev>> byAnarchy = new LinkedHashMap<>();
        for (TelegramEvents.Ev e : all) {
            // Timer ran out client-side (stale until the next ~45s backend poll) — drop the
            // event entirely rather than show a dead "0с". If that was the anarchy's only
            // event, the whole card disappears too (nothing left to put in it). EXCEPT a
            // just-opened/erupting event (see justOpened()) — that's a real, actionable state
            // worth showing ("Открыт"/"Извергается"), not a stale dead timer.
            if (e.liveSecondsLeft() == 0 && !e.justOpened()) continue;
            // Nothing to do yet on a "not spawned" event more than 10 min out (or with no
            // known time at all) — too far off to be useful, just clutters the list.
            if (priorityRank(e) == 3) {
                int s = e.liveSecondsLeft();
                if (s < 0 || s > APPEAR_SOON_SEC) continue;
            }
            byAnarchy.computeIfAbsent(e.anarchy, k -> new ArrayList<>()).add(e);
        }
        List<Card> cards = new ArrayList<>();
        for (Map.Entry<String, List<TelegramEvents.Ev>> en : byAnarchy.entrySet()) {
            List<TelegramEvents.Ev> evs = en.getValue();
            evs.sort(EventsScreen::compareEv);
            cards.add(new Card(en.getKey(), evs));
        }
        cards.sort((a, b) -> {
            boolean amine = a.anarchy.equals(curAnarchy), bmine = b.anarchy.equals(curAnarchy);
            if (amine != bmine) return amine ? -1 : 1;
            return compareEv(a.primary(), b.primary());
        });
        return cards;
    }

    /** Colour for one event's status line (text itself comes from {@link TelegramEvents.Ev#statusText()}). */
    private static int statusColor(TelegramEvents.Ev e) {
        if (e.isActive() || e.justOpened()) return 0xFF6FCF7F;
        if (e.isVoting()) return Theme.accent();
        return e.liveSecondsLeft() > 0 ? 0xFFE8C15A : Theme.txtDim();
    }

    /** Card height (native px) for an anarchy with {@code n} events: title row + one block per event. */
    private static int cardHeight(int n, int S) {
        return (18 + Math.max(1, n) * 26 + 8) * S;
    }

    /** Shrinks the font just enough for {@code text} to fit {@code maxW}, never below {@code minSize}. */
    private static float fitSize(long vg, float baseSize, String text, float maxW, float minSize) {
        float w = NanoVgRenderer.textWidth(vg, baseSize, text);
        if (w <= maxW || w <= 0) return baseSize;
        return Math.max(minSize, baseSize * maxW / w);
    }

    /** DrawContext equivalent of {@link #fitSize} — shrinks the RenderUtil scale just enough for
     *  {@code text} to fit {@code maxW}, never below {@code minScale}. */
    private static float fitSizeLegacy(net.minecraft.client.font.TextRenderer tr, float baseScale, String text, float maxW, float minScale) {
        float w = RenderUtil.width(tr, text, baseScale);
        if (w <= maxW || w <= 0) return baseScale;
        return Math.max(minScale, baseScale * maxW / w);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);   // same background blur as the main menu
        ctx.fill(0, 0, width, height, Theme.backdrop());
        drawHudEditor(ctx, mouseX, mouseY);          // draggable HUD frames stay visible here
        NanoVgRenderer.ensureInit();

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int sw = width * S, sh = height * S;
        int W = WIN_W * S, H = WIN_H * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        computeTotal(WIN_W, WIN_H);
        int mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        com.lume.client.fthw.TelegramEvents.load();
        boolean tg = TelegramEvents.available();
        final List<Card> cards;
        final List<EventRule> localRules;
        final String curAnarchy = CurrentAnarchy.get();
        if (tg) {
            cards = buildCards(TelegramEvents.events(), curAnarchy);
            localRules = null;
        } else {
            cards = null; localRules = new ArrayList<>(EventManager.rules);
        }

        int margin = 20 * S, selY = y + 34 * S, selH = 20 * S, gy = selY + selH + 8 * S;
        int clipTop = gy - 2 * S, clipBot = y + H - 12 * S, visH = clipBot - gy;
        final int sx = x + margin, ew = W - margin * 2;

        int contentH;
        int cardW = 0, colGap = 8 * S, rowGap = 8 * S;
        // Masonry packing: each card goes into whichever column is shortest so far,
        // right under the card already there — no row-height alignment, so a short
        // card never leaves a big gap under it waiting for a taller neighbour.
        final int[] cardH;
        final int[] cardCol;
        final int[] cardY;
        if (tg) {
            cardW = (ew - (COLS - 1) * colGap) / COLS;
            cardH = new int[cards.size()];
            cardCol = new int[cards.size()];
            cardY = new int[cards.size()];
            int[] colY = new int[COLS];
            for (int i = 0; i < cards.size(); i++) {
                cardH[i] = cardHeight(cards.get(i).events.size(), S);
                int col = 0;
                for (int c2 = 1; c2 < COLS; c2++) if (colY[c2] < colY[col]) col = c2;
                cardCol[i] = col;
                cardY[i] = colY[col];
                colY[col] += cardH[i] + rowGap;
            }
            int maxColY = 0;
            for (int c2 = 0; c2 < COLS; c2++) maxColY = Math.max(maxColY, colY[c2]);
            contentH = maxColY > 0 ? maxColY - rowGap : 0;
        } else {
            cardH = null; cardCol = null; cardY = null;
            int rowH = 38 * S;
            contentH = (localRules == null ? 0 : localRules.size()) * (rowH + 8 * S);
        }
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = (float) Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        final int scrollI = Math.round(scroll);
        final int fMaxScroll = maxScroll, fContentH = contentH;
        final int fCardW = cardW, fColGap = colGap;

        if (!NanoVgRenderer.ready()) {
            renderLegacyContent(ctx, S, sw, sh, x, y, W, H, mx, my, dt, tg, cards, localRules, curAnarchy,
                    sx, ew, selY, selH, gy, clipTop, clipBot, visH, scrollI, fMaxScroll, fContentH,
                    fCardW, fColGap, cardH, cardCol, cardY);
            drawOpenTransition(S, sw, sh, x, y, W, H);
            return;
        }

        drawGlassBackdrop(S, sw, sh, x, y, W, H, 18 * S);
        ctx.draw();   // flush DrawContext's own queued geometry before raw-GL NanoVG draws
        NvgTextQueue.begin(ClickGuiScreen.getWinOffX() * S, ClickGuiScreen.getWinOffY() * S, sw / 2.0, sh / 2.0, total);
        NanoVgRenderer.frame(vg -> {
            applyTransform(vg, S, sw, sh);
            drawWindowFrame(vg, x, y, W, H, S, mx, my, 1, dt);

            // Network selector — only FunTime for now; laid out so a second network
            // (HolyWorld) just becomes another pill here once it's actually supported.
            NanoVgRenderer.text(vg, sx + 2 * S, selY + selH / 2f, 9.5f * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, "Сервер:");
            float lblW = NanoVgRenderer.textWidth(vg, 9.5f * S, "Сервер:");
            int pillW = (int) NanoVgRenderer.textWidth(vg, 10 * S, "FunTime") + 20 * S;
            int pillX = sx + (int) lblW + 8 * S;
            NanoVgRenderer.gradientRoundedRect(vg, pillX, selY, pillW, selH, selH / 2f, Theme.accent(), Theme.accent2());
            NanoVgRenderer.text(vg, pillX + pillW / 2f, selY + selH / 2f, 10 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "FunTime");

            NanoVgRenderer.save(vg);
            NanoVgRenderer.scissor(vg, x, clipTop, W, visH);
            int cur = 0;
            titleHits.clear();

            if (tg && cards.isEmpty()) {
                NanoVgRenderer.text(vg, sx + ew / 2f, gy + visH / 2f, 11 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, "Нет активных ивентов");
            }

            if (tg) {
                float innerW = fCardW - 20 * S;
                for (int i = 0; i < cards.size(); i++) {
                    Card c = cards.get(i);
                    int cx = sx + cardCol[i] * (fCardW + fColGap);
                    int h = cardH[i];
                    int ry = gy + cardY[i] - scrollI;
                    if (ry + h >= clipTop && ry <= clipBot) {
                        boolean mine = c.anarchy.equals(curAnarchy);
                        NanoVgRenderer.roundedRect(vg, cx, ry, fCardW, h, 10 * S, mine ? Theme.glassHov() : Theme.glassRow());
                        String title = (mine ? "★ " : "") + "/an" + c.anarchy;
                        float titleSize = fitSize(vg, 12 * S, title, innerW, 8 * S);
                        NanoVgRenderer.text(vg, cx + 10 * S, ry + 15 * S, titleSize, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, title);
                        titleHits.add(new Object[]{ "/an" + c.anarchy, cx, ry, fCardW, 18 * S });
                        int ey = ry + 18 * S;
                        for (TelegramEvents.Ev e : c.events) {
                            int col2 = statusColor(e);
                            float nameSize = fitSize(vg, 9.5f * S, e.name, innerW, 7 * S);
                            NanoVgRenderer.text(vg, cx + 10 * S, ey + 11 * S, nameSize, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE, e.name);
                            String status = e.statusText();
                            float statusSize = fitSize(vg, 10 * S, status, innerW, 7 * S);
                            NanoVgRenderer.text(vg, cx + 10 * S, ey + 23 * S, statusSize, col2, NanoVgRenderer.ALIGN_MIDDLE, status);
                            ey += 26 * S;
                        }
                    }
                }
            } else if (localRules != null) {
                int rowH = 38 * S, gapr = 8 * S;
                for (EventRule er : localRules) {
                    int ry = gy + cur - scrollI;
                    if (ry + rowH >= clipTop && ry <= clipBot) {
                        int left = -1;
                        for (EventManager.Active a : EventManager.active) if (a.rule == er) { left = a.secondsLeft(); break; }
                        long eta = er.etaSec(), ago = er.agoSec();
                        int dotCol; String status; int statusCol;
                        if (left >= 0) { dotCol = 0xFF6FCF7F; status = "идёт сейчас"; statusCol = 0xFF6FCF7F; }
                        else if (eta > 0) { dotCol = 0xFFE8C15A; status = "≈ через " + fmtDur(eta); statusCol = 0xFFE8C15A; }
                        else if (ago >= 0) { dotCol = Theme.txtDim(); status = "был " + fmtDur(ago) + " назад"; statusCol = Theme.txtDim(); }
                        else { dotCol = Theme.pillOff(); status = "ещё не видел"; statusCol = Theme.txtDim(); }
                        NanoVgRenderer.roundedRect(vg, sx, ry, ew, rowH, 10 * S, Theme.glassRow());
                        NanoVgRenderer.roundedRect(vg, sx, ry, 3 * S, rowH, 2 * S, dotCol);
                        NanoVgRenderer.text(vg, sx + 14 * S, ry + 15 * S, 13 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, er.name);
                        NanoVgRenderer.text(vg, sx + 14 * S, ry + 29 * S, 10 * S, statusCol, NanoVgRenderer.ALIGN_MIDDLE, status);
                        String big = left > 0 ? left + "с" : (eta > 0 ? fmtDur(eta) : "");
                        if (!big.isEmpty()) {
                            float tw = NanoVgRenderer.textWidth(vg, 18 * S, big);
                            NanoVgRenderer.text(vg, sx + ew - tw - 14 * S, ry + rowH / 2f, 18 * S,
                                    left >= 0 ? 0xFF6FCF7F : 0xFFE8C15A, NanoVgRenderer.ALIGN_MIDDLE, big);
                        }
                    }
                    cur += rowH + gapr;
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
        NvgTextQueue.flush(ctx);
        drawOpenTransition(S, sw, sh, x, y, W, H);
    }

    /** DrawContext port of the NanoVG frame body above — same layout math (passed in from
     *  render(), computed once regardless of path), 1:1 primitive swap per the established
     *  NanoVG-removal conversion pattern (NanoVgRenderer.text/roundedRect/scissor -> RenderUtil
     *  equivalents / ctx.enableScissor, native px straight through). */
    private void renderLegacyContent(DrawContext ctx, int S, int sw, int sh, int x, int y, int W, int H,
                                      int mx, int my, float dt, boolean tg, List<Card> cards, List<EventRule> localRules,
                                      String curAnarchy, int sx, int ew, int selY, int selH, int gy,
                                      int clipTop, int clipBot, int visH, int scrollI, int fMaxScroll, int fContentH,
                                      int fCardW, int fColGap, int[] cardH, int[] cardCol, int[] cardY) {
        applyTransformLegacy(ctx, S, sw, sh);
        drawWindowFrameLegacy(ctx, x, y, W, H, S, mx, my, 1, dt);
        var tr = this.textRenderer;

        // Network selector — only FunTime for now, same layout as the NanoVG version.
        RenderUtil.textVCentered(ctx, tr, "Сервер:", sx + 2 * S, selY, selH, Theme.txtDim(), 9.5f / 18f);
        int lblW = RenderUtil.width(tr, "Сервер:", 9.5f / 18f);
        int pillW = RenderUtil.width(tr, "FunTime", 10f / 18f) + 20 * S;
        int pillX = sx + lblW + 8 * S;
        sdfFill(ctx, S, pillX, selY, pillW, selH, selH / 2, Theme.accent());
        RenderUtil.textCentered(ctx, tr, "FunTime", pillX, selY, pillW, selH, Theme.activeText(), 10f / 18f);

        ctx.enableScissor(x, clipTop, x + W, clipTop + visH);
        titleHits.clear();

        if (tg && cards.isEmpty()) {
            RenderUtil.textCentered(ctx, tr, "Нет активных ивентов", sx, gy, ew, visH, Theme.txtDim(), 11f / 18f);
        }

        if (tg) {
            float innerW = fCardW - 20 * S;
            for (int i = 0; i < cards.size(); i++) {
                Card c = cards.get(i);
                int cx2 = sx + cardCol[i] * (fCardW + fColGap);
                int h = cardH[i];
                int ry = gy + cardY[i] - scrollI;
                if (ry + h >= clipTop && ry <= clipBot) {
                    boolean mine = c.anarchy.equals(curAnarchy);
                    sdfFill(ctx, S, cx2, ry, fCardW, h, 10 * S, mine ? Theme.glassHov() : Theme.glassRow());
                    String title = (mine ? "★ " : "") + "/an" + c.anarchy;
                    float titleScale = fitSizeLegacy(tr, 12f / 18f, title, innerW, 8f / 18f);
                    RenderUtil.textVCentered(ctx, tr, title, cx2 + 10 * S, ry + 8 * S, 14 * S, Theme.accent(), titleScale);
                    titleHits.add(new Object[]{ "/an" + c.anarchy, cx2, ry, fCardW, 18 * S });
                    int ey = ry + 18 * S;
                    for (TelegramEvents.Ev e : c.events) {
                        int col2 = statusColor(e);
                        float nameScale = fitSizeLegacy(tr, 9.5f / 18f, e.name, innerW, 7f / 18f);
                        RenderUtil.textVCentered(ctx, tr, e.name, cx2 + 10 * S, ey + 4 * S, 14 * S, Theme.txtDim(), nameScale);
                        String status = e.statusText();
                        float statusScale = fitSizeLegacy(tr, 10f / 18f, status, innerW, 7f / 18f);
                        RenderUtil.textVCentered(ctx, tr, status, cx2 + 10 * S, ey + 16 * S, 14 * S, col2, statusScale);
                        ey += 26 * S;
                    }
                }
            }
        } else if (localRules != null) {
            int rowH = 38 * S, gapr = 8 * S;
            int cur = 0;
            for (EventRule er : localRules) {
                int ry = gy + cur - scrollI;
                if (ry + rowH >= clipTop && ry <= clipBot) {
                    int left = -1;
                    for (EventManager.Active a : EventManager.active) if (a.rule == er) { left = a.secondsLeft(); break; }
                    long eta = er.etaSec(), ago = er.agoSec();
                    int dotCol; String status; int statusCol;
                    if (left >= 0) { dotCol = 0xFF6FCF7F; status = "идёт сейчас"; statusCol = 0xFF6FCF7F; }
                    else if (eta > 0) { dotCol = 0xFFE8C15A; status = "≈ через " + fmtDur(eta); statusCol = 0xFFE8C15A; }
                    else if (ago >= 0) { dotCol = Theme.txtDim(); status = "был " + fmtDur(ago) + " назад"; statusCol = Theme.txtDim(); }
                    else { dotCol = Theme.pillOff(); status = "ещё не видел"; statusCol = Theme.txtDim(); }
                    sdfFill(ctx, S, sx, ry, ew, rowH, 10 * S, Theme.glassRow());
                    sdfFill(ctx, S, sx, ry, 3 * S, rowH, 2 * S, dotCol);
                    RenderUtil.textVCentered(ctx, tr, er.name, sx + 14 * S, ry + 6 * S, 14 * S, Theme.txt(), 13f / 18f);
                    RenderUtil.textVCentered(ctx, tr, status, sx + 14 * S, ry + 20 * S, 14 * S, statusCol, 10f / 18f);
                    String big = left > 0 ? left + "с" : (eta > 0 ? fmtDur(eta) : "");
                    if (!big.isEmpty()) {
                        int tw = RenderUtil.width(tr, big, 18f / 18f);
                        RenderUtil.textVCentered(ctx, tr, big, sx + ew - tw - 14 * S, ry, rowH,
                                left >= 0 ? 0xFF6FCF7F : 0xFFE8C15A, 18f / 18f);
                    }
                }
                cur += rowH + gapr;
            }
        }

        ctx.disableScissor();

        if (fMaxScroll > 0) {
            int sbW = 3 * S, sbX = x + W - 14 * S;
            sdfFill(ctx, S, sbX, gy, sbW, visH, sbW / 2, Theme.glassRow());
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) fContentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / fMaxScroll));
            sdfFill(ctx, S, sbX, thumbY, sbW, thumbH, sbW / 2, Theme.accent());
        }

        ctx.getMatrices().pop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tryNavClick(mouseX, mouseY)) return true;
        if (button == 0 && tryCopyClick(mouseX, mouseY)) return true;
        if (button == 0 && tryHudDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Click a card's "/anXXX" title to copy the connect command to the clipboard. */
    private boolean tryCopyClick(double mouseX, double mouseY) {
        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        int mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);
        for (Object[] h : titleHits) {
            int hx = (int) h[1], hy = (int) h[2], hw = (int) h[3], hh = (int) h[4];
            if (mx >= hx && mx <= hx + hw && my >= hy && my <= hy + hh) {
                String cmd = (String) h[0];
                client.keyboard.setClipboard(cmd);
                Notifications.push("Скопировано: " + cmd, Theme.accent(), 2000);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollTarget -= (float) v * 30 * (int) Math.max(1, client.getWindow().getScaleFactor());
        return true;
    }
}

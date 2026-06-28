package com.lume.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lightweight on-screen toast notifications (top-centre), used for FT/HW event
 * alerts and other one-off messages — NOT chat. Glass pill that slides in and
 * fades out.
 */
public final class Notifications {

    private static final class Toast {
        final String text; final int color; final long start, expire;
        Toast(String t, int c, long s, long e) { text = t; color = c; start = s; expire = e; }
    }

    private static final List<Toast> toasts = new ArrayList<>();

    private Notifications() {}

    public static void push(String text, int color, int durationMs) {
        long now = System.currentTimeMillis();
        toasts.add(new Toast(text, color, now, now + durationMs));
    }

    /** Drawn from HudRenderer in native space. */
    public static void render(DrawContext ctx, TextRenderer tr, int S, int sw) {
        long now = System.currentTimeMillis();
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); ) if (now >= it.next().expire) it.remove();
        if (toasts.isEmpty()) return;

        int y = 26 * S;
        for (Toast t : toasts) {
            float life = (t.expire - now) / 400f;            // fade out in last 400ms
            float in = (now - t.start) / 200f;               // fade in over 200ms
            float a = Math.max(0f, Math.min(1f, Math.min(in, life)));
            int alpha = Math.round(a * 255);
            int tw = RenderUtil.vanillaWidth(tr, t.text, S);
            int pw = tw + 28 * S, ph = 20 * S, px = sw / 2 - pw / 2;
            int slide = Math.round((1f - Math.min(1f, in)) * 8 * S);   // slide down on entry
            int py = y - slide;
            RenderUtil.roundedRect(ctx, px, py, pw, ph, 8 * S, withA(Theme.winBg(), alpha));
            RenderUtil.roundedRect(ctx, px, py, 3 * S, ph, 1 * S, withA(t.color, alpha));
            RenderUtil.vanillaText(ctx, tr, t.text, px + 12 * S, py + (ph - 8 * S) / 2.0, withA(Theme.txt(), alpha), S);
            y += ph + 5 * S;
        }
    }

    private static int withA(int argb, int alpha) {
        int base = (argb >>> 24) & 0xFF;
        int aa = base == 0 ? alpha : Math.min(base, alpha) * alpha / 255;
        return (aa << 24) | (argb & 0xFFFFFF);
    }
}

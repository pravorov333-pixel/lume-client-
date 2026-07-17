package com.lume.client.menu;

import com.lume.client.gui.Theme;
import com.lume.client.nanovg.NanoVgRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The menu's sparkle effects, in the theme's own accent colours: a drifting ambient field
 * behind the title screen, plus a short trail that follows the cursor (which replaced the
 * old "Cursor Glow" light-under-the-hovered-button effect).
 *
 * <p>This is 2D screen-space NanoVG only — deliberately NOT the world-space
 * {@code fx.ParticleEngine}, which needs a live camera and renders through MC's own
 * pipeline; neither exists on the title screen.
 *
 * <p>All motion integrates against a real per-frame delta rather than reading an absolute
 * clock. That's not just tidiness: the previous background styles derived motion from
 * {@code System.currentTimeMillis()} cast to float, which silently quantises to ~131s steps
 * (a float's 24-bit mantissa can't hold a ~1.7e12 millisecond count) and froze the animation
 * for minutes at a time. Integrating a small dt never touches that cliff.
 */
public final class MenuSparkles {

    private MenuSparkles() {}

    // ---- ambient background field ----------------------------------------------------

    private static final int COUNT = 70;

    /** One drifting mote. Coords are normalised 0..1 so a window resize doesn't teleport them. */
    private static final class Mote {
        float x, y, size, riseSpd, swayAmp, swayHz, swayPh, twinkleHz, twinklePh;
        boolean second;   // tint from accent2 instead of accent
        boolean star;     // drawn as a 4-point star (like the logo) instead of a round dot
    }

    private static final List<Mote> motes = new ArrayList<>();
    // Fixed seed: the field's layout is then identical every launch, so a "the menu looks
    // different today" report is always a real change and never just reseeded randomness.
    private static final java.util.Random RNG = new java.util.Random(0x10BE5EEDL);

    private static void seed() {
        if (!motes.isEmpty()) return;
        for (int i = 0; i < COUNT; i++) motes.add(newMote(RNG.nextFloat()));
    }

    private static Mote newMote(float y) {
        Mote m = new Mote();
        m.x = RNG.nextFloat();
        m.y = y;
        m.size = 0.7f + RNG.nextFloat() * 2.1f;
        m.riseSpd = 0.008f + RNG.nextFloat() * 0.022f;   // screens per second
        m.swayAmp = 0.004f + RNG.nextFloat() * 0.016f;
        m.swayHz = 0.15f + RNG.nextFloat() * 0.4f;
        m.swayPh = RNG.nextFloat() * 6.283f;
        m.twinkleHz = 0.4f + RNG.nextFloat() * 1.1f;
        m.twinklePh = RNG.nextFloat() * 6.283f;
        m.second = RNG.nextFloat() < 0.4f;
        m.star = RNG.nextFloat() < 0.22f;
        return m;
    }

    // The background field and the cursor trail are drawn from two different call paths
    // (the background via the title-screen mixin, the trail from LumeTitleMenu's own render),
    // and either can run without the other. They therefore keep SEPARATE clocks — a single
    // shared delta() would hand the whole frame's dt to whichever ran first and ~0 to the
    // second, freezing it.
    private static long lastMoteNanos = 0L, lastTrailNanos = 0L;

    /** Seconds since this clock's last tick, clamped so a stutter or a paused menu can't fling everything. */
    private static float delta(boolean trail) {
        long now = System.nanoTime();
        long last = trail ? lastTrailNanos : lastMoteNanos;
        if (trail) lastTrailNanos = now; else lastMoteNanos = now;
        if (last == 0L) return 0f;
        return Math.min(0.05f, Math.max(0f, (now - last) / 1_000_000_000f));
    }

    /** Ambient sparkles across the whole screen. {@code w}/{@code h} are framebuffer px. */
    public static void drawBackground(long vg, float w, float h) {
        seed();
        float dt = delta(false);

        int acc = Theme.accentRgb(), acc2 = Theme.accent2Rgb();
        float scale = Math.max(1f, Math.min(w, h) / 400f);   // keep motes proportional on any resolution

        for (Mote m : motes) {
            m.y -= m.riseSpd * dt;
            m.swayPh += m.swayHz * dt * 6.283f;
            m.twinklePh += m.twinkleHz * dt * 6.283f;
            if (m.y < -0.03f) {                 // rose off the top — respawn along the bottom
                m.y = 1.03f;
                m.x = RNG.nextFloat();
            }

            float px = (m.x + (float) Math.sin(m.swayPh) * m.swayAmp) * w;
            float py = m.y * h;
            // Never fully off: 0.35..1.0, so the field reads as a steady shimmer rather than
            // motes blinking in and out of existence.
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(m.twinklePh));
            int rgb = m.second ? acc2 : acc;
            float r = m.size * scale;

            NanoVgRenderer.radialGlow(vg, px, py, r * 0.2f, r * 5f, rgb, (int) (60 * tw));
            if (m.star) NanoVgRenderer.sparkle4(vg, px, py, r * 2.1f, r * 0.62f, (int) (220 * tw) << 24 | rgb);
            else NanoVgRenderer.circle(vg, px, py, r, (int) (210 * tw) << 24 | rgb);
        }
    }

    // ---- cursor trail ----------------------------------------------------------------

    private static final class Spark {
        float x, y, vx, vy, size, life, maxLife;
        boolean second;
    }

    private static final List<Spark> trail = new ArrayList<>();
    private static final int TRAIL_MAX = 90;
    private static float lastMx = Float.NaN, lastMy = Float.NaN;
    private static float spawnAccum = 0f;

    private static void advanceTrail(float dt) {
        for (Iterator<Spark> it = trail.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            s.life -= dt;
            if (s.life <= 0f) { it.remove(); continue; }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vy += 26f * dt;      // gentle gravity so the trail settles instead of flying off
            s.vx *= (1f - 1.6f * dt);
            s.vy *= (1f - 1.6f * dt);
        }
    }

    /**
     * Sparks that follow the cursor. Emission is proportional to how far the mouse actually
     * moved (not per frame), so a fast flick and a slow drag leave the same density of trail
     * and a parked cursor stops emitting entirely instead of piling sparks up in one spot.
     * Coords are framebuffer px.
     */
    public static void drawCursor(long vg, float mx, float my) {
        float dt = delta(true);

        if (!Float.isNaN(lastMx)) {
            float dist = (float) Math.hypot(mx - lastMx, my - lastMy);
            spawnAccum += dist;
            float step = 7f * Math.max(1f, NanoVgRenderer.pxScale());
            while (spawnAccum >= step && trail.size() < TRAIL_MAX) {
                spawnAccum -= step;
                Spark s = new Spark();
                s.x = mx + (RNG.nextFloat() - 0.5f) * 4f;
                s.y = my + (RNG.nextFloat() - 0.5f) * 4f;
                float ang = RNG.nextFloat() * 6.283f;
                float spd = 6f + RNG.nextFloat() * 20f;
                s.vx = (float) Math.cos(ang) * spd;
                s.vy = (float) Math.sin(ang) * spd - 14f;   // bias upward, like embers
                s.size = 0.8f + RNG.nextFloat() * 1.5f;
                s.maxLife = 0.45f + RNG.nextFloat() * 0.45f;
                s.life = s.maxLife;
                s.second = RNG.nextFloat() < 0.4f;
                trail.add(s);
            }
        }
        lastMx = mx; lastMy = my;

        advanceTrail(dt);

        int acc = Theme.accentRgb(), acc2 = Theme.accent2Rgb();
        float scale = Math.max(1f, NanoVgRenderer.pxScale());
        for (Spark s : trail) {
            float f = Math.max(0f, s.life / s.maxLife);      // 1 → 0 over its lifetime
            float r = s.size * scale * (0.4f + 0.6f * f);
            int rgb = s.second ? acc2 : acc;
            NanoVgRenderer.radialGlow(vg, s.x, s.y, r * 0.2f, r * 4.5f, rgb, (int) (70 * f));
            NanoVgRenderer.circle(vg, s.x, s.y, r, (int) (230 * f) << 24 | rgb);
        }
    }

    /** Drop transient state — called when the menu closes so a re-open starts clean. The mote
     *  field itself is kept: it's ambient and identical every run, so there's nothing to reset. */
    public static void reset() {
        trail.clear();
        lastMx = Float.NaN; lastMy = Float.NaN;
        spawnAccum = 0f;
        lastMoteNanos = 0L;
        lastTrailNanos = 0L;
    }
}

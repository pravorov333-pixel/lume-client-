package com.lume.client.fx;

/**
 * Shared animated fill palettes for the "Cosmos"/"Swirl" Fill styles — used by both
 * {@code cosmetic.BlockOutline} (per-corner colour of a world-space block) and
 * {@code module.modules.render.CustomHand} (per-vertex colour of the held item mesh, in
 * local mesh space via {@link com.lume.client.util.ForcedColorVertexConsumer}). Both callers
 * just need SOME spatially-varying position to sample — world-space or local mesh-space both
 * work identically here, since these are pure functions of (x,y,z,t) with no external state.
 *
 * <p>All CPU-side per-vertex colour math (no shaders) — the same technique the original
 * "Cosmos" fill already used, deliberately kept: this project tried a real custom GLSL core
 * shader once (see {@code RoundShader}, still in the codebase, dormant) and it compiled but
 * never actually loaded in-game on this hardware. A handful of vertices' worth of Java maths
 * per frame is not a real cost either way, so there's no reason to reach for a shader here.
 *
 * <p>Original math throughout — "Swirl" is inspired by the LOOK of curl-noise fluid shaders
 * (flowing, non-repeating organic movement) but implemented via simple domain warping
 * (sampling a pattern at a position that's itself been offset by a lower-frequency wave —
 * a well-known, generic technique, not derived from any specific shader's source), not a
 * ported/adapted version of any particular reference implementation.
 */
public final class ProceduralFill {
    private ProceduralFill() {}

    /**
     * Deep-space drift (blue → purple → pink), shifting over time + position. Two
     * incommensurate (non-integer-ratio) sine drifts combine so the pattern doesn't visibly
     * repeat for a very long time — a single fast modulo would loop every ~3s.
     */
    public static int cosmos(double x, double y, double z, double timeSec, int alpha) {
        double drift = Math.sin(timeSec / 47.0) * 60.0 + Math.sin(timeSec / 71.0 + 1.7) * 40.0;
        float hue = (float) (260 + drift + (x * 6 + y * 11 + z * 6));
        return (alpha << 24) | (hsv(hue, 0.75f, 0.95f) & 0xFFFFFF);
    }

    /**
     * Flowing liquid/marble swirl (teal/cyan, glossy). Domain-warp: each sample position is
     * offset by a LOWER-frequency wave before a HIGHER-frequency band pattern reads it — this
     * is what turns plain sine stripes into non-repeating organic swirls, the same core idea
     * every curl/flow-noise shader leans on, just built here from stacked sines instead of a
     * real noise function (cheap, no texture lookups, good enough at this vertex density).
     */
    public static int swirl(double x, double y, double z, double timeSec, int alpha) {
        double t = timeSec;
        double wx = x + Math.sin(y * 2.1 + t * 0.55) * 0.45 + Math.sin(z * 1.3 - t * 0.35) * 0.30;
        double wy = y + Math.sin(z * 2.3 - t * 0.50) * 0.45 + Math.cos(x * 1.7 + t * 0.30) * 0.30;
        double wz = z + Math.cos(x * 1.9 + t * 0.40) * 0.40 + Math.sin(y * 1.5 - t * 0.45) * 0.30;

        double band = Math.sin(wx * 2.6 + wy * 1.9 - wz * 1.4 + t * 0.6);        // primary flow band
        double band2 = Math.sin(wy * 3.1 - wx * 1.2 + t * 0.9);                  // finer secondary ripple
        // Third, higher-frequency octave riding on top of the warped field — adds fine glossy
        // detail/sparkle inside the broad bands instead of them reading as flat smooth blobs.
        double band3 = Math.sin(wz * 5.4 - wx * 3.7 + wy * 2.1 + t * 1.3);

        double hue = 186 + 34 * band + 14 * band2 + 6 * band3;                   // teal/cyan neighbourhood
        double sat = clamp(0.55 + 0.28 * Math.sin(wx * 2.0 - wz * 2.4 + t * 0.5) + 0.06 * band3, 0.35, 0.92);
        double val = clamp(0.62 + 0.32 * Math.sin(wy * 2.4 + wz * 1.7 - t * 0.65) + 0.08 * band3, 0.36, 0.99);
        return (alpha << 24) | (hsv((float) hue, (float) sat, (float) val) & 0xFFFFFF);
    }

    /**
     * Starfield — inspired by the LOOK of Shadertoy's "No Man's Starfield" (marian42, flight
     * through clustered, twinkling, coloured stars against near-black deep space) — not its
     * raymarched 3D flight-camera technique (that shoots a ray per screen pixel through a chunked
     * 3D grid; this project only ever samples ONE point at a time, world or mesh space). Reworked
     * as a compact multi-scale point-hash "star cluster" field instead: at a few zoom levels, a
     * cell's hash decides whether it holds a star at all (sparse — most don't), a second hash
     * jitters that star's centre within the cell so stars don't sit on a visible grid, and
     * distance to that jittered centre gives a small round core. The brightest hit across levels
     * wins and is tinted by its own hash into a blue→violet→pink starlight hue, twinkling via a
     * per-star sine offset (seeded by the same hash, so nearby stars don't pulse in lockstep).
     * {@code timeSec} drifts the sample slowly along one axis — the "flight" — and a faint
     * constant deep-space base tint keeps the space between stars from reading as flat void.
     */
    public static int starfield(double x, double y, double z, double timeSec, int alpha) {
        double fz = z + timeSec * 0.6;
        double best = 0, bestHue = 220;
        for (int oct = 0; oct < 3; oct++) {
            double scale = 2.2 * Math.pow(2.0, oct);
            double sx = x * scale, sy = y * scale, sz = fz * scale;
            double cx = Math.floor(sx), cy = Math.floor(sy), cz = Math.floor(sz);
            double h = hash3(cx, cy, cz);
            if (h < 0.72) continue;   // sparse — most cells at this scale hold no star
            double jx = hash3(cx + 4.1, cy, cz), jy = hash3(cx, cy + 7.3, cz), jz = hash3(cx, cy, cz + 2.9);
            double dx = (sx - cx) - jx, dy = (sy - cy) - jy, dz = (sz - cz) - jz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double core = Math.max(0, 1.0 - d * 6.0);
            double twinkle = 0.55 + 0.45 * Math.sin(timeSec * 2.4 + h * 62.0);
            double bright = core * core * twinkle;
            if (bright > best) { best = bright; bestHue = 200 + h * 160; }   // blue..violet..pink range
        }
        best = Math.min(1.0, best);
        int base = hsv(230f, 0.6f, 0.045f);          // near-black deep-space navy, never flat void
        int star = hsv((float) bestHue, 0.35f, 1f);
        int rgb = lerpRgb(base, star, (float) best);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Worms / Cobweb — inspired by the LOOK of Shadertoy's "Worms / cobweb / something"
     * (ianertson: thin, glowing, curling filament lines threading through mostly-empty space) —
     * not its technique (a 2D radial fan of texture-noise lookups; no texture sampling available
     * here). Reworked as a domain-warped RIDGED accumulation: the sample position is pushed
     * around by a slow low-frequency wave (same domain-warp idea {@link #swirl} uses, so the
     * tendrils curl instead of running straight), then several octaves of a summed-sine field are
     * turned into thin bright veins via {@code 1 - |v|} (near 1 only close to a zero-crossing)
     * raised to a steep power — that steepness is what makes it read as slender glowing threads
     * rather than smooth blobby bands. Kept in Lume's own dark/lavender palette (the reference is
     * plain grayscale) — asked for on purpose, not an oversight.
     */
    public static int worms(double x, double y, double z, double timeSec, int alpha) {
        double t = timeSec * 0.35;
        double wx = x + Math.sin(y * 1.7 - t) * 0.6;
        double wy = y + Math.sin(z * 1.9 + t * 1.3) * 0.6;
        double wz = z + Math.sin(x * 1.5 - t * 0.8) * 0.6;

        double ridge = 0, amp = 1.0, freq = 1.0, norm = 0;
        for (int oct = 0; oct < 4; oct++) {
            double v = Math.sin(wx * freq + t) + Math.sin(wy * freq * 1.3 - t * 1.1) + Math.sin(wz * freq * 0.7 + t * 0.6);
            double r = Math.max(0, 1.0 - Math.abs(v / 3.0));
            ridge += amp * Math.pow(r, 9.0);
            norm += amp;
            amp *= 0.55;
            freq *= 2.15;
            wx += Math.sin(wy * 0.7 - t * 0.4) * 0.15;
            wy += Math.sin(wz * 0.7 + t * 0.4) * 0.15;
            wz += Math.sin(wx * 0.7 - t * 0.4) * 0.15;
        }
        double glow = Math.min(1.0, ridge / norm * 2.2);
        int dark = hsv(260f, 0.5f, 0.04f);
        int bright = hsv(268f, 0.25f, 1f);   // near-white lavender glow, matches Lume's accent family
        int rgb = lerpRgb(dark, bright, (float) glow);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /** Generic sine-dot hash → [0,1), same lineage as the ubiquitous GLSL "rand(vec2)" idiom
     *  (boilerplate common to countless shaders, not specific to any one reference). */
    private static double hash3(double x, double y, double z) {
        double s = Math.sin(x * 12.9898 + y * 78.233 + z * 37.719) * 43758.5453;
        return s - Math.floor(s);
    }

    /** Linear-interpolates two 0xRRGGBB colours by {@code t} (0..1). */
    private static int lerpRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    public static int hsv(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        switch ((int) (h / 60f) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }
}

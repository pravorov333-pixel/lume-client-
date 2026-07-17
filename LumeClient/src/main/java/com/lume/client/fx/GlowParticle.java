package com.lume.client.fx;

/**
 * One soft additive glow particle with simple, tunable physics.
 * Position is world-space; rendering is camera-relative (see {@link ParticleEngine}).
 */
public class GlowParticle {

    public double x, y, z;          // world position
    public double vx, vy, vz;       // velocity (blocks / sec)
    public float gravity;           // downward accel (blocks / sec^2); negative floats up
    public float drag;              // velocity retained per second (0.90 = quick stop, 1.0 = none)
    public float turbulence;        // random jitter magnitude (blocks / sec)
    public float size, sizeEnd;     // radius at birth / death (blocks)
    public int rgb;                 // 0xRRGGBB
    public float alpha;             // peak alpha 0..1
    public double life, maxLife;    // seconds
    public int shape = 0;           // ParticleEngine.SHAPE_* — silhouette to render as
    public net.minecraft.util.Identifier texture;   // non-null → drawn as this textured billboard instead of a glow shape

    private static final java.util.Random RND = new java.util.Random();

    public GlowParticle(double x, double y, double z, int rgb) {
        this.x = x; this.y = y; this.z = z; this.rgb = rgb;
        this.drag = 0.98f; this.alpha = 1f; this.size = 0.25f; this.sizeEnd = 0f;
        this.maxLife = this.life = 1.0;
    }

    /** Integrate physics over {@code dt} seconds. Returns false when dead. */
    public boolean tick(double dt) {
        life -= dt;
        if (life <= 0) return false;
        if (turbulence > 0) {
            vx += (RND.nextDouble() - 0.5) * turbulence * dt;
            vy += (RND.nextDouble() - 0.5) * turbulence * dt;
            vz += (RND.nextDouble() - 0.5) * turbulence * dt;
        }
        vy -= gravity * dt;
        double d = Math.pow(drag, dt);   // frame-rate independent drag
        vx *= d; vy *= d; vz *= d;
        x += vx * dt; y += vy * dt; z += vz * dt;
        return true;
    }

    /** 0..1 progress through life (0 = just born). */
    public float progress() {
        return (float) (1.0 - life / maxLife);
    }
}

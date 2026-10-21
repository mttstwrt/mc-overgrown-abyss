package dev.syrval.overgrownabyss.basilisk.entity;

/**
 * Pure-math follow chain for the body. No Minecraft imports on purpose: it is identical on server and
 * client, it survives version ports untouched, and it can be unit-tested without booting the game.
 *
 * Adapted from the idea (not the code) of Naga.moveSegments: each segment is pulled to a fixed distance
 * behind its leader, then nudged toward "straight behind the leader's heading", with the front segments
 * straightening more than the tail. Differences from the naga:
 *  - segments hug the ground via a sampler instead of only escaping walls (jungle slopes, ledges);
 *  - the first NECK_SEGMENTS can rise into a cobra neck while rearing.
 *
 * Convention: yaw/pitch are Minecraft's (yaw 0 faces +Z; positive pitch = nose down).
 */
public final class BasiliskSegmentChain {
    public static final double SPACING = 0.95D;
    public static final int NECK_SEGMENTS = 3;

    /** Returns the standing height of the ground under (x, y, z), or NaN if none is close. */
    public interface GroundSampler {
        double groundY(double x, double y, double z);
    }

    public final int count;
    public final double[] x, y, z, px, py, pz;
    public final float[] yaw, pitch, pyaw, ppitch;

    public BasiliskSegmentChain(int count) {
        this.count = count;
        x = new double[count]; y = new double[count]; z = new double[count];
        px = new double[count]; py = new double[count]; pz = new double[count];
        yaw = new float[count]; pitch = new float[count];
        pyaw = new float[count]; ppitch = new float[count];
    }

    /** Lay the body out straight behind the head. Call once before the first update. */
    public void reset(double hx, double hy, double hz, float headYaw) {
        double rad = Math.toRadians(headYaw);
        double bx = Math.sin(rad), bz = -Math.cos(rad); // unit vector pointing behind the head
        for (int i = 0; i < count; i++) {
            x[i] = hx + bx * SPACING * (i + 1);
            y[i] = hy;
            z[i] = hz + bz * SPACING * (i + 1);
            yaw[i] = headYaw;
            pitch[i] = 0.0F;
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            pyaw[i] = yaw[i]; ppitch[i] = pitch[i];
        }
    }

    /**
     * @param rear 0..1 raw rear progress (eased internally)
     */
    public void update(double hx, double hy, double hz, float headYaw, float rear, GroundSampler ground) {
        for (int i = 0; i < count; i++) {
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            pyaw[i] = yaw[i]; ppitch[i] = pitch[i];
        }

        double rad = Math.toRadians(headYaw);
        double bx = Math.sin(rad), bz = -Math.cos(rad);
        double ease = rear * rear * (3.0D - 2.0D * rear);

        for (int i = 0; i < count; i++) {
            double lx, ly, lz;
            float leadYaw;
            if (i == 0) {
                lx = hx; ly = hy; lz = hz; leadYaw = headYaw;
            } else if (i == NECK_SEGMENTS) {
                // While rearing, the first segment behind the neck attaches to the base, not the raised neck.
                lx = lerp(ease, x[i - 1], hx);
                ly = lerp(ease, y[i - 1], hy);
                lz = lerp(ease, z[i - 1], hz);
                leadYaw = yaw[i - 1];
            } else {
                lx = x[i - 1]; ly = y[i - 1]; lz = z[i - 1]; leadYaw = yaw[i - 1];
            }

            double dx = x[i] - lx, dy = y[i] - ly, dz = z[i] - lz;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-6D) { dx = bx; dy = 0.0D; dz = bz; len = 1.0D; }
            dx /= len; dy /= len; dz /= len;

            // Naga's straighten force: front segments straighten more than the tail.
            double straighten = 0.05D + 0.5D / (i + 1);
            double ang = Math.toRadians(leadYaw + 180.0D);
            dx += -Math.sin(ang) * straighten;
            dz += Math.cos(ang) * straighten;
            len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len; dy /= len; dz /= len;

            double nx = lx + SPACING * dx, ny = ly + SPACING * dy, nz = lz + SPACING * dz;

            boolean neckRaised = i < NECK_SEGMENTS && ease > 0.0D;
            if (neckRaised) {
                double h = 0.45D + i * 0.62D;
                double back = 0.30D * Math.sin(Math.PI * (i + 0.5D) / NECK_SEGMENTS); // gentle S-curve
                nx = lerp(ease, nx, hx + bx * back);
                ny = lerp(ease, ny, hy + h);
                nz = lerp(ease, nz, hz + bz * back);
            }
            if (!(neckRaised && ease > 0.5D)) {
                double g = ground.groundY(nx, ny, nz);
                if (!Double.isNaN(g)) {
                    if (ny < g) ny = g;                       // never sink into the floor
                    else if (ny - g < 1.5D) ny += (g - ny) * 0.4D; // settle onto it; beyond 1.5 stay in the air (climbing)
                }
            }

            double mx = nx - lx, my = ny - ly, mz = nz - lz;
            double horiz = Math.sqrt(mx * mx + mz * mz);
            x[i] = nx; y[i] = ny; z[i] = nz;
            yaw[i] = horiz > 1.0E-3D ? (float) (Math.toDegrees(Math.atan2(mz, mx)) + 90.0D) : headYaw;
            pitch[i] = (float) Math.toDegrees(Math.atan2(my, horiz));
        }
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}

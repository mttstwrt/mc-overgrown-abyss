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
    /** Sleeping coil: Archimedean spiral r = COIL_START_RADIUS + COIL_RADIUS_PER_RAD * theta around the head. */
    public static final double COIL_START_RADIUS = 0.9D;
    public static final double COIL_RADIUS_PER_RAD = 0.17D; // 1.07 blocks between turns
    /** Ticks to blend into / out of the coil. */
    public static final int COIL_BLEND_TICKS = 10;

    /** Returns the standing height of the ground under (x, y, z), or NaN if none is close. */
    public interface GroundSampler {
        double groundY(double x, double y, double z);
    }

    /** Active segment count (arrays are allocated for the maximum). Changed only by {@link #configure}. */
    public int count;
    /** Body scale of this individual: multiplies spacing, neck heights and coil size. */
    public double scale = 1.0D;
    public final double[] x, y, z, px, py, pz;
    public final float[] yaw, pitch, pyaw, ppitch;
    /** 0 = follow chain, 1 = fully coiled. */
    public float coil, pcoil;

    public BasiliskSegmentChain(int maxCount) {
        this.count = maxCount;
        x = new double[maxCount]; y = new double[maxCount]; z = new double[maxCount];
        px = new double[maxCount]; py = new double[maxCount]; pz = new double[maxCount];
        yaw = new float[maxCount]; pitch = new float[maxCount];
        pyaw = new float[maxCount]; ppitch = new float[maxCount];
    }

    /** Set the body scale and active segment count; the caller must {@link #reset} afterwards. */
    public void configure(double scale, int count) {
        this.scale = scale;
        this.count = Math.min(count, x.length);
    }

    public double spacing() {
        return SPACING * scale;
    }

    /** Lay the body out straight behind the head. Call once before the first update. */
    public void reset(double hx, double hy, double hz, float headYaw) {
        double rad = Math.toRadians(headYaw);
        double bx = Math.sin(rad), bz = -Math.cos(rad); // unit vector pointing behind the head
        for (int i = 0; i < count; i++) {
            x[i] = hx + bx * spacing() * (i + 1);
            y[i] = hy;
            z[i] = hz + bz * spacing() * (i + 1);
            yaw[i] = headYaw;
            pitch[i] = 0.0F;
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            pyaw[i] = yaw[i]; ppitch[i] = pitch[i];
        }
    }

    /** Like {@link #reset} but already lying in the sleeping coil, so a sleeper never has a tail outside its chunk. */
    public void resetCoiled(double hx, double hy, double hz, float headYaw) {
        reset(hx, hy, hz, headYaw);
        double[][] layout = coilLayout(count, headYaw, scale);
        for (int i = 0; i < count; i++) {
            x[i] = hx + layout[i][0];
            z[i] = hz + layout[i][1];
            px[i] = x[i]; pz[i] = z[i];
        }
        coil = 1.0F;
        pcoil = 1.0F;
    }

    /**
     * @param rear 0..1 raw rear progress (eased internally)
     */
    public void update(double hx, double hy, double hz, float headYaw, float rear, GroundSampler ground) {
        update(hx, hy, hz, headYaw, rear, false, ground);
    }

    /** @param coiled whether the body should lie in the sleeping coil (blended in over COIL_BLEND_TICKS) */
    public void update(double hx, double hy, double hz, float headYaw, float rear, boolean coiled, GroundSampler ground) {
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

            double sp = spacing();
            double nx = lx + sp * dx, ny = ly + sp * dy, nz = lz + sp * dz;

            boolean neckRaised = i < NECK_SEGMENTS && ease > 0.0D;
            if (neckRaised) {
                double h = (0.45D + i * 0.62D) * scale;
                double back = 0.30D * scale * Math.sin(Math.PI * (i + 0.5D) / NECK_SEGMENTS); // gentle S-curve
                nx = lerp(ease, nx, hx + bx * back);
                ny = lerp(ease, ny, hy + h);
                nz = lerp(ease, nz, hz + bz * back);
            }
            if (!(neckRaised && ease > 0.5D)) {
                double g = ground.groundY(nx, ny, nz);
                if (!Double.isNaN(g)) {
                    if (ny < g) { if (g - ny <= 1.5D * scale) ny = g; } // never sink into the floor; deep inside a wall (climbing) do not snap to its top
                    else if (ny - g < 1.5D * scale) ny += (g - ny) * 0.4D; // settle onto it; beyond 1.5 stay in the air (climbing)
                }
            }

            double mx = nx - lx, my = ny - ly, mz = nz - lz;
            double horiz = Math.sqrt(mx * mx + mz * mz);
            x[i] = nx; y[i] = ny; z[i] = nz;
            yaw[i] = horiz > 1.0E-3D ? (float) (Math.toDegrees(Math.atan2(mz, mx)) + 90.0D) : headYaw;
            pitch[i] = (float) Math.toDegrees(Math.atan2(my, horiz));
        }

        pcoil = coil;
        double step = 1.0D / COIL_BLEND_TICKS;
        coil = (float) Math.max(0.0D, Math.min(1.0D, coil + (coiled ? step : -step)));
        if (coil > 0.0F) applyCoil(hx, hy, hz, headYaw, ground);
    }

    private void applyCoil(double hx, double hy, double hz, float headYaw, GroundSampler ground) {
        double e = coil * coil * (3.0D - 2.0D * coil);
        double[][] layout = coilLayout(count, headYaw, scale);
        double lastX = 0.0D, lastZ = 0.0D;
        for (int i = 0; i < count; i++) {
            double ox = layout[i][0], oz = layout[i][1];
            double tx = hx + ox, tz = hz + oz;
            double ty = hy;
            double g = ground.groundY(tx, hy + 0.5D, tz);
            if (!Double.isNaN(g) && Math.abs(g - hy) < 1.5D * scale) ty = g;
            x[i] = lerp(e, x[i], tx);
            y[i] = lerp(e, y[i], ty);
            z[i] = lerp(e, z[i], tz);
            float coilYaw = (float) (Math.toDegrees(Math.atan2(oz - lastZ, ox - lastX)) + 90.0D);
            yaw[i] = yaw[i] + wrapDegrees(coilYaw - yaw[i]) * (float) e;
            pitch[i] = pitch[i] * (float) (1.0D - e);
            lastX = ox; lastZ = oz;
        }
    }

    /**
     * Horizontal offsets from the head of each body segment on the sleeping spiral. Segment 0 starts straight
     * behind the head at COIL_START_RADIUS; each next segment is exactly SPACING from the previous one.
     * Depends only on yaw, so client and server agree.
     */
    public static double[][] coilLayout(int count, float headYaw) {
        return coilLayout(count, headYaw, 1.0D);
    }

    /** Same spiral scaled uniformly: start radius, turn gap and segment spacing all multiply by {@code scale}. */
    public static double[][] coilLayout(int count, float headYaw, double scale) {
        double startRadius = COIL_START_RADIUS * scale, radiusPerRad = COIL_RADIUS_PER_RAD * scale, spacing = SPACING * scale;
        double rad = Math.toRadians(headYaw);
        double theta0 = Math.atan2(-Math.cos(rad), Math.sin(rad)); // direction behind the head
        double[][] out = new double[count][2];
        double theta = 0.0D;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                double r1 = startRadius + radiusPerRad * theta;
                double lo = 0.0D, hi = Math.PI;
                for (int k = 0; k < 40; k++) {
                    double mid = 0.5D * (lo + hi);
                    double r2 = startRadius + radiusPerRad * (theta + mid);
                    double chord2 = r1 * r1 + r2 * r2 - 2.0D * r1 * r2 * Math.cos(mid);
                    if (chord2 < spacing * spacing) lo = mid; else hi = mid;
                }
                theta += 0.5D * (lo + hi);
            }
            double r = startRadius + radiusPerRad * theta;
            out[i][0] = Math.cos(theta0 + theta) * r;
            out[i][1] = Math.sin(theta0 + theta) * r;
        }
        return out;
    }

    private static float wrapDegrees(float d) {
        d %= 360.0F;
        if (d >= 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}

package dev.syrval.overgrownabyss.ravine;

import java.util.Optional;

/**
 * Hashed jittered grid: each {@code cell_size} square holds at most one ravine, derived only from the world seed,
 * the salt and the cell coordinates. The ravine always fits inside its cell (see {@link RavineSettings#maxReach()}),
 * so callers only need the cell that contains the sample point.
 */
public final class RavineCells {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private RavineCells() {}

    public static Optional<RavineCell> containing(long seed, RavineSettings settings, double x, double z) {
        int size = settings.cellSize();
        return at(seed, settings, Math.floorDiv((long) Math.floor(x), size), Math.floorDiv((long) Math.floor(z), size));
    }

    public static Optional<RavineCell> at(long seed, RavineSettings settings, long cellX, long cellZ) {
        long h = mix(mix(mix(seed ^ mix(settings.salt())) + cellX * 0xC2B2AE3D27D4EB4FL) + cellZ * 0x165667B19E3779F9L);
        if (unit(h, 0) >= settings.chance()) {
            return Optional.empty();
        }
        int size = settings.cellSize();
        double reach = settings.maxReach();
        double centreX = cellX * size + reach + unit(h, 1) * (size - 2 * reach);
        double centreZ = cellZ * size + reach + unit(h, 2) * (size - 2 * reach);
        double angle = unit(h, 3) * Math.PI;
        double length = lerp(unit(h, 4), settings.length().minInclusive(), settings.length().maxInclusive());
        double width = lerp(unit(h, 5), settings.width().minInclusive(), settings.width().maxInclusive());
        return Optional.of(new RavineCell(centreX, centreZ, Math.cos(angle), Math.sin(angle), length / 2, width / 2));
    }

    /** The {@code index}-th uniform value in {@code [0, 1)} drawn from a cell hash. */
    private static double unit(long hash, int index) {
        return (mix(hash + index * GOLDEN) >>> 11) * 0x1.0p-53;
    }

    private static double lerp(double t, double from, double to) {
        return from + (to - from) * t;
    }

    // SplitMix64 finaliser.
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}

package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;

/**
 * Hashed jittered grid: each {@code cell_size} square holds at most one ravine, derived only from the world seed,
 * the salt and the cell coordinates. The ravine always fits inside its cell (see {@link RavineSettings#maxReach()}),
 * so callers only need the cell that contains the sample point.
 */
public final class RavineCells {
    private static final double S_CURVE_CHANCE = 0.75;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    // The levels that a twentieth, two twentieths and so on of smoothAt lie below, measured over three million draws. Blended
    // values gather around a half, so a share of a surface is turned into a level through these, and a level back into a share.
    private static final List<Double> LEVELS = List.of(
            0.000, 0.148, 0.209, 0.258, 0.299, 0.337, 0.372, 0.405, 0.438, 0.469, 0.500,
            0.531, 0.562, 0.595, 0.628, 0.663, 0.701, 0.742, 0.791, 0.852, 1.000);

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
        // One draw sets both dimensions so small ravines are short as well as narrow: a round hole at 0, the
        // configured maximum at 1. The size_bias exponent moves the mix towards large ravines (below 1) or small ones (above 1).
        double scale = Math.pow(unit(h, 4), settings.sizeBias());
        double halfLength = lerp(scale, settings.length().minInclusive(), settings.length().maxInclusive()) / 2;
        double halfWidth = lerp(scale, settings.width().minInclusive(), settings.width().maxInclusive()) / 2;
        return Optional.of(new RavineCell(
                centreX, centreZ, Math.cos(angle), Math.sin(angle), halfLength, halfWidth,
                bend(h, settings.curvature(), scale),
                lean(h, settings.curvature()),
                h));
    }

    // Curving a short ravine by the full amount would fold it onto itself, so the bend follows its size.
    private static RavineCell.Bend bend(long h, RavineCurvature curvature, double scale) {
        // The wiggle is the S-shaped term, so it never fades to nothing: a ravine is at least half-wiggled.
        double wiggle = signed(h, 6);
        return new RavineCell.Bend(
                signed(h, 5) * curvature.maxBend() * scale,
                Math.signum(wiggle) * lerp(Math.abs(wiggle), 0.5, 1) * curvature.maxWiggle() * scale);
    }

    private static RavineCell.Lean lean(long h, RavineCurvature curvature) {
        double angle = unit(h, 7) * 2 * Math.PI;
        // The bow opposes the lean most of the time, which makes the shaft an S in section instead of a plain slant.
        double bow = unit(h, 9) * curvature.maxBow() * (unit(h, 13) < S_CURVE_CHANCE ? -1 : 1);
        return new RavineCell.Lean(Math.cos(angle), Math.sin(angle), unit(h, 8) * curvature.maxLean(), bow);
    }

    /**
     * The one chunk of the cell containing chunk {@code (chunkX, chunkZ)} that may start the city: the chunk holding
     * the ravine centre, or the cell's middle chunk when the cell has no ravine (the structure then declines to
     * generate there). Settings guarantee cells span whole chunks.
     */
    public static ChunkPos centreChunk(long seed, RavineSettings settings, int chunkX, int chunkZ) {
        int size = settings.cellSize();
        long cellX = Math.floorDiv(chunkX * 16L, size);
        long cellZ = Math.floorDiv(chunkZ * 16L, size);
        Optional<RavineCell> cell = at(seed, settings, cellX, cellZ);
        double blockX = cell.map(RavineCell::centreX).orElse(cellX * (double) size + size / 2.0);
        double blockZ = cell.map(RavineCell::centreZ).orElse(cellZ * (double) size + size / 2.0);
        return new ChunkPos(Math.floorDiv((int) Math.floor(blockX), 16), Math.floorDiv((int) Math.floor(blockZ), 16));
    }

    /** The {@code index}-th uniform value in {@code [0, 1)} drawn from a cell hash. */
    static double unit(long hash, int index) {
        return (bits(hash, index) >>> 11) * 0x1.0p-53;
    }

    /** The {@code index}-th draw from a cell hash as it comes, for seeding a sequence of further draws. */
    static long bits(long hash, int index) {
        return mix(hash + index * GOLDEN);
    }

    /** The {@code index}-th uniform value in {@code [0, 1)} drawn from a cell hash for one block column. */
    static double unitAt(long hash, int index, int x, int z) {
        return unit(mix(mix(hash + x * 0xC2B2AE3D27D4EB4FL) + z * 0x165667B19E3779F9L), index);
    }

    /**
     * The {@code index}-th smooth value in {@code [0, 1)} drawn from a cell hash: {@link #unitAt} at whole coordinates, blended
     * in between. Given block coordinates divided by a size, it changes little from one block to the next and is something
     * else altogether that many blocks away.
     */
    static double smoothAt(long hash, int index, double x, double z) {
        int wholeX = (int) Math.floor(x);
        int wholeZ = (int) Math.floor(z);
        double alongX = ease(x - wholeX);
        return lerp(ease(z - wholeZ),
                lerp(alongX, unitAt(hash, index, wholeX, wholeZ), unitAt(hash, index, wholeX + 1, wholeZ)),
                lerp(alongX, unitAt(hash, index, wholeX, wholeZ + 1), unitAt(hash, index, wholeX + 1, wholeZ + 1)));
    }

    /**
     * {@link #smoothAt} over block columns, something else altogether about {@code size} blocks away. Its grid is square, which
     * would show as ponds or patches lined up north and south or east and west, so each index turns it by an angle of its own
     * and it lines up with nothing.
     */
    static double smoothOver(long hash, int index, int x, int z, double size) {
        double angle = unit(hash, index) * 2 * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return smoothAt(hash, index, (x * cos - z * sin) / size, (x * sin + z * cos) / size);
    }

    /**
     * The {@code index}-th smooth value in {@code [0, 1)} drawn from a cell hash on a surface that closes on itself one way: it
     * is the same at {@code around} as at 0, {@code around} being a whole number of columns. Each column is slid along the other
     * way by an amount of its own (drawn from {@code index + 1}), so the values line up in columns and never in rows.
     */
    static double smoothRound(long hash, int index, double round, int around, double along) {
        int column = (int) Math.floor(round);
        return lerp(ease(round - column),
                smoothIn(hash, index, Math.floorMod(column, around), along),
                smoothIn(hash, index, Math.floorMod(column + 1, around), along));
    }

    private static double smoothIn(long hash, int index, int column, double along) {
        double slid = along + unitAt(hash, index + 1, column, 0);
        int whole = (int) Math.floor(slid);
        return lerp(ease(slid - whole), unitAt(hash, index, column, whole), unitAt(hash, index, column, whole + 1));
    }

    /** The smooth value that {@code share} of all smooth values lie below. */
    static double levelBelow(double share) {
        double at = Math.clamp(share, 0, 1) * (LEVELS.size() - 1);
        int below = Math.min((int) at, LEVELS.size() - 2);
        return LEVELS.get(below) + (LEVELS.get(below + 1) - LEVELS.get(below)) * (at - below);
    }

    /** The share of all smooth values that lie below {@code level}: the inverse of {@link #levelBelow}. */
    static double shareBelow(double level) {
        int above = 1;
        while (above < LEVELS.size() - 1 && LEVELS.get(above) < level) {
            above++;
        }
        double from = LEVELS.get(above - 1);
        double along = Math.clamp((level - from) / (LEVELS.get(above) - from), 0, 1);
        return (above - 1 + along) / (LEVELS.size() - 1);
    }

    // Level at both ends, so the blend has no crease along the grid.
    private static double ease(double t) {
        return t * t * (3 - 2 * t);
    }

    /** The {@code index}-th value in [-1, 1) drawn from a cell hash. */
    private static double signed(long hash, int index) {
        return unit(hash, index) * 2 - 1;
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

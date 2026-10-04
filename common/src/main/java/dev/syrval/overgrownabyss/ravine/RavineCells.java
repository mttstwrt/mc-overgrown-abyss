package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;

/**
 * Hashed jittered grid: each {@code cell_size} square holds at most one ravine, derived only from the world seed,
 * the salt and the cell coordinates. The ravine always fits inside its cell (see {@link RavineSettings#maxReach()}),
 * so callers only need the cell that contains the sample point.
 */
public final class RavineCells {
    private static final int MAX_FREE_LEDGES = 40;
    private static final double S_CURVE_CHANCE = 0.75;
    private static final int MAX_DISCS_PER_LEVEL = 10;
    /** Least extra reach, in half widths, that makes a level boundary a shelf worth putting a disc on. */
    private static final double MIN_SHELF = 0.05;
    private static final double ZIGZAG_CHANCE = 0.8;
    /** Least width, in half widths, that two neighbouring levels share. */
    private static final double MIN_TIER_OVERLAP = 0.5;
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
        // One draw sets both dimensions so small ravines are short as well as narrow: a round hole at 0, the
        // configured maximum at 1. The size_bias exponent makes the large ones the rare ones.
        double scale = Math.pow(unit(h, 4), settings.sizeBias());
        double halfLength = lerp(scale, settings.length().minInclusive(), settings.length().maxInclusive()) / 2;
        double halfWidth = lerp(scale, settings.width().minInclusive(), settings.width().maxInclusive()) / 2;
        List<RavineCell.Bridge> bridges = bridges(h, settings, scale, halfLength);
        RavineCell.Tiers tiers = tiers(h, settings.walls().tiers(), scale);
        return Optional.of(new RavineCell(
                centreX, centreZ, Math.cos(angle), Math.sin(angle), halfLength, halfWidth,
                bend(h, settings.curvature(), scale),
                lean(h, settings.curvature()),
                new RavineCell.Wobble(side(h, 10, settings.walls()), side(h, 14, settings.walls())),
                bridges,
                ledges(h, settings.ledges(), scale, halfLength, bridges),
                tiers,
                discs(h, settings.walls().discs(), tiers, scale, halfLength)));
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

    // Each level is placed beside the one below, at most as far as keeps them overlapping, so the shaft stays connected.
    private static RavineCell.Tiers tiers(long h, RavineTiers config, double scale) {
        if (scale < config.minSize() || config.maxCount() == 0) {
            return RavineCell.Tiers.NONE;
        }
        int count = 1 + (int) Math.round(unit(h, 1000) * (config.maxCount() - 1));
        List<RavineCell.Tiers.Level> levels = new ArrayList<>();
        levels.add(RavineCell.Tiers.Level.NEUTRAL);
        RavineCell.Tiers.Level previous = RavineCell.Tiers.Level.NEUTRAL;
        double direction = signed(h, 1001) >= 0 ? 1 : -1;
        for (int i = 1; i <= count; i++) {
            double start = (i + 0.4 * signed(h, 1002 + 4 * i)) / (count + 1);
            double width = lerp(unit(h, 1003 + 4 * i), config.minWidth(), config.maxWidth());
            if (unit(h, 1004 + 4 * i) < ZIGZAG_CHANCE) {
                direction = -direction;
            }
            double step = lerp(unit(h, 1005 + 4 * i), 0.5, 1) * Math.max(0, previous.width() + width - MIN_TIER_OVERLAP);
            double shift = Math.clamp(previous.shift() + direction * step, -config.maxShift(), config.maxShift());
            previous = new RavineCell.Tiers.Level(start, shift, width);
            levels.add(previous);
        }
        return new RavineCell.Tiers(levels);
    }

    // A disc grows from the shelf a level boundary leaves on the side where the upper opening reaches further than the
    // lower one; on the other side the boundary is a ceiling, which has nothing to stand on.
    private static List<RavineCell.Disc> discs(long h, RavineDiscs config, RavineCell.Tiers tiers, double scale, double halfLength) {
        List<RavineCell.Tiers.Level> levels = tiers.levels();
        if (config.spacing() == 0 || levels.size() < 2 || scale < config.minSize()) {
            return List.of();
        }
        List<RavineCell.Disc> discs = new ArrayList<>();
        for (int level = 1; level < levels.size(); level++) {
            RavineCell.Tiers.Level lower = levels.get(level - 1);
            RavineCell.Tiers.Level upper = levels.get(level);
            List<Integer> sides = new ArrayList<>(2);
            for (int side : new int[] {1, -1}) {
                if (side * upper.shift() + upper.width() > side * lower.shift() + lower.width() + MIN_SHELF) {
                    sides.add(side);
                }
            }
            if (sides.isEmpty()) {
                continue;
            }
            int base = 2000 + 64 * level;
            int count = (int) Math.clamp(Math.round(halfLength * 2 / config.spacing() * lerp(unit(h, base), 0.7, 1.3)), 1, MAX_DISCS_PER_LEVEL);
            for (int i = 0; i < count; i++) {
                int index = base + 4 + 5 * i;
                discs.add(new RavineCell.Disc(
                        level,
                        halfLength * (-0.85 + 1.7 * (i + 0.5 + 0.4 * signed(h, index)) / count),
                        sides.get((int) (unit(h, index + 1) * sides.size())),
                        signed(h, index + 2) * config.yJitter(),
                        // Squaring the draw keeps most rooms modest and a few large.
                        lerp(Math.pow(unit(h, index + 3), 1.5), config.minRadius(), config.maxRadius()),
                        lerp(unit(h, index + 4), config.minOffset(), config.maxOffset())));
            }
        }
        return discs;
    }

    private static RavineCell.Side side(long h, int index, RavineWalls walls) {
        return new RavineCell.Side(
                walls.widthWobble() * (0.5 + 0.5 * unit(h, index)),
                lerp(unit(h, index + 1), 40, 90),
                unit(h, index + 2) * 2 * Math.PI);
    }

    private static List<RavineCell.Bridge> bridges(long h, RavineSettings settings, double scale, double halfLength) {
        RavineBridges config = settings.bridges();
        if (scale < config.minSize() || config.maxCount() == 0) {
            return List.of();
        }
        int count = (int) Math.floor(unit(h, 20) * (config.maxCount() + 1));
        List<RavineCell.Bridge> bridges = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bridges.add(new RavineCell.Bridge(
                    signed(h, 21 + 3 * i) * 0.7 * halfLength,
                    lerp(unit(h, 22 + 3 * i), config.minHeight(), config.maxHeight()),
                    config.width() * lerp(unit(h, 23 + 3 * i), 0.7, 1.3),
                    config.thickness()));
        }
        return bridges;
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

    // Every bridge gets a ledge on each wall at its own height and position, so it lands on level, walkable rock.
    // The rest are scattered along both walls, each turned a little so neighbours at different heights cross.
    private static List<RavineCell.Ledge> ledges(long h, RavineLedges config, double scale, double halfLength, List<RavineCell.Bridge> bridges) {
        List<RavineCell.Ledge> ledges = new ArrayList<>();
        for (RavineCell.Bridge bridge : bridges) {
            for (int side : new int[] {1, -1}) {
                ledges.add(new RavineCell.Ledge(bridge.along(), side, bridge.height(), bridge.width() + 12, config.maxDepth(), config.thickness(), 0));
            }
        }
        if (scale < config.minSize()) {
            return ledges;
        }
        int count = (int) Math.min(MAX_FREE_LEDGES, Math.round(halfLength * 2 / 100 * config.density() * lerp(unit(h, 60), 0.7, 1.3)));
        double maxYaw = Math.toRadians(config.maxYaw());
        for (int i = 0; i < count; i++) {
            int base = 100 + 8 * i;
            ledges.add(new RavineCell.Ledge(
                    signed(h, base) * 0.9 * halfLength,
                    unit(h, base + 1) < 0.5 ? 1 : -1,
                    lerp(unit(h, base + 2), config.minHeight(), config.maxHeight()),
                    lerp(unit(h, base + 3), config.minLength(), config.maxLength()),
                    lerp(unit(h, base + 4), config.minDepth(), config.maxDepth()),
                    config.thickness(),
                    signed(h, base + 5) * maxYaw));
        }
        return ledges;
    }

    /** The {@code index}-th uniform value in {@code [0, 1)} drawn from a cell hash. */
    private static double unit(long hash, int index) {
        return (mix(hash + index * GOLDEN) >>> 11) * 0x1.0p-53;
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

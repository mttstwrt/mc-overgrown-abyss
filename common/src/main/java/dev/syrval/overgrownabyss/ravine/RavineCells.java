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
        // configured maximum at 1.
        double scale = unit(h, 4);
        double halfLength = lerp(scale, settings.length().minInclusive(), settings.length().maxInclusive()) / 2;
        double halfWidth = lerp(scale, settings.width().minInclusive(), settings.width().maxInclusive()) / 2;
        return Optional.of(new RavineCell(
                centreX, centreZ, Math.cos(angle), Math.sin(angle), halfLength, halfWidth,
                bend(h, settings.curvature(), scale),
                lean(h, settings.curvature()),
                new RavineCell.Wobble(side(h, 10, settings.walls()), side(h, 14, settings.walls())),
                bridges(h, settings, scale, halfLength)));
    }

    // Curving a short ravine by the full amount would fold it onto itself, so the bend follows its size.
    private static RavineCell.Bend bend(long h, RavineCurvature curvature, double scale) {
        return new RavineCell.Bend(signed(h, 5) * curvature.maxBend() * scale, signed(h, 6) * curvature.maxWiggle() * scale);
    }

    private static RavineCell.Lean lean(long h, RavineCurvature curvature) {
        double angle = unit(h, 7) * 2 * Math.PI;
        return new RavineCell.Lean(Math.cos(angle), Math.sin(angle), unit(h, 8) * curvature.maxLean(), signed(h, 9) * curvature.maxBow());
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

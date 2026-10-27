package dev.syrval.verdantdeep.ravine;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function {@code verdant_deep:ravine_carve}: -1 inside the ravine volume, 1 outside, ramping across
 * {@code edge_falloff} blocks at the walls. Meant to be combined as {@code min(original, carve)}, so terrain outside
 * the ravine keeps its exact sign and therefore its blocks.
 *
 * <p>A cone (see {@link ConeSettings}) also has free-standing structures, which are rock where the original terrain may be
 * air (a cave, or above the surface), so {@code min} cannot make them. {@link #rock()} is a second function of the same type,
 * 1 inside those structures and -1 elsewhere, and the cone is combined as {@code max(min(original, carve), rock)}.
 *
 * <p>Vanilla wiring only hands the world seed to noise holders, so the carve is created unseeded by the codec and
 * re-created with the seed and the level's vertical bounds by the density hook ({@link #bind}); until then it is inert
 * and reads as solid everywhere.
 *
 * <p>The level's bounds are the floor every hole shares and the highest a hole's top may be. A cone whose top follows the
 * ground (see {@link RimSettings}) has a top of its own in each cell, so everything here works with the cell's own bounds
 * ({@link #boundsOf}).
 */
public final class RavineCarve implements DensityFunction.SimpleFunction {
    public static final MapCodec<RavineCarve> MAP_CODEC = RavineSettings.MAP_CODEC.xmap(RavineCarve::of, RavineCarve::settings);
    private static final KeyDispatchDataCodec<RavineCarve> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final double CAVERN_BIOME_MARGIN = 4;
    // One biome cell: enough for what stands on a disc's rim or hangs under it to be in the disc's biome.
    private static final double DISC_BIOME_MARGIN = 4;
    private static final int CHUNK_SIZE = 16;

    private final RavineSettings settings;
    private final long seed;
    // Null only for the unbound instance the codec produces.
    private final RavineBounds bounds;
    private final RavineSites sites;
    private final Output output;

    /** Which of a ravine's two functions an instance computes. */
    private enum Output { OPEN, ROCK }

    /** A cell that holds a hole in this level, and the heights that hole lies between. */
    private record Hole(RavineCell cell, RavineBounds bounds) {}

    private RavineCarve(RavineSettings settings, long seed, RavineBounds bounds, RavineSites sites, Output output) {
        this.settings = settings;
        this.seed = seed;
        this.bounds = bounds;
        this.sites = sites;
        this.output = output;
    }

    /** The carve of these settings as the codec makes it: unseeded, and inert until it is bound. */
    public static RavineCarve of(RavineSettings settings) {
        return new RavineCarve(settings, 0, null, new RavineSites(settings, null), Output.OPEN);
    }

    /** The ravine settings behind a datapack reference; fails loudly if the reference is not a ravine carve. */
    public static RavineSettings settingsOf(DensityFunction function) {
        if (function instanceof RavineCarve carve) {
            return carve.settings;
        }
        throw new IllegalStateException("Expected an verdant_deep:ravine_carve density function but got " + function);
    }

    public RavineCarve bind(long seed, RavineBounds bounds) {
        return new RavineCarve(settings, seed, bounds, new RavineSites(settings, bounds), Output.OPEN);
    }

    /**
     * The rock this ravine adds back, which only a cone has: 1 inside its structures, -1 elsewhere. Shares what this carve
     * knows of each cell.
     */
    public RavineCarve rock() {
        return new RavineCarve(settings, seed, bounds, sites, Output.ROCK);
    }

    public RavineSettings settings() {
        return settings;
    }

    public long seed() {
        return seed;
    }

    /** The level's bounds: the floor of every hole, and the highest a hole's top may be. */
    public RavineBounds bounds() {
        return bounds;
    }

    /** Only cells whose sample columns all pass {@code check} hold a ravine. Called once, before any chunk is built. */
    public void restrictToLand(LandCheck check) {
        sites.restrictToLand(check);
    }

    /**
     * A cone whose top follows the ground reads the ground from {@code ground}, and holds no hole where that is too near
     * {@code seaLevel}. Called once, before any chunk is built.
     */
    public void followGround(SurfaceProbe ground, int seaLevel) {
        sites.followGround(ground, seaLevel);
    }

    /**
     * The ruins on this ravine's discs are made of {@code pieces}; until then there are none. Called once, before any chunk is
     * built.
     */
    public void furnishRuins(RuinPieces pieces) {
        sites.furnishRuins(pieces);
    }

    /**
     * The heights the cell's hole lies between in this level, or empty if it holds none: the hash may place one, but not over
     * an ocean, nor where the ground is too near sea level for a top that follows it.
     */
    public Optional<RavineBounds> boundsOf(RavineCell cell) {
        return bounds == null ? Optional.empty() : sites.boundsOf(cell);
    }

    /**
     * The gas of each hole whose middle is within {@code reach} blocks of the column {@code (x, z)}, each with the id the
     * client knows it by: the column of its middle.
     */
    public List<RavineFootprint.GasNear> gasNear(double x, double z, double reach) {
        int size = settings.cellSize();
        long cellX = Math.floorDiv((long) Math.floor(x), size);
        long cellZ = Math.floorDiv((long) Math.floor(z), size);
        int ring = (int) Math.ceil(reach / size);
        List<RavineFootprint.GasNear> near = new ArrayList<>();
        for (long dx = -ring; dx <= ring; dx++) {
            for (long dz = -ring; dz <= ring; dz++) {
                RavineCells.at(seed, settings, cellX + dx, cellZ + dz)
                        .filter(cell -> cell.distanceToCentre(x, z) <= reach)
                        .ifPresent(cell -> boundsOf(cell).ifPresent(own -> near.add(
                                new RavineFootprint.GasNear(holeId(cell), cell.centreX(), cell.centreZ(), sites.gasOf(cell, own)))));
            }
        }
        return near;
    }

    // The column of a hole's middle, which tells the holes of a level apart and is no secret: it is there to be seen.
    private static long holeId(RavineCell cell) {
        return ((long) Math.floor(cell.centreX()) << 32) | ((long) Math.floor(cell.centreZ()) & 0xFFFFFFFFL);
    }

    /** Whether the cell holds a ravine in this level. */
    public boolean isActive(RavineCell cell) {
        return boundsOf(cell).isPresent();
    }

    /**
     * What the hole of the cell containing {@code (x, z)} holds, with what each piece holds given by {@code held}; empty if the
     * cell has no hole in this level. Reads the discs the carve has already built for the cell.
     */
    public Optional<ChasmStats> statsAt(int x, int z, Function<RuinSite, ChasmStats.Containers> held) {
        return holeAt(x, z).map(hole -> ChasmStats.of(settings, discsOf(hole), held));
    }

    private Optional<Hole> holeAt(int x, int z) {
        return RavineCells.containing(seed, settings, x, z).flatMap(cell -> boundsOf(cell).map(own -> new Hole(cell, own)));
    }

    /** Whether the column at {@code (x, z)} is within reach of an active ravine's cell centre. */
    public boolean isInFootprint(int x, int z) {
        return holeAt(x, z)
                .filter(hole -> hole.cell().distanceToCentre(x, z) <= settings.maxReach())
                .isPresent();
    }

    /**
     * How far down its hole a point is, 0 at the mouth's height to 1 at the floor, or empty if the point is not in the hole's
     * open air, its discs' domes or within {@code margin} blocks of them.
     */
    public OptionalDouble depthShareIn(int x, int y, int z, double margin) {
        Optional<Hole> found = holeAt(x, z);
        if (found.isEmpty()) {
            return OptionalDouble.empty();
        }
        Hole hole = found.get();
        double distance = RavineShape.signedDistance(settings, hole.bounds(), hole.cell(), discsOf(hole).layout(), x, y, z);
        if (distance > margin) {
            return OptionalDouble.empty();
        }
        double floor = hole.bounds().floorY();
        return OptionalDouble.of(Math.clamp((hole.bounds().topY() - y) / (hole.bounds().topY() - floor), 0, 1));
    }

    /** Whether a point is in the cavern's biome volume: the dome plus a margin around its surfaces. */
    public boolean isCavern(int x, int y, int z) {
        return holeAt(x, z)
                .filter(hole -> RavineShape.cavernContains(settings, hole.bounds(), hole.cell(), x, y, z, CAVERN_BIOME_MARGIN))
                .isPresent();
    }

    /**
     * Calls {@code sink} with every block that takes a disc's material in the chunk whose lowest corner is {@code (minX, minZ)},
     * between {@code minY} and {@code maxY} (exclusive). See {@link DiscBlocks}.
     */
    public void forEachDiscBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
        if (settings.discThemes().isEmpty()) {
            return;
        }
        // A cell is a whole number of chunks, so the chunk's corner is in the same cell as the rest of it.
        holeAt(minX, minZ).ifPresent(hole -> DiscBlocks.forEach(settings, hole.bounds(), hole.cell(), discsOf(hole), minX, minZ, minY, maxY, sink));
    }

    /** Calls {@code sink} with every column where a disc holds water in the chunk whose lowest corner is {@code (minX, minZ)}. */
    public void forEachDiscWater(int minX, int minZ, DiscWaterSink sink) {
        if (settings.discThemes().isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> DiscBlocks.forEachWater(hole.cell(), discsOf(hole), minX, minZ, sink));
    }

    /**
     * Calls {@code sink} with every block of ground that stands over a disc's platform in the chunk whose lowest corner is
     * {@code (minX, minZ)}, between {@code minY} and {@code maxY} (exclusive). See {@link DiscGround}.
     */
    public void forEachMoundBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
        if (settings.discThemes().isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> DiscBlocks.forEachMound(discsOf(hole), minX, minZ, minY, maxY, sink));
    }

    /** Calls {@code sink} with every column where a fall's still water lies in the chunk whose lowest corner is {@code (minX, minZ)}. */
    public void forEachFallWater(int minX, int minZ, DiscFallSink sink) {
        if (settings.falls().isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> DiscBlocks.forEachFallWater(discsOf(hole), minX, minZ, sink));
    }

    /**
     * Calls {@code sink} with every spring whose first place is in the chunk whose lowest corner is {@code (minX, minZ)}: the
     * feature that makes it, and the places in the wall to try it at, in order. See {@link DiscFalls}.
     */
    public void forEachSpring(int minX, int minZ, DiscSpringSink sink) {
        Optional<FallSettings.Springs> springs = settings.falls().flatMap(FallSettings::springs);
        if (springs.isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> discsOf(hole).falls().forEachSpring(minX, minZ, spring -> {
            sink.begin(springs.get().feature());
            spring.tries().forEach(place -> sink.tryAt(place.x(), place.y(), place.z()));
        }));
    }

    /**
     * Calls {@code sink} with every block that is root in the chunk whose lowest corner is {@code (minX, minZ)}, between
     * {@code minY} and {@code maxY} (exclusive). See {@link RootBlocks}.
     */
    public void forEachRootBlock(int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
        if (settings.roots().isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> RootBlocks.forEach(settings, hole.cell(), discsOf(hole), minX, minZ, minY, maxY, sink));
    }

    /**
     * Calls {@code sink} with every place under a root where the roots grow something in the chunk whose lowest corner is
     * {@code (minX, minZ)}, between {@code minY} and {@code maxY} (exclusive). See {@link RootBlocks#forEachGrowth}.
     */
    public void forEachRootGrowth(int minX, int minZ, int minY, int maxY, DiscGrowthSink sink) {
        if (settings.roots().isEmpty() || settings.roots().get().growth().isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> RootBlocks.forEachGrowth(settings, hole.cell(), discsOf(hole), minX, minZ, minY, maxY, sink));
    }

    /**
     * Calls {@code sink} with every place where a disc's theme grows something in the chunk whose lowest corner is
     * {@code (minX, minZ)}. See {@link DiscGrowth}.
     */
    public void forEachGrowth(int minX, int minZ, DiscGrowthSink sink) {
        if (settings.discThemes().isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> DiscGrowth.forEach(settings, hole.cell(), discsOf(hole), minX, minZ, sink));
    }

    /**
     * The ruins or the troves whose middle is in the chunk whose lowest corner is {@code (minX, minZ)}. See
     * {@link DiscRuinSites}. Structure starts are asked for in every chunk of a level, so a chunk out of the hole's reach is
     * answered without building the hole's discs.
     */
    public List<RuinSite> sitesIn(DiscSites which, int minX, int minZ) {
        if (settings.discThemes().stream().allMatch(theme -> which.of(theme).isEmpty())) {
            return List.of();
        }
        return holeAt(minX, minZ)
                .filter(hole -> hole.cell().distanceToCentre(minX + CHUNK_SIZE / 2.0, minZ + CHUNK_SIZE / 2.0) <= settings.maxReach() + CHUNK_SIZE)
                .map(hole -> discsOf(hole).sites(which).stream()
                        .filter(site -> site.x() >= minX && site.x() < minX + CHUNK_SIZE && site.z() >= minZ && site.z() < minZ + CHUNK_SIZE)
                        .toList())
                .orElse(List.of());
    }

    /**
     * The biome of the disc a point belongs to (see {@link CellDiscs#ownerAt}), if there is one: the point is in the disc's
     * dome or platform, or within a margin of them.
     */
    public Optional<ResourceKey<Biome>> discBiomeAt(int x, int y, int z) {
        if (settings.discThemes().isEmpty()) {
            return Optional.empty();
        }
        Optional<Hole> hole = holeAt(x, z).filter(h -> h.cell().distanceToCentre(x, z) <= settings.maxReach() + DISC_BIOME_MARGIN);
        if (hole.isEmpty()) {
            return Optional.empty();
        }
        CellDiscs cellDiscs = discsOf(hole.get());
        OptionalInt owner = cellDiscs.ownerAt(settings.discs(), x, y, z, DISC_BIOME_MARGIN);
        return owner.isPresent() ? cellDiscs.themes().get(owner.getAsInt()).flatMap(DiscTheme::biome) : Optional.empty();
    }

    /**
     * Calls {@code sink} with every disc whose theme inherits a biome's features and whose space reaches into the chunk whose
     * lowest corner is {@code (minX, minZ)}, as a plot to grow those features on.
     */
    public void forEachInheritingDisc(int minX, int minZ, DiscPlotSink sink) {
        if (settings.discThemes().isEmpty()) {
            return;
        }
        holeAt(minX, minZ).ifPresent(hole -> {
            CellDiscs cellDiscs = discsOf(hole);
            List<Disc> all = cellDiscs.layout().discs();
            for (int i = 0; i < all.size(); i++) {
                Disc disc = all.get(i);
                double reach = disc.radius() + DISC_BIOME_MARGIN;
                boolean inChunk = disc.x() + reach >= minX && disc.x() - reach <= minX + 15 && disc.z() + reach >= minZ && disc.z() - reach <= minZ + 15;
                Optional<DiscTheme.Inherits> inherits = cellDiscs.themes().get(i).flatMap(DiscTheme::inherits);
                if (inChunk && inherits.isPresent()) {
                    sink.accept(inherits.get(), new Plot(cellDiscs, i));
                }
            }
        });
    }

    private final class Plot implements DiscPlot {
        private final CellDiscs cellDiscs;
        private final int index;
        private final Disc disc;

        Plot(CellDiscs cellDiscs, int index) {
            this.cellDiscs = cellDiscs;
            this.index = index;
            this.disc = cellDiscs.layout().discs().get(index);
        }

        @Override
        public OptionalInt groundAt(int x, int z) {
            double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
            return fromAxis < disc.radius() ? OptionalInt.of(cellDiscs.grounds().get(index).topBlockAt(x, z) + 1) : OptionalInt.empty();
        }

        @Override
        public boolean owns(int x, int y, int z) {
            OptionalInt owner = cellDiscs.ownerAt(settings.discs(), x, y, z, DISC_BIOME_MARGIN);
            return owner.isPresent() && owner.getAsInt() == index;
        }
    }

    private CellDiscs discsOf(Hole hole) {
        return sites.discsOf(hole.cell(), hole.bounds());
    }

    @Override
    public double compute(FunctionContext context) {
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        Optional<Hole> found = holeAt(x, z);
        if (found.isEmpty()) {
            return nothing();
        }
        Hole hole = found.get();
        DiscLayout layout = discsOf(hole).layout();
        double distance = output == Output.ROCK
                ? RavineShape.rockDistance(settings, hole.bounds(), hole.cell(), layout, x, y, z)
                : RavineShape.signedDistance(settings, hole.bounds(), hole.cell(), layout, x, y, z);
        return Math.clamp((output == Output.ROCK ? -distance : distance) / settings.edgeFalloff(), minValue(), maxValue());
    }

    // The value where the function has no say: solid for the carve, no added rock for the rock function.
    private double nothing() {
        return output == Output.ROCK ? minValue() : maxValue();
    }

    @Override
    public double minValue() {
        return -1;
    }

    @Override
    public double maxValue() {
        return 1;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}

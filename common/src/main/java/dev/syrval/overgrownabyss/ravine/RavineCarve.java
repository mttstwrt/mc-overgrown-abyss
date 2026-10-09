package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function {@code overgrown_abyss:ravine_carve}: -1 inside the ravine volume, 1 outside, ramping across
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
    public static final MapCodec<RavineCarve> MAP_CODEC =
            RavineSettings.MAP_CODEC.xmap(settings -> new RavineCarve(settings, 0, null, new RavineSites(settings, null), Output.OPEN), RavineCarve::settings);
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

    /** The ravine settings behind a datapack reference; fails loudly if the reference is not a ravine carve. */
    public static RavineSettings settingsOf(DensityFunction function) {
        if (function instanceof RavineCarve carve) {
            return carve.settings;
        }
        throw new IllegalStateException("Expected an overgrown_abyss:ravine_carve density function but got " + function);
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

    /** Whether the cell holds a ravine in this level. */
    public boolean isActive(RavineCell cell) {
        return boundsOf(cell).isPresent();
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
     * The ruins whose middle is in the chunk whose lowest corner is {@code (minX, minZ)}. See {@link DiscRuinSites}. Structure
     * starts are asked for in every chunk of a level, so a chunk out of the hole's reach is answered without building the
     * hole's discs.
     */
    public List<RuinSite> ruinsIn(int minX, int minZ) {
        if (settings.discThemes().stream().allMatch(theme -> theme.ruins().isEmpty())) {
            return List.of();
        }
        return holeAt(minX, minZ)
                .filter(hole -> hole.cell().distanceToCentre(minX + CHUNK_SIZE / 2.0, minZ + CHUNK_SIZE / 2.0) <= settings.maxReach() + CHUNK_SIZE)
                .map(hole -> discsOf(hole).ruins().stream()
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
            return fromAxis < disc.radius() ? OptionalInt.of(disc.topBlockAt(fromAxis) + 1) : OptionalInt.empty();
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

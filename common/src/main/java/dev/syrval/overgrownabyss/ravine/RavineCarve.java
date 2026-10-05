package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.util.KeyDispatchDataCodec;
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
 */
public final class RavineCarve implements DensityFunction.SimpleFunction {
    public static final MapCodec<RavineCarve> MAP_CODEC =
            RavineSettings.MAP_CODEC.xmap(settings -> new RavineCarve(settings, 0, null, new LandGate(), Output.OPEN), RavineCarve::settings);
    private static final KeyDispatchDataCodec<RavineCarve> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final double CAVERN_BIOME_MARGIN = 4;

    private final RavineSettings settings;
    private final long seed;
    // Null only for the unbound instance the codec produces.
    private final RavineBounds bounds;
    private final LandGate landGate;
    private final Output output;

    /** Which of a ravine's two functions an instance computes. */
    private enum Output { OPEN, ROCK }

    private RavineCarve(RavineSettings settings, long seed, RavineBounds bounds, LandGate landGate, Output output) {
        this.settings = settings;
        this.seed = seed;
        this.bounds = bounds;
        this.landGate = landGate;
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
        return new RavineCarve(settings, seed, bounds, new LandGate(), Output.OPEN);
    }

    /** The rock this ravine adds back, which only a cone has: 1 inside its structures, -1 elsewhere. Shares this carve's land check. */
    public RavineCarve rock() {
        return new RavineCarve(settings, seed, bounds, landGate, Output.ROCK);
    }

    public RavineSettings settings() {
        return settings;
    }

    public long seed() {
        return seed;
    }

    public RavineBounds bounds() {
        return bounds;
    }

    /** Only cells whose sample columns all pass {@code check} hold a ravine. Called once, before any chunk is built. */
    public void restrictToLand(LandCheck check) {
        landGate.use(check);
    }

    /** Whether the cell holds a ravine in this level (the hash may place one, but not over an ocean). */
    public boolean isActive(RavineCell cell) {
        return landGate.allows(cell);
    }

    private Optional<RavineCell> cellAt(int x, int z) {
        return RavineCells.containing(seed, settings, x, z).filter(this::isActive);
    }

    /** Whether the column at {@code (x, z)} is within reach of an active ravine's cell centre. */
    public boolean isInFootprint(int x, int z) {
        return cellAt(x, z)
                .filter(cell -> cell.distanceToCentre(x, z) <= settings.maxReach())
                .isPresent();
    }

    /** Whether a point is in the cavern's biome volume: the dome plus a margin around its surfaces. */
    public boolean isCavern(int x, int y, int z) {
        return bounds != null && cellAt(x, z)
                .filter(cell -> RavineShape.cavernContains(settings, bounds, cell, x, y, z, CAVERN_BIOME_MARGIN))
                .isPresent();
    }

    @Override
    public double compute(FunctionContext context) {
        if (bounds == null) {
            return nothing();
        }
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        Optional<RavineCell> found = cellAt(x, z);
        if (found.isEmpty()) {
            return nothing();
        }
        double distance = output == Output.ROCK
                ? RavineShape.rockDistance(settings, bounds, found.get(), x, y, z)
                : RavineShape.signedDistance(settings, bounds, found.get(), x, y, z);
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

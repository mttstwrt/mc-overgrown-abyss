package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Density function {@code overgrown_abyss:ravine_carve}: -1 inside the ravine volume, 1 outside, ramping across
 * {@code edge_falloff} blocks at the walls. Meant to be combined as {@code min(original, carve)}, so terrain outside
 * the ravine keeps its exact sign and therefore its blocks.
 *
 * <p>Vanilla wiring only hands the world seed to noise holders, so the carve is created unseeded by the codec and
 * re-created with the seed and the level's vertical bounds by the density hook ({@link #bind}); until then it is inert
 * and reads as solid everywhere.
 */
public final class RavineCarve implements DensityFunction.SimpleFunction {
    public static final MapCodec<RavineCarve> MAP_CODEC =
            RavineSettings.MAP_CODEC.xmap(settings -> new RavineCarve(settings, 0, null), RavineCarve::settings);
    private static final KeyDispatchDataCodec<RavineCarve> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final RavineSettings settings;
    private final long seed;
    // Null only for the unbound instance the codec produces.
    private final RavineBounds bounds;
    private final SimplexNoise wallNoise;

    private RavineCarve(RavineSettings settings, long seed, RavineBounds bounds) {
        this.settings = settings;
        this.seed = seed;
        this.bounds = bounds;
        this.wallNoise = new SimplexNoise(new XoroshiroRandomSource(seed, settings.salt()));
    }

    public RavineCarve bind(long seed, RavineBounds bounds) {
        return new RavineCarve(settings, seed, bounds);
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

    /** Whether the column at {@code (x, z)} can be touched by the carve, including wall noise and falloff. */
    public boolean isInFootprint(int x, int z) {
        return RavineCells.containing(seed, settings, x, z)
                .filter(cell -> RavineShape.horizontalDistance(settings, cell, x, z) <= wallMargin())
                .isPresent();
    }

    @Override
    public double compute(FunctionContext context) {
        if (bounds == null) {
            return maxValue();
        }
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        var cell = RavineCells.containing(seed, settings, x, z);
        if (cell.isEmpty()) {
            return maxValue();
        }
        double distance = RavineShape.signedDistance(settings, bounds, cell.get(), x, y, z);
        if (distance >= wallMargin()) {
            return maxValue();
        }
        double scale = settings.wallNoiseScale();
        distance -= wallNoise.getValue(x * scale, y * scale, z * scale) * settings.wallNoiseAmplitude();
        return Math.clamp(distance / settings.edgeFalloff(), minValue(), maxValue());
    }

    private double wallMargin() {
        return settings.wallNoiseAmplitude() + settings.edgeFalloff();
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

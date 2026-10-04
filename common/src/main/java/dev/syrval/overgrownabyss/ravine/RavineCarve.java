package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
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

    private static final double CAVERN_BIOME_MARGIN = 4;
    // Ledge heights drift slowly along the ravine.
    private static final double TERRACE_NOISE_SCALE = 0.008;
    // Sampling broad noise with a stretched vertical axis makes lumps taller than wide, so they read as overhangs.
    private static final double OVERHANG_VERTICAL_STRETCH = 1.6;
    // Noise may narrow a wall by at most this fraction of the shaft's half width, so the centre line stays open and
    // a small hole cannot be pinched shut.
    private static final double MAX_NARROWING = 0.6;

    private final RavineSettings settings;
    private final long seed;
    // Null only for the unbound instance the codec produces.
    private final RavineBounds bounds;
    private final SimplexNoise wallNoise;
    private final SimplexNoise overhangNoise;
    private final SimplexNoise terraceNoise;
    private final LandGate landGate = new LandGate();

    private RavineCarve(RavineSettings settings, long seed, RavineBounds bounds) {
        this.settings = settings;
        this.seed = seed;
        this.bounds = bounds;
        this.wallNoise = new SimplexNoise(new XoroshiroRandomSource(seed, settings.salt()));
        this.overhangNoise = new SimplexNoise(new XoroshiroRandomSource(seed ^ 0x5DEECE66DL, settings.salt() + 1));
        this.terraceNoise = new SimplexNoise(new XoroshiroRandomSource(seed ^ 0x9E3779B9L, settings.salt() + 2));
    }

    /** The ravine settings behind a datapack reference; fails loudly if the reference is not a ravine carve. */
    public static RavineSettings settingsOf(DensityFunction function) {
        if (function instanceof RavineCarve carve) {
            return carve.settings;
        }
        throw new IllegalStateException("Expected an overgrown_abyss:ravine_carve density function but got " + function);
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
            return maxValue();
        }
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        Optional<RavineCell> found = cellAt(x, z);
        if (found.isEmpty()) {
            return maxValue();
        }
        RavineCell cell = found.get();
        double terraceShift = terraceNoise.getValue(x * TERRACE_NOISE_SCALE, z * TERRACE_NOISE_SCALE) * settings.walls().terraceWarp();
        double open = RavineShape.signedDistance(settings, bounds, cell, x, y, z, terraceShift);
        if (open >= wallMargin()) {
            return maxValue();
        }
        double displacement = wallDisplacement(cell, x, y, z, terraceShift);
        open -= displacement;
        double bridge = RavineShape.bridgeDistance(settings, bounds, cell, x, y, z);
        if (Double.isFinite(bridge)) {
            // Rock cut out of the open volume; half the wall noise keeps arches ragged without eating them away.
            open = Math.max(open, -(bridge - displacement * 0.5));
        }
        return Math.clamp(open / settings.walls().edgeFalloff(), minValue(), maxValue());
    }

    // Positive opens the wall outwards, negative pushes rock into the shaft.
    private double wallDisplacement(RavineCell cell, int x, int y, int z, double terraceShift) {
        RavineWalls walls = settings.walls();
        double fineScale = walls.noiseScale();
        double broadScale = walls.overhangScale();
        double fine = wallNoise.getValue(x * fineScale, y * fineScale, z * fineScale) * walls.noiseAmplitude();
        double broad = overhangNoise.getValue(x * broadScale, y * broadScale * OVERHANG_VERTICAL_STRETCH, z * broadScale)
                * walls.overhangAmplitude();
        double halfWidth = RavineShape.halfWidthAt(settings, bounds, cell, y, terraceShift);
        return Math.max(fine + broad, -MAX_NARROWING * halfWidth);
    }

    private double wallMargin() {
        return settings.walls().maxNoiseDisplacement() + settings.walls().edgeFalloff();
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

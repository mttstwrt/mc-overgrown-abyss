package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.ravine.RavineCarve;
import dev.syrval.overgrownabyss.ravine.RavineCells;
import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Wraps the final density of every noise-settings in {@code #overgrown_abyss:carved} with
 * {@code min(original, overgrown_abyss:ravine/carve)} when a level's {@link RandomState} is created. The wrap happens
 * at runtime, so whichever terrain source owns the noise-settings (vanilla, Larion, ...) is kept as is.
 */
public final class RavineDensityHook {
    private static final TagKey<NoiseGeneratorSettings> CARVED = TagKey.create(Registries.NOISE_SETTINGS, OvergrownAbyss.id("carved"));
    private static final ResourceKey<DensityFunction> CARVE = ResourceKey.create(Registries.DENSITY_FUNCTION, OvergrownAbyss.id("ravine/carve"));

    private RavineDensityHook() {}

    public static RandomState createRandomState(
            RegistryAccess registries,
            ChunkGenerator generator,
            NoiseGeneratorSettings settings,
            long seed,
            Function<NoiseGeneratorSettings, RandomState> factory) {
        if (!(generator instanceof NoiseBasedChunkGenerator noise) || !noise.generatorSettings().is(CARVED)) {
            return factory.apply(settings);
        }
        var carve = registries.registryOrThrow(Registries.DENSITY_FUNCTION).getHolder(CARVE).map(Holder::value);
        if (carve.isEmpty()) {
            OvergrownAbyss.LOGGER.error("Density function {} is missing; ravines are disabled", CARVE.location());
            return factory.apply(settings);
        }
        List<RavineCarve> seeded = new ArrayList<>();
        DensityFunction seededCarve = carve.get().mapAll(function -> {
            if (function instanceof RavineCarve ravine) {
                RavineCarve withSeed = ravine.withSeed(seed);
                seeded.add(withSeed);
                return withSeed;
            }
            return function;
        });
        DensityFunction finalDensity = DensityFunctions.min(settings.noiseRouter().finalDensity(), seededCarve);
        RandomState state = factory.apply(withFinalDensity(settings, finalDensity));
        ((RavineFootprintHolder) (Object) state).overgrownAbyss$setFootprint(RavineFootprint.of(seeded));
        seeded.forEach(RavineDensityHook::logRavinesNearOrigin);
        return state;
    }

    // disableMobGeneration() is deprecated by Mojang but still a record component, so copying the record needs it.
    @SuppressWarnings("deprecation")
    private static NoiseGeneratorSettings withFinalDensity(NoiseGeneratorSettings settings, DensityFunction finalDensity) {
        NoiseRouter r = settings.noiseRouter();
        NoiseRouter router = new NoiseRouter(
                r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(),
                r.temperature(), r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(),
                r.initialDensityWithoutJaggedness(), finalDensity, r.veinToggle(), r.veinRidged(), r.veinGap());
        return new NoiseGeneratorSettings(
                settings.noiseSettings(), settings.defaultBlock(), settings.defaultFluid(), router,
                settings.surfaceRule(), settings.spawnTarget(), settings.seaLevel(), settings.disableMobGeneration(),
                settings.aquifersEnabled(), settings.oreVeinsEnabled(), settings.useLegacyRandomSource());
    }

    // There is no /locate target until the city exists, so log where to look.
    private static void logRavinesNearOrigin(RavineCarve carve) {
        for (int cellX = -1; cellX <= 1; cellX++) {
            for (int cellZ = -1; cellZ <= 1; cellZ++) {
                RavineCells.at(carve.seed(), carve.settings(), cellX, cellZ).ifPresent(cell -> OvergrownAbyss.LOGGER.info(
                        "Ravine centre at x={} z={} (floor y={})",
                        Math.round(cell.centreX()), Math.round(cell.centreZ()), carve.settings().floorY()));
            }
        }
    }
}

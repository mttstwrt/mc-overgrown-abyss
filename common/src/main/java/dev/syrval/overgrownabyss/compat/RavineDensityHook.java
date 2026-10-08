package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.ravine.ConeSettings;
import dev.syrval.overgrownabyss.ravine.RavineBounds;
import dev.syrval.overgrownabyss.ravine.RavineCarve;
import dev.syrval.overgrownabyss.ravine.RavineCell;
import dev.syrval.overgrownabyss.ravine.LandCheck;
import dev.syrval.overgrownabyss.ravine.RavineCells;
import dev.syrval.overgrownabyss.ravine.DiscShape;
import dev.syrval.overgrownabyss.ravine.DiscTheme;
import dev.syrval.overgrownabyss.ravine.RavinePlacement;
import dev.syrval.overgrownabyss.ravine.RavineEnvironment;
import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import dev.syrval.overgrownabyss.ravine.RavineSettings;
import dev.syrval.overgrownabyss.ravine.RavineShape;
import dev.syrval.overgrownabyss.ravine.RimSettings;
import dev.syrval.overgrownabyss.ravine.SurfaceProbe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Wraps the final density of every noise-settings in {@code #overgrown_abyss:carved} with
 * {@code min(original, overgrown_abyss:ravine/carve)} when a level's {@link RandomState} is created. The wrap happens
 * at runtime, so whichever terrain source owns the noise-settings (vanilla, Larion, ...) is kept as is.
 */
public final class RavineDensityHook {
    private static final TagKey<NoiseGeneratorSettings> CARVED = TagKey.create(Registries.NOISE_SETTINGS, OvergrownAbyss.id("carved"));
    private static final ResourceKey<DensityFunction> CARVE = ResourceKey.create(Registries.DENSITY_FUNCTION, OvergrownAbyss.id("ravine/carve"));
    // The world-load log covers this many cells from the origin, each way.
    private static final int LOGGED_CELLS = 3;

    private RavineDensityHook() {}

    public static RandomState createRandomState(
            RegistryAccess registries,
            ChunkGenerator generator,
            LevelHeightAccessor level,
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
        var context = new WorldGenerationContext(generator, level);
        boolean[] invalid = {false};
        DensityFunction seededCarve = carve.get().mapAll(function -> {
            if (!(function instanceof RavineCarve ravine)) {
                return function;
            }
            Optional<RavineBounds> bounds = ravine.settings().resolveBounds(context);
            if (bounds.isEmpty()) {
                invalid[0] = true;
                return function;
            }
            RavineCarve bound = ravine.bind(seed, bounds.get());
            seeded.add(bound);
            return bound;
        });
        if (invalid[0]) {
            OvergrownAbyss.LOGGER.error("Ravine top is not above its floor in this level's height range; ravines are disabled");
            return factory.apply(settings);
        }
        DensityFunction carved = DensityFunctions.min(settings.noiseRouter().finalDensity(), seededCarve);
        // The discs' platforms and stems are rock the terrain may not have (a cave, or above the surface), so they are added with max.
        DensityFunction finalDensity = seededCarve instanceof RavineCarve single ? DensityFunctions.max(carved, single.rock()) : carved;
        RandomState state = factory.apply(withFinalDensity(settings, finalDensity));
        // A top that follows the ground needs the ground as it is without the carve: the level's own settings, wired once more
        // with the same seed, give exactly that.
        SurfaceProbe ground = seeded.stream().anyMatch(ravine -> rimOf(ravine).isPresent())
                ? TerrainSurface.of(settings.noiseSettings(), factory.apply(settings).router().finalDensity())
                : SurfaceProbe.SOLID;
        installLevelBindings(registries, generator, settings, state, ground, seeded);
        return state;
    }

    private static Optional<RimSettings> rimOf(RavineCarve carve) {
        return carve.settings().cone().flatMap(ConeSettings::rim);
    }

    // The land check and the biomes need the finished RandomState (climate sampler) and the level's registries.
    private static void installLevelBindings(
            RegistryAccess registries,
            ChunkGenerator generator,
            NoiseGeneratorSettings settings,
            RandomState state,
            SurfaceProbe ground,
            List<RavineCarve> carves) {
        Registry<Biome> biomes = registries.registryOrThrow(Registries.BIOME);
        Registry<ConfiguredFeature<?, ?>> features = registries.registryOrThrow(Registries.CONFIGURED_FEATURE);
        List<RavineFootprint.Region> regions = new ArrayList<>();
        for (RavineCarve carve : carves) {
            RavineEnvironment environment = carve.settings().environment();
            carve.restrictToLand(landCheck(generator.getBiomeSource(), state.sampler(), settings.seaLevel(), environment.forbiddenBiomes()));
            carve.followGround(ground, settings.seaLevel());
            regions.add(new RavineFootprint.Region(carve, cavernBiome(biomes, environment), discBiomes(biomes, carve.settings().discThemes())));
            warnOfMissingGrowth(features, carve.settings().discThemes());
            bindInheritance(biomes, carve.settings().discThemes());
            logSettings(carve.settings());
            logRavinesNearOrigin(carve, settings.seaLevel());
        }
        ((RavineFootprintHolder) (Object) state).overgrownAbyss$setFootprint(RavineFootprint.of(regions));
    }

    // Sampled at sea level: over ocean that is an ocean biome, over land it is a surface or cave biome.
    private static LandCheck landCheck(BiomeSource source, Climate.Sampler sampler, int sampleY, TagKey<Biome> forbidden) {
        return (x, z) -> !source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sampleY), QuartPos.fromBlock(z), sampler).is(forbidden);
    }

    private static Optional<Holder<Biome>> cavernBiome(Registry<Biome> biomes, RavineEnvironment environment) {
        return environment.cavernBiome().flatMap(key -> {
            Optional<Holder<Biome>> biome = biomes.getHolder(key).map(holder -> holder);
            if (biome.isEmpty()) {
                OvergrownAbyss.LOGGER.warn("Cavern biome {} does not exist; the cavern keeps its natural biome", key.location());
            }
            return biome;
        });
    }

    // A theme whose biome is missing still gives its discs their material and growth; they keep the biome they lie in.
    private static Map<ResourceKey<Biome>, Holder<Biome>> discBiomes(Registry<Biome> biomes, List<DiscTheme> themes) {
        Map<ResourceKey<Biome>, Holder<Biome>> found = new HashMap<>();
        for (DiscTheme theme : themes) {
            theme.biome().ifPresent(key -> biomes.getHolder(key).ifPresentOrElse(
                    holder -> found.put(key, holder),
                    () -> OvergrownAbyss.LOGGER.warn("Disc biome {} does not exist; discs of that theme keep the biome they lie in", key.location())));
        }
        return found;
    }

    /**
     * Points each inheriting disc biome at its parent. Only vanilla-style biomes are parents: a disc biome is never one, which
     * also rules out two of them inheriting from each other.
     */
    private static void bindInheritance(Registry<Biome> biomes, List<DiscTheme> themes) {
        for (DiscTheme theme : themes) {
            if (theme.inherits().isEmpty() || theme.biome().isEmpty()) {
                continue;
            }
            DiscTheme.Inherits inherits = theme.inherits().get();
            Optional<Holder.Reference<Biome>> child = biomes.getHolder(theme.biome().get());
            Optional<Holder.Reference<Biome>> parent = biomes.getHolder(inherits.biome());
            boolean parentIsADiscBiome = themes.stream().anyMatch(other -> other.biome().filter(inherits.biome()::equals).isPresent());
            if (child.isEmpty() || parent.isEmpty() || parentIsADiscBiome) {
                OvergrownAbyss.LOGGER.warn(
                        "Disc biome {} cannot inherit from {}: {}", theme.biome().get().location(), inherits.biome().location(),
                        parentIsADiscBiome ? "that is a disc biome itself" : "one of the two does not exist");
                continue;
            }
            Set<EntityType<?>> withoutSpawns = new HashSet<>();
            for (ResourceLocation id : inherits.withoutSpawns()) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresentOrElse(
                        withoutSpawns::add,
                        () -> OvergrownAbyss.LOGGER.warn("Mob {} does not exist; a disc theme's leaving it out changes nothing", id));
            }
            ((InheritingBiome) (Object) child.get().value()).overgrownAbyss$inheritFrom(parent.get(), withoutSpawns);
            var inherited = parent.get().value().getGenerationSettings().features();
            long grown = inherits.stages().stream().filter(stage -> stage.ordinal() < inherited.size())
                    .mapToLong(stage -> inherited.get(stage.ordinal()).stream().filter(f -> f.unwrapKey().filter(inherits.withoutFeatures()::contains).isEmpty()).count())
                    .sum();
            // Read back through the child, which shows what its spawn lists have become.
            OvergrownAbyss.LOGGER.info(
                    "Disc biome {} inherits from {}: {} features in {}, and {} spawn entries where the parent has {}{}",
                    theme.biome().get().location(), inherits.biome().location(), grown, inherits.stages(),
                    spawnEntries(child.get().value()), spawnEntries(parent.get().value()),
                    inherits.withoutSpawns().isEmpty() ? "" : " (left out: " + inherits.withoutSpawns() + ")");
        }
    }

    private static long spawnEntries(Biome biome) {
        return Arrays.stream(MobCategory.values()).mapToLong(category -> biome.getMobSettings().getMobs(category).unwrap().size()).sum();
    }

    private static void warnOfMissingGrowth(Registry<ConfiguredFeature<?, ?>> features, List<DiscTheme> themes) {
        for (DiscTheme theme : themes) {
            for (DiscTheme.Growth growth : theme.growth()) {
                if (features.getHolder(growth.feature()).isEmpty()) {
                    OvergrownAbyss.LOGGER.warn("Configured feature {} does not exist; a disc theme's growth of it is skipped", growth.feature().location());
                }
            }
        }
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

    // Shows which values the world actually loaded: a datapack's carve.json replaces the mod's, and this is how to tell.
    private static void logSettings(RavineSettings s) {
        DiscShape discs = s.discs();
        OvergrownAbyss.LOGGER.info(
                "Ravine settings: size_bias {}, edge falloff {}, discs (radius {}-{}, height ratio {}, floor thickness {}, stem fraction {}, stem radius {}-{}, funnel scale {}, root spread {}, root scale {}, bowl depth {}-{})",
                s.sizeBias(), s.edgeFalloff(), discs.minRadius(), discs.maxRadius(), discs.heightRatio(), discs.floorThickness(),
                discs.stemFraction(), discs.minStemRadius(), discs.maxStemRadius(), discs.funnelScale(), discs.rootSpread(), discs.rootScale(),
                discs.minBowlDepth(), discs.maxBowlDepth());
        OvergrownAbyss.LOGGER.info(
                "Disc themes: {}{}", s.discThemes().size(),
                s.discThemes().isEmpty() ? " (every disc is the terrain's own rock)" : " " + s.discThemes().stream()
                        .map(theme -> theme.biome().map(key -> key.location().toString()).orElse("no biome")).toList());
        s.ravine().ifPresent(ravine -> {
            RavinePlacement p = ravine.placement();
            OvergrownAbyss.LOGGER.info(
                    "Ravine: spacing {}, row spacing {}, row jitter {}, large offset bonus {}, max overshoot {}, side stagger {}, curvature (bend {}, wiggle {}, lean {}, bow {})",
                    p.spacing(), p.rowSpacing(), p.rowJitter(), p.largeOffsetBonus(), p.maxOvershoot(), p.sideStagger(),
                    ravine.curvature().maxBend(), ravine.curvature().maxWiggle(), ravine.curvature().maxLean(), ravine.curvature().maxBow());
        });
        s.cone().ifPresent(cone -> OvergrownAbyss.LOGGER.info("Cone: {}, wall noise {}", cone, s.wallNoise().layers()));
    }

    /**
     * Lists the ravines in the cells nearest the origin, and counts the cells there whose hash holds one that the level does not
     * allow: how many of those there are is what {@code min_above_sea}, {@code chance} and {@code cell_size} are tuned by.
     */
    private static void logRavinesNearOrigin(RavineCarve carve, int seaLevel) {
        // The ground is not read above the top, so ground that has to stand higher than that is never found.
        rimOf(carve).filter(rim -> seaLevel + rim.minAboveSea() > carve.bounds().topY()).ifPresent(rim -> OvergrownAbyss.LOGGER.error(
                "The rim's min_above_sea {} over a sea at {} is above the ravine's top {}; no ravine can generate",
                rim.minAboveSea(), seaLevel, carve.bounds().topY()));
        int drawn = 0;
        int held = 0;
        for (int cellX = -LOGGED_CELLS; cellX < LOGGED_CELLS; cellX++) {
            for (int cellZ = -LOGGED_CELLS; cellZ < LOGGED_CELLS; cellZ++) {
                Optional<RavineCell> cell = RavineCells.at(carve.seed(), carve.settings(), cellX, cellZ);
                Optional<RavineBounds> bounds = cell.flatMap(carve::boundsOf);
                drawn += cell.isPresent() ? 1 : 0;
                if (bounds.isEmpty()) {
                    continue;
                }
                held++;
                OvergrownAbyss.LOGGER.info(
                        "Ravine centre at x={} z={} (floor y={}, top y={}, mouth's edge up to y={}, {} long, {} wide, {} rows of discs or layers of structures)",
                        Math.round(cell.get().centreX()), Math.round(cell.get().centreZ()), bounds.get().floorY(), bounds.get().topY(),
                        bounds.get().highestEdge(), Math.round(cell.get().halfLength() * 2), Math.round(cell.get().halfWidth() * 2),
                        RavineShape.discRows(carve.settings(), bounds.get()));
            }
        }
        OvergrownAbyss.LOGGER.info(
                "Ravines within {} blocks of the origin each way: {}, in {} cells drawn for one (the rest are ocean, river or beach, or ground too near sea level for the rim's min_above_sea)",
                LOGGED_CELLS * carve.settings().cellSize(), held, drawn);
    }
}

package dev.syrval.verdantdeep.compat;

import dev.syrval.verdantdeep.VerdantDeep;
import dev.syrval.verdantdeep.ravine.DiscGrowthSink;
import dev.syrval.verdantdeep.ravine.DiscPlot;
import dev.syrval.verdantdeep.ravine.DiscSpringSink;
import dev.syrval.verdantdeep.ravine.DiscTheme;
import dev.syrval.verdantdeep.ravine.RavineFootprint;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Grows things on the discs of one chunk: what each disc's theme asks for itself, and then the features of the biome the
 * theme inherits from (see {@code DiscTheme}). A disc's biome is not one the level's biome source can produce, so vanilla
 * never runs a feature list for it; this does, in vanilla's own way but with each disc as the ground. What hangs under a
 * hole's roots (see {@code RootSettings.Growth}) is grown here too, after the discs.
 */
public final class DiscGrower {
    // Keeps these draws apart from vanilla's own decoration draws for the same chunk.
    private static final long SALT = 0x4F41_4752_4F57_5321L;
    // Feature seeds for the plots of a chunk are spaced this far apart, as vanilla spaces the features of a stage.
    private static final int FEATURES_PER_PLOT = 10_000;

    private DiscGrower() {}

    public static void grow(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator) {
        RavineFootprint footprint = ((RavineFootprintHolder) (Object) level.getLevel().getChunkSource().randomState()).verdantDeep$footprint();
        if (footprint == RavineFootprint.NONE) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        var draws = new WorldgenRandom(new XoroshiroRandomSource(level.getSeed() ^ SALT));
        long decorationSeed = draws.setDecorationSeed(level.getSeed() ^ SALT, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        DiscGrowthSink place = placing(level, generator, draws);
        // A theme's own growth is its trees, and vanilla too grows a biome's trees before its grass: grass that came first
        // would hold the very blocks the trees start in. What is inherited then fills in around them.
        footprint.forEachGrowth(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), place);
        growInherited(level, generator, footprint, chunkPos, draws, decorationSeed);
        // Last of what grows, so that what hangs under a root finds a disc's trees already standing in its way.
        footprint.forEachRootGrowth(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), level.getMinBuildHeight(), level.getMaxBuildHeight(), place);
        openSprings(level, generator, footprint, chunkPos, draws);
    }

    // A spring is a feature given a block in the wall behind a disc. The wall is whatever the terrain left there, so the
    // feature is the judge of whether a place will do, and the places reckoned for it are tried until one does.
    private static void openSprings(WorldGenLevel level, ChunkGenerator generator, RavineFootprint footprint, ChunkPos chunkPos, WorldgenRandom draws) {
        Registry<ConfiguredFeature<?, ?>> features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        footprint.forEachSpring(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), new DiscSpringSink() {
            private Optional<Holder.Reference<ConfiguredFeature<?, ?>>> spring = Optional.empty();
            private boolean open;

            @Override
            public void begin(ResourceKey<ConfiguredFeature<?, ?>> feature) {
                spring = features.getHolder(feature);
                open = false;
            }

            @Override
            public void tryAt(int x, int y, int z) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!open && spring.isPresent() && !level.isOutsideBuildHeight(pos)) {
                    open = spring.get().value().place(level, generator, draws, pos);
                }
            }
        });
    }

    // The parent biome's features, read now: the list is whatever the parent has after other mods have changed it.
    private static void growInherited(
            WorldGenLevel level, ChunkGenerator generator, RavineFootprint footprint, ChunkPos chunkPos, WorldgenRandom draws, long decorationSeed) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        // Vanilla starts every feature of a chunk from its lowest corner and lets the feature's own rules spread it out.
        BlockPos origin = new BlockPos(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ());
        int[] plots = {0};
        footprint.forEachInheritingDisc(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), (inherits, plot) -> {
            int plotIndex = plots[0]++;
            Optional<Holder.Reference<Biome>> parent = biomes.getHolder(inherits.biome());
            if (parent.isEmpty()) {
                return;
            }
            List<HolderSet<PlacedFeature>> byStage = parent.get().value().getGenerationSettings().features();
            for (GenerationStep.Decoration stage : inherits.stages()) {
                if (stage.ordinal() >= byStage.size()) {
                    continue;
                }
                int featureIndex = 0;
                for (Holder<PlacedFeature> feature : byStage.get(stage.ordinal())) {
                    featureIndex++;
                    if (feature.unwrapKey().filter(inherits.withoutFeatures()::contains).isPresent()) {
                        continue;
                    }
                    draws.setFeatureSeed(decorationSeed, plotIndex * FEATURES_PER_PLOT + featureIndex, stage.ordinal());
                    try {
                        placeOnPlot(feature.value(), level, generator, draws, origin, plot);
                    } catch (RuntimeException e) {
                        // These features were written for open ground, some by other mods, and one may not cope with a
                        // disc. It is left out of this chunk, since an exception here would stop the chunk generating at all.
                        VerdantDeep.LOGGER.error(
                                "Feature {} inherited from {} failed on a disc in chunk {} and is left out there: {}",
                                feature.unwrapKey().map(key -> key.location().toString()).orElse("(unnamed)"), inherits.biome().location(), chunkPos, e.toString());
                        VerdantDeep.LOGGER.debug("The failure in full", e);
                    }
                }
            }
        });
    }

    /**
     * Runs a placed feature the way vanilla does, with two changes. Its placement rules ask a {@link DiscPlacementContext} for
     * the ground, so they find the disc. And where a rule asks whether the biome at the place lists the feature, the answer
     * is whether the place is this disc's: the disc's own biome lists nothing, which is what keeps other biomes' features off
     * it, so the usual question would turn away the very features the disc inherits.
     */
    private static void placeOnPlot(PlacedFeature placed, WorldGenLevel level, ChunkGenerator generator, WorldgenRandom draws, BlockPos origin, DiscPlot plot) {
        PlacementContext context = new DiscPlacementContext(level, generator, placed, plot);
        Stream<BlockPos> places = Stream.of(origin);
        for (PlacementModifier rule : placed.placement()) {
            places = rule instanceof BiomeFilter
                    ? places.filter(pos -> plot.owns(pos.getX(), pos.getY(), pos.getZ()))
                    : places.flatMap(pos -> rule.getPositions(context, draws, pos));
        }
        ConfiguredFeature<?, ?> feature = placed.feature().value();
        // Asked again at the end, since a feature with no biome rule of its own would otherwise land anywhere in the chunk.
        places.filter(pos -> plot.owns(pos.getX(), pos.getY(), pos.getZ())).forEach(pos -> feature.place(level, generator, draws, pos));
    }

    // Places a feature where a disc's theme or a hole's roots grow one, if the place is still what it was reckoned to be.
    private static DiscGrowthSink placing(WorldGenLevel level, ChunkGenerator generator, WorldgenRandom draws) {
        Registry<ConfiguredFeature<?, ?>> features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        return (x, y, z, feature, on) -> {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.isOutsideBuildHeight(pos) || !isOnItsSurface(level, pos, on)) {
                return;
            }
            features.getHolder(feature).ifPresent(found -> found.value().place(level, generator, draws, pos));
        };
    }

    // The place must be open and still be on the disc's own surface: another disc's platform or stem may have taken it, the
    // water of a pond is left out where something stands on the platform, and a rim inside the hole's wall has no open side.
    // Under a root it is the same question as under a disc: a root runs into the wall, a platform and the floor alike.
    private static boolean isOnItsSurface(WorldGenLevel level, BlockPos pos, DiscTheme.Surface on) {
        return switch (on) {
            case TOP -> level.isEmptyBlock(pos) && isSturdy(level, pos.below(), Direction.UP);
            case WATER -> level.getBlockState(pos).is(Blocks.WATER) && isSturdy(level, pos.below(), Direction.UP);
            case LAVA -> level.isEmptyBlock(pos) && level.getBlockState(pos.below()).is(Blocks.LAVA);
            case UNDERSIDE -> level.isEmptyBlock(pos) && isSturdy(level, pos.above(), Direction.DOWN);
            case RIM -> level.isEmptyBlock(pos)
                    && Direction.Plane.HORIZONTAL.stream().anyMatch(side -> isSturdy(level, pos.relative(side), side.getOpposite()));
        };
    }

    private static boolean isSturdy(WorldGenLevel level, BlockPos pos, Direction face) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, face);
    }
}

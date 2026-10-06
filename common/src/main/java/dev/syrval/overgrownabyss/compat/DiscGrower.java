package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.DiscTheme;
import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Grows what each disc's theme asks for on the discs of one chunk (see {@code DiscTheme.Growth}). A disc's biome is not one the
 * level's biome source can produce, so vanilla never runs a feature list for it; this places the theme's features itself, at
 * the places the disc's shape gives, which a biome's own features could not find under other discs anyway.
 */
public final class DiscGrower {
    // Keeps these draws apart from vanilla's own decoration draws for the same chunk.
    private static final long SALT = 0x4F41_4752_4F57_5321L;

    private DiscGrower() {}

    public static void grow(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator) {
        RavineFootprint footprint = ((RavineFootprintHolder) (Object) level.getLevel().getChunkSource().randomState()).overgrownAbyss$footprint();
        if (footprint == RavineFootprint.NONE) {
            return;
        }
        Registry<ConfiguredFeature<?, ?>> features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        ChunkPos chunkPos = chunk.getPos();
        var draws = new WorldgenRandom(new XoroshiroRandomSource(level.getSeed() ^ SALT));
        draws.setDecorationSeed(level.getSeed() ^ SALT, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        footprint.forEachGrowth(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), (x, y, z, feature, on) -> {
            BlockPos pos = new BlockPos(x, y, z);
            // The place must be open and still be on the disc's own surface: another disc's platform or stem may have taken it.
            boolean onTop = on == DiscTheme.Surface.TOP;
            BlockPos surface = onTop ? pos.below() : pos.above();
            if (level.isOutsideBuildHeight(pos) || !level.isEmptyBlock(pos)
                    || !level.getBlockState(surface).isFaceSturdy(level, surface, onTop ? Direction.UP : Direction.DOWN)) {
                return;
            }
            features.getHolder(feature).ifPresent(found -> found.value().place(level, generator, draws, pos));
        });
    }
}

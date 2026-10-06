package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Gives the discs of one chunk their materials (see {@code DiscPalette}), by writing over the rock the terrain made them of,
 * and then their ponds and streams (see {@code DiscWater}). It writes straight into the chunk, as carvers do, so it handles
 * plain blocks only: a block that needs a block entity is placed without one.
 */
public final class DiscPainter {
    // Keeps these draws apart from vanilla's own decoration draws for the same chunk.
    private static final long SALT = 0x4F41_4449_5343_5321L;

    private DiscPainter() {}

    public static void paint(RandomState random, ChunkAccess chunk, long seed) {
        RavineFootprint footprint = ((RavineFootprintHolder) (Object) random).overgrownAbyss$footprint();
        if (footprint == RavineFootprint.NONE) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        // One sequence per chunk, drawn in the fixed order the blocks are visited, so a mix comes out the same every time.
        var draws = new WorldgenRandom(new XoroshiroRandomSource(seed ^ SALT));
        draws.setDecorationSeed(seed ^ SALT, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        var pos = new BlockPos.MutableBlockPos();
        footprint.forEachDiscBlock(
                chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(),
                (x, y, z, block) -> chunk.setBlockState(pos.set(x, y, z), block.getState(draws, pos), false));
        BlockState water = Blocks.WATER.defaultBlockState();
        footprint.forEachDiscWater(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), (x, surface, z, depth) -> {
            // Only under open air. Where a stem or a root stands on the platform, or the rock of the wall lies on it, the
            // ground painted above stays; so do the sides and beds that hold the water in.
            if (chunk.isOutsideBuildHeight(surface + 1) || !chunk.getBlockState(pos.set(x, surface + 1, z)).isAir()) {
                return;
            }
            for (int below = 0; below < depth; below++) {
                chunk.setBlockState(pos.set(x, surface - below, z), water, false);
            }
        });
    }
}

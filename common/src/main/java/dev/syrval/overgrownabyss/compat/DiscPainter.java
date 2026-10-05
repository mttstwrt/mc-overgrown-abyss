package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Gives the discs of one chunk their materials (see {@code DiscPalette}), by writing over the rock the terrain made them of.
 * It writes straight into the chunk, as carvers do, so it handles plain blocks only: a block that needs a block entity is
 * placed without one.
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
    }
}

package dev.syrval.verdantdeep.compat;

import dev.syrval.verdantdeep.ravine.RavineFootprint;
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
 * then the ground that stands over their platforms (see {@code DiscGround}), their ponds and streams (see {@code DiscWater}),
 * the still water of the hole's falls (see {@code DiscFalls}), and last the hole's roots (see {@code RootBlocks}), which go through
 * whatever is there. It writes straight into the chunk, as carvers do, so it handles plain blocks only: a block that needs a
 * block entity is placed without one.
 */
public final class DiscPainter {
    // Keeps these draws apart from vanilla's own decoration draws for the same chunk.
    private static final long SALT = 0x4F41_4449_5343_5321L;

    private DiscPainter() {}

    public static void paint(RandomState random, ChunkAccess chunk, long seed) {
        RavineFootprint footprint = ((RavineFootprintHolder) (Object) random).verdantDeep$footprint();
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
        // Ground over a platform is for open air: a stem that comes down on the platform, or the wall where the disc lies in it, stays.
        footprint.forEachMoundBlock(
                chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(),
                (x, y, z, block) -> {
                    if (chunk.getBlockState(pos.set(x, y, z)).isAir()) {
                        chunk.setBlockState(pos, block.getState(draws, pos), false);
                    }
                });
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        footprint.forEachDiscWater(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), (x, surface, z, depth, fluid) -> {
            // Only under open air. Where a stem or a root stands on the platform, or the rock of the wall lies on it, the
            // ground painted above stays; so do the sides and beds that hold the water in.
            if (chunk.isOutsideBuildHeight(surface + 1) || !chunk.getBlockState(pos.set(x, surface + 1, z)).isAir()) {
                return;
            }
            BlockState held = switch (fluid) {
                case WATER -> water;
                case LAVA -> lava;
            };
            for (int below = 0; below < depth; below++) {
                chunk.setBlockState(pos.set(x, surface - below, z), held, false);
            }
        });
        BlockState air = Blocks.AIR.defaultBlockState();
        footprint.forEachFallWater(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), (x, level, z, depth, cut, lip) -> {
            // As for a pond, only under open air, which here is over the ground that is cut away to let the water through.
            if (chunk.isOutsideBuildHeight(level + cut + 1) || !chunk.getBlockState(pos.set(x, level + cut + 1, z)).isAir()) {
                return;
            }
            for (int up = cut; up > 0; up--) {
                chunk.setBlockState(pos.set(x, level + up, z), air, false);
            }
            for (int below = 0; below < depth; below++) {
                chunk.setBlockState(pos.set(x, level - below, z), water, false);
            }
            // A lip is water with an open side. Marked, the game ticks it when the chunk comes alive, as it does water the terrain
            // left with somewhere to run, and from there it runs by the game's own rules.
            if (lip) {
                chunk.markPosForPostprocessing(pos.set(x, level, z));
            }
        });
        footprint.forEachRootBlock(
                chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), chunk.getMinBuildHeight(), chunk.getMaxBuildHeight(),
                (x, y, z, block) -> chunk.setBlockState(pos.set(x, y, z), block.getState(draws, pos), false));
    }
}

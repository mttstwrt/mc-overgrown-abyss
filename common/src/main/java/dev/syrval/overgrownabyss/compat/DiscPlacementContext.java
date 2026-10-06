package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.DiscPlot;
import dev.syrval.overgrownabyss.ravine.DiscWater;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

/**
 * Placement on a disc. A biome's features find the ground by asking for the height of the column, which in the world is the
 * surface far above a disc or the highest disc of several. Vanilla's placement rules ask through this class, so extending it
 * is the one way to answer with the disc's own top. A column that is not over the disc answers with the bottom of the world,
 * which those rules take as no ground at all.
 */
final class DiscPlacementContext extends PlacementContext {
    // How far up from a disc's top a column is followed through what stands on it; taller than any tree under a dome.
    private static final int CLIMB = 48;
    // How far down into a disc's top a column is followed through its water.
    private static final int SINK = DiscWater.MAX_DEPTH;

    private final DiscPlot plot;

    DiscPlacementContext(WorldGenLevel level, ChunkGenerator generator, PlacedFeature feature, DiscPlot plot) {
        super(level, generator, Optional.of(feature));
        this.plot = plot;
    }

    @Override
    public int getHeight(Heightmap.Types heightmap, int x, int z) {
        OptionalInt ground = plot.groundAt(x, z);
        if (ground.isEmpty()) {
            return getMinBuildHeight();
        }
        var pos = new BlockPos.MutableBlockPos(x, ground.getAsInt(), z);
        // A disc's water lies in its top, under what the plot calls the ground. The heightmaps that see through water, by
        // which a feature finds a pond's bed and how deep it is, are followed down to it.
        int bed = ground.getAsInt() - SINK;
        while (pos.getY() > bed && !heightmap.isOpaque().test(getLevel().getBlockState(pos.below()))) {
            pos.move(Direction.DOWN);
        }
        // The two worldgen heightmaps are the terrain alone, which vanilla leaves as it was while features are placed: the
        // disc's top, whatever stands on it by now.
        if (pos.getY() < ground.getAsInt() || heightmap == Heightmap.Types.WORLD_SURFACE_WG || heightmap == Heightmap.Types.OCEAN_FLOOR_WG) {
            return pos.getY();
        }
        // The others take in what has been placed since, and features rely on that: a tree started at the disc's top inside
        // the trunk of an earlier one crashes the game. So the column is followed up through what stands on the disc.
        int limit = Math.min(ground.getAsInt() + CLIMB, getLevel().getMaxBuildHeight() - 1);
        while (pos.getY() < limit && heightmap.isOpaque().test(getLevel().getBlockState(pos))) {
            pos.move(Direction.UP);
        }
        return pos.getY();
    }
}

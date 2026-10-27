package dev.syrval.verdantdeep.basilisk.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Cheap column scans used by the move control, the strike goal and the body chain. */
public final class TerrainProbe {
    private TerrainProbe() {}

    /**
     * True if a creature stepping or falling into this column would not land safely: lava reached first,
     * no floor within maxDrop blocks (an abyss), or the chunk is not loaded.
     */
    public static boolean isUnsafeLanding(Level level, double x, double y, double z, int maxDrop) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), top = Mth.floor(y);
        for (int dy = 0; dy <= maxDrop + 1; dy++) {
            pos.set(bx, top - dy, bz);
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return true;
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.LAVA)) return true;
            if (!state.getCollisionShape(level, pos).isEmpty()) return false;
            if (state.getFluidState().is(FluidTags.WATER)) return false;
        }
        return true;
    }

    /** Blocks of floor-less drop that count as an edge: nothing may be planned or walked past this. */
    public static final int EDGE_DROP = 3;
    /** How far from a drop a destination or step must stay. */
    public static final double EDGE_MARGIN = 2.0D;

    /**
     * True if (x, z) is a place to be near: its own column and the columns EDGE_MARGIN blocks around it all have a
     * floor (or a wall) within EDGE_DROP below y + 1. A wall counts as floor, so climbing alongside one is fine.
     */
    public static boolean isNearEdge(Level level, double x, double y, double z) {
        if (isUnsafeLanding(level, x, y + 1.0D, z, EDGE_DROP)) return true;
        for (int i = 0; i < 4; i++) {
            double ox = (i == 0 ? EDGE_MARGIN : i == 1 ? -EDGE_MARGIN : 0.0D);
            double oz = (i == 2 ? EDGE_MARGIN : i == 3 ? -EDGE_MARGIN : 0.0D);
            if (isUnsafeLanding(level, x + ox, y + 1.0D, z + oz, EDGE_DROP)) return true;
        }
        return false;
    }

    /** True if the entity could stand {@code height} blocks tall where it is (used to allow the full rear-up). */
    public static boolean hasHeadroom(Level level, Entity e, double height) {
        AABB bb = e.getBoundingBox();
        AABB tall = new AABB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY + height, bb.maxZ);
        return level.noCollision(e, tall);
    }

    /** True if the point is inside a block with collision (unloaded chunks count as open). */
    public static boolean isSolid(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() && shape.bounds().move(pos).contains(x, y, z);
    }

    /** Standing height of the first collidable surface at or below y (within maxDown cells), else NaN. */
    public static double groundY(Level level, double x, double y, double z, int maxDown) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z), top = Mth.floor(y + 0.01D);
        for (int dy = 0; dy <= maxDown; dy++) {
            pos.set(bx, top - dy, bz);
            if (level.isOutsideBuildHeight(pos)) return Double.NaN;
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return pos.getY() + shape.max(Direction.Axis.Y);
        }
        return Double.NaN;
    }
}

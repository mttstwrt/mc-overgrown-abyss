package dev.syrval.overgrownabyss.basilisk.entity.ai;

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

    /** True if the entity could stand {@code height} blocks tall where it is (used to allow the full rear-up). */
    public static boolean hasHeadroom(Level level, Entity e, double height) {
        AABB bb = e.getBoundingBox();
        AABB tall = new AABB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY + height, bb.maxZ);
        return level.noCollision(e, tall);
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

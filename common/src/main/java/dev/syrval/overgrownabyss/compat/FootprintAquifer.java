package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Leaves every open block inside a ravine footprint as air. Without this, the carved volume below the global lava
 * level fills with lava, and aquifers place water and lava pockets in the open shaft.
 */
public record FootprintAquifer(Aquifer delegate, RavineFootprint footprint) implements Aquifer {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public static Aquifer wrap(Aquifer aquifer, RavineFootprint footprint) {
        return footprint == RavineFootprint.NONE ? aquifer : new FootprintAquifer(aquifer, footprint);
    }

    @Override
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
        // Vanilla treats density > 0 as solid and leaves it to the default block; only open blocks are ours.
        if (density <= 0 && footprint.contains(context.blockX(), context.blockZ())) {
            return AIR;
        }
        return delegate.computeSubstance(context, density);
    }

    // May be stale after we answered AIR, which is harmless: the generator only schedules ticks for fluid blocks.
    @Override
    public boolean shouldScheduleFluidUpdate() {
        return delegate.shouldScheduleFluidUpdate();
    }
}

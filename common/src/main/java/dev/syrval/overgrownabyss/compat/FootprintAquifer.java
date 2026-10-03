package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Leaves every open block inside the carved ravine volume as air. Without this, the volume fills with aquifer water
 * and lava pockets, or with the global lava level when the floor is deep. Natural terrain next to the volume keeps
 * its vanilla fluids, so lava and water meet the ravine walls as they would any other cave.
 */
public record FootprintAquifer(Aquifer delegate, RavineFootprint footprint) implements Aquifer {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public static Aquifer wrap(Aquifer aquifer, RavineFootprint footprint) {
        return footprint == RavineFootprint.NONE ? aquifer : new FootprintAquifer(aquifer, footprint);
    }

    @Override
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
        // Vanilla treats density > 0 as solid and leaves it to the default block; only open blocks are ours.
        if (density <= 0 && footprint.isOpen(context)) {
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

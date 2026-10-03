package dev.syrval.overgrownabyss.mixin;

import dev.syrval.overgrownabyss.compat.RavineFootprintHolder;
import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the level's ravine footprint from the density hook to the noise fill (see NoiseChunkMixin). */
@Mixin(RandomState.class)
abstract class RandomStateMixin implements RavineFootprintHolder {
    // Set once on the server thread before any chunk task runs; volatile keeps the hand-off to worker threads safe.
    @Unique
    private volatile RavineFootprint overgrownAbyss$footprint = RavineFootprint.NONE;

    @Override
    public RavineFootprint overgrownAbyss$footprint() {
        return overgrownAbyss$footprint;
    }

    @Override
    public void overgrownAbyss$setFootprint(RavineFootprint footprint) {
        overgrownAbyss$footprint = footprint;
    }
}

package dev.syrval.verdantdeep.mixin;

import dev.syrval.verdantdeep.compat.RavineFootprintHolder;
import dev.syrval.verdantdeep.ravine.RavineFootprint;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the level's ravine footprint from the density hook to the noise fill (see NoiseChunkMixin). */
@Mixin(RandomState.class)
abstract class RandomStateMixin implements RavineFootprintHolder {
    // Set once on the server thread before any chunk task runs; volatile keeps the hand-off to worker threads safe.
    @Unique
    private volatile RavineFootprint verdantDeep$footprint = RavineFootprint.NONE;

    @Override
    public RavineFootprint verdantDeep$footprint() {
        return verdantDeep$footprint;
    }

    @Override
    public void verdantDeep$setFootprint(RavineFootprint footprint) {
        verdantDeep$footprint = footprint;
    }
}

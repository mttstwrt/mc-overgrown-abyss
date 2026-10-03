package dev.syrval.overgrownabyss.mixin;

import dev.syrval.overgrownabyss.compat.FootprintAquifer;
import dev.syrval.overgrownabyss.compat.RavineFootprintHolder;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fluid override. The aquifer decides every open block's fluid (global lava level, local aquifers), so wrapping it
 * is the narrowest point that covers both. Vanilla reads the field lazily, so replacing it at the end of the
 * constructor is enough.
 */
@Mixin(NoiseChunk.class)
abstract class NoiseChunkMixin {
    @Shadow
    @Final
    @Mutable
    private Aquifer aquifer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void overgrownAbyss$keepFluidsOutOfRavines(
            int cellCountXZ,
            RandomState random,
            int firstNoiseX,
            int firstNoiseZ,
            NoiseSettings noiseSettings,
            DensityFunctions.BeardifierOrMarker beardifier,
            NoiseGeneratorSettings settings,
            Aquifer.FluidPicker fluidPicker,
            Blender blender,
            CallbackInfo ci) {
        aquifer = FootprintAquifer.wrap(aquifer, ((RavineFootprintHolder) (Object) random).overgrownAbyss$footprint());
    }
}

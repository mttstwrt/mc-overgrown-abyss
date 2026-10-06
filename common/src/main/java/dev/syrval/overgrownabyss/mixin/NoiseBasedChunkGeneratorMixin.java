package dev.syrval.overgrownabyss.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.syrval.overgrownabyss.compat.DiscPainter;
import dev.syrval.overgrownabyss.compat.FootprintBiomeResolver;
import dev.syrval.overgrownabyss.compat.RavineFootprintHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The two things a ravine changes in a chunk after its terrain is shaped: its own biomes and the discs' materials. */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    /**
     * The biomes of the cavern and of themed discs. Biomes are assigned per chunk from a resolver; wrapping it at the single
     * call that fills them keeps the level's own biome source untouched, so structure and /locate biome checks still see the
     * natural biomes.
     */
    @WrapOperation(
            method = "doCreateBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"))
    private void overgrownAbyss$ownBiomes(
            ChunkAccess chunk,
            BiomeResolver resolver,
            Climate.Sampler sampler,
            Operation<Void> original,
            @Local(argsOnly = true) RandomState random) {
        var footprint = ((RavineFootprintHolder) (Object) random).overgrownAbyss$footprint();
        original.call(chunk, FootprintBiomeResolver.wrap(resolver, footprint), sampler);
    }

    /**
     * Disc materials. Carving is the last step that touches only its own chunk, and it runs for every chunk whatever its
     * biome. Every neighbour's features wait for it, so painting here never covers something a feature placed, and the
     * result does not depend on the order chunks generate in. Surface rules and carvers have run by now, so neither undoes it.
     */
    @Inject(method = "applyCarvers", at = @At("TAIL"))
    private void overgrownAbyss$paintDiscs(
            WorldGenRegion region,
            long seed,
            RandomState random,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step,
            CallbackInfo ci) {
        DiscPainter.paint(random, chunk, seed);
    }
}

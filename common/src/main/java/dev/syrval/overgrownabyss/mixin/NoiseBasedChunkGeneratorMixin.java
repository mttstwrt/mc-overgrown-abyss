package dev.syrval.overgrownabyss.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.syrval.overgrownabyss.compat.CavernBiomeResolver;
import dev.syrval.overgrownabyss.compat.RavineFootprintHolder;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cavern biome. Biomes are assigned per chunk from a resolver; wrapping it at the single call that fills them keeps
 * the level's own biome source untouched, so structure and /locate biome checks still see the natural biomes.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @WrapOperation(
            method = "doCreateBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;fillBiomesFromNoise(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/biome/Climate$Sampler;)V"))
    private void overgrownAbyss$cavernBiome(
            ChunkAccess chunk,
            BiomeResolver resolver,
            Climate.Sampler sampler,
            Operation<Void> original,
            @Local(argsOnly = true) RandomState random) {
        var footprint = ((RavineFootprintHolder) (Object) random).overgrownAbyss$footprint();
        original.call(chunk, CavernBiomeResolver.wrap(resolver, footprint), sampler);
    }
}

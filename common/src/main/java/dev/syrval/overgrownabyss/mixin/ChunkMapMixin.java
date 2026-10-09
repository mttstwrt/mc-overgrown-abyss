package dev.syrval.overgrownabyss.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.syrval.overgrownabyss.compat.RavineDensityHook;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Density wrap hook. ChunkMap is where a level's RandomState is built and the only place that has the seed, the
 * generator (to read the noise-settings tag), the registries (to find the carve) and the level's templates (to measure the
 * pieces of the ruins on discs) at the same time.
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState overgrownAbyss$carveRavines(
            NoiseGeneratorSettings settings,
            HolderGetter<NormalNoise.NoiseParameters> noises,
            long seed,
            Operation<RandomState> original,
            @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) StructureTemplateManager templates,
            @Local(argsOnly = true) ChunkGenerator generator) {
        return RavineDensityHook.createRandomState(
                level.registryAccess(), generator, level, templates, settings, seed, wrapped -> original.call(wrapped, noises, seed));
    }
}

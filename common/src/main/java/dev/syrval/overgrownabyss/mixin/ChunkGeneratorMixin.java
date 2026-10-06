package dev.syrval.overgrownabyss.mixin;

import dev.syrval.overgrownabyss.compat.DiscGrower;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disc growth. Decoration is the one step that may place features, with their reach into neighbouring chunks, and this is
 * its single entry point for every chunk whatever its biomes. Vanilla has finished its own features by the tail, so what a
 * disc grows is not built over.
 */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void overgrownAbyss$growOnDiscs(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
        DiscGrower.grow(level, chunk, (ChunkGenerator) (Object) this);
    }
}

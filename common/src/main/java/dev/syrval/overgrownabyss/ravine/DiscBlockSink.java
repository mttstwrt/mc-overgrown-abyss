package dev.syrval.overgrownabyss.ravine;

import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/** Receives each block of a chunk that takes a disc's material, with the provider that chooses its block. */
@FunctionalInterface
public interface DiscBlockSink {
    void accept(int x, int y, int z, BlockStateProvider block);
}

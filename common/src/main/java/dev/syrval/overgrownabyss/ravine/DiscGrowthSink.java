package dev.syrval.overgrownabyss.ravine;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** Receives each open block on a disc's surface where its theme grows something, with the feature to place there. */
@FunctionalInterface
public interface DiscGrowthSink {
    void accept(int x, int y, int z, ResourceKey<ConfiguredFeature<?, ?>> feature, DiscTheme.Surface on);
}

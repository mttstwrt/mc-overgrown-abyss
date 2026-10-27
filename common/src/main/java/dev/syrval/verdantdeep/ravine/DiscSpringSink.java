package dev.syrval.verdantdeep.ravine;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Receives the springs of a chunk (see {@link DiscFalls}), one after another: {@code begin} with the feature that makes a
 * spring, then {@code tryAt} for each place in the wall where it may be made, in the order to try them. The first place the
 * feature takes is the spring; the rest are then left alone.
 */
public interface DiscSpringSink {
    void begin(ResourceKey<ConfiguredFeature<?, ?>> feature);

    void tryAt(int x, int y, int z);
}

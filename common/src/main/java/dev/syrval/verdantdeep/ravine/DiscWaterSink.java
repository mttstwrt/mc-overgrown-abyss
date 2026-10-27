package dev.syrval.verdantdeep.ravine;

/**
 * Receives each column of a chunk where a disc holds water, or the lava a theme has in its place: its highest block of
 * {@code fluid} is at {@code surface}, and it is {@code depth} deep.
 */
@FunctionalInterface
public interface DiscWaterSink {
    void accept(int x, int surface, int z, int depth, DiscWater.Fluid fluid);
}

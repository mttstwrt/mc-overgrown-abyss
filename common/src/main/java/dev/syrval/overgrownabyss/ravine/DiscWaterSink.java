package dev.syrval.overgrownabyss.ravine;

/** Receives each column of a chunk where a disc holds water: its highest block of water is at {@code surface}, and it is {@code depth} deep. */
@FunctionalInterface
public interface DiscWaterSink {
    void accept(int x, int surface, int z, int depth);
}

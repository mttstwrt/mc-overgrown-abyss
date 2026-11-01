package dev.syrval.verdantdeep.ravine;

/**
 * Receives a fall's still and falling water (see {@link DiscFalls}). A still column's top block is at {@code level} and it is
 * {@code depth} blocks deep, {@code cut} blocks of ground over it are to be cleared away, and a {@code lip} is where the
 * water leaves the disc, which the game is to run.
 */
@FunctionalInterface
public interface DiscFallSink {
    void accept(int x, int level, int z, int depth, int cut, boolean lip);

    /** Falling water in the open column from {@code top} through {@code bottom}, inclusive. */
    default void flow(int x, int z, int top, int bottom) {}
}

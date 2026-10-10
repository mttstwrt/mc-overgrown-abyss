package dev.syrval.overgrownabyss.ravine;

/**
 * Receives one column of a fall's still water (see {@link DiscFalls}): its top block is at {@code level} and it is
 * {@code depth} blocks deep, {@code cut} blocks of ground over it are to be cleared away, and a {@code lip} is where the
 * water leaves the disc, which the game is to run.
 */
@FunctionalInterface
public interface DiscFallSink {
    void accept(int x, int level, int z, int depth, int cut, boolean lip);
}

package dev.syrval.overgrownabyss.ravine;

/**
 * Picks the columns of one disc, in one chunk, where something of its theme starts: what grows there and where gas comes from.
 * Each column of a surface is drawn for from the cell's hash and its own coordinates, so the picks are the same whatever order
 * chunks generate in, and each thing spread has draws of its own.
 */
final class DiscScatter {
    private static final int CHUNK_SIZE = 16;
    // Nothing is started on a disc this close to its rim, where a tree would stand half over the edge.
    private static final double EDGE_CLEAR = 1.5;

    /** How many times its average rate a thing is picked in one column. */
    @FunctionalInterface
    interface Weight {
        double at(int x, int z);
    }

    /** Takes a picked column and how far it is from the disc's axis. */
    @FunctionalInterface
    interface Sink {
        void accept(int x, int z, double fromAxis);
    }

    private DiscScatter() {}

    /**
     * The columns of {@code disc} in the chunk whose lowest corner is {@code (minX, minZ)} picked, on average, one for every
     * {@code every} blocks of surface, times {@code factor} and the weight of each column. The rim's columns are those right
     * outside the platform; every other surface's are inside it, short of the edge.
     */
    static void forEachPick(long hash, int draws, Disc disc, DiscTheme.Surface on, int every, double factor, Weight weight, int minX, int minZ, Sink sink) {
        double reach = disc.radius() + (on == DiscTheme.Surface.RIM ? 1 : 0);
        int fromX = Math.max(minX, (int) Math.ceil(disc.x() - reach));
        int toX = Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(disc.x() + reach));
        int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - reach));
        int toZ = Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(disc.z() + reach));
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
                if (on != DiscTheme.Surface.RIM && fromAxis > disc.radius() - EDGE_CLEAR) {
                    continue;
                }
                double draw = RavineCells.unitAt(hash, draws, x, z) * every;
                if (draw < factor * weight.at(x, z)) {
                    sink.accept(x, z, fromAxis);
                }
            }
        }
    }
}

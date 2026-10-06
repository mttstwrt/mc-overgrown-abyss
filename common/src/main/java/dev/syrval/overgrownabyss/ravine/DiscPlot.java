package dev.syrval.overgrownabyss.ravine;

import java.util.OptionalInt;

/**
 * One disc as a place to grow a biome's features on: where its ground is, and which blocks are its own. A feature that looks
 * for the ground finds this disc's top instead of whatever is highest in the column, and nothing is placed outside the disc.
 */
public interface DiscPlot {

    /** The first open block over the disc's top in this column, or empty where the column is not over the disc. */
    OptionalInt groundAt(int x, int z);

    /** Whether a block belongs to this disc: it is in the disc's space, and no higher disc's space holds it too. */
    boolean owns(int x, int y, int z);
}

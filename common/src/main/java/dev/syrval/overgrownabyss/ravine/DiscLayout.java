package dev.syrval.overgrownabyss.ravine;

import java.util.List;

/**
 * The discs of one hole or ravine. A geometry implements this, and {@link Discs} does the rest, so discs behave the same in every
 * geometry. Each disc is a pure function of the hole's hash and its grid position, so a layout is built once for a cell and
 * then only read, which makes it safe to share between worker threads.
 */
interface DiscLayout {

    /** Every disc, in a fixed order, each with the support it has among the others. */
    List<Disc> discs();

    /**
     * The discs that can matter to the carve at a column, in the same order: every disc within the carve's falloff of it, and
     * maybe more. A layout of few discs gives all of them.
     */
    default List<Disc> near(double x, double z) {
        return discs();
    }
}

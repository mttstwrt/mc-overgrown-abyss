package dev.syrval.overgrownabyss.ravine;

import java.util.List;

/**
 * The discs of one hole or ravine. A geometry implements this, and {@link Discs} does the rest, so discs behave the same in every
 * geometry. Each disc is a pure function of the hole's hash and its grid position, so a layout is built once for a cell and
 * then only read, which makes it safe to share between worker threads.
 */
interface DiscLayout {

    /**
     * Every disc, in a fixed order, each with the support it has among the others. A layout has few discs, so a sample looks
     * at all of them.
     */
    List<Disc> discs();
}

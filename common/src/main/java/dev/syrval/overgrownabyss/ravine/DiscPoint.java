package dev.syrval.overgrownabyss.ravine;

/** Where a point lies inside a disc's rock, which decides its material (see {@link DiscPalette}). Depths are in blocks. */
sealed interface DiscPoint {

    /** In the platform: {@code belowTop} under its flat top and {@code aboveUnderside} over its bottom face. */
    record Platform(double belowTop, double aboveUnderside) implements DiscPoint {}

    /** In the stem, or the root of a hanging disc: {@code inside} is how far in from its side. */
    record Stem(double inside) implements DiscPoint {}
}

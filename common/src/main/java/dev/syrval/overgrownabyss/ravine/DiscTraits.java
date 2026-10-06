package dev.syrval.overgrownabyss.ravine;

import java.util.List;

/**
 * What decides a disc's theme, each from 0 to 1 among the discs of its own hole: {@code height} from the lowest disc to the
 * highest, {@code distance} from the hole's centre to the disc furthest from it, and {@code size} from the smallest radius a
 * disc may have to the largest. Measuring against the hole's own discs makes a ramp run its whole length in every hole,
 * however deep the world is and however far its discs reach.
 */
record DiscTraits(double height, double distance, double size) {

    /** The traits of each disc of a cell, in the order given. */
    static List<DiscTraits> of(RavineSettings settings, RavineCell cell, List<Disc> discs) {
        DiscShape shape = settings.discs();
        double lowest = discs.stream().mapToDouble(Disc::floor).min().orElse(0);
        double highest = discs.stream().mapToDouble(Disc::floor).max().orElse(0);
        double furthest = discs.stream().mapToDouble(disc -> cell.distanceToCentre(disc.x(), disc.z())).max().orElse(0);
        return discs.stream().map(disc -> new DiscTraits(
                share(disc.floor() - lowest, highest - lowest),
                share(cell.distanceToCentre(disc.x(), disc.z()), furthest),
                share(disc.radius() - shape.minRadius(), shape.maxRadius() - shape.minRadius()))).toList();
    }

    private static double share(double part, double whole) {
        return whole > 0 ? Math.clamp(part / whole, 0, 1) : 0;
    }
}

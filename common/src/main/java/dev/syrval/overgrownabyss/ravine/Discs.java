package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The carving of a layout's discs, shared by every geometry. The domes are carved out as air, and the platforms, stems and
 * roots are rock put back afterwards, so none of them is ever cut off by the dome of a disc it passes through: that is what
 * "a disc's dome stays clear, except for stems and platforms of other discs" means.
 */
final class Discs {
    private Discs() {}

    /** Distance to the nearest dome: negative inside, infinity if none is close. */
    static double domeDistance(DiscLayout layout, double x, double y, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Disc disc : layout.near(x, z)) {
            nearest = Math.min(nearest, disc.domeDistance(x, y, z));
        }
        return nearest;
    }

    /** Distance to the nearest platform, stem or root: negative inside, infinity if none is close. */
    static double rockDistance(DiscLayout layout, DiscShape shape, double x, double y, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Disc disc : layout.near(x, z)) {
            nearest = Math.min(nearest, disc.rockDistance(shape, x, y, z));
        }
        return nearest;
    }

    /** The discs as placed, each given the stem it has among the others. */
    static List<Disc> standing(List<Disc> placed, DiscShape shape) {
        return placed.stream().map(disc -> disc.withSupport(new Disc.Support.Standing(bottomOf(placed, shape, disc)))).toList();
    }

    /**
     * Where a stem ends: halfway down through the platform of the highest lower disc whose platform holds the stem's axis, so it
     * merges into that platform and nothing of it shows under the platform's curved underside. Negative infinity if there is
     * none, so the stem runs down to the floor.
     */
    static double bottomOf(List<Disc> discs, DiscShape shape, Disc owner) {
        double bottom = Double.NEGATIVE_INFINITY;
        double stem = shape.stemRadiusFor(owner.radius());
        for (Disc lower : discs) {
            double apart = Math.hypot(owner.x() - lower.x(), owner.z() - lower.z());
            if (apart <= lower.radius() - stem - 1 && lower.topAt(apart) < owner.floor() - shape.floorThickness()) {
                bottom = Math.max(bottom, lower.topAt(apart) - shape.floorThickness() / 2);
            }
        }
        return bottom;
    }

    /**
     * Height, over the root's axis, of the underside of the platform of the lowest higher disc whose platform holds that axis,
     * which is a ceiling for the root to hang from; empty if there is none.
     */
    static OptionalDouble platformAbove(List<Disc> discs, DiscShape shape, Disc owner) {
        double ceiling = Double.POSITIVE_INFINITY;
        double root = shape.rootRadiusFor(owner.radius());
        for (Disc upper : discs) {
            double apart = Math.hypot(owner.x() - upper.x(), owner.z() - upper.z());
            double underside = upper.undersideAt(shape, apart);
            if (underside > owner.floor() && apart <= upper.radius() - root - 1) {
                ceiling = Math.min(ceiling, underside);
            }
        }
        return ceiling == Double.POSITIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(ceiling);
    }
}

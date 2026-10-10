package dev.syrval.overgrownabyss.ravine;

import java.util.List;

/**
 * One root of a hole (see {@link RootSettings}): the line down its middle as a row of knots no more than a block or so
 * apart, each with the root's radius there. The root is everything within that radius of the line from each knot to the
 * next.
 */
record Root(Kind kind, List<Knot> knots) {

    /** What a root is for, which decides where it starts and what it makes for (see {@link RootRoutes}). */
    enum Kind {
        /** From the wall under the mouth to the floor. */
        GREAT,
        /** Across the clear air round the axis. */
        CROSSING,
        /** From another root. */
        BRANCH,
        /** From one disc up to the next, at a slope a player can walk. */
        LINK
    }

    /** A point on the line down a root's middle, and the root's radius there. */
    record Knot(double x, double y, double z, double radius) {

        double distanceTo(double px, double py, double pz) {
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    Root {
        knots = List.copyOf(knots);
        if (knots.size() < 2) {
            throw new IllegalArgumentException("a root needs at least two knots");
        }
    }

    Knot first() {
        return knots.getFirst();
    }

    Knot last() {
        return knots.getLast();
    }
}

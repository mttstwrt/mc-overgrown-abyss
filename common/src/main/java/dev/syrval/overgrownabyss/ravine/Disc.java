package dev.syrval.overgrownabyss.ravine;

import java.util.Optional;

/**
 * One disc, in world coordinates with a vertical axis, whatever geometry placed it: the middle of its platform's top is at
 * {@code floor}, {@code radius} is the platform's radius, {@code height} is the dome of clear air above it, {@code bowl} is
 * how far the platform rises from the middle to the rim (a shallow bowl, top and underside alike; 0 is flat), and
 * {@code support} is what holds it up. All volumes are signed distances in blocks, negative inside, infinity where the disc
 * has none.
 *
 * <p>Nothing here knows about ravines or cones. A biome or structure for a disc can be tied to this record and the same
 * volumes in either geometry.
 */
record Disc(double x, double z, double floor, double radius, double height, double bowl, Support support) {

    /**
     * What holds a disc up. Where a stem ends depends on the discs around it, so a layout works this out once for all of its
     * discs (see {@link Discs#standing}) instead of on every sample.
     */
    sealed interface Support {

        /** A stem under the platform, down to {@code bottom}; negative infinity is the floor of the hole. */
        record Standing(double bottom) implements Support {
            static final Standing TO_THE_FLOOR = new Standing(Double.NEGATIVE_INFINITY);
        }

        /**
         * A root above the platform and nothing under it. The root is widest at {@code anchor}, where it meets the ceiling,
         * and ends at {@code top}: the anchor itself under a flat ceiling, or a little above it where the ceiling slopes
         * away, so the root has no flat cut in open air.
         */
        record Hanging(double anchor, double top) implements Support {
            // Members of an interface are public, so the compact constructor has to say so too.
            public Hanging {
                if (top < anchor) {
                    throw new IllegalArgumentException("a root's top " + top + " must not be below its anchor " + anchor);
                }
            }
        }
    }

    Disc {
        if (bowl < 0) {
            throw new IllegalArgumentException("a bowl cannot be " + bowl + " deep");
        }
        if (support instanceof Support.Hanging hanging && hanging.anchor() <= floor) {
            throw new IllegalArgumentException("a root's anchor " + hanging.anchor() + " must be above the platform at " + floor);
        }
    }

    /** A flat disc exactly where it is given, with a stem to the floor of the hole. */
    Disc(double x, double z, double floor, double radius, double height) {
        this(x, z, floor, radius, height, 0, Support.Standing.TO_THE_FLOOR);
    }

    /**
     * A disc as a layout places it, before the discs around it are known: its stem runs to the floor of the hole. Blocks are
     * sampled at whole coordinates, so the centre is moved halfway between them: that makes a stem the same number of blocks
     * across in every direction, down to 2 by 2.
     */
    static Disc placed(double x, double z, double floor, double radius, double height, double bowl) {
        return new Disc(Math.floor(x) + 0.5, Math.floor(z) + 0.5, floor, radius, height, bowl, Support.Standing.TO_THE_FLOOR);
    }

    Disc withSupport(Support support) {
        return new Disc(x, z, floor, radius, height, bowl, support);
    }

    /** Height of the platform's top {@code fromAxis} blocks from the axis: the floor in the middle, rising to the rim. */
    double topAt(double fromAxis) {
        double share = Math.min(fromAxis / radius, 1);
        return floor + bowl * share * share;
    }

    /** Height of the platform's underside there: it follows the top, so the platform is as thick at the rim as in the middle. */
    double undersideAt(DiscShape shape, double fromAxis) {
        return topAt(fromAxis) - shape.floorThickness();
    }

    /**
     * Whether a point is in the space this disc's biome covers: its dome and its platform, grown by {@code margin} blocks all
     * round, so that what stands on the rim or hangs under the platform is in it too.
     */
    boolean biomeContains(DiscShape shape, double px, double py, double pz, double margin) {
        double fromAxis = Math.hypot(px - x, pz - z);
        if (fromAxis > radius + margin || py < undersideAt(shape, fromAxis) - margin) {
            return false;
        }
        double share = Math.min(fromAxis / radius, 1);
        double roof = floor + height * Math.sqrt(1 - share * share);
        return py <= Math.max(roof, topAt(fromAxis)) + margin;
    }

    /** The dome: a roof of the same shape as the cavern's over the whole platform, so a ledge has headroom. */
    double domeDistance(double px, double py, double pz) {
        if (py >= floor + height) {
            return Double.POSITIVE_INFINITY;
        }
        double t = Math.clamp((py - floor) / height, 0, 1);
        double fromAxis = Math.hypot(px - x, pz - z);
        double roof = fromAxis - radius * Math.sqrt(1 - t * t);
        return Math.max(roof, topAt(fromAxis) - py);
    }

    /** The disc's rock: its platform and what holds it up. */
    double rockDistance(DiscShape shape, double px, double py, double pz) {
        double support = supportDistance(shape, px, py, pz);
        // Over its rim a disc has at most its root; the platform is left out there, as it has nothing to say about the air above it.
        return py > floor + bowl ? support : Math.min(platformDistance(shape, px, py, pz), support);
    }

    /** The stem or root, ended where the disc's support says. */
    double supportDistance(DiscShape shape, double px, double py, double pz) {
        return switch (support) {
            case Support.Standing standing -> py < standing.bottom() ? Double.POSITIVE_INFINITY : stemDistance(shape, px, py, pz);
            case Support.Hanging hanging -> rootDistance(shape, hanging, px, py, pz);
        };
    }

    /** Where a point lies in this disc's rock, or empty outside it. Where a stem meets its own platform, the platform has it. */
    Optional<DiscPoint> pointAt(DiscShape shape, double px, double py, double pz) {
        if (platformDistance(shape, px, py, pz) < 0) {
            double fromAxis = Math.hypot(px - x, pz - z);
            return Optional.of(new DiscPoint.Platform(topAt(fromAxis) - py, py - undersideAt(shape, fromAxis)));
        }
        double support = supportDistance(shape, px, py, pz);
        return support < 0 ? Optional.of(new DiscPoint.Stem(-support)) : Optional.empty();
    }

    /** The platform: the whole round footprint, {@code floor_thickness} deep everywhere, curved into the bowl. */
    double platformDistance(DiscShape shape, double px, double py, double pz) {
        double fromAxis = Math.hypot(px - x, pz - z);
        double vertical = Math.max(py - topAt(fromAxis), undersideAt(shape, fromAxis) - py);
        return Math.max(fromAxis - radius, vertical);
    }

    /**
     * The stem under the platform, a vertical column: as wide as the disc at the platform, narrowing along a hyperbola, quickly
     * at first and then ever more slowly, never thinner than the stem's radius. Its depth is counted from the underside straight
     * above each point, so the flare follows the bowl and no air is left between the two. This is the whole column; where it
     * ends is up to the disc's {@link Support}.
     */
    double stemDistance(DiscShape shape, double px, double py, double pz) {
        double fromAxis = Math.hypot(px - x, pz - z);
        double depth = undersideAt(shape, fromAxis) - py;
        return depth < 0 ? Double.POSITIVE_INFINITY : fromAxis - stemRadiusAt(shape, depth);
    }

    /** The stem's radius {@code depth} blocks below the underside of the platform. */
    double stemRadiusAt(DiscShape shape, double depth) {
        return Math.max(shape.stemRadiusFor(radius), radius * shape.funnelScale() / (depth + shape.funnelScale()));
    }

    /** The root above the platform, a vertical column from the platform's top to the root's top. */
    double rootDistance(DiscShape shape, Support.Hanging hanging, double px, double py, double pz) {
        if (py <= floor || py > hanging.top()) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot(px - x, pz - z) - rootRadiusAt(shape, Math.abs(hanging.anchor() - py));
    }

    /** The root's radius {@code distance} blocks above or below its anchor: the same curve as a stem, never thinner than one. */
    double rootRadiusAt(DiscShape shape, double distance) {
        return Math.max(shape.stemRadiusFor(radius), shape.rootRadiusFor(radius) * shape.rootScale() / (distance + shape.rootScale()));
    }
}

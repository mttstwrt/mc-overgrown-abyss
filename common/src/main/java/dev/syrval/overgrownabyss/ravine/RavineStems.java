package dev.syrval.overgrownabyss.ravine;

/**
 * The stem under each disc: rock hanging from the underside of the disc's floor slab, centred under the disc. It starts as
 * wide as the disc and narrows along a hyperbola, quickly at first and then ever more slowly, never thinner than
 * {@code stem_radius}, so the disc seems to grow out of a curved support, like the stem of a mushroom. The carve can only
 * take rock away, so {@link RavineShape} puts the stem back into the air the shaft and rooms left, the same way it puts the
 * floor slabs back.
 *
 * <p>A stem is not cut off by the dome of a room it passes through, so it stands in that room as a pillar. It ends where it
 * lands on the floor slab of the highest lower disc that holds its axis, or runs down to the chasm floor if there is none.
 * The cavern is not subtracted from, so a stem stops at the cavern's dome. The stem is a vertical column in the world, so
 * it is placed using the shaft's lean at the disc's floor, not at the height being sampled.
 */
final class RavineStems {
    private RavineStems() {}

    /** Distance to the nearest stem near a point: negative inside, infinity if none is close. */
    static double distance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineCell.Frame frame, double x, double y, double z) {
        RavineDiscs config = settings.discs();
        int rows = RavineDomes.rows(settings, bounds);
        if (rows == 0 || y > bounds.topY()) {
            return Double.POSITIVE_INFINITY;
        }
        double lowest = RavineDomes.lowestFloor(settings, bounds);
        // A stem hangs from its disc's slab down, so every room above this point can reach it. Floors
        // are jittered and the second wall is staggered by less than two rows, hence the margin.
        int firstRow = Math.max(0, (int) Math.floor((y - lowest) / config.rowSpacing()) - 2);
        RavineDomes.SlotRange slots = RavineDomes.slotsNear(settings, cell, frame.along());
        double nearest = Double.POSITIVE_INFINITY;
        for (int side = 1; side >= -1; side -= 2) {
            for (int row = firstRow; row < rows; row++) {
                for (int slot = slots.first(); slot <= slots.last(); slot++) {
                    var room = RavineDomes.at(settings, bounds, cell, side, row, slot);
                    if (room.isPresent() && y <= room.get().floor() - config.floorThickness()) {
                        nearest = Math.min(nearest, stemDistance(settings, bounds, cell, room.get(), x, y, z));
                    }
                }
            }
        }
        return nearest;
    }

    /** The stem's radius {@code depth} blocks below the underside of its disc's slab: a hyperbola, with a floor. */
    static double radiusAt(RavineDiscs discs, RavineDomes.Dome room, double depth) {
        return Math.max(discs.stemRadius(), room.radius() * discs.funnelScale() / (depth + discs.funnelScale()));
    }

    private static double stemDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome room, double x, double y, double z) {
        RavineDiscs config = settings.discs();
        double depth = room.floor() - config.floorThickness() - y;
        RavineCell.Frame atFloor = RavineShape.leanedFrame(bounds, cell, x, room.floor(), z);
        double centre = RavineShape.centreSideways(settings, bounds, cell, room);
        double distance = Math.hypot(atFloor.along() - room.along(), atFloor.sideways() - centre) - radiusAt(config, room, depth);
        // Past the carve's falloff a stem makes no difference, so skip the search for what it lands on.
        if (distance >= settings.edgeFalloff()) {
            return distance;
        }
        return y < bottomOf(settings, bounds, cell, room) ? Double.POSITIVE_INFINITY : distance;
    }

    /** Height of the underside of the highest lower disc whose footprint holds the stem's axis, where the stem ends. */
    static double bottomOf(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome owner) {
        double[] axis = RavineShape.worldOf(bounds, cell, owner.along(), RavineShape.centreSideways(settings, bounds, cell, owner), owner.floor());
        double thickness = settings.discs().floorThickness();
        RavineDomes.SlotRange slots = RavineDomes.slotsNear(settings, cell, owner.along());
        double bottom = Double.NEGATIVE_INFINITY;
        for (int side = 1; side >= -1; side -= 2) {
            for (int row = 0; row < RavineDomes.rows(settings, bounds); row++) {
                for (int slot = slots.first(); slot <= slots.last(); slot++) {
                    var below = RavineDomes.at(settings, bounds, cell, side, row, slot);
                    if (below.isPresent() && below.get().floor() < owner.floor() && holds(settings, bounds, cell, below.get(), axis)) {
                        bottom = Math.max(bottom, below.get().floor() - thickness);
                    }
                }
            }
        }
        return bottom;
    }

    private static boolean holds(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome room, double[] axis) {
        RavineCell.Frame atFloor = RavineShape.leanedFrame(bounds, cell, axis[0], room.floor(), axis[1]);
        return Math.hypot(atFloor.along() - room.along(), atFloor.sideways() - RavineShape.centreSideways(settings, bounds, cell, room))
                <= room.radius();
    }
}

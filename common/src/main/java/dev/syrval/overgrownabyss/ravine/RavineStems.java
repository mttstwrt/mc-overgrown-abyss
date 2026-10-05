package dev.syrval.overgrownabyss.ravine;

/**
 * The stem under each disc's ledge: an inverted funnel that narrows from the ledge's underside to a column at least
 * {@code stem_radius} wide, then runs straight down until it meets a disc below or the cavern or terrain. The stem is
 * rock put back into the shaft's open air; the carve can only take rock away, so {@link RavineShape} subtracts the stem
 * from the shaft, the same way it puts the floor slabs back.
 *
 * <p>The stem hangs from the middle of the part of the ledge that sticks out into the shaft, which is the part that can
 * be seen; a stem under the disc's own centre would be inside the wall. It is a vertical column in the world, so it is
 * placed using the shaft's lean at the disc's floor, not at the height being sampled.
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
        // Only rooms whose floor is a slab thickness above this point can have a stem down to it; the jitter and the
        // second wall's stagger move floors by less than two rows.
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

    private static double stemDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome room, double x, double y, double z) {
        RavineDiscs config = settings.discs();
        double reach = room.radius() * (1 - room.offset());
        // The widest circle centred on the lip's middle that still fits under the disc's footprint has this radius.
        double top = Math.max(config.stemRadius(), reach / 2);
        double depth = (top - config.stemRadius()) * config.funnelSlope();
        double below = room.floor() - config.floorThickness() - y;
        double radius = depth == 0 ? config.stemRadius() : config.stemRadius() + (top - config.stemRadius()) * Math.max(0, 1 - below / depth);
        RavineCell.Frame atFloor = RavineShape.leanedFrame(bounds, cell, x, room.floor(), z);
        double axisSideways = axisSideways(settings, bounds, cell, room);
        double distance = Math.hypot(atFloor.along() - room.along(), atFloor.sideways() - axisSideways) - radius;
        // Past the carve's falloff a stem makes no difference, so skip the search for what it rests on.
        if (distance >= settings.edgeFalloff()) {
            return distance;
        }
        return y < bottom(settings, bounds, cell, room, axisSideways) ? Double.POSITIVE_INFINITY : distance;
    }

    private static double axisSideways(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome room) {
        double reach = room.radius() * (1 - room.offset());
        return RavineShape.wallSideways(settings, bounds, cell, room) - room.side() * reach / 2;
    }

    /** Height of the underside of the highest lower disc whose footprint holds the stem's axis; the stem ends there. */
    private static double bottom(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome owner, double axisSideways) {
        double[] axis = RavineShape.worldOf(bounds, cell, owner.along(), axisSideways, owner.floor());
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

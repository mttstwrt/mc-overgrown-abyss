package dev.syrval.overgrownabyss.ravine;

/**
 * The stem under each disc: rock hanging from the underside of the disc's floor slab, centred under the disc and running
 * down to the floor of the chasm. It starts as wide as the disc and narrows along a hyperbola, quickly at first and then
 * ever more slowly, never thinner than {@code stem_radius}, so the disc seems to grow out of a trumpet-shaped support.
 * The carve can only take rock away, so {@link RavineShape} subtracts the stem from the shaft's open air, the same way it
 * puts the floor slabs back.
 *
 * <p>Discs are centred inside the wall, so the thin column is usually in the rock; what shows in the shaft is the flared
 * top of the stem under a ledge. Rooms are carved after the stems, so a stem never fills a room, and the cavern is not
 * subtracted from, so a stem stops at the cavern's dome. The stem is a vertical column in the world, so it is placed using
 * the shaft's lean at the disc's floor, not at the height being sampled.
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
        // A stem hangs from its disc's slab to the chasm floor, so every room above this point can reach it. Floors
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
        return Math.hypot(atFloor.along() - room.along(), atFloor.sideways() - centre) - radiusAt(config, room, depth);
    }
}

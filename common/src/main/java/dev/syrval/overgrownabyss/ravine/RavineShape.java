package dev.syrval.overgrownabyss.ravine;

/**
 * Signed distance, in blocks, from a point to the open volume of one ravine: negative inside (air), positive outside.
 * The volume is the ravine shaft from the floor upwards, a domed cavern centred on the cell whose floor is flat, and the
 * disc rooms cut sideways into the shaft's walls (see {@link RavineDomes}). Each room then gets its floor slab put back
 * as rock: it keeps the floor solid where lower rooms' domes would cut up through it, and the part of it that lies in
 * the shaft is a ledge. The shaft follows a centre line that bends in plan view and sways sideways with height, and
 * narrows towards the floor.
 *
 * <p>These are distance estimates, exact for a straight shaft and close for gentle curves, which is all the carve
 * needs: it only uses the sign and a few blocks of falloff either side of the wall. Each room's floor is exactly the
 * plane {@code y = floor}, so floors come out flat.
 */
public final class RavineShape {
    private RavineShape() {}

    public static double signedDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        RavineCell.Frame frame = leanedFrame(bounds, cell, x, y, z);
        double radial = cell.distanceToCentre(x, z);
        double shaft = shaftDistance(settings, bounds, cell, frame, y);
        double cavern = cavernDistance(settings, bounds, radial, y);
        Rooms rooms = discDistance(settings, bounds, cell, frame, radial, y);
        // Slabs are subtracted from the shaft and the rooms but not the cavern, so they never close its roof.
        double walls = Math.max(Math.min(shaft, rooms.dome()), -rooms.slab());
        return Math.min(walls, cavern);
    }

    /**
     * Whether a point is inside the cavern dome grown outwards by {@code margin} blocks. The margin reaches the floor,
     * walls and roof surfaces, so features placed on them are placed in the cavern's biome too.
     */
    public static boolean cavernContains(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z, double margin) {
        double above = y - bounds.floorY();
        if (above < -margin || above >= settings.cavernHeight() + margin) {
            return false;
        }
        double t = Math.clamp(above / settings.cavernHeight(), 0, 1);
        return cell.distanceToCentre(x, z) <= settings.cavernRadius() * Math.sqrt(1 - t * t) + margin;
    }

    /** How many rows of disc rooms fit between the cavern roof and the ceiling margin in this level. */
    public static int discRows(RavineSettings settings, RavineBounds bounds) {
        return RavineDomes.rows(settings, bounds);
    }

    /** Half the shaft's width at height {@code y}. */
    static double halfWidthAt(RavineSettings settings, RavineBounds bounds, RavineCell cell, double y) {
        double t = Math.clamp((y - bounds.floorY()) / (bounds.topY() - bounds.floorY()), 0, 1);
        double bottom = settings.bottomWidthFactor();
        return cell.halfWidth() * (bottom + (1 - bottom) * t);
    }

    /** Distances to the nearest room dome and the nearest floor slab near a point; infinity when none is close. */
    private record Rooms(double dome, double slab) {
        static final Rooms NONE = new Rooms(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    private static Rooms discDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineCell.Frame frame, double radial, double y) {
        RavineDiscs config = settings.discs();
        double lowest = RavineDomes.lowestFloor(settings, bounds);
        int rows = RavineDomes.rows(settings, bounds);
        if (rows == 0 || y < lowest - config.floorThickness() || y > bounds.topY() || radial > settings.maxReach()) {
            return Rooms.NONE;
        }
        double spacing = RavineDomes.slotSpacing(settings, cell);
        int slots = RavineDomes.slots(settings, cell);
        // Rooms whose floor is at or below this point can hold it in their dome, and rooms whose floor is up to a slab
        // thickness above it can hold it in their slab. Domes reach at most one dome's height up from their floor.
        int topRow = Math.min(rows - 1, (int) Math.floor((y + config.floorThickness() - lowest) / config.rowSpacing()));
        int firstRow = Math.max(0, topRow - (int) Math.ceil((config.maxDomeHeight() + config.floorThickness()) / config.rowSpacing()) - 1);
        int centreSlot = spacing == 0 ? 0 : (int) Math.floor((frame.along() + cell.halfLength()) / spacing);
        int slotSpan = spacing == 0 ? 0 : (int) Math.ceil(config.largestRadius() / spacing) + 1;
        int firstSlot = Math.max(0, centreSlot - slotSpan);
        int lastSlot = Math.min(slots - 1, centreSlot + slotSpan);
        double nearestDome = Double.POSITIVE_INFINITY;
        double nearestSlab = Double.POSITIVE_INFINITY;
        for (int side = 1; side >= -1; side -= 2) {
            for (int row = firstRow; row <= topRow; row++) {
                for (int slot = firstSlot; slot <= lastSlot; slot++) {
                    var room = RavineDomes.at(settings, bounds, cell, side, row, slot);
                    if (room.isPresent()) {
                        nearestDome = Math.min(nearestDome, domeDistance(settings, bounds, cell, room.get(), frame, y));
                        nearestSlab = Math.min(nearestSlab, slabDistance(settings, bounds, cell, room.get(), frame, y));
                    }
                }
            }
        }
        return new Rooms(nearestDome, nearestSlab);
    }

    /**
     * One room: a flat floor at {@code dome.floor()} and a roof of the same shape as the cavern's, centred
     * {@code offset} of its radius inside the wall, so it opens onto the shaft through a wide mouth.
     */
    static double domeDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome dome, RavineCell.Frame frame, double y) {
        if (y >= dome.floor() + dome.height()) {
            return Double.POSITIVE_INFINITY;
        }
        double dAlong = frame.along() - dome.along();
        double dSide = frame.sideways() - centreSideways(settings, bounds, cell, dome);
        double t = Math.clamp((y - dome.floor()) / dome.height(), 0, 1);
        double roof = Math.sqrt(dAlong * dAlong + dSide * dSide) - dome.radius() * Math.sqrt(1 - t * t);
        return Math.max(roof, dome.floor() - y);
    }

    /**
     * The rock under one room's floor: the room's round footprint, {@code floor_thickness} deep, kept to at most
     * {@code max_lip} of the shaft's width out from the wall (all the way across when that is 1, and through the far wall at 2). Where the footprint lies past the wall it is a ledge.
     */
    static double slabDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome dome, RavineCell.Frame frame, double y) {
        RavineDiscs discs = settings.discs();
        double dAlong = frame.along() - dome.along();
        double dSide = frame.sideways() - centreSideways(settings, bounds, cell, dome);
        double footprint = Math.sqrt(dAlong * dAlong + dSide * dSide) - dome.radius();
        double vertical = Math.max(y - dome.floor(), dome.floor() - discs.floorThickness() - y);
        double lipLimit = discs.maxLip() * 2 * halfWidthAt(settings, bounds, cell, dome.floor());
        // Measured from the wall at this point's own place along the ravine, not the room's: on a curve those differ, and
        // the lip must never pass the centre line.
        double lateral = frame.sideways() - cell.bend().offset(unitAlong(cell, frame.along()));
        double intoShaft = halfWidthAt(settings, bounds, cell, dome.floor()) - dome.side() * lateral;
        return Math.max(Math.max(footprint, vertical), intoShaft - lipLimit);
    }

    /** Where the wall the room opens from sits, sideways of the chord, at the room's floor height. */
    private static double wallSideways(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome dome) {
        return cell.bend().offset(unitAlong(cell, dome.along())) + dome.side() * halfWidthAt(settings, bounds, cell, dome.floor());
    }

    private static double centreSideways(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome dome) {
        return wallSideways(settings, bounds, cell, dome) + dome.side() * dome.offset() * dome.radius();
    }

    private static double shaftDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineCell.Frame frame, double y) {
        double u = unitAlong(cell, frame.along());
        double lateral = frame.sideways() - cell.bend().offset(u);
        // The curve is longer than its chord, so a step along the chord covers less of the wall than it seems to.
        double slope = cell.halfLength() == 0 ? 0 : cell.bend().slope(u) / cell.halfLength();
        double across = lateral / Math.sqrt(1 + slope * slope);
        double beyond = Math.max(Math.abs(frame.along()) - cell.halfLength(), 0);
        return Math.sqrt(across * across + beyond * beyond) - halfWidthAt(settings, bounds, cell, y);
    }

    // The whole shaft slides sideways with height, so view the point from where the shaft is at that height.
    private static RavineCell.Frame leanedFrame(RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        double t = Math.clamp((y - bounds.floorY()) / (double) (bounds.topY() - bounds.floorY()), 0, 1);
        double shift = cell.lean().shift(t);
        return cell.frame(x - shift * cell.lean().dirX(), z - shift * cell.lean().dirZ());
    }

    /** Position along the chord as a fraction of the half length in [-1, 1]; a round hole has no length, so 0. */
    private static double unitAlong(RavineCell cell, double along) {
        return cell.halfLength() == 0 ? 0 : Math.clamp(along / cell.halfLength(), -1, 1);
    }

    private static double cavernDistance(RavineSettings settings, RavineBounds bounds, double radial, double y) {
        double t = (y - bounds.floorY()) / settings.cavernHeight();
        if (t >= 1) {
            return Double.POSITIVE_INFINITY;
        }
        return radial - settings.cavernRadius() * Math.sqrt(1 - t * t);
    }
}

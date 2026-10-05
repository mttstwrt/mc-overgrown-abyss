package dev.syrval.overgrownabyss.ravine;

import java.util.Optional;

/**
 * Where the disc rooms of one ravine are. They are not stored: each room is a pure function of the ravine's hash and
 * its grid position (wall side, row, slot), so a sample point only asks about the few rooms near it.
 */
final class RavineDomes {
    static final int MAX_ROWS = 64;
    static final int MAX_SLOTS = 64;
    private static final int HASH_BASE = 5000;
    private static final double JITTER = 0.8;

    private RavineDomes() {}

    /**
     * One room: {@code side} is +1 for the left wall or -1 for the right, {@code floor} its flat floor's height, {@code radius}
     * the floor radius, {@code height} the dome's height and {@code offset} how far the centre sits inside the wall as a
     * fraction of the radius.
     */
    record Dome(int side, double along, double floor, double radius, double height, double offset) {}

    /** Floors start this high, at the cavern roof; rooms below it would be inside the cavern. */
    static double lowestFloor(RavineSettings settings, RavineBounds bounds) {
        return bounds.floorY() + settings.cavernHeight();
    }

    static int slots(RavineSettings settings, RavineCell cell) {
        return Math.clamp(Math.round(cell.halfLength() * 2 / settings.discs().spacing()), 1, MAX_SLOTS);
    }

    /** Distance along the chord between slot centres; 0 for a round hole, whose rooms all sit at its middle. */
    static double slotSpacing(RavineSettings settings, RavineCell cell) {
        return cell.halfLength() * 2 / slots(settings, cell);
    }

    /** The slots whose rooms can reach a point at {@code along}, within one largest radius either way. */
    record SlotRange(int first, int last) {}

    static SlotRange slotsNear(RavineSettings settings, RavineCell cell, double along) {
        double spacing = slotSpacing(settings, cell);
        int slots = slots(settings, cell);
        int centre = spacing == 0 ? 0 : (int) Math.floor((along + cell.halfLength()) / spacing);
        int span = spacing == 0 ? 0 : (int) Math.ceil(settings.discs().maxRadius() / spacing) + 1;
        return new SlotRange(Math.max(0, centre - span), Math.min(slots - 1, centre + span));
    }

    static int rows(RavineSettings settings, RavineBounds bounds) {
        RavineDiscs discs = settings.discs();
        double span = bounds.topY() - discs.ceilingMargin() - discs.minHeight() - lowestFloor(settings, bounds);
        return span < 0 ? 0 : (int) Math.min(MAX_ROWS, Math.floor(span / discs.rowSpacing()) + 1);
    }

    /** The room at a grid position, or empty if its dome could not reach the minimum height under the ceiling margin. */
    static Optional<Dome> at(RavineSettings settings, RavineBounds bounds, RavineCell cell, int side, int row, int slot) {
        RavineDiscs discs = settings.discs();
        int base = HASH_BASE + 8 * (((side > 0 ? 1 : 0) * MAX_ROWS + row) * MAX_SLOTS + slot);
        // Rows on the second wall sit higher by the stagger, so overhangs alternate from side to side.
        double stagger = side > 0 ? 0 : discs.sideStagger();
        double floor = lowestFloor(settings, bounds)
                + (row + 0.5 + stagger + (RavineCells.unit(cell.hash(), base) - 0.5) * discs.rowJitter()) * discs.rowSpacing();
        double radius = radius(discs, cell, base);
        double largeness = discs.maxRadius() > discs.minRadius() ? (radius - discs.minRadius()) / (discs.maxRadius() - discs.minRadius()) : 0;
        double offset = lerp(RavineCells.unit(cell.hash(), base + 2), discs.minOffset(), discs.maxOffset()) + largeness * discs.largeOffsetBonus();
        // A disc reaches radius * (1 - offset) into the shaft, so setting it back keeps the whole disc and limits its reach.
        double reach = RavineShape.halfWidthAt(settings, bounds, cell, floor) + discs.maxOvershoot();
        offset = Math.max(offset, 1 - reach / radius);
        double height = Math.min(
                Math.max(discs.heightRatio() * radius, discs.minHeight()),
                bounds.topY() - discs.ceilingMargin() - floor);
        if (height < discs.minHeight()) {
            return Optional.empty();
        }
        // Odd rows are shifted by half a slot so rooms in neighbouring rows overlap instead of stacking.
        double along = -cell.halfLength()
                + (slot + 0.5 + (RavineCells.unit(cell.hash(), base + 3) - 0.5) * JITTER + (row % 2) * 0.5) * slotSpacing(settings, cell);
        return Optional.of(new Dome(side, along, floor, radius, height, offset));
    }

    // Raising the draw to a power keeps most rooms modest and a few large.
    private static double radius(RavineDiscs discs, RavineCell cell, int base) {
        return lerp(Math.pow(RavineCells.unit(cell.hash(), base + 1), 1.3), discs.minRadius(), discs.maxRadius());
    }

    private static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }
}

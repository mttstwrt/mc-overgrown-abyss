package dev.syrval.overgrownabyss.ravine;

import java.util.Optional;

/**
 * Where the discs of one ravine are, as positions along and across the ravine (see {@link RavineDiscLayout} for the discs
 * themselves). They are not stored: each is a pure function of the ravine's hash and its grid position (wall side, row, slot),
 * so a sample point only asks about the few near it.
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
        return Math.clamp(Math.round(cell.halfLength() * 2 / settings.placement().spacing()), 1, MAX_SLOTS);
    }

    /** Distance along the chord between slot centres; 0 for a round hole, whose rooms all sit at its middle. */
    static double slotSpacing(RavineSettings settings, RavineCell cell) {
        return cell.halfLength() * 2 / slots(settings, cell);
    }

    static int rows(RavineSettings settings, RavineBounds bounds) {
        RavinePlacement placement = settings.placement();
        double span = bounds.topY() - placement.ceilingMargin() - settings.discs().minHeight() - lowestFloor(settings, bounds);
        return span < 0 ? 0 : (int) Math.min(MAX_ROWS, Math.floor(span / placement.rowSpacing()) + 1);
    }

    /** The room at a grid position, or empty if its dome could not reach the minimum height under the ceiling margin. */
    static Optional<Dome> at(RavineSettings settings, RavineBounds bounds, RavineCell cell, int side, int row, int slot) {
        DiscShape shape = settings.discs();
        RavinePlacement placement = settings.placement();
        int base = HASH_BASE + 8 * (((side > 0 ? 1 : 0) * MAX_ROWS + row) * MAX_SLOTS + slot);
        // Rows on the second wall sit higher by the stagger, so overhangs alternate from side to side.
        double stagger = side > 0 ? 0 : placement.sideStagger();
        double floor = lowestFloor(settings, bounds)
                + (row + 0.5 + stagger + (RavineCells.unit(cell.hash(), base) - 0.5) * placement.rowJitter()) * placement.rowSpacing();
        double radius = shape.radiusFor(RavineCells.unit(cell.hash(), base + 1));
        double largeness = shape.maxRadius() > shape.minRadius() ? (radius - shape.minRadius()) / (shape.maxRadius() - shape.minRadius()) : 0;
        double offset = lerp(RavineCells.unit(cell.hash(), base + 2), placement.minOffset(), placement.maxOffset()) + largeness * placement.largeOffsetBonus();
        // A disc reaches radius * (1 - offset) into the shaft, so setting it back keeps the whole disc and limits its reach.
        double reach = RavineShape.halfWidthAt(settings, bounds, cell, floor) + placement.maxOvershoot();
        offset = Math.max(offset, 1 - reach / radius);
        double height = Math.min(shape.heightFor(radius), bounds.topY() - placement.ceilingMargin() - floor);
        if (height < shape.minHeight()) {
            return Optional.empty();
        }
        // Odd rows are shifted by half a slot so rooms in neighbouring rows overlap instead of stacking.
        double along = -cell.halfLength()
                + (slot + 0.5 + (RavineCells.unit(cell.hash(), base + 3) - 0.5) * JITTER + (row % 2) * 0.5) * slotSpacing(settings, cell);
        return Optional.of(new Dome(side, along, floor, radius, height, offset));
    }

    private static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }
}

package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Where a ravine puts its discs, and how the carved ravine looks around them. */
class RavineDomesTest {
    static final RavineSettings SETTINGS = RavineCellsTest.SETTINGS;
    static final DiscShape SHAPE = SETTINGS.discs();
    static final RavinePlacement PLACEMENT = SETTINGS.placement();
    static final RavineBounds BOUNDS = RavineShapeTest.BOUNDS;
    // Straight, 300 long, 100 wide at the top, with a hash so its rooms are not all identical.
    static final RavineCell CELL = new RavineCell(0, 0, 1, 0, 150, 50, RavineCell.Bend.NONE, RavineCell.Lean.NONE, 123456789L);

    @Test
    void roomsAreDeterministicAndWithinTheirLimits() {
        int rows = RavineDomes.rows(SETTINGS, BOUNDS);
        int slots = RavineDomes.slots(SETTINGS, CELL);
        int made = 0;
        for (int side : new int[] {1, -1}) {
            for (int row = 0; row < rows; row++) {
                for (int slot = 0; slot < slots; slot++) {
                    Optional<RavineDomes.Dome> dome = RavineDomes.at(SETTINGS, BOUNDS, CELL, side, row, slot);
                    assertEquals(dome, RavineDomes.at(SETTINGS, BOUNDS, CELL, side, row, slot));
                    if (dome.isEmpty()) {
                        continue;
                    }
                    made++;
                    RavineDomes.Dome d = dome.get();
                    assertTrue(d.radius() >= SHAPE.minRadius() - 1e-9 && d.radius() <= SHAPE.maxRadius() + 1e-9);
                    assertTrue(d.offset() >= PLACEMENT.minOffset() - 1e-9 && d.offset() < 1);
                    assertTrue(d.height() >= SHAPE.minHeight() - 1e-9);
                    assertTrue(d.floor() >= RavineDomes.lowestFloor(SETTINGS, BOUNDS));
                    assertTrue(d.floor() + d.height() <= BOUNDS.topY() - PLACEMENT.ceilingMargin() + 1e-9, "stays under the ceiling margin");
                    assertTrue(Math.abs(d.along()) <= CELL.halfLength() + PLACEMENT.spacing());
                }
            }
        }
        assertTrue(made > 20, "made " + made);
    }

    @Test
    void roomsVaryInSize() {
        double smallest = Double.MAX_VALUE;
        double largest = 0;
        for (RavineDomes.Dome d : allRooms()) {
            smallest = Math.min(smallest, d.radius());
            largest = Math.max(largest, d.radius());
        }
        assertTrue(largest - smallest > 20, "radii " + smallest + " to " + largest);
    }

    @Test
    void aRavinesDiscIsTheSameDiscAsAConesAndHasAVerticalAxis() {
        RavineDiscLayout layout = new RavineDiscLayout(SETTINGS, BOUNDS, CELL);
        Disc disc = layout.at(1, 0, 3).orElseThrow();
        RavineDomes.Dome dome = RavineDomes.at(SETTINGS, BOUNDS, CELL, 1, 0, 3).orElseThrow();
        assertEquals(dome.floor(), disc.floor(), 1e-9);
        assertEquals(dome.radius(), disc.radius(), 1e-9);
        assertEquals(dome.height(), disc.height(), 1e-9);
        // In this straight ravine the world position is the position along and across the chord, to within the half block
        // a placed disc's centre is moved by.
        assertEquals(dome.along(), disc.x(), 0.5);
        assertEquals(RavineShape.centreSideways(SETTINGS, BOUNDS, CELL, dome), disc.z(), 0.5);
        // The dome does not lean with height: at every height it is symmetric about the same vertical axis.
        for (double up : new double[] {1, disc.height() * 0.4, disc.height() * 0.8}) {
            double side = disc.radius() * 0.5;
            assertEquals(disc.domeDistance(disc.x() + side, disc.floor() + up, disc.z()), disc.domeDistance(disc.x() - side, disc.floor() + up, disc.z()), 1e-9);
        }
    }

    @Test
    void roomsCoverMostOfTheWallFromTheCavernRoofToTheCeilingMargin() {
        double wallInset = 6;
        double lowest = RavineDomes.lowestFloor(SETTINGS, BOUNDS) + 3;
        double highest = BOUNDS.topY() - PLACEMENT.ceilingMargin() - 3;
        int open = 0;
        int total = 0;
        for (int side : new int[] {1, -1}) {
            for (double along = -CELL.halfLength() * 0.8; along <= CELL.halfLength() * 0.8; along += 7) {
                for (double y = lowest; y <= highest; y += 3) {
                    double z = side * (RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, y) + wallInset);
                    total++;
                    open += Carved.solid(SETTINGS, BOUNDS, CELL, along, y, z) ? 0 : 1;
                }
            }
        }
        assertTrue(open > total * 0.4, open + " of " + total + " points just inside the wall are open");
    }

    @Test
    void everyDiscKeepsASolidPlatformEvenWhereDomesBelowItOverlap() {
        double thickness = SHAPE.floorThickness();
        int checked = 0;
        for (RavineDomes.Dome d : allRooms()) {
            double behindWall = d.side() * (RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, d.floor()) + (d.offset() + 0.5) * d.radius());
            assertTrue(Carved.solid(SETTINGS, BOUNDS, CELL, d.along(), d.floor() - thickness / 2, behindWall), "platform of " + d);
            checked++;
        }
        assertTrue(checked > 20, "checked " + checked);
    }

    @Test
    void aPlatformReachesOutIntoTheShaftAsALedge() {
        double thickness = SHAPE.floorThickness();
        int checked = 0;
        for (RavineDomes.Dome d : allRooms()) {
            double half = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, d.floor());
            double reach = d.radius() * (1 - d.offset());
            double z = d.side() * (half - reach / 2);
            assertTrue(Carved.solid(SETTINGS, BOUNDS, CELL, d.along(), d.floor() - thickness / 2, z), "ledge of " + d);
            checked++;
        }
        assertTrue(checked > 20, "checked " + checked);
    }

    @Test
    void aDiscsDomeStaysClearApartFromStemsAndPlatformsOfOtherDiscs() {
        RavineDiscLayout layout = new RavineDiscLayout(SETTINGS, BOUNDS, CELL);
        List<Disc> discs = new ArrayList<>();
        for (int side : new int[] {1, -1}) {
            for (int row = 0; row < RavineDomes.rows(SETTINGS, BOUNDS); row++) {
                for (int slot = 0; slot < RavineDomes.slots(SETTINGS, CELL); slot++) {
                    layout.at(side, row, slot).ifPresent(discs::add);
                }
            }
        }
        int checked = 0;
        for (Disc d : discs) {
            double y = d.floor() + 2;
            for (double share : new double[] {0, 0.3, 0.6}) {
                double x = d.x() + share * d.radius();
                // Open air from the dome, unless the rock of another disc (or the cavern's dome) is put back there.
                boolean otherRock = false;
                for (Disc other : discs) {
                    otherRock |= other != d && (other.platformDistance(SHAPE, x, y, d.z()) < 0 || other.stemDistance(SHAPE, x, y, d.z()) < 0);
                }
                if (otherRock) {
                    continue;
                }
                assertFalse(Carved.solid(SETTINGS, BOUNDS, CELL, x, y, d.z()), "dome of " + d + " at share " + share);
                checked++;
            }
        }
        assertTrue(checked > 30, "checked " + checked);
    }

    @Test
    void noDiscReachesMoreThanTheOvershootPastTheMiddleOfTheChasm() {
        for (RavineDomes.Dome d : allRooms()) {
            double half = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, d.floor());
            assertTrue(d.radius() * (1 - d.offset()) <= half + PLACEMENT.maxOvershoot() + 1e-9, "reach of " + d);
            assertTrue(d.radius() >= SHAPE.minRadius() - 1e-9 && d.radius() <= SHAPE.maxRadius() + 1e-9, "radius is never changed to fit");
        }
    }

    @Test
    void aDiscThatWouldReachTooFarIsSetBackAndStaysAFullRound() {
        RavineCell narrow = new RavineCell(0, 0, 1, 0, 150, 25, RavineCell.Bend.NONE, RavineCell.Lean.NONE, 5L);
        RavineSettings settings = RavineShapeTest.discs(45, 20, 50, 50, 0.45F, 14, 12, 0F, 0F, 4, 0F, 0F, 4F, 0F);
        var room = RavineDomes.at(settings, BOUNDS, narrow, 1, 0, 3).orElseThrow();
        double half = RavineShape.halfWidthAt(settings, BOUNDS, narrow, room.floor());
        assertEquals(50, room.radius(), 1e-9);
        assertEquals(half + 4, room.radius() * (1 - room.offset()), 1e-9);
        // Every direction round the centre is still platform, all the way out to the radius.
        Disc disc = new RavineDiscLayout(settings, BOUNDS, narrow).at(1, 0, 3).orElseThrow();
        for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 6) {
            double x = disc.x() + 0.98 * disc.radius() * Math.cos(angle);
            double z = disc.z() + 0.98 * disc.radius() * Math.sin(angle);
            assertTrue(disc.platformDistance(settings.discs(), x, disc.floor() - 1, z) < 0, "platform at angle " + angle);
        }
    }

    @Test
    void rowsOnTheSecondWallSitHigherByTheStagger() {
        RavineSettings settings = RavineShapeTest.discs(45, 20, 20, 50, 0.45F, 14, 12, 0F, 0.4F, 4, 0F, 0F, 4F, 0.5F);
        int compared = 0;
        for (int row = 0; row < 3; row++) {
            for (int slot = 0; slot < 5; slot++) {
                var first = RavineDomes.at(settings, BOUNDS, CELL, 1, row, slot);
                var second = RavineDomes.at(settings, BOUNDS, CELL, -1, row, slot);
                if (first.isPresent() && second.isPresent()) {
                    assertEquals(10, second.get().floor() - first.get().floor(), 1e-9);
                    compared++;
                }
            }
        }
        assertTrue(compared > 5, "compared " + compared);
    }

    @Test
    void floorsOfNeighbouringRowsStayAtLeastTheMinimumApart() {
        RavineSettings settings = RavineShapeTest.discs(40, 20, 22, 55, 0.45F, 14, 12, 0.4F, 0.75F, 4, 0.4F, 0F, 4F, 0.5F);
        double minimum = (1 - settings.placement().rowJitter()) * settings.placement().rowSpacing();
        int compared = 0;
        for (int side : new int[] {1, -1}) {
            for (int slot = 0; slot < RavineDomes.slots(settings, CELL); slot++) {
                for (int row = 0; row + 1 < RavineDomes.rows(settings, BOUNDS); row++) {
                    var lower = RavineDomes.at(settings, BOUNDS, CELL, side, row, slot);
                    var upper = RavineDomes.at(settings, BOUNDS, CELL, side, row + 1, slot);
                    if (lower.isPresent() && upper.isPresent()) {
                        assertTrue(upper.get().floor() - lower.get().floor() >= minimum - 1e-9);
                        compared++;
                    }
                }
            }
        }
        assertTrue(compared > 10, "compared " + compared);
    }

    @Test
    void largerDiscsSitFurtherBackAndReachLessWhileSmallerOnesReachFurthestIn() {
        // The shipped offsets, with the overshoot limit switched off so only the setback decides the reach.
        RavineSettings settings = RavineShapeTest.discs(45, 20, 20, 48, 0.45F, 14, 12, 0F, 0.1F, 4, 0.4F, 0.8F, 256F, 0.5F);
        double smallOffsets = 0;
        double largeOffsets = 0;
        double smallReach = 0;
        double largeReach = 0;
        int small = 0;
        int large = 0;
        for (RavineDomes.Dome d : roomsOf(settings)) {
            if (d.radius() < 28) {
                smallOffsets += d.offset();
                smallReach += d.radius() * (1 - d.offset());
                small++;
            } else if (d.radius() > 40) {
                largeOffsets += d.offset();
                largeReach += d.radius() * (1 - d.offset());
                large++;
            }
        }
        assertTrue(small > 10 && large > 5, small + " small, " + large + " large");
        assertTrue(largeOffsets / large > smallOffsets / small + 0.4, "large " + largeOffsets / large + " vs small " + smallOffsets / small);
        assertTrue(largeReach / large < smallReach / small - 5, "large reach " + largeReach / large + " vs small " + smallReach / small);
    }

    private static List<RavineDomes.Dome> allRooms() {
        return roomsOf(SETTINGS);
    }

    private static List<RavineDomes.Dome> roomsOf(RavineSettings settings) {
        var rooms = new ArrayList<RavineDomes.Dome>();
        for (int side : new int[] {1, -1}) {
            for (int row = 0; row < RavineDomes.rows(settings, BOUNDS); row++) {
                for (int slot = 0; slot < RavineDomes.slots(settings, CELL); slot++) {
                    RavineDomes.at(settings, BOUNDS, CELL, side, row, slot).ifPresent(rooms::add);
                }
            }
        }
        return rooms;
    }

    @Test
    void noRoomsFitWhenTheCeilingMarginLeavesNoRoom() {
        assertEquals(0, RavineDomes.rows(RavineShapeTest.PLAIN, BOUNDS));
        assertTrue(RavineDomes.rows(SETTINGS, BOUNDS) >= 2);
    }
}

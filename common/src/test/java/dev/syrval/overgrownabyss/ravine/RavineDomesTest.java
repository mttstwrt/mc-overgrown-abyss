package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RavineDomesTest {
    static final RavineSettings SETTINGS = RavineCellsTest.SETTINGS;
    static final RavineDiscs DISCS = RavineCellsTest.DISCS;
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
                    assertTrue(d.radius() >= DISCS.minRadius() - 1e-9 && d.radius() <= DISCS.maxRadius() + 1e-9);
                    assertTrue(d.offset() >= DISCS.minOffset() - 1e-9 && d.offset() < 1);
                    assertTrue(d.height() >= DISCS.minHeight() - 1e-9);
                    assertTrue(d.floor() >= RavineDomes.lowestFloor(SETTINGS, BOUNDS));
                    assertTrue(d.floor() + d.height() <= BOUNDS.topY() - DISCS.ceilingMargin() + 1e-9, "stays under the ceiling margin");
                    assertTrue(Math.abs(d.along()) <= CELL.halfLength() + DISCS.spacing());
                }
            }
        }
        assertTrue(made > 20, "made " + made);
    }

    @Test
    void roomsVaryInSize() {
        double smallest = Double.MAX_VALUE;
        double largest = 0;
        for (int slot = 0; slot < RavineDomes.slots(SETTINGS, CELL); slot++) {
            for (int row = 0; row < RavineDomes.rows(SETTINGS, BOUNDS); row++) {
                var dome = RavineDomes.at(SETTINGS, BOUNDS, CELL, 1, row, slot);
                if (dome.isPresent()) {
                    smallest = Math.min(smallest, dome.get().radius());
                    largest = Math.max(largest, dome.get().radius());
                }
            }
        }
        assertTrue(largest - smallest > 20, "radii " + smallest + " to " + largest);
    }

    @Test
    void aRoomHasAFlatFloorARoundFootprintAndADomedRoof() {
        RavineDomes.Dome dome = RavineDomes.at(SETTINGS, BOUNDS, CELL, 1, 0, 3).orElseThrow();
        double centreSide = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, dome.floor()) + dome.offset() * dome.radius();
        RavineCell.Frame middle = new RavineCell.Frame(dome.along(), centreSide);
        double h = dome.height();
        assertEquals(-0.5, RavineShape.domeDistance(SETTINGS, BOUNDS, CELL, dome, middle, dome.floor() + 0.5), 1e-9);
        assertTrue(RavineShape.domeDistance(SETTINGS, BOUNDS, CELL, dome, middle, dome.floor() - 0.5) > 0, "solid under the floor");
        // The floor is the same plane everywhere inside the footprint.
        for (double r : new double[] {0, 0.4, 0.8}) {
            RavineCell.Frame p = new RavineCell.Frame(dome.along() + r * dome.radius(), centreSide);
            assertTrue(RavineShape.domeDistance(SETTINGS, BOUNDS, CELL, dome, p, dome.floor() + 0.01) < 0);
            assertTrue(RavineShape.domeDistance(SETTINGS, BOUNDS, CELL, dome, p, dome.floor() - 0.01) > 0);
        }
        RavineCell.Frame outside = new RavineCell.Frame(dome.along() + dome.radius() + 1, centreSide);
        assertTrue(RavineShape.domeDistance(SETTINGS, BOUNDS, CELL, dome, outside, dome.floor() + 1) > 0, "round: solid beyond the radius");
        assertTrue(RavineShape.domeDistance(SETTINGS, BOUNDS, CELL, dome, middle, dome.floor() + h + 1) > 0, "solid above the dome");
        double nearRoof = dome.floor() + h * 0.9;
        RavineCell.Frame edge = new RavineCell.Frame(dome.along() + dome.radius() * 0.8, centreSide);
        assertTrue(RavineShape.domeDistance(SETTINGS, BOUNDS, CELL, dome, edge, nearRoof) > 0, "the roof curves in towards the rim");
    }

    @Test
    void roomsCoverMostOfTheWallFromTheCavernRoofToTheCeilingMargin() {
        double wallInset = 6;
        double lowest = RavineDomes.lowestFloor(SETTINGS, BOUNDS) + 3;
        double highest = BOUNDS.topY() - DISCS.ceilingMargin() - 3;
        int open = 0;
        int total = 0;
        for (int side : new int[] {1, -1}) {
            for (double along = -CELL.halfLength() * 0.8; along <= CELL.halfLength() * 0.8; along += 7) {
                for (double y = lowest; y <= highest; y += 3) {
                    double z = side * (RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, y) + wallInset);
                    total++;
                    open += RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, along, y, z) < 0 ? 1 : 0;
                }
            }
        }
        assertTrue(open > total * 0.4, open + " of " + total + " points just inside the wall are open");
    }

    @Test
    void everyRoomKeepsASolidFloorEvenWhereDomesBelowItOverlap() {
        double thickness = DISCS.floorThickness();
        int checked = 0;
        for (RavineDomes.Dome d : allRooms()) {
            double behindWall = d.side() * (RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, d.floor()) + (d.offset() + 0.5) * d.radius());
            assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, d.along(), d.floor() - thickness / 2, behindWall) > 0, "floor of " + d);
            checked++;
        }
        assertTrue(checked > 20, "checked " + checked);
    }

    @Test
    void aFloorReachesOutIntoTheShaftAsALedge() {
        double thickness = DISCS.floorThickness();
        int checked = 0;
        for (RavineDomes.Dome d : allRooms()) {
            double half = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, d.floor());
            double reach = d.radius() * (1 - d.offset());
            double z = d.side() * (half - reach / 2);
            assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, d.along(), d.floor() - thickness / 2, z) > 0, "ledge of " + d);
            checked++;
        }
        assertTrue(checked > 20, "checked " + checked);
    }

    @Test
    void noDiscReachesMoreThanTheOvershootPastTheMiddleOfTheChasm() {
        for (RavineDomes.Dome d : allRooms()) {
            double half = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, d.floor());
            assertTrue(d.radius() * (1 - d.offset()) <= half + DISCS.maxOvershoot() + 1e-9, "reach of " + d);
            assertTrue(d.radius() >= DISCS.minRadius() - 1e-9 && d.radius() <= DISCS.maxRadius() + 1e-9, "radius is never changed to fit");
        }
    }

    @Test
    void aDiscThatWouldReachTooFarIsSetBackAndStaysAFullRound() {
        RavineCell narrow = new RavineCell(0, 0, 1, 0, 150, 25, RavineCell.Bend.NONE, RavineCell.Lean.NONE, 5L);
        RavineSettings settings = RavineShapeTest.withDiscs(new RavineDiscs(45, 20, 50, 50, 0.45F, 14, 12, 0F, 0F, 4, 0F, 0F, 4F, 0F, 2.5F, 1.5F));
        var room = RavineDomes.at(settings, BOUNDS, narrow, 1, 0, 3).orElseThrow();
        double half = RavineShape.halfWidthAt(settings, BOUNDS, narrow, room.floor());
        assertEquals(50, room.radius(), 1e-9);
        assertEquals(half + 4, room.radius() * (1 - room.offset()), 1e-9);
        // Every direction round the centre is still floor, all the way out to the radius.
        double centreSide = half + room.offset() * room.radius();
        for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 6) {
            RavineCell.Frame p = new RavineCell.Frame(
                    room.along() + 0.98 * room.radius() * Math.cos(angle), centreSide + 0.98 * room.radius() * Math.sin(angle));
            assertTrue(RavineShape.slabDistance(settings, BOUNDS, narrow, room, p, room.floor() - 1) < 0, "floor at angle " + angle);
        }
    }

    @Test
    void rowsOnTheSecondWallSitHigherByTheStagger() {
        RavineDiscs discs = new RavineDiscs(45, 20, 20, 50, 0.45F, 14, 12, 0F, 0.4F, 4, 0F, 0F, 4F, 0.5F, 2.5F, 1.5F);
        RavineSettings settings = RavineShapeTest.withDiscs(discs);
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
        RavineDiscs discs = new RavineDiscs(40, 20, 22, 55, 0.45F, 14, 12, 0.4F, 0.75F, 4, 0.4F, 0F, 4F, 0.5F, 2.5F, 1.5F);
        RavineSettings settings = RavineShapeTest.withDiscs(discs);
        double minimum = (1 - discs.rowJitter()) * discs.rowSpacing();
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
        RavineSettings settings = RavineShapeTest.withDiscs(
                new RavineDiscs(45, 20, 20, 48, 0.45F, 14, 12, 0F, 0.1F, 4, 0.4F, 0.8F, 256F, 0.5F, 2.5F, 1.5F));
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

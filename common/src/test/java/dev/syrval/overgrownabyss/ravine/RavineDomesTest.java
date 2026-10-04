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
                    assertTrue(d.offset() >= DISCS.minOffset() - 1e-9 && d.offset() <= DISCS.maxOffset() + 1e-9);
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
        assertTrue(open > total * 0.5, open + " of " + total + " points just inside the wall are open");
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
            double reach = Math.min(DISCS.maxLip() * 2 * half, d.radius() * (1 - d.offset()));
            double z = d.side() * (half - reach / 2);
            assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, d.along(), d.floor() - thickness / 2, z) > 0, "ledge of " + d);
            checked++;
        }
        assertTrue(checked > 20, "checked " + checked);
    }

    @Test
    void aLedgeStopsAtTheMaximumLip() {
        // Big enough that its footprint reaches well past the lip limit.
        RavineDomes.Dome d = new RavineDomes.Dome(1, 0, 20, 55, 25, 0.4);
        double half = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, d.floor());
        double limit = DISCS.maxLip() * 2 * half;
        assertTrue(d.radius() * (1 - d.offset()) > limit + 2);
        RavineCell.Frame past = new RavineCell.Frame(d.along(), half - limit - 1);
        RavineCell.Frame within = new RavineCell.Frame(d.along(), half - limit + 1);
        assertTrue(RavineShape.slabDistance(SETTINGS, BOUNDS, CELL, d, past, d.floor() - 1) > 0, "no slab past the lip limit");
        assertTrue(RavineShape.slabDistance(SETTINGS, BOUNDS, CELL, d, within, d.floor() - 1) < 0, "slab within the lip limit");
    }

    private static List<RavineDomes.Dome> allRooms() {
        var rooms = new ArrayList<RavineDomes.Dome>();
        for (int side : new int[] {1, -1}) {
            for (int row = 0; row < RavineDomes.rows(SETTINGS, BOUNDS); row++) {
                for (int slot = 0; slot < RavineDomes.slots(SETTINGS, CELL); slot++) {
                    RavineDomes.at(SETTINGS, BOUNDS, CELL, side, row, slot).ifPresent(rooms::add);
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

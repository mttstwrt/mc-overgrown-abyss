package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertTrue(open > total * 0.6, open + " of " + total + " points just inside the wall are open");
    }

    @Test
    void noRoomsFitWhenTheCeilingMarginLeavesNoRoom() {
        assertEquals(0, RavineDomes.rows(RavineShapeTest.PLAIN, BOUNDS));
        assertTrue(RavineDomes.rows(SETTINGS, BOUNDS) >= 2);
    }
}

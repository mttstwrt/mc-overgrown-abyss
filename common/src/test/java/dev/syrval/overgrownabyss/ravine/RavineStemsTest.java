package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RavineStemsTest {
    static final RavineBounds BOUNDS = RavineShapeTest.BOUNDS;
    // A ceiling margin of 45 leaves room for one row of discs only, so nothing is below a stem.
    static final RavineSettings ONE_ROW = RavineShapeTest.withDiscs(
            new RavineDiscs(45, 20, 20, 48, 0.45F, 14, 45, 0F, 0.4F, 4, 0.4F, 0.45F, 4F, 0.5F, 2.5F, 2F));
    static final RavineCell CELL = RavineDomesTest.CELL;

    private static double[] axis(RavineSettings settings, RavineCell cell, RavineDomes.Dome d) {
        double half = RavineShape.halfWidthAt(settings, BOUNDS, cell, d.floor());
        double reach = d.radius() * (1 - d.offset());
        return new double[] {d.along(), d.side() * (half - reach / 2)};
    }

    private static double stem(RavineSettings settings, RavineCell cell, double x, double y, double z) {
        return RavineStems.distance(settings, BOUNDS, cell, new RavineCell.Frame(x, z), x, y, z);
    }

    private static List<RavineDomes.Dome> rooms(RavineSettings settings, RavineCell cell, int row) {
        var rooms = new ArrayList<RavineDomes.Dome>();
        for (int side : new int[] {1, -1}) {
            for (int slot = 0; slot < RavineDomes.slots(settings, cell); slot++) {
                RavineDomes.at(settings, BOUNDS, cell, side, row, slot).ifPresent(rooms::add);
            }
        }
        return rooms;
    }

    @Test
    void aStemIsNeverThinnerThanFiveBlocksAcrossAndNarrowsDownFromTheLedge() {
        assertEquals(1, RavineDomes.rows(ONE_ROW, BOUNDS));
        int checked = 0;
        for (RavineDomes.Dome d : rooms(ONE_ROW, CELL, 0)) {
            double[] axis = axis(ONE_ROW, CELL, d);
            double top = d.floor() - ONE_ROW.discs().floorThickness();
            double shallow = stem(ONE_ROW, CELL, axis[0], top - 1, axis[1]);
            double deep = stem(ONE_ROW, CELL, axis[0], BOUNDS.floorY() + 1, axis[1]);
            assertTrue(deep <= -2.5 + 1e-9, "at least 2.5 radius at the axis everywhere: " + deep);
            assertTrue(shallow <= deep + 1e-9, "the funnel is widest at the ledge");
            // Just outside the thinnest radius, deep below the funnel, there is no stem.
            assertTrue(stem(ONE_ROW, CELL, axis[0] + 2.4, BOUNDS.floorY() + 1, axis[1]) < 0, "inside at 2.4");
            checked++;
        }
        assertTrue(checked > 3, "checked " + checked);
    }

    @Test
    void aStemIsSolidRockHangingInTheShaftAwayFromTheCavern() {
        int checked = 0;
        for (RavineDomes.Dome d : rooms(ONE_ROW, CELL, 0)) {
            double[] axis = axis(ONE_ROW, CELL, d);
            double y = d.floor() - ONE_ROW.discs().floorThickness() - 3;
            if (RavineShape.cavernContains(ONE_ROW, BOUNDS, CELL, axis[0], y, axis[1], 2)) {
                continue;
            }
            boolean insideARoom = false;
            for (RavineDomes.Dome other : rooms(ONE_ROW, CELL, 0)) {
                RavineCell.Frame f = new RavineCell.Frame(axis[0], axis[1]);
                insideARoom |= RavineShape.domeDistance(ONE_ROW, BOUNDS, CELL, other, f, y) <= 0;
            }
            if (insideARoom || stem(ONE_ROW, CELL, axis[0], y, axis[1]) >= 0) {
                continue;
            }
            assertTrue(RavineShape.signedDistance(ONE_ROW, BOUNDS, CELL, axis[0], y, axis[1]) > 0, "stem of " + d);
            checked++;
        }
        assertTrue(checked > 3, "checked " + checked);
    }

    @Test
    void aStemEndsUnderTheFirstLowerDiscThatHoldsItsAxis() {
        RavineCell hole = new RavineCell(0, 0, 1, 0, 0, 60, RavineCell.Bend.NONE, RavineCell.Lean.NONE, 99L);
        RavineSettings settings = RavineShapeTest.withDiscs(
                new RavineDiscs(45, 20, 20, 48, 0.45F, 14, 12, 0F, 0.4F, 4, 0.4F, 0.45F, 4F, 0F, 2.5F, 2F));
        int checked = 0;
        for (int row = 1; row < RavineDomes.rows(settings, BOUNDS); row++) {
            for (RavineDomes.Dome owner : rooms(settings, hole, row)) {
                if (owner.side() < 0) {
                    continue;
                }
                var lower = RavineDomes.at(settings, BOUNDS, hole, 1, row - 1, 0);
                if (lower.isEmpty()) {
                    continue;
                }
                double[] axis = axis(settings, hole, owner);
                double centre = RavineShape.centreSideways(settings, BOUNDS, hole, lower.get());
                boolean held = Math.hypot(axis[0] - lower.get().along(), axis[1] - centre) <= lower.get().radius();
                double yBelow = lower.get().floor() - settings.discs().floorThickness() - 2;
                // The other wall's stems are far from this axis in a hole this wide.
                boolean otherWallFar = rooms(settings, hole, row).stream().filter(o -> o.side() < 0).allMatch(
                        o -> Math.abs(axis(settings, hole, o)[1] - axis[1]) > 45);
                if (held && otherWallFar && yBelow > BOUNDS.floorY()) {
                    assertTrue(stem(settings, hole, axis[0], yBelow, axis[1]) > 2.5, "stem continues under the disc below it");
                    assertTrue(stem(settings, hole, axis[0], lower.get().floor() + 2, axis[1]) < 0, "stem is there above the disc");
                    checked++;
                }
            }
        }
        assertTrue(checked > 0, "found no stem resting on a disc");
    }
}

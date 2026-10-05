package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RavineStemsTest {
    static final RavineBounds BOUNDS = RavineShapeTest.BOUNDS;
    // A ceiling margin of 45 leaves room for one row of discs only.
    static final RavineSettings ONE_ROW = RavineShapeTest.withDiscs(
            new RavineDiscs(45, 20, 20, 48, 0.45F, 14, 45, 0F, 0.4F, 4, 0.4F, 0.45F, 4F, 0.5F, 2.5F, 6F));
    static final RavineCell CELL = RavineDomesTest.CELL;

    private static double stem(RavineSettings settings, RavineCell cell, double x, double y, double z) {
        return RavineStems.distance(settings, BOUNDS, cell, new RavineCell.Frame(x, z), x, y, z);
    }

    private static double centreSideways(RavineSettings settings, RavineCell cell, RavineDomes.Dome d) {
        return d.side() * (RavineShape.halfWidthAt(settings, BOUNDS, cell, d.floor()) + d.offset() * d.radius());
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
    void theProfileIsAHyperbolaThatStartsAtTheDiscAndNeverGetsThinnerThanFiveBlocksAcross() {
        RavineDiscs discs = ONE_ROW.discs();
        RavineDomes.Dome disc = new RavineDomes.Dome(1, 0, 20, 40, 20, 0.2);
        assertEquals(40, RavineStems.radiusAt(discs, disc, 0), 1e-9, "as wide as the disc under its slab");
        assertEquals(20, RavineStems.radiusAt(discs, disc, discs.funnelScale()), 1e-9, "halves one scale below it");
        double previous = Double.MAX_VALUE;
        double previousDrop = Double.MAX_VALUE;
        for (double depth = 0; depth <= 200; depth += 2) {
            double radius = RavineStems.radiusAt(discs, disc, depth);
            assertTrue(radius >= 2.5 - 1e-9, "never below 2.5 radius, which is 5 across");
            assertTrue(radius <= previous + 1e-9, "never widens going down");
            if (radius > 2.5 + 1e-9 && previous > 2.5 + 1e-9 && previous != Double.MAX_VALUE) {
                assertTrue(previous - radius <= previousDrop + 1e-9, "narrows fast at first, then ever more slowly");
                previousDrop = previous - radius;
            }
            previous = radius;
        }
        assertEquals(2.5, RavineStems.radiusAt(discs, disc, 200), 1e-9, "capped at the minimum deep down");
    }

    @Test
    void everyStemRunsDownToTheChasmFloorEvenPastDiscsBelowIt() {
        int checked = 0;
        for (RavineSettings settings : List.of(ONE_ROW, RavineDomesTest.SETTINGS)) {
            int lastRow = RavineDomes.rows(settings, BOUNDS) - 1;
            for (RavineDomes.Dome d : rooms(settings, CELL, lastRow)) {
                double x = d.along();
                double z = centreSideways(settings, CELL, d);
                double top = d.floor() - settings.discs().floorThickness();
                double shallow = stem(settings, CELL, x, top - 1, z);
                double deep = stem(settings, CELL, x, BOUNDS.floorY(), z);
                assertTrue(deep <= -2.5 + 1e-9, "stem is at least 2.5 radius at the floor of the chasm: " + deep);
                assertTrue(shallow <= deep + 1e-9, "widest under the slab");
                checked++;
            }
        }
        assertTrue(checked > 6, "checked " + checked);
    }

    @Test
    void aFlaredStemIsSolidRockInTheShaftUnderALedge() {
        int checked = 0;
        for (RavineDomes.Dome d : rooms(ONE_ROW, CELL, 0)) {
            // A disc set back no more than half its radius has its lip within three quarters of a radius of its centre.
            if (d.offset() > 0.4) {
                continue;
            }
            double half = RavineShape.halfWidthAt(ONE_ROW, BOUNDS, CELL, d.floor());
            double reach = d.radius() * (1 - d.offset());
            double z = d.side() * (half - reach / 2);
            double y = d.floor() - ONE_ROW.discs().floorThickness() - 2;
            RavineCell.Frame f = new RavineCell.Frame(d.along(), z);
            boolean inARoom = false;
            for (RavineDomes.Dome other : rooms(ONE_ROW, CELL, 0)) {
                inARoom |= RavineShape.domeDistance(ONE_ROW, BOUNDS, CELL, other, f, y) <= 0;
            }
            if (inARoom || RavineShape.cavernContains(ONE_ROW, BOUNDS, CELL, d.along(), y, z, 2)) {
                continue;
            }
            assertTrue(stem(ONE_ROW, CELL, d.along(), y, z) < 0, "inside the stem of " + d);
            assertTrue(RavineShape.signedDistance(ONE_ROW, BOUNDS, CELL, d.along(), y, z) > 0, "solid under the ledge of " + d);
            checked++;
        }
        assertTrue(checked > 2, "checked " + checked);
    }
}

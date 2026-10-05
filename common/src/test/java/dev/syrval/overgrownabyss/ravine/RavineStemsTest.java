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
            new RavineDiscs(45, 20, 20, 48, 0.45F, 14, 45, 0F, 0.4F, 4, 0.4F, 0.45F, 4F, 0.5F, 2.5F, 1.5F));
    static final RavineCell CELL = RavineDomesTest.CELL;

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
    void theProfileIsAHyperbolaThatStartsAtTheDiscAndNeverGetsThinnerThanFiveBlocksAcross() {
        RavineDiscs discs = ONE_ROW.discs();
        RavineDomes.Dome disc = new RavineDomes.Dome(1, 0, 20, 40, 20, 0.2);
        assertEquals(40, RavineStems.radiusAt(discs, disc, 0), 1e-9, "as wide as the disc under its slab");
        assertEquals(20, RavineStems.radiusAt(discs, disc, discs.funnelScale()), 1e-9, "halves one scale below it");
        double previous = Double.MAX_VALUE;
        double previousDrop = Double.MAX_VALUE;
        for (double depth = 0; depth <= 200; depth += 0.5) {
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
        assertTrue(RavineStems.radiusAt(discs, disc, 10) < 7, "the flare is a short fillet, not a wide cone");
    }

    @Test
    void aStemWithNothingBelowItRunsDownToTheChasmFloor() {
        int checked = 0;
        for (RavineDomes.Dome d : rooms(ONE_ROW, CELL, 0)) {
            if (RavineStems.bottomOf(ONE_ROW, BOUNDS, CELL, d) != Double.NEGATIVE_INFINITY) {
                continue;
            }
            double x = d.along();
            double z = RavineShape.centreSideways(ONE_ROW, BOUNDS, CELL, d);
            double shallow = stem(ONE_ROW, CELL, x, d.floor() - ONE_ROW.discs().floorThickness() - 1, z);
            double deep = stem(ONE_ROW, CELL, x, BOUNDS.floorY(), z);
            assertTrue(deep <= -2.5 + 1e-9, "at least 2.5 radius at the floor of the chasm: " + deep);
            assertTrue(shallow <= deep + 1e-9, "widest under the slab");
            checked++;
        }
        assertTrue(checked > 2, "checked " + checked);
    }

    @Test
    void aStemIsNotCutOffByTheDomeOfTheRoomItPassesThrough() {
        RavineSettings settings = RavineDomesTest.SETTINGS;
        double thickness = settings.discs().floorThickness();
        int top = RavineDomes.rows(settings, BOUNDS) - 1;
        List<RavineDomes.Dome> all = new ArrayList<>();
        for (int row = 0; row <= top; row++) {
            all.addAll(rooms(settings, CELL, row));
        }
        int checked = 0;
        for (RavineDomes.Dome owner : rooms(settings, CELL, top)) {
            double bottom = RavineStems.bottomOf(settings, BOUNDS, CELL, owner);
            for (RavineDomes.Dome lower : all) {
                if (lower.floor() - thickness != bottom) {
                    continue;
                }
                double x = owner.along();
                double z = RavineShape.centreSideways(settings, BOUNDS, CELL, owner);
                double y = lower.floor() + 3;
                if (y > owner.floor() - thickness - 1) {
                    continue;
                }
                RavineCell.Frame f = new RavineCell.Frame(x, z);
                if (RavineShape.domeDistance(settings, BOUNDS, CELL, lower, f, y) >= 0 || RavineShape.cavernContains(settings, BOUNDS, CELL, x, y, z, 2)) {
                    continue;
                }
                assertTrue(RavineShape.signedDistance(settings, BOUNDS, CELL, x, y, z) > 0, "the pillar of " + owner + " stands in the room of " + lower);
                checked++;
            }
        }
        assertTrue(checked > 2, "checked " + checked);
    }

    @Test
    void aStemEndsOnTheFloorSlabOfTheHighestLowerDiscThatHoldsItsAxis() {
        RavineCell hole = new RavineCell(0, 0, 1, 0, 0, 60, RavineCell.Bend.NONE, RavineCell.Lean.NONE, 99L);
        RavineSettings settings = RavineShapeTest.withDiscs(
                new RavineDiscs(45, 20, 20, 48, 0.45F, 14, 12, 0F, 0.4F, 4, 0.4F, 0.45F, 4F, 0F, 2.5F, 1.5F));
        double thickness = settings.discs().floorThickness();
        int checked = 0;
        for (int row = 1; row < RavineDomes.rows(settings, BOUNDS); row++) {
            var owner = RavineDomes.at(settings, BOUNDS, hole, 1, row, 0);
            var lower = RavineDomes.at(settings, BOUNDS, hole, 1, row - 1, 0);
            if (owner.isEmpty() || lower.isEmpty()) {
                continue;
            }
            double centreOwner = RavineShape.centreSideways(settings, BOUNDS, hole, owner.get());
            double centreLower = RavineShape.centreSideways(settings, BOUNDS, hole, lower.get());
            boolean held = Math.hypot(owner.get().along() - lower.get().along(), centreOwner - centreLower) <= lower.get().radius();
            if (held) {
                assertTrue(RavineStems.bottomOf(settings, BOUNDS, hole, owner.get()) >= lower.get().floor() - thickness - 1e-9,
                        "ends at the slab of the disc below it, or one even higher");
                checked++;
            }
        }
        assertTrue(checked > 0, "found no stem landing on a disc");
    }

    @Test
    void aFlaredStemIsSolidRockInTheShaftUnderALedge() {
        int checked = 0;
        for (RavineDomes.Dome d : rooms(ONE_ROW, CELL, 0)) {
            // A disc set back no more than 0.4 of its radius has its lip within three quarters of a radius of its centre.
            if (d.offset() > 0.4) {
                continue;
            }
            double half = RavineShape.halfWidthAt(ONE_ROW, BOUNDS, CELL, d.floor());
            double reach = d.radius() * (1 - d.offset());
            double z = d.side() * (half - reach / 2);
            double y = d.floor() - ONE_ROW.discs().floorThickness() - 0.5;
            if (RavineStems.bottomOf(ONE_ROW, BOUNDS, CELL, d) > y) {
                continue;
            }
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
        assertTrue(checked > 0, "checked " + checked);
    }
}

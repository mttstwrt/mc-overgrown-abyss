package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/** The disc, whatever geometry placed it: its dome, platform and stem or root, and how stems and roots end. */
class DiscTest {
    static final DiscShape SHAPE = new DiscShape(20, 48, 0.45F, 14, 4, 0.2F, 2.5F, 32F, 1.5F, 3F, 4F, 0F, 0F, 0F);
    /** Stems from 2 to 6 blocks across and a bowl 1 to 4 deep by size, every disc with the full depth for its size. */
    static final DiscShape SLIM = new DiscShape(20, 64, 0.45F, 14, 4, 0.05F, 1F, 3F, 1.5F, 3F, 4F, 1F, 4F, 0F);
    /** The same with bowls that vary, as the mod ships them. */
    static final DiscShape VARIED = new DiscShape(20, 64, 0.45F, 14, 4, 0.05F, 1F, 3F, 1.5F, 3F, 4F, 1F, 4F, 0.75F);

    /** A list of discs, each standing on the others: enough to test the carving without a geometry. */
    private record ListLayout(List<Disc> discs) implements DiscLayout {
        static ListLayout of(Disc... placed) {
            return new ListLayout(Discs.standing(List.of(placed), SHAPE));
        }
    }

    @Test
    void aDiscHasAFlatFloorARoundFootprintAndADomedRoof() {
        Disc d = new Disc(10, -5, 20, 40, 18);
        assertEquals(-0.5, d.domeDistance(10, 20.5, -5), 1e-9);
        assertTrue(d.domeDistance(10, 19.5, -5) > 0, "solid under the floor");
        for (double r : new double[] {0, 0.4, 0.8}) {
            assertTrue(d.domeDistance(10 + r * 40, 20.01, -5) < 0, "open just over the floor");
            assertTrue(d.domeDistance(10 + r * 40, 19.99, -5) > 0, "solid just under it: the floor is one plane");
        }
        assertTrue(d.domeDistance(10 + 41, 21, -5) > 0, "round: solid beyond the radius");
        assertTrue(d.domeDistance(10, 20 + 18 + 1, -5) > 0, "solid above the dome");
        assertTrue(d.domeDistance(10 + 0.8 * 40, 20 + 18 * 0.9, -5) > 0, "the roof curves in towards the rim");
    }

    @Test
    void aPlatformIsSolidOutToItsRimWithAFlatTopAndTheStemHangsFromIt() {
        Disc d = new Disc(0, 0, 20, 40, 18);
        double thickness = SHAPE.floorThickness();
        for (double r : new double[] {0, 0.5, 0.97}) {
            assertTrue(d.platformDistance(SHAPE, r * 40, 20 - 0.5, 0) < 0, "solid just under the top");
            assertTrue(d.platformDistance(SHAPE, r * 40, 20 + 0.5, 0) > 0, "open just over it: the top is one plane");
            assertTrue(d.platformDistance(SHAPE, r * 40, 20 - thickness - 0.5, 0) > 0, "no platform below its thickness");
        }
        assertTrue(d.platformDistance(SHAPE, 41, 19, 0) > 0, "round");
        assertEquals(Double.POSITIVE_INFINITY, d.stemDistance(SHAPE, 0, 20 - thickness + 1, 0), "no stem beside the platform");
        assertTrue(d.stemDistance(SHAPE, 0, 20 - thickness - 1, 0) < 0, "a stem hangs from the underside");
    }

    @Test
    void theStemIsAHyperbolaThatStartsAtTheDiscAndNeverGetsThinnerThanFiveBlocksAcross() {
        Disc d = new Disc(0, 0, 20, 40, 18);
        assertEquals(40, d.stemRadiusAt(SHAPE, 0), 1e-9, "as wide as the disc under its platform");
        assertEquals(20, d.stemRadiusAt(SHAPE, SHAPE.funnelScale()), 1e-9, "halves one scale below it");
        double previous = Double.MAX_VALUE;
        double previousDrop = Double.MAX_VALUE;
        for (double depth = 0; depth <= 200; depth += 0.5) {
            double radius = d.stemRadiusAt(SHAPE, depth);
            assertTrue(radius >= 2.5 - 1e-9, "never below 2.5 radius, which is 5 across");
            assertTrue(radius >= SHAPE.stemFraction() * 40 - 1e-9, "never below its share of the disc's radius");
            assertTrue(radius <= previous + 1e-9, "never widens going down");
            if (radius > SHAPE.stemRadiusFor(40) + 1e-9 && previous > SHAPE.stemRadiusFor(40) + 1e-9 && previous != Double.MAX_VALUE) {
                assertTrue(previous - radius <= previousDrop + 1e-9, "narrows fast at first, then ever more slowly");
                previousDrop = previous - radius;
            }
            previous = radius;
        }
        assertEquals(SHAPE.stemRadiusFor(40), d.stemRadiusAt(SHAPE, 200), 1e-6, "capped at the stem's radius deep down");
        assertEquals(8, SHAPE.stemRadiusFor(40), 1e-6);
        assertEquals(2.5, SHAPE.stemRadiusFor(10), 1e-9, "a small disc's stem is still 5 across");
        assertTrue(d.stemRadiusAt(SHAPE, 10) < 14, "the flare is a short fillet, not a wide cone");
    }

    @Test
    void aStemWithNothingBelowItRunsDownAndOneThatLandsEndsFlushUnderThePlatformItLandsOn() {
        Disc owner = new Disc(0, 0, 60, 20, 14);
        // Far enough along that its own stem is not where the owner's stem would be.
        Disc lower = new Disc(12, 0, 30, 40, 18);
        double thickness = SHAPE.floorThickness();
        assertEquals(Double.NEGATIVE_INFINITY, Discs.bottomOf(List.of(owner), SHAPE, owner));
        assertTrue(Discs.rockDistance(ListLayout.of(owner), SHAPE, 0, -50, 0) < 0, "runs far down");
        ListLayout both = ListLayout.of(owner, lower);
        assertEquals(lower.floor() - thickness / 2, Discs.bottomOf(List.of(owner, lower), SHAPE, owner), 1e-9, "it ends inside the platform it lands on");
        assertEquals(new Disc.Support.Standing(lower.floor() - thickness / 2), both.discs().get(0).support(), "the layout's disc knows where its stem ends");
        assertEquals(Disc.Support.Standing.TO_THE_FLOOR, both.discs().get(1).support());
        // The stem stands in the lower disc's dome (rock beats the dome), and is gone below that disc's platform.
        assertTrue(Discs.rockDistance(both, SHAPE, 0, 40, 0) < 0, "stem in the open above the lower disc");
        assertTrue(Discs.rockDistance(both, SHAPE, 0, lower.floor() - thickness - 15, 0) > 0, "no stem under the platform it landed on");
        Disc beside = new Disc(100, 0, 30, 20, 14);
        assertEquals(Double.NEGATIVE_INFINITY, Discs.bottomOf(List.of(owner, beside), SHAPE, owner), "a disc beside it does not hold it");
    }

    @Test
    void aStemIsNotCutOffByTheDomeOfADiscItPassesThrough() {
        Disc owner = new Disc(0, 0, 60, 20, 14);
        Disc lower = new Disc(12, 0, 30, 40, 18);
        ListLayout both = ListLayout.of(owner, lower);
        // A point on the axis, in the lower disc's dome: air by the dome, rock by the stem, and rock wins.
        assertTrue(lower.domeDistance(0, 40, 0) < 0);
        assertTrue(Discs.domeDistance(both, 0, 40, 0) < 0);
        assertTrue(Discs.rockDistance(both, SHAPE, 0, 40, 0) < 0);
    }

    @Test
    void aHangingDiscHasARootThatIsWidestAtItsAnchorAndNothingUnderItsPlatform() {
        Disc d = new Disc(0, 0, 20, 40, 18).withSupport(new Disc.Support.Hanging(60, 60));
        double stem = SHAPE.stemRadiusFor(40);
        assertEquals(24, d.rootRadiusAt(SHAPE, 0), 1e-6, "three stem radii wide at the anchor");
        assertEquals(12, d.rootRadiusAt(SHAPE, SHAPE.rootScale()), 1e-6, "halves one scale away from it");
        double previous = Double.MAX_VALUE;
        for (double distance = 0; distance <= 100; distance += 0.5) {
            double radius = d.rootRadiusAt(SHAPE, distance);
            assertTrue(radius >= stem - 1e-9, "never thinner than a stem");
            assertTrue(radius <= previous + 1e-9, "never widens away from the anchor");
            previous = radius;
        }
        assertEquals(6, new Disc(0, 0, 20, 6, 14).rootRadiusAt(SHAPE, 0), 1e-9, "never wider than the disc");
        assertTrue(d.rockDistance(SHAPE, 0, 40, 0) < 0, "the root runs up the axis");
        assertTrue(d.rockDistance(SHAPE, 10, 40, 0) > 0, "thin in the middle of its run");
        assertTrue(d.rockDistance(SHAPE, 10, 59, 0) < 0, "wide under the ceiling");
        assertTrue(d.rockDistance(SHAPE, 0, 61, 0) > 0, "ends flush under a flat ceiling");
        assertTrue(d.rockDistance(SHAPE, 0, 20 - SHAPE.floorThickness() - 3, 0) > 0, "no stem under the platform");
        assertTrue(d.rockDistance(SHAPE, 30, 19, 0) < 0, "the platform is still there");
    }

    @Test
    void aRootAtASlopingCeilingNarrowsAgainAboveItsAnchorAndEndsAtItsTop() {
        Disc d = new Disc(0, 0, 20, 40, 18).withSupport(new Disc.Support.Hanging(60, 84));
        assertTrue(d.rockDistance(SHAPE, 10, 64, 0) < 0, "still wide just above the anchor");
        assertTrue(d.rockDistance(SHAPE, 10, 80, 0) > 0, "back to a thin column further up");
        assertTrue(d.rockDistance(SHAPE, 0, 83, 0) < 0);
        assertTrue(d.rockDistance(SHAPE, 0, 85, 0) > 0, "nothing above its top");
    }

    @Test
    void aRootNeedsItsAnchorAboveThePlatformAndItsTopNoLowerThanTheAnchor() {
        Disc d = new Disc(0, 0, 20, 40, 18);
        assertThrows(IllegalArgumentException.class, () -> d.withSupport(new Disc.Support.Hanging(20, 20)));
        assertThrows(IllegalArgumentException.class, () -> new Disc.Support.Hanging(60, 59));
    }

    @Test
    void aStemIsItsShareOfTheDiscAndBetweenTwoAndSixBlocksAcross() {
        assertEquals(1, SLIM.stemRadiusFor(20), 1e-6);
        assertEquals(2, SLIM.stemRadiusFor(40), 1e-6);
        assertEquals(3, SLIM.stemRadiusFor(64), 1e-6, "the largest are held to 6 across");
        assertEquals(1, SLIM.stemRadiusFor(10), 1e-6, "the smallest still get 2 across");
        double previous = 0;
        for (double radius = 5; radius <= 80; radius += 2.5) {
            assertTrue(SLIM.stemRadiusFor(radius) >= previous, "a larger disc never has a thinner stem");
            previous = SLIM.stemRadiusFor(radius);
        }
    }

    @Test
    void aPlacedDiscIsCentredBetweenBlocksSoAStemIsAsWideOneWayAsTheOther() {
        for (double[] at : new double[][] {{0, 0}, {10.3, -5.9}, {-0.01, 7.5}, {123.99, 0.49}}) {
            Disc small = Disc.placed(at[0], at[1], 20, 20, 14, 0);
            assertEquals(0.5, small.x() - Math.floor(small.x()), 1e-9);
            assertEquals(0.5, small.z() - Math.floor(small.z()), 1e-9);
            assertTrue(Math.abs(small.x() - at[0]) <= 0.5 && Math.abs(small.z() - at[1]) <= 0.5, "moved by half a block at most");
            assertEquals(4, stemColumns(small, -84, 0, 4), "the thinnest stem is 2 by 2 wherever the disc was put");
            Disc large = Disc.placed(at[0], at[1], 20, 64, 28, 0);
            assertEquals(6, stemColumns(large, -84, 0, 0), "the thickest is 6 across along x");
            assertEquals(6, acrossZ(large, -84), "and along z");
        }
    }

    // Block columns of a stem at one height, within `spanZ` rows either side of the one through `rowOffset` from its axis.
    private static int stemColumns(Disc d, int y, int rowOffset, int spanZ) {
        int columns = 0;
        int baseX = (int) Math.floor(d.x());
        int baseZ = (int) Math.floor(d.z()) + rowOffset;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -spanZ; dz <= spanZ; dz++) {
                columns += d.stemDistance(SLIM, baseX + dx, y, baseZ + dz) < 0 ? 1 : 0;
            }
        }
        return columns;
    }

    private static int acrossZ(Disc d, int y) {
        int columns = 0;
        for (int dz = -6; dz <= 6; dz++) {
            columns += d.stemDistance(SLIM, (int) Math.floor(d.x()), y, (int) Math.floor(d.z()) + dz) < 0 ? 1 : 0;
        }
        return columns;
    }

    @Test
    void aDiscIsAShallowBowlTopAndUndersideAlike() {
        Disc d = new Disc(0, 0, 20, 40, 18, 3, Disc.Support.Standing.TO_THE_FLOOR);
        assertEquals(20, d.topAt(0), 1e-9, "the floor is the middle of the top");
        assertEquals(23, d.topAt(40), 1e-9, "the rim stands the bowl's depth above it");
        assertEquals(23, d.topAt(60), 1e-9);
        double previous = 0;
        for (double r : new double[] {0, 10, 20, 30, 39}) {
            double top = d.topAt(r);
            assertTrue(top >= previous, "rises all the way out");
            previous = top;
            assertTrue(d.platformDistance(SHAPE, r, top - 0.3, 0) < 0, "solid just under the top at " + r);
            assertTrue(d.platformDistance(SHAPE, r, top + 0.3, 0) > 0, "open just over it at " + r);
            assertTrue(d.domeDistance(r, top + 0.3, 0) < 0, "the dome's air starts at the bowl at " + r);
            assertTrue(d.domeDistance(r, top - 0.3, 0) > 0, "and not under it");
            assertEquals(top - 4, d.undersideAt(SHAPE, r), 1e-9, "as thick at " + r + " as in the middle");
            assertTrue(d.platformDistance(SHAPE, r, top - 3.7, 0) < 0, "solid just over the underside");
            assertTrue(d.platformDistance(SHAPE, r, top - 4.3, 0) > 0, "no platform just under it");
            // The stem's flare hangs from the curved underside, so there is rock, not a slit of air, right under it.
            assertTrue(d.rockDistance(SHAPE, r, top - 4.3, 0) < 0 || r > 0.8 * 40, "air between the underside and the flare at " + r);
        }
        assertTrue(d.rockDistance(SHAPE, 38, 21.5, 0) < 0, "the raised rim is rock above the floor's height");
        assertTrue(d.rockDistance(SHAPE, 5, 21.5, 0) > 0, "and the middle is open at that height");
        assertTrue(d.rockDistance(SHAPE, 38, 17, 0) > 0, "and under the raised rim it is open where the middle is platform");
        assertTrue(d.platformDistance(SHAPE, 5, 17, 0) < 0);
        double top30 = d.topAt(30);
        assertEquals(Optional.of(new DiscPoint.Platform(top30 - 21, 21 - (top30 - 4))), d.pointAt(SHAPE, 30, 21, 0), "depths are measured from the bowl's two faces");
        assertThrows(IllegalArgumentException.class, () -> new Disc(0, 0, 20, 40, 18, -1, Disc.Support.Standing.TO_THE_FLOOR));
    }

    @Test
    void theBowlIsShallowestOnTheSmallestDiscsAndDeepestOnTheLargest() {
        assertEquals(1, SLIM.bowlDepthFor(20, 0.7), 1e-9);
        assertEquals(4, SLIM.bowlDepthFor(64, 0.7), 1e-9);
        assertEquals(2.5, SLIM.bowlDepthFor(42, 0.7), 1e-9);
        assertEquals(1, SLIM.bowlDepthFor(10, 0.7), 1e-9, "a disc under the smallest size gets the least");
        assertEquals(4, SLIM.bowlDepthFor(100, 0.7), 1e-9);
        assertEquals(0, SHAPE.bowlDepthFor(40, 0.7), 1e-9, "flat when both depths are 0");
        assertEquals(SLIM.bowlDepthFor(30, 0), Disc.placed(0, 0, 20, 30, 14, SLIM.bowlDepthFor(30, 0)).bowl(), 1e-9);
    }

    @Test
    void bowlsVarySoThatSomeDiscsStayFlatter() {
        assertEquals(4, VARIED.bowlDepthFor(64, 0), 1e-9, "the full depth for its size at one end of the draw");
        assertEquals(1, VARIED.bowlDepthFor(64, 1), 1e-9, "a quarter of it at the other");
        assertEquals(2.5, VARIED.bowlDepthFor(64, 0.5), 1e-9);
        assertEquals(0.25, VARIED.bowlDepthFor(20, 1), 1e-9, "a small disc can be all but flat");
        double previous = Double.MAX_VALUE;
        for (double unit = 0; unit <= 1; unit += 0.1) {
            assertTrue(VARIED.bowlDepthFor(50, unit) <= previous, "a higher draw is never deeper");
            previous = VARIED.bowlDepthFor(50, unit);
        }
    }

    @Test
    void aStemLandsInsideTheCurvedPlatformUnderItAndARootRunsIntoTheOneAboveIt() {
        Disc lower = new Disc(0, 0, 30, 40, 18, 4, Disc.Support.Standing.TO_THE_FLOOR);
        Disc owner = new Disc(24, 0, 60, 20, 14);
        double top = lower.topAt(24);
        double bottom = Discs.bottomOf(List.of(owner, lower), SHAPE, owner);
        assertTrue(bottom < top && bottom > lower.undersideAt(SHAPE, 24), "the stem ends between the bowl's two faces where it lands");
        Disc standing = owner.withSupport(new Disc.Support.Standing(bottom));
        for (double dx = -4; dx <= 4; dx += 2) {
            // Across the stem's width the underside of the bowl moves up and down, and the stem never shows below it.
            assertTrue(standing.supportDistance(SHAPE, 24 + dx, lower.undersideAt(SHAPE, 24 + dx) - 0.3, 0) > 0, "a stub under the bowl at " + dx);
        }
        Disc hanging = new Disc(24, 0, 0, 12, 14);
        assertEquals(lower.undersideAt(SHAPE, 24), Discs.platformAbove(List.of(hanging, lower), SHAPE, hanging).orElseThrow(), 1e-9, "the ceiling is the curved underside over the axis");
    }

    @Test
    void aPointInADiscKnowsWhichPartItIsInAndHowDeep() {
        Disc d = new Disc(0, 0, 20, 40, 18);
        assertEquals(Optional.of(new DiscPoint.Platform(1, 3)), d.pointAt(SHAPE, 5, 19, 0), "the top block of a platform 4 thick");
        assertEquals(Optional.of(new DiscPoint.Platform(3, 1)), d.pointAt(SHAPE, 5, 17, 0), "its bottom block");
        assertEquals(Optional.empty(), d.pointAt(SHAPE, 5, 20, 0), "the top plane itself is air");
        assertEquals(Optional.empty(), d.pointAt(SHAPE, 41, 19, 0), "beyond the rim");
        // 30 below the underside the stem is down to its 8 radius, so 5 from the axis is 3 inside it.
        assertTrue(d.pointAt(SHAPE, 5, -14, 0).orElseThrow() instanceof DiscPoint.Stem stem && Math.abs(stem.inside() - 3) < 1e-6);
        assertEquals(Optional.empty(), d.pointAt(SHAPE, 9, -14, 0), "beside the stem");
        assertTrue(d.pointAt(SHAPE, 20, 15, 0).orElseThrow() instanceof DiscPoint.Stem, "the flare just under the platform is stem");
        assertEquals(Optional.empty(), d.withSupport(new Disc.Support.Standing(0)).pointAt(SHAPE, 0, -5, 0), "below where the stem ends");
        Disc hanging = d.withSupport(new Disc.Support.Hanging(60, 60));
        assertTrue(hanging.pointAt(SHAPE, 0, 40, 0).orElseThrow() instanceof DiscPoint.Stem, "a root counts as stem");
        assertEquals(Optional.empty(), hanging.pointAt(SHAPE, 0, 10, 0), "a hanging disc has nothing under its platform");
        assertTrue(hanging.pointAt(SHAPE, 0, 19, 0).orElseThrow() instanceof DiscPoint.Platform);
    }

    @Test
    void aRootHangsFromTheLowestPlatformAboveThatHoldsItsAxis() {
        Disc owner = new Disc(0, 0, 20, 20, 14);
        Disc low = new Disc(5, 0, 50, 40, 18);
        Disc high = new Disc(0, 0, 80, 40, 18);
        Disc beside = new Disc(100, 0, 40, 20, 14);
        assertEquals(OptionalDouble.empty(), Discs.platformAbove(List.of(owner, beside), SHAPE, owner), "a disc beside it is no ceiling");
        assertEquals(OptionalDouble.of(50 - SHAPE.floorThickness()), Discs.platformAbove(List.of(owner, high, low, beside), SHAPE, owner));
    }
}

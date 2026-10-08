package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class RavineSitesTest {
    static {
        MinecraftBootstrap.init();
    }

    // Along the x axis: ends at x = -150 and x = 150.
    private static final RavineCell CELL = new RavineCell(0, 0, 1, 0, 150, 50);
    private static final RavineBounds LEVEL = new RavineBounds(-40, 160);
    private static final RavineSettings RAVINE = RavineShapeTest.SETTINGS;
    /** The sea of a level of vanilla's, which the ground round a mouth has to stand 20 blocks above. */
    static final int SEA = 63;
    /** A cone whose top follows the ground, with its lip 3 under the lowest ground round a mouth of radius 45. */
    private static final RavineSettings CONE = ConeRimTest.withLowShare(0);
    private static final RavineCell HOLE = RavineCells.at(7L, CONE, 0, 0).orElseThrow();

    /** Ground of the given height in each column, which is all a probe is in a test. */
    static SurfaceProbe ground(IntBinaryOperator height) {
        return (x, z, from) -> Math.min(from, height.applyAsInt(x, z));
    }

    private static Optional<RavineBounds> holeOn(SurfaceProbe ground) {
        return holeOn(CONE, ground);
    }

    private static Optional<RavineBounds> holeOn(RavineSettings settings, SurfaceProbe ground) {
        RavineSites sites = new RavineSites(settings, LEVEL);
        sites.followGround(ground, SEA);
        return sites.boundsOf(HOLE);
    }

    private static int lipOn(RavineSettings settings, SurfaceProbe ground) {
        return holeOn(settings, ground).orElseThrow().topY();
    }

    // The column of the mouth's edge straight east of the axis, and the next one round the mouth from it.
    private static boolean isEastProbe(int x, int z) {
        return x == (int) Math.floor(HOLE.centreX() + 45) && z == (int) Math.floor(HOLE.centreZ());
    }

    private static final List<Integer> RIM_OF_117 = Collections.nCopies(RavineSites.RIM_PROBES, 117);

    private static boolean isNextProbe(int x, int z) {
        double angle = 2 * Math.PI / RavineSites.RIM_PROBES;
        return x == (int) Math.floor(HOLE.centreX() + 45 * Math.cos(angle)) && z == (int) Math.floor(HOLE.centreZ() + 45 * Math.sin(angle));
    }

    @Test
    void samplePointsRunFromEndToEndAlongTheCentreLine() {
        assertEquals(
                List.of(new RavineCell.Column(-150, 0), new RavineCell.Column(-75, 0), new RavineCell.Column(0, 0),
                        new RavineCell.Column(75, 0), new RavineCell.Column(150, 0)),
                CELL.samplePoints());
    }

    @Test
    void aCellHoldsAHoleBetweenTheLevelsBoundsUntilACheckIsInstalled() {
        assertEquals(Optional.of(LEVEL), new RavineSites(RAVINE, LEVEL).boundsOf(CELL));
    }

    @Test
    void oneWetSamplePointRejectsTheWholeRavine() {
        RavineSites sites = new RavineSites(RAVINE, LEVEL);
        sites.restrictToLand((x, z) -> x < 140);
        assertTrue(sites.boundsOf(CELL).isEmpty());
        sites.restrictToLand((x, z) -> true);
        assertEquals(Optional.of(LEVEL), sites.boundsOf(CELL));
    }

    @Test
    void asksOncePerCell() {
        AtomicInteger asked = new AtomicInteger();
        RavineSites sites = new RavineSites(RAVINE, LEVEL);
        sites.restrictToLand((x, z) -> {
            asked.incrementAndGet();
            return true;
        });
        sites.boundsOf(CELL);
        sites.boundsOf(CELL);
        assertEquals(5, asked.get());
    }

    @Test
    void aConesLipIsTheDipUnderTheLowestGroundRoundItsMouth() {
        RavineBounds level = holeOn(ground((x, z) -> 120)).orElseThrow();
        assertEquals(117, level.topY(), "level ground");
        assertEquals(RIM_OF_117, level.edge(), "and a level edge");
        // A slope rising to the east. The lowest column of the mouth is the one straight west, 45 blocks from the axis and 15
        // blocks down; that one alone counts as a pothole, and the two beside it are 14 down.
        int centre = (int) Math.floor(HOLE.centreX());
        RavineBounds onASlope = holeOn(ground((x, z) -> 130 + (x - centre) / 3)).orElseThrow();
        assertEquals(130 - 14 - 3, onASlope.topY());
    }

    @Test
    void theMouthsEdgeIsTheDipUnderTheGroundOnEachSideAndNowhereUnderTheLip() {
        // A slope rising to the east by a block in three: 30 blocks from one side of the mouth to the other.
        int centre = (int) Math.floor(HOLE.centreX());
        SurfaceProbe slope = ground((x, z) -> 130 + (x - centre) / 3);
        RavineBounds low = holeOn(slope).orElseThrow();
        assertEquals(RavineSites.RIM_PROBES, low.edge().size());
        assertEquals(113, low.edgeAt(-1, 0), 1e-9, "the lip on the downhill side");
        assertEquals(130 + 14 - 3, low.edgeAt(1, 0), 1e-9, "the ground on the uphill side, the highest column alone not counted");
        assertEquals(low.edgeAt(0, 1), low.edgeAt(0, -1), 1.01, "the same to the north and the south");
        assertTrue(low.edgeAt(0, 1) > 120 && low.edgeAt(0, 1) < 130, "and between the two there: " + low.edgeAt(0, 1));
        assertEquals(141, low.highestEdge());
        // With the lip under the middle ground the downhill half of the edge is the lip, since the ground there is lower.
        RavineBounds middle = holeOn(ConeRimTest.withLowShare(0.5F), slope).orElseThrow();
        assertEquals(127, middle.topY());
        assertEquals(127, middle.edgeAt(-1, 0), 1e-9);
        assertEquals(141, middle.edgeAt(1, 0), 1e-9);
        for (int height : middle.edge()) {
            assertTrue(height >= 127 && height <= 141, "an edge at " + height);
        }
        // Between two columns the edge is blended.
        double step = 2 * Math.PI / RavineSites.RIM_PROBES;
        double between = middle.edgeAt(Math.cos(2.5 * step), Math.sin(2.5 * step));
        assertEquals((middle.edge().get(2) + middle.edge().get(3)) / 2.0, between, 1e-9);
        assertEquals(middle.edge().get(RavineSites.RIM_PROBES - 1), middle.edgeAt(Math.cos(-step), Math.sin(-step)), 1e-9, "the last column is the one before the first");
    }

    @Test
    void aShareOfTheMouthsEdgeMayLieBelowTheLip() {
        // High ground with a third of the mouth's edge, on its east side, in a valley 40 blocks lower.
        SurfaceProbe valley = ground((x, z) -> x > HOLE.centreX() + 22 ? 100 : 140);
        assertEquals(97, lipOn(CONE, valley), "under the lowest ground");
        assertEquals(97, lipOn(ConeRimTest.withLowShare(0.25F), valley), "a quarter is not enough to pass over a third");
        assertEquals(137, lipOn(ConeRimTest.withLowShare(0.5F), valley), "under the middle ground");
        assertEquals(137, lipOn(ConeRimTest.withLowShare(1), valley), "under the highest");
        assertEquals(137, holeOn(valley).orElseThrow().edgeAt(-1, 0), 1e-9, "the edge is the high ground's on its side whatever the lip");
        // A slope rising to the east by a block in three: 30 blocks from one side of the mouth to the other.
        int centre = (int) Math.floor(HOLE.centreX());
        SurfaceProbe slope = ground((x, z) -> 130 + (x - centre) / 3);
        assertEquals(113, lipOn(CONE, slope));
        assertEquals(117, lipOn(ConeRimTest.withLowShare(0.25F), slope));
        assertEquals(127, lipOn(ConeRimTest.withLowShare(0.5F), slope), "the dip under the ground at the axis");
        assertEquals(141, lipOn(ConeRimTest.withLowShare(1), slope), "the dip under the highest ground but for one column");
    }

    @Test
    void aLipIsNeverAboveTheLevelsTopAndTheGroundIsNotMeasuredAboveIt() {
        AtomicInteger highest = new AtomicInteger();
        RavineBounds hole = holeOn((x, z, from) -> {
            highest.accumulateAndGet(from, Math::max);
            return Math.min(from, 240);
        }).orElseThrow();
        assertEquals(LEVEL.topY(), hole.topY());
        assertEquals(LEVEL.topY(), hole.highestEdge(), "nor is the edge");
        assertEquals(163, highest.get(), "asked no higher than the dip over the top");
        assertEquals(LEVEL.topY(), new RavineSites(CONE, LEVEL).boundsOf(HOLE).orElseThrow().topY(), "the top is the level's until the ground is installed");
    }

    @Test
    void aCellWhoseGroundIsTooNearSeaLevelHoldsNoHole() {
        assertEquals(80, lipOn(CONE, ground((x, z) -> 83)), "20 above the sea is enough");
        assertTrue(holeOn(ground((x, z) -> 82)).isEmpty(), "19 is not");
        assertTrue(holeOn(ground((x, z) -> 64)).isEmpty());
        // High everywhere but in a valley across the east side of the mouth.
        SurfaceProbe aboveAShore = ground((x, z) -> x > HOLE.centreX() + 30 ? 70 : 140);
        assertTrue(holeOn(aboveAShore).isEmpty());
        assertTrue(holeOn(ConeRimTest.withLowShare(1), aboveAShore).isEmpty(), "however high the lip would be: it is the ground that counts");
        // The same valley 20 over the sea: the hole is there, and its lip is wherever the share puts it.
        SurfaceProbe aboveAValley = ground((x, z) -> x > HOLE.centreX() + 30 ? 83 : 140);
        assertEquals(80, lipOn(CONE, aboveAValley));
        assertEquals(137, lipOn(ConeRimTest.withLowShare(0.5F), aboveAValley));
        // The sea is the level's own.
        RavineSites sites = new RavineSites(CONE, LEVEL);
        sites.followGround(ground((x, z) -> 83), 70);
        assertTrue(sites.boundsOf(HOLE).isEmpty(), "13 over a sea at 70");
    }

    @Test
    void onePotholeDoesNotCountButLowGroundTwoColumnsWideDoes() {
        assertEquals(117, lipOn(CONE, ground((x, z) -> isEastProbe(x, z) ? 60 : 120)), "though the pothole is under the sea");
        assertEquals(107, lipOn(CONE, ground((x, z) -> isEastProbe(x, z) || isNextProbe(x, z) ? 110 : 120)));
        assertTrue(holeOn(ground((x, z) -> isEastProbe(x, z) || isNextProbe(x, z) ? 60 : 120)).isEmpty());
    }

    @Test
    void theGroundIsReadOncePerCellAndOnlyWhereTheLandCheckPasses() {
        AtomicInteger read = new AtomicInteger();
        SurfaceProbe counting = (x, z, from) -> {
            read.incrementAndGet();
            return Math.min(from, x < 5000 ? 120 : 70);
        };
        RavineSites sites = new RavineSites(CONE, LEVEL);
        sites.followGround(counting, SEA);
        sites.boundsOf(HOLE);
        sites.boundsOf(HOLE);
        assertEquals(RavineSites.RIM_PROBES, read.get());
        // A cell on low ground is given up after two columns.
        RavineCell low = RavineCells.at(7L, CONE, 6, 0).orElseThrow();
        assertTrue(low.centreX() > 5100);
        assertTrue(sites.boundsOf(low).isEmpty());
        assertEquals(RavineSites.RIM_PROBES + 2, read.get());
        sites.restrictToLand((x, z) -> false);
        assertTrue(sites.boundsOf(HOLE).isEmpty());
        assertEquals(RavineSites.RIM_PROBES + 2, read.get(), "an ocean cell's ground is not read");
    }

    @Test
    void lowGroundIsFoundWhereverItLiesRoundTheMouth() {
        // Two columns of low ground next to each other, or with one between them, at every place round the mouth: also where
        // the last column meets the first.
        int[] centre = {(int) Math.floor(HOLE.centreX()), (int) Math.floor(HOLE.centreZ())};
        for (int first = 0; first < RavineSites.RIM_PROBES; first++) {
            for (int apart = 1; apart <= 3; apart++) {
                int[] one = probe(first);
                int[] other = probe((first + apart) % RavineSites.RIM_PROBES);
                SurfaceProbe ground = ground((x, z) -> x == one[0] && z == one[1] || x == other[0] && z == other[1] ? 70 : 120);
                assertEquals(apart == 3, holeOn(ground).isPresent(), "low at columns " + first + " and " + apart + " on, centre " + centre[0]);
            }
        }
    }

    private static int[] probe(int index) {
        double angle = 2 * Math.PI * index / RavineSites.RIM_PROBES;
        return new int[] {(int) Math.floor(HOLE.centreX() + 45 * Math.cos(angle)), (int) Math.floor(HOLE.centreZ() + 45 * Math.sin(angle))};
    }

    @Test
    void aRavineKeepsTheLevelsBoundsWhateverTheGround() {
        RavineSites sites = new RavineSites(RAVINE, LEVEL);
        sites.followGround(ground((x, z) -> 20), SEA);
        assertEquals(Optional.of(LEVEL), sites.boundsOf(CELL));
    }
}

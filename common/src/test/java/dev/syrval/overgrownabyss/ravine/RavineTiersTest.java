package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RavineTiersTest {
    static final RavineTiers TIERS = new RavineTiers(3, 0.3F, 0.9F, 0.5F, 1.1F, 4);
    static final RavineWalls WALLS = withTiers(RavineCellsTest.WALLS, TIERS);
    static final RavineSettings SETTINGS = RavineShapeTest.withWalls(WALLS);
    static final RavineBounds BOUNDS = RavineShapeTest.BOUNDS;

    static RavineWalls withTiers(RavineWalls w, RavineTiers tiers) {
        return new RavineWalls(w.terraceStep(), w.terraceStrength(), w.terraceWarp(), w.noiseScale(), w.noiseAmplitude(),
                w.overhangScale(), w.overhangAmplitude(), w.strataHeight(), w.strataAmplitude(), w.strataScale(),
                w.widthWobble(), w.edgeFalloff(), tiers, w.discs());
    }

    @Test
    void levelsStayWithinLimitsAndConnected() {
        RavineSettings always = new RavineSettings(
                1L, 2048, 1F, 2F, SETTINGS.length(), SETTINGS.width(), SETTINGS.floor(), SETTINGS.top(), SETTINGS.bottomWidthFactor(),
                SETTINGS.cavernRadius(), SETTINGS.cavernHeight(), WALLS, SETTINGS.curvature(), SETTINGS.bridges(), SETTINGS.ledges(),
                SETTINGS.environment());
        boolean sawTiers = false;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                RavineCell c = RavineCells.at(11L, always, x, z).orElseThrow();
                List<RavineCell.Tiers.Level> levels = c.tiers().levels();
                double scale = c.halfLength() * 2 / 400;
                if (scale < TIERS.minSize()) {
                    assertEquals(1, levels.size(), "a small ravine keeps one level");
                }
                assertTrue(levels.size() <= TIERS.maxCount() + 1);
                assertEquals(RavineCell.Tiers.Level.NEUTRAL, levels.get(0));
                for (int i = 1; i < levels.size(); i++) {
                    RavineCell.Tiers.Level below = levels.get(i - 1);
                    RavineCell.Tiers.Level above = levels.get(i);
                    sawTiers = true;
                    assertTrue(above.start() > below.start() && above.start() < 1);
                    assertTrue(Math.abs(above.shift()) <= TIERS.maxShift() + 1e-9);
                    assertTrue(above.width() >= TIERS.minWidth() - 1e-9 && above.width() <= TIERS.maxWidth() + 1e-9);
                    // The two openings share some width, so the shaft is one passage and not separate pockets.
                    assertTrue(Math.abs(above.shift() - below.shift()) <= below.width() + above.width() - 0.5 + 1e-9);
                }
            }
        }
        assertTrue(sawTiers);
    }

    @Test
    void anUpperLevelSlidesTheOpeningSideways() {
        RavineCell stepped = new RavineCell(0, 0, 1, 0, 150, 50, RavineCell.Bend.NONE, RavineCell.Lean.NONE, RavineCell.Wobble.NONE,
                List.of(), List.of(), new RavineCell.Tiers(List.of(
                        RavineCell.Tiers.Level.NEUTRAL, new RavineCell.Tiers.Level(0.5, 0.8, 0.6))));
        double roof = BOUNDS.floorY() + SETTINGS.cavernHeight();
        double low = roof + 5;
        double high = BOUNDS.topY() - 2;
        double halfWidth = RavineShape.halfWidthAt(SETTINGS, BOUNDS, stepped, high, 0);
        double side = 0.8 * halfWidth + 0.5 * halfWidth;
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, stepped, 0, high, side, 0) < 0, "upper level is open off to the left");
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, stepped, 0, low, side, 0) > 0, "lower level is rock there");
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, stepped, 0, low, 0, 0) < 0, "lower level keeps the centre line");
    }

    static final RavineDiscs DISCS = new RavineDiscs(110, 0.3F, 24, 70, 0.45F, 14, 12, 2.5F, 0.4F, 0.75F);
    static final RavineSettings WITH_DISCS = RavineShapeTest.withWalls(new RavineWalls(
            WALLS.terraceStep(), WALLS.terraceStrength(), WALLS.terraceWarp(), WALLS.noiseScale(), WALLS.noiseAmplitude(),
            WALLS.overhangScale(), WALLS.overhangAmplitude(), WALLS.strataHeight(), WALLS.strataAmplitude(), WALLS.strataScale(),
            WALLS.widthWobble(), WALLS.edgeFalloff(), TIERS, DISCS));

    @Test
    void discsGrowOnlyFromShelvesWithinTheirLimits() {
        RavineSettings always = new RavineSettings(
                1L, 2048, 1F, 2F, WITH_DISCS.length(), WITH_DISCS.width(), WITH_DISCS.floor(), WITH_DISCS.top(),
                WITH_DISCS.bottomWidthFactor(), WITH_DISCS.cavernRadius(), WITH_DISCS.cavernHeight(), WITH_DISCS.walls(),
                WITH_DISCS.curvature(), WITH_DISCS.bridges(), WITH_DISCS.ledges(), WITH_DISCS.environment());
        boolean sawDisc = false;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                RavineCell c = RavineCells.at(12L, always, x, z).orElseThrow();
                List<RavineCell.Tiers.Level> levels = c.tiers().levels();
                if (levels.size() == 1) {
                    assertTrue(c.discs().isEmpty(), "no levels, no discs");
                }
                for (RavineCell.Disc disc : c.discs()) {
                    sawDisc = true;
                    RavineCell.Tiers.Level lower = levels.get(disc.level() - 1);
                    RavineCell.Tiers.Level upper = levels.get(disc.level());
                    assertTrue(disc.side() * upper.shift() + upper.width() > disc.side() * lower.shift() + lower.width(),
                            "a disc stands on a shelf, never under a ceiling");
                    assertTrue(Math.abs(disc.yOffset()) <= DISCS.yJitter() + 1e-9);
                    assertTrue(disc.radius() >= DISCS.minRadius() - 1e-9 && disc.radius() <= DISCS.maxRadius() + 1e-9);
                    assertTrue(disc.offset() >= DISCS.minOffset() - 1e-9 && disc.offset() <= DISCS.maxOffset() + 1e-9);
                    assertTrue(Math.abs(disc.along()) <= c.halfLength() + 1e-9);
                }
                for (int level = 1; level < levels.size(); level++) {
                    int onLevel = level;
                    assertTrue(c.discs().stream().filter(d -> d.level() == onLevel).count() <= 10);
                }
            }
        }
        assertTrue(sawDisc);
    }

    @Test
    void aDiscIsARoundDomedRoomCutIntoTheWallOnAFlatFloor() {
        RavineCell.Disc disc = new RavineCell.Disc(1, 0, 1, 0, 30, 0.6);
        RavineCell cell = roomCell(disc);
        double roof = BOUNDS.floorY() + WITH_DISCS.cavernHeight();
        double floor = roof + 0.5 * (BOUNDS.topY() - roof);
        double wall = 1.4 * RavineShape.halfWidthAt(WITH_DISCS, BOUNDS, cell, floor, 0);
        double centre = wall + 0.6 * 30;
        assertTrue(distance(cell, 0, floor + 2, centre) < 0, "open in the middle of the room");
        assertTrue(distance(cell, 0, floor - 2, centre) > 0, "solid below the floor");
        assertTrue(distance(cell, 0, floor + 16, centre) > 0, "solid above the dome");
        assertTrue(distance(cell, 0, floor + 2, centre + 35) > 0, "solid beyond the radius");
        assertTrue(distance(cell, 0, floor + 2, centre - 25) < 0, "the mouth opens towards the chasm");
        assertTrue(distance(cell, 50, floor + 2, centre) > 0, "a round room: solid along the ravine too");
    }

    @Test
    void aRoomStaysUnderTheCeilingMargin() {
        RavineCell cell = roomCell(new RavineCell.Disc(1, 0, 1, 0, 70, 0.6));
        double roof = BOUNDS.floorY() + WITH_DISCS.cavernHeight();
        double floor = roof + 0.5 * (BOUNDS.topY() - roof);
        double centre = 1.4 * RavineShape.halfWidthAt(WITH_DISCS, BOUNDS, cell, floor, 0) + 0.6 * 70;
        double cap = BOUNDS.topY() - DISCS.ceilingMargin();
        assertTrue(distance(cell, 0, cap - 1, centre) < 0, "open just under the cap");
        assertTrue(distance(cell, 0, cap + 1, centre) > 0, "solid above it");
    }

    private static RavineCell roomCell(RavineCell.Disc disc) {
        return new RavineCell(0, 0, 1, 0, 150, 50, RavineCell.Bend.NONE, RavineCell.Lean.NONE, RavineCell.Wobble.NONE,
                List.of(), List.of(), new RavineCell.Tiers(List.of(
                        RavineCell.Tiers.Level.NEUTRAL, new RavineCell.Tiers.Level(0.5, 0.8, 0.6))),
                List.of(disc));
    }

    private static double distance(RavineCell cell, double x, double y, double z) {
        return RavineShape.signedDistance(WITH_DISCS, BOUNDS, cell, x, y, z, 0);
    }
}

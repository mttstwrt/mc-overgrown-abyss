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

    static final RavineDiscs DISCS = new RavineDiscs(70, 0.3F, 18, 42, 6, 2.5F, 0.45F, 12);
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
    void aDiscIsALevelPlateRootedInTheWallThatLeavesTheCentreLineOpen() {
        RavineCell.Disc disc = new RavineCell.Disc(1, 0, 1, 0, 40, 6);
        RavineCell cell = new RavineCell(0, 0, 1, 0, 150, 50, RavineCell.Bend.NONE, RavineCell.Lean.NONE, RavineCell.Wobble.NONE,
                List.of(), List.of(), new RavineCell.Tiers(List.of(
                        RavineCell.Tiers.Level.NEUTRAL, new RavineCell.Tiers.Level(0.5, 0.8, 0.6))),
                List.of(disc));
        double roof = BOUNDS.floorY() + WITH_DISCS.cavernHeight();
        double top = roof + 0.5 * (BOUNDS.topY() - roof);
        double wall = RavineShape.halfWidthAt(WITH_DISCS, BOUNDS, cell, top, 0);
        // Left wall is +z here (the frame's sideways axis), the plate's middle is 12 blocks inside it.
        double inside = wall + 12;
        assertTrue(RavineShape.discDistance(WITH_DISCS, BOUNDS, cell, 0, top - 1, inside) < 0, "rock inside the plate");
        assertTrue(RavineShape.discDistance(WITH_DISCS, BOUNDS, cell, 0, top + 1, inside) > 0, "level top");
        assertTrue(RavineShape.discDistance(WITH_DISCS, BOUNDS, cell, 0, top - 1, 0) > 0, "centre line stays clear");
        assertTrue(RavineShape.discDistance(WITH_DISCS, BOUNDS, cell, 0, top - 1, inside + 45) > 0, "nothing beyond the radius");
        assertTrue(RavineShape.discDistance(WITH_DISCS, BOUNDS, cell, 0, top - 30, inside) > 0, "nothing far below");
        double tip = wall - 0.45 * 2 * wall + 1;
        assertTrue(RavineShape.discDistance(WITH_DISCS, BOUNDS, cell, 0, top - 1, tip - 3) > 0, "reach is capped at max_reach of the opening");
    }
}

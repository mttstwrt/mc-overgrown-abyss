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
                w.widthWobble(), w.edgeFalloff(), tiers);
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
}

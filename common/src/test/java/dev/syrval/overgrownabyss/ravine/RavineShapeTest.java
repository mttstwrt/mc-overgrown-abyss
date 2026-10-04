package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RavineShapeTest {
    static final RavineSettings SETTINGS = RavineCellsTest.SETTINGS;
    static final RavineBounds BOUNDS = new RavineBounds(-40, 80);
    // Along the x axis, 300 long and 100 wide at the top.
    static final RavineCell CELL = new RavineCell(0, 0, 1, 0, 150, 50);

    private static double distance(RavineCell cell, double x, double y, double z) {
        return RavineShape.signedDistance(SETTINGS, BOUNDS, cell, x, y, z, 0);
    }

    @Test
    void openAboveTheFloorAndSolidBelowIt() {
        assertTrue(distance(CELL, 0, BOUNDS.floorY(), 0) < 0);
        assertEquals(Double.POSITIVE_INFINITY, distance(CELL, 0, BOUNDS.floorY() - 1, 0));
    }

    @Test
    void wallsSitAtTheHalfWidthAtTheTop() {
        assertEquals(-10, distance(CELL, 100, 200, 40), 1e-9);
        assertEquals(10, distance(CELL, 100, 200, 60), 1e-9);
        assertEquals(10, distance(CELL, 210, 200, 0), 1e-9);
    }

    @Test
    void shaftNarrowsTowardsTheFloor() {
        double top = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, BOUNDS.topY(), 0);
        double bottom = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, BOUNDS.floorY(), 0);
        assertEquals(50, top, 1e-9);
        assertEquals(50.0 * SETTINGS.bottomWidthFactor(), bottom, 1e-9);
    }

    @Test
    void terracesHoldWidthWithinAStepAndJumpBetweenSteps() {
        RavineSettings flat = withWalls(new RavineWalls(12, 1F, 0, 0.03F, 10, 0.012F, 24, 0.22F, 6));
        int stepStart = BOUNDS.floorY() + 24;
        double inside1 = RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 1, 0);
        double inside2 = RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 11, 0);
        double next = RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 12, 0);
        assertEquals(inside1, inside2, 1e-9);
        assertTrue(next > inside2 + 1, "each step should leave a ledge");
    }

    @Test
    void terraceShiftMovesWhereTheLedgesFall() {
        RavineSettings flat = withWalls(new RavineWalls(12, 1F, 8, 0.03F, 10, 0.012F, 24, 0.22F, 6));
        int stepStart = BOUNDS.floorY() + 24;
        // Unshifted, y = stepStart + 11 is the last block of a step; shifted up by 2 it is the first of the next.
        assertTrue(RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 11, 2) > RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 11, 0) + 1);
    }

    @Test
    void cavernHasAFlatFloorAndADomedRoof() {
        // 120 out from the centre across the ravine: outside the shaft but inside the 128 cavern radius at the floor.
        assertTrue(distance(CELL, 0, BOUNDS.floorY(), 120) < 0);
        assertTrue(distance(CELL, 0, BOUNDS.floorY() + 40, 120) > 0);
    }

    @Test
    void bendCarriesTheCentreLineAwayFromTheChord() {
        RavineCell bent = new RavineCell(0, 0, 1, 0, 150, 50, new RavineCell.Bend(60, 0), RavineCell.Lean.NONE, RavineCell.Wobble.NONE, List.of());
        // At the end of a 300 long ravine bent by 60 the centre line is 60 to the side: open in the bent one only.
        assertTrue(distance(bent, 150, 200, 60) < -49);
        assertTrue(distance(CELL, 150, 200, 60) > 0);
        // It still passes through the cell centre, above the cavern.
        assertEquals(distance(CELL, 0, 200, 0), distance(bent, 0, 200, 0), 1e-9);
    }

    @Test
    void leanShiftsTheShaftWithHeightAndIsZeroAtTheFloor() {
        RavineCell.Lean lean = new RavineCell.Lean(0, 1, 30, 0);
        RavineCell leaning = new RavineCell(0, 0, 1, 0, 0, 12, RavineCell.Bend.NONE, lean, RavineCell.Wobble.NONE, List.of());
        assertEquals(0, lean.shift(0), 1e-12);
        // A round hole. At the rim its centre has moved the full 30 in z.
        assertEquals(-12, distance(leaning, 0, BOUNDS.topY(), 30), 1e-9);
        assertTrue(distance(leaning, 0, BOUNDS.topY(), 0) > 0);
        // Halfway up (above the cavern roof at 8) it has moved 30 * 0.5^2.
        double y = BOUNDS.floorY() + 60;
        assertEquals(-RavineShape.halfWidthAt(SETTINGS, BOUNDS, leaning, y, 0), distance(leaning, 0, y, 7.5), 1e-9);
    }

    @Test
    void wallsSwellAndNarrowIndependently() {
        RavineCell.Side swelling = new RavineCell.Side(0.2, 50, 0);
        RavineCell wobbly = new RavineCell(0, 0, 1, 0, 150, 50, RavineCell.Bend.NONE, RavineCell.Lean.NONE,
                new RavineCell.Wobble(swelling, RavineCell.Side.NONE), List.of());
        double y = 200;
        // dirX = 1 puts positive z on the left.
        assertEquals(40 - 50 * (1 + swelling.at(y)), distance(wobbly, 0, y, 40), 1e-9);
        assertEquals(40 - 50, distance(wobbly, 0, y, -40), 1e-9);
    }

    @Test
    void aBridgeIsAnArchThatIsThinInTheMiddleAndThickAtTheWalls() {
        RavineCell bridged = new RavineCell(0, 0, 1, 0, 150, 50, RavineCell.Bend.NONE, RavineCell.Lean.NONE, RavineCell.Wobble.NONE,
                List.of(new RavineCell.Bridge(0, 0.5, 8, 6)));
        // Between the cavern roof (y 8) and 6 below the rim (74), halfway: 8 + 6 + 0.5 * (74 - 14) = 44.
        double centreY = 44;
        assertTrue(RavineShape.bridgeDistance(SETTINGS, BOUNDS, bridged, 0, centreY, 0) < 0);
        assertTrue(RavineShape.bridgeDistance(SETTINGS, BOUNDS, bridged, 0, centreY + 10, 0) > 0);
        assertTrue(RavineShape.bridgeDistance(SETTINGS, BOUNDS, bridged, 20, centreY, 0) > 0, "only 8 long along the ravine");
        // Lower surface at 38 near a wall but only 41 in the middle.
        assertTrue(RavineShape.bridgeDistance(SETTINGS, BOUNDS, bridged, 0, 38, 40) < 0);
        assertTrue(RavineShape.bridgeDistance(SETTINGS, BOUNDS, bridged, 0, 38, 0) > 0);
    }

    @Test
    void aRavineWithoutBridgesHasNoBridgeDistance() {
        assertEquals(Double.POSITIVE_INFINITY, RavineShape.bridgeDistance(SETTINGS, BOUNDS, CELL, 0, 44, 0));
    }

    @Test
    void theCentreLineStaysOpenFromFloorToRimForEveryRavine() {
        RavineSettings always = RavineCellsTest.settings(42L, 1F);
        for (int cx = -15; cx < 15; cx++) {
            for (int cz = -15; cz < 15; cz++) {
                RavineCell c = RavineCells.at(11L, always, cx, cz).orElseThrow();
                for (int y = BOUNDS.floorY(); y <= BOUNDS.topY() + 20; y += 5) {
                    double t = Math.clamp((y - BOUNDS.floorY()) / 120.0, 0, 1);
                    double shift = c.lean().shift(t);
                    double x = c.centreX() + shift * c.lean().dirX();
                    double z = c.centreZ() + shift * c.lean().dirZ();
                    assertTrue(RavineShape.signedDistance(always, BOUNDS, c, x, y, z, 0) < 0,
                            "closed at y=" + y + " in cell " + cx + "," + cz + " halfWidth " + c.halfWidth());
                }
            }
        }
    }

    @Test
    void nothingOpensBeyondTheReachThatCellsAreSizedFor() {
        RavineSettings always = RavineCellsTest.settings(42L, 1F);
        double reach = always.maxReach() - always.walls().maxNoiseDisplacement() - always.walls().edgeFalloff();
        for (int cx = -10; cx < 10; cx++) {
            for (int cz = -10; cz < 10; cz++) {
                RavineCell c = RavineCells.at(13L, always, cx, cz).orElseThrow();
                for (int y = BOUNDS.floorY(); y <= BOUNDS.topY() + 40; y += 10) {
                    for (int degrees = 0; degrees < 360; degrees += 15) {
                        double x = c.centreX() + reach * Math.cos(Math.toRadians(degrees));
                        double z = c.centreZ() + reach * Math.sin(Math.toRadians(degrees));
                        assertFalse(RavineShape.signedDistance(always, BOUNDS, c, x, y, z, 8) < 0,
                                "open beyond the reach in cell " + cx + "," + cz);
                    }
                }
            }
        }
    }

    @Test
    void cavernBiomeVolumeIsTheDomeGrownByTheMargin() {
        int floor = BOUNDS.floorY();
        // Dome radius is 128 and height 48 in the test settings.
        assertTrue(RavineShape.cavernContains(SETTINGS, BOUNDS, CELL, 0, floor + 1, 100, 4));
        assertTrue(RavineShape.cavernContains(SETTINGS, BOUNDS, CELL, 0, floor + 1, 131, 4), "margin reaches past the wall");
        assertTrue(!RavineShape.cavernContains(SETTINGS, BOUNDS, CELL, 0, floor + 1, 133, 4));
        assertTrue(RavineShape.cavernContains(SETTINGS, BOUNDS, CELL, 0, floor - 3, 0, 4), "margin reaches under the floor");
        assertTrue(!RavineShape.cavernContains(SETTINGS, BOUNDS, CELL, 0, floor - 5, 0, 4));
        assertTrue(RavineShape.cavernContains(SETTINGS, BOUNDS, CELL, 0, floor + 50, 0, 4), "margin reaches above the roof");
        assertTrue(!RavineShape.cavernContains(SETTINGS, BOUNDS, CELL, 0, floor + 53, 0, 4));
    }

    @Test
    void codecRoundTripsTheShippedCarveFile() throws Exception {
        try (var in = RavineShapeTest.class.getResourceAsStream("/data/overgrown_abyss/worldgen/density_function/ravine/carve.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("type");
            assertEquals("minecraft:lush_caves", json.getAsJsonObject("environment").get("cavern_biome").getAsString());
            RavineSettings parsed = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(2048, parsed.cellSize());
            assertEquals(0, parsed.length().minInclusive(), "the smallest ravine is a round hole");
            assertEquals(parsed, RavineSettings.MAP_CODEC.codec()
                    .parse(JsonOps.INSTANCE, RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, parsed).getOrThrow())
                    .getOrThrow());
        }
    }

    @Test
    void codecRejectsACellTooSmallForTheRavine() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, SETTINGS).getOrThrow().getAsJsonObject();
        json.addProperty("cell_size", 256);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void codecRejectsACellThatIsNotWholeChunks() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, SETTINGS).getOrThrow().getAsJsonObject();
        json.addProperty("cell_size", 2056);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void codecRejectsBridgesWhoseLowestHeightIsAboveTheHighest() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, SETTINGS).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("bridges").addProperty("min_height", 0.9);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
    }

    private static RavineSettings withWalls(RavineWalls walls) {
        return new RavineSettings(SETTINGS.salt(), SETTINGS.cellSize(), SETTINGS.chance(), SETTINGS.length(),
                SETTINGS.width(), SETTINGS.floor(), SETTINGS.top(), SETTINGS.bottomWidthFactor(),
                SETTINGS.cavernRadius(), SETTINGS.cavernHeight(), walls, SETTINGS.curvature(), SETTINGS.bridges(),
                SETTINGS.environment());
    }
}

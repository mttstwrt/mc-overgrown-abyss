package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RavineShapeTest {
    static final RavineSettings SETTINGS = RavineCellsTest.SETTINGS;
    /** The same ravine with the ceiling margin so large that no disc room fits: just the shaft and the cavern. */
    static final RavineSettings PLAIN = withDiscs(new RavineDiscs(40, 12, 22, 55, 0.45F, 14, 128, 0.4F, 0.75F, 4, 0.4F));
    static final RavineBounds BOUNDS = new RavineBounds(-40, 80);
    // Along the x axis, 300 long and 100 wide at the top.
    static final RavineCell CELL = new RavineCell(0, 0, 1, 0, 150, 50);

    private static double distance(RavineSettings settings, RavineCell cell, double x, double y, double z) {
        return RavineShape.signedDistance(settings, BOUNDS, cell, x, y, z);
    }

    @Test
    void openAboveTheFloorAndSolidBelowIt() {
        assertTrue(distance(PLAIN, CELL, 0, BOUNDS.floorY(), 0) < 0);
        assertEquals(Double.POSITIVE_INFINITY, distance(PLAIN, CELL, 0, BOUNDS.floorY() - 1, 0));
    }

    @Test
    void wallsSitAtTheHalfWidthAtTheTop() {
        assertEquals(-10, distance(PLAIN, CELL, 100, 200, 40), 1e-9);
        assertEquals(10, distance(PLAIN, CELL, 100, 200, 60), 1e-9);
        assertEquals(10, distance(PLAIN, CELL, 210, 200, 0), 1e-9);
    }

    @Test
    void shaftNarrowsTowardsTheFloor() {
        assertEquals(50, RavineShape.halfWidthAt(PLAIN, BOUNDS, CELL, BOUNDS.topY()), 1e-9);
        assertEquals(50.0 * PLAIN.bottomWidthFactor(), RavineShape.halfWidthAt(PLAIN, BOUNDS, CELL, BOUNDS.floorY()), 1e-9);
    }

    @Test
    void cavernHasAFlatFloorAndADomedRoof() {
        // 120 out from the centre across the ravine: outside the shaft but inside the 128 cavern radius at the floor.
        assertTrue(distance(PLAIN, CELL, 0, BOUNDS.floorY(), 120) < 0);
        assertTrue(distance(PLAIN, CELL, 0, BOUNDS.floorY() + 40, 120) > 0);
    }

    @Test
    void bendCarriesTheCentreLineAwayFromTheChord() {
        RavineCell bent = new RavineCell(0, 0, 1, 0, 150, 50, new RavineCell.Bend(60, 0), RavineCell.Lean.NONE);
        // At the end of a 300 long ravine bent by 60 the centre line is 60 to the side: open in the bent one only.
        assertTrue(distance(PLAIN, bent, 150, 200, 60) < -49);
        assertTrue(distance(PLAIN, CELL, 150, 200, 60) > 0);
        // It still passes through the cell centre, above the cavern.
        assertEquals(distance(PLAIN, CELL, 0, 200, 0), distance(PLAIN, bent, 0, 200, 0), 1e-9);
    }

    @Test
    void leanShiftsTheShaftWithHeightAndIsZeroAtTheFloor() {
        RavineCell.Lean lean = new RavineCell.Lean(0, 1, 30, 0);
        RavineCell leaning = new RavineCell(0, 0, 1, 0, 0, 12, RavineCell.Bend.NONE, lean);
        assertEquals(0, lean.shift(0), 1e-12);
        // A round hole. At the rim its centre has moved the full 30 in z.
        assertEquals(-12, distance(PLAIN, leaning, 0, BOUNDS.topY(), 30), 1e-9);
        assertTrue(distance(PLAIN, leaning, 0, BOUNDS.topY(), 0) > 0);
        // Halfway up (above the cavern roof at 8) it has moved 30 * 0.5^2.
        double y = BOUNDS.floorY() + 60;
        assertEquals(-RavineShape.halfWidthAt(PLAIN, BOUNDS, leaning, y), distance(PLAIN, leaning, 0, y, 7.5), 1e-9);
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
                    assertTrue(RavineShape.signedDistance(always, BOUNDS, c, x, y, z) < 0,
                            "closed at y=" + y + " in cell " + cx + "," + cz + " halfWidth " + c.halfWidth());
                }
            }
        }
    }

    @Test
    void nothingOpensBeyondTheReachThatCellsAreSizedFor() {
        RavineSettings always = RavineCellsTest.settings(42L, 1F);
        double reach = always.maxReach() - always.edgeFalloff();
        for (int cx = -10; cx < 10; cx++) {
            for (int cz = -10; cz < 10; cz++) {
                RavineCell c = RavineCells.at(13L, always, cx, cz).orElseThrow();
                for (int y = BOUNDS.floorY(); y <= BOUNDS.topY() + 40; y += 10) {
                    for (int degrees = 0; degrees < 360; degrees += 15) {
                        double x = c.centreX() + reach * Math.cos(Math.toRadians(degrees));
                        double z = c.centreZ() + reach * Math.sin(Math.toRadians(degrees));
                        assertFalse(RavineShape.signedDistance(always, BOUNDS, c, x, y, z) < 0,
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
    void codecRejectsDiscsWhoseMinimumRadiusExceedsTheirMaximum() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, SETTINGS).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("discs").addProperty("min_radius", 100);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
    }

    static RavineSettings withDiscs(RavineDiscs discs) {
        RavineSettings s = RavineCellsTest.SETTINGS;
        return new RavineSettings(s.salt(), s.cellSize(), s.chance(), s.sizeBias(), s.length(), s.width(), s.floor(), s.top(),
                s.bottomWidthFactor(), s.cavernRadius(), s.cavernHeight(), s.edgeFalloff(), s.curvature(), discs, s.environment());
    }
}

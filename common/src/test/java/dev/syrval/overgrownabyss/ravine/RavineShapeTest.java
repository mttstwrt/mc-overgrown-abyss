package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RavineShapeTest {
    static final RavineSettings SETTINGS = RavineCellsTest.SETTINGS;
    // Along the x axis, 300 long and 100 wide at the top.
    static final RavineBounds BOUNDS = new RavineBounds(-40, 80);
    static final RavineCell CELL = new RavineCell(0, 0, 1, 0, 150, 50);

    @Test
    void openAboveTheFloorAndSolidBelowIt() {
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, 0, BOUNDS.floorY(), 0) < 0);
        assertEquals(Double.POSITIVE_INFINITY, RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, 0, BOUNDS.floorY() - 1, 0));
    }

    @Test
    void wallsSitAtTheHalfWidthAtTheTop() {
        assertEquals(-10, RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, 100, 200, 40), 1e-9);
        assertEquals(10, RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, 100, 200, 60), 1e-9);
        assertEquals(10, RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, 210, 200, 0), 1e-9);
    }

    @Test
    void shaftNarrowsTowardsTheFloor() {
        double top = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, BOUNDS.topY());
        double bottom = RavineShape.halfWidthAt(SETTINGS, BOUNDS, CELL, BOUNDS.floorY());
        assertEquals(50, top, 1e-9);
        assertEquals(50.0 * SETTINGS.bottomWidthFactor(), bottom, 1e-9);
    }

    @Test
    void terracesHoldWidthWithinAStepAndJumpBetweenSteps() {
        RavineSettings flat = withTerrace(12, 1F);
        int stepStart = BOUNDS.floorY() + 24;
        double inside1 = RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 1);
        double inside2 = RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 11);
        double next = RavineShape.halfWidthAt(flat, BOUNDS, CELL, stepStart + 12);
        assertEquals(inside1, inside2, 1e-9);
        assertTrue(next > inside2 + 1, "each step should leave a ledge");
    }

    @Test
    void cavernHasAFlatFloorAndADomedRoof() {
        // 120 out from the centre across the ravine: outside the shaft but inside the 128 cavern radius at the floor.
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, 0, BOUNDS.floorY(), 120) < 0);
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, CELL, 0, BOUNDS.floorY() + 40, 120) > 0);
        assertEquals(-8, RavineShape.horizontalDistance(SETTINGS, CELL, 0, 120), 1e-9);
    }

    @Test
    void codecRoundTripsTheShippedCarveFile() throws Exception {
        try (var in = RavineShapeTest.class.getResourceAsStream("/data/overgrown_abyss/worldgen/density_function/ravine/carve.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("type");
            RavineSettings parsed = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(2048, parsed.cellSize());
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

    private static RavineSettings withTerrace(int step, float strength) {
        return new RavineSettings(SETTINGS.salt(), SETTINGS.cellSize(), SETTINGS.chance(), SETTINGS.length(),
                SETTINGS.width(), SETTINGS.floor(), SETTINGS.top(), SETTINGS.bottomWidthFactor(),
                SETTINGS.cavernRadius(), SETTINGS.cavernHeight(), step, strength, SETTINGS.wallNoiseScale(),
                SETTINGS.wallNoiseAmplitude(), SETTINGS.edgeFalloff());
    }
}

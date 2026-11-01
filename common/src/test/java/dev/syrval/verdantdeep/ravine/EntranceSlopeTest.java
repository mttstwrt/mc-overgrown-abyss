package dev.syrval.verdantdeep.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntranceSlopeTest {
    static {
        MinecraftBootstrap.init();
    }

    private static final DiscLayout EMPTY = List::of;
    private static final RavineBounds BOUNDS = new RavineBounds(-40, 160);

    private static ConeSettings shippedCone() throws IOException {
        try (var reader = new InputStreamReader(EntranceSlopeTest.class.getResourceAsStream(
                "/data/verdant_deep/worldgen/density_function/ravine/carve.json"), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            return ConeSettings.CODEC.parse(JsonOps.INSTANCE, json.get("cone")).getOrThrow();
        }
    }

    private static double distance(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell,
            double radius, double y, double angle) {
        return ConeShape.signedDistance(settings, cone, bounds, cell, EMPTY,
                cell.centreX() + radius * Math.cos(angle), y, cell.centreZ() + radius * Math.sin(angle));
    }

    @Test
    void theShippedEntranceVariesItsJoinAroundTheUpperPlatformsWithoutMovingThem() throws IOException {
        ConeSettings cone = shippedCone();
        RavineSettings settings = ConeShapeTest.withCone(cone, DiscTest.VARIED);
        RavineCell cell = RavineCells.at(7L, settings, 0, 0).orElseThrow();
        ConeDiscLayout layout = new ConeDiscLayout(settings, cone, BOUNDS, cell);
        double upperFloor = layout.nominalFloor(layout.layers() - 1);
        double bottom = ConeShape.entranceFloor(cone, BOUNDS, cell);
        assertTrue(bottom >= upperFloor - cone.layerSpacing() && bottom <= upperFloor + 12,
                "the slope ends near the upper platforms, with at most a short drop");
        double join = ConeShape.radiusAt(settings, cone, BOUNDS, bottom);
        for (int degrees = 0; degrees < 360; degrees += 10) {
            double angle = Math.toRadians(degrees);
            assertTrue(distance(settings, cone, BOUNDS, cell, join + 20, Math.max(upperFloor, bottom + 8), angle) < 0,
                    "terrain above the local join is opened by the approach");
            assertTrue(distance(settings, cone, BOUNDS, cell, join + 20, bottom - 1, angle) > 0,
                    "the lower chasm wall is retained");
        }
    }

    @Test
    void theApproachWidensGraduallyAndStaysInsideTheFootprint() throws IOException {
        ConeSettings cone = shippedCone();
        RavineSettings settings = ConeShapeTest.withCone(cone, DiscTest.VARIED);
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        double bottom = ConeShape.entranceFloor(cone, BOUNDS, cell);
        for (int degrees = 0; degrees < 360; degrees += 15) {
            double angle = Math.toRadians(degrees);
            double previous = 0;
            for (double y = bottom + 2; y <= BOUNDS.topY() + 12; y += 2) {
                double radius = 0;
                while (distance(settings, cone, BOUNDS, cell, radius, y, angle) < 0) {
                    radius++;
                }
                assertTrue(radius >= previous, "the approach widens towards the surface");
                assertTrue(previous == 0 || radius - previous <= 12, "no abrupt horizontal shelf");
                assertTrue(radius < cone.outerRadius(), "the approach meets rock before the footprint boundary");
                previous = radius;
            }
        }
    }

    @Test
    void rootAnchorsFollowTheLoweredWallAndColumnsUnderOpenSkyHaveNoCeiling() throws IOException {
        ConeSettings cone = shippedCone();
        RavineSettings settings = ConeShapeTest.withCone(cone, DiscTest.VARIED);
        RavineCell cell = RavineCells.at(13L, settings, 0, 0).orElseThrow();
        double y = BOUNDS.topY() - 20;
        double wall = ConeShape.wallRadiusAt(settings, cone, BOUNDS, cell, cell.centreX() + 1, cell.centreZ(), y);
        assertTrue(wall > cone.topRadius() + 30, "root attachment follows the broadened entrance");
        assertTrue(distance(settings, cone, BOUNDS, cell, wall - 1, y, 0) < 0);
        assertTrue(distance(settings, cone, BOUNDS, cell, wall + 1, y, 0) > 0);
        assertTrue(ConeShape.wallOver(settings, cone, BOUNDS, cell, cell.centreX() + 50, cell.centreZ()).isEmpty(),
                "a column opened into the entrance cannot hold a hanging root");
    }

    @Test
    void omittingDepthKeepsExistingPacksAndRejectsNegativeDepth() throws IOException {
        var json = ConeSettings.CODEC.encodeStart(JsonOps.INSTANCE, shippedCone()).getOrThrow().getAsJsonObject();
        var collar = json.getAsJsonObject("rim").getAsJsonObject("collar");
        collar.remove("depth");
        assertEquals(0, ConeSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().rim().orElseThrow().collar().depth());
        collar.addProperty("depth", -1);
        assertTrue(ConeSettings.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }
}

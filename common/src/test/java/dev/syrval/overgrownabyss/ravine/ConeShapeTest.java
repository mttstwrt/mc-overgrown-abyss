package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.util.InclusiveRange;
import org.junit.jupiter.api.Test;

class ConeShapeTest {
    static {
        MinecraftBootstrap.init();
    }

    static final ConeSettings CONE = new ConeSettings(45, 1.6F, 8, 26, 0.3F, 80, 18, 40, 6, 0.22F, 2.5F, 1.5F, 0.4F, 14, 8);
    static final RavineBounds BOUNDS = new RavineBounds(-104, 80);

    static RavineSettings withCone(ConeSettings cone) {
        RavineSettings s = RavineCellsTest.settings(42L, 1F);
        return new RavineSettings(s.salt(), s.cellSize(), 1F, s.sizeBias(), new InclusiveRange<>(0, 0), s.width(), s.floor(), s.top(),
                s.bottomWidthFactor(), 136, 56, s.edgeFalloff(), s.curvature(), s.discs(), s.environment(), Optional.of(cone));
    }

    static final RavineSettings SETTINGS = withCone(CONE);

    // The carve and the rock are separate functions: a point is solid if the carve leaves it or the rock adds it.
    private static boolean solid(RavineCell c, double x, double y, double z) {
        return RavineShape.signedDistance(SETTINGS, BOUNDS, c, x, y, z) > 0 || RavineShape.rockDistance(SETTINGS, BOUNDS, c, x, y, z) < 0;
    }

    private static RavineCell cell(int cx, int cz) {
        return RavineCells.at(7L, SETTINGS, cx, cz).orElseThrow();
    }

    @Test
    void aClearLineOfSightRunsStraightDownTheMiddleOfEveryCone() {
        for (int cx = -6; cx < 6; cx++) {
            for (int cz = -6; cz < 6; cz++) {
                RavineCell c = cell(cx, cz);
                for (int y = BOUNDS.floorY(); y <= BOUNDS.topY() + 20; y += 3) {
                    for (double r = 0; r < CONE.clearRadius(); r += 2) {
                        assertFalse(solid(c, c.centreX() + r, y, c.centreZ()), "blocked at y=" + y + " r=" + r + " in cell " + cx + "," + cz);
                    }
                }
            }
        }
    }

    @Test
    void theConeIsAsWideAsTheCavernAtTheFloorAndNarrowsToItsTopRadius() {
        RavineCell c = cell(0, 0);
        double floor = BOUNDS.floorY() + 1;
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, c, c.centreX() + 133, floor, c.centreZ()) < 0);
        assertTrue(RavineShape.signedDistance(SETTINGS, BOUNDS, c, c.centreX() + 139, floor, c.centreZ()) > 0);
        double top = BOUNDS.topY();
        assertEquals(CONE.topRadius(), ConeShape.radiusAt(SETTINGS, CONE, BOUNDS, top), 1e-9);
        assertEquals(136, ConeShape.radiusAt(SETTINGS, CONE, BOUNDS, BOUNDS.floorY()), 1e-9);
        double previous = Double.MAX_VALUE;
        for (double y = BOUNDS.floorY(); y <= top; y += 4) {
            double radius = ConeShape.radiusAt(SETTINGS, CONE, BOUNDS, y);
            assertTrue(radius <= previous + 1e-9, "never widens going up");
            previous = radius;
        }
    }

    @Test
    void nothingOpensBeyondTheReachThatCellsAreSizedFor() {
        double reach = SETTINGS.maxReach() - SETTINGS.edgeFalloff() + 0.01;
        for (int cx = -5; cx < 5; cx++) {
            for (int cz = -5; cz < 5; cz++) {
                RavineCell c = cell(cx, cz);
                for (int y = BOUNDS.floorY(); y <= BOUNDS.topY() + 40; y += 8) {
                    for (int degrees = 0; degrees < 360; degrees += 30) {
                        double x = c.centreX() + reach * Math.cos(Math.toRadians(degrees));
                        double z = c.centreZ() + reach * Math.sin(Math.toRadians(degrees));
                        assertFalse(RavineShape.signedDistance(SETTINGS, BOUNDS, c, x, y, z) < 0, "open beyond the reach");
                    }
                }
            }
        }
    }

    @Test
    void structuresHaveFlatTopsAndSolidCapsAndStayOutsideTheClearCylinder() {
        int sampled = 0;
        int solidCount = 0;
        int flat = 0;
        for (int cz = 1; cz < 4; cz++) {
            RavineCell c = cell(1, cz);
            int top = ConeStructures.layers(SETTINGS, CONE, BOUNDS) - 1;
            for (int slot = 0; slot < ConeStructures.slots(SETTINGS, CONE, BOUNDS, top); slot++) {
                var found = ConeStructures.at(SETTINGS, CONE, BOUNDS, c, top, slot);
                if (found.isEmpty()) {
                    continue;
                }
                ConeStructures.Structure s = found.get();
                double centre = Math.hypot(s.x() - c.centreX(), s.z() - c.centreZ());
                assertTrue(centre - s.radius() >= CONE.clearRadius() - 1e-9, "outside the clear cylinder");
                // Points on the cap's inner side, which are inside the cone, at several distances from its axis.
                for (double share : new double[] {0.2, 0.5, 0.8}) {
                    double x = s.x() + (c.centreX() - s.x()) / centre * share * s.radius();
                    double z = s.z() + (c.centreZ() - s.z()) / centre * share * s.radius();
                    sampled++;
                    solidCount += solid(c, x, s.top() - 0.5, z) ? 1 : 0;
                    flat += solid(c, x, s.top() + 0.5, z) ? 0 : 1;
                }
            }
        }
        assertTrue(sampled > 10, "sampled " + sampled);
        assertEquals(sampled, solidCount, "the cap is solid right up to its flat top");
        // Neighbouring caps in the same layer may overlap a point, so a flat top is only open above where nothing else is.
        assertTrue(flat > sampled * 0.7, flat + " of " + sampled + " are open just above the top");
    }

    @Test
    void aStackedStructureHasItsStemLandOnTheCapBelowIt() {
        ConeSettings stacking = new ConeSettings(45, 1.6F, 8, 26, 0F, 80, 18, 40, 6, 0.22F, 2.5F, 1.5F, 1F, 14, 8);
        RavineSettings settings = withCone(stacking);
        RavineCell c = RavineCells.at(7L, settings, 2, 2).orElseThrow();
        int landed = 0;
        for (int layer = 1; layer < ConeStructures.layers(settings, stacking, BOUNDS); layer++) {
            for (int slot = 0; slot < ConeStructures.slots(settings, stacking, BOUNDS, layer); slot++) {
                var upper = ConeStructures.at(settings, stacking, BOUNDS, c, layer, slot);
                var lower = ConeStructures.at(settings, stacking, BOUNDS, c, layer - 1, slot);
                if (upper.isEmpty() || lower.isEmpty()) {
                    continue;
                }
                double bottom = ConeStructures.bottomOf(settings, stacking, BOUNDS, c, upper.get());
                if (bottom > Double.NEGATIVE_INFINITY) {
                    assertTrue(bottom >= lower.get().top() - stacking.capThickness() - 1e-9 || bottom > lower.get().top() - 40, "lands on a cap below");
                    landed++;
                }
            }
        }
        assertTrue(landed > 3, "landed " + landed);
    }

    @Test
    void theConeBlockRoundTripsAndRejectsAClearRadiusThatLeavesNoRoom() {
        assertEquals(CONE, ConeSettings.CODEC.parse(JsonOps.INSTANCE, ConeSettings.CODEC.encodeStart(JsonOps.INSTANCE, CONE).getOrThrow()).getOrThrow());
        var json = ConeSettings.CODEC.encodeStart(JsonOps.INSTANCE, CONE).getOrThrow().getAsJsonObject();
        json.addProperty("clear_radius", 50);
        assertTrue(ConeSettings.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }
}

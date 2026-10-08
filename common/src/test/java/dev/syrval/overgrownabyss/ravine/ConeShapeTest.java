package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import org.junit.jupiter.api.Test;

class ConeShapeTest {
    static {
        MinecraftBootstrap.init();
    }

    static final ConeSettings CONE = new ConeSettings(45, 136, 1.6F, 8, 26, 0.3F, 80, 0.4F, 14, 8, 0F, 200F, 0F, 0.6F, Optional.empty(), Optional.empty());
    static final DiscShape SHAPE = new DiscShape(18, 40, 0.45F, 14, 6, 0.22F, 2.5F, 32F, 1.5F, 3F, 4F, 0F, 0F, 0F);
    static final RavineBounds BOUNDS = new RavineBounds(-104, 80);

    static RavineSettings withCone(ConeSettings cone) {
        return withCone(cone, SHAPE);
    }

    static RavineSettings withCone(ConeSettings cone, DiscShape shape) {
        return withCone(cone, shape, WallNoise.NONE);
    }

    static RavineSettings withCone(ConeSettings cone, DiscShape shape, WallNoise wallNoise) {
        RavineSettings s = RavineCellsTest.settings(42L, 1F);
        return new RavineSettings(s.salt(), s.cellSize(), 1F, s.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                136, 56, s.edgeFalloff(), shape, s.discThemes(), s.environment(), wallNoise, Optional.empty(), Optional.of(cone));
    }

    static final RavineSettings SETTINGS = withCone(CONE);
    /** The cone the mod ships: discs down into the cavern's airspace and out past the cone, riders, thin stems, varied bowls. */
    static final ConeSettings LOW = new ConeSettings(45, 136, 1.6F, 8, 20, 0.3F, 56, 0.4F, 16, -16, 0.35F, 200F, 0.6F, 0.6F, Optional.empty(), Optional.empty());
    static final RavineSettings LOW_SETTINGS = withCone(LOW, DiscTest.VARIED);
    /** The same with a rider at every place that can hold one, and with none. */
    static final ConeSettings RIDDEN = new ConeSettings(45, 136, 1.6F, 8, 20, 0.3F, 56, 0.4F, 16, -16, 0.35F, 200F, 1F, 0.6F, Optional.empty(), Optional.empty());
    static final ConeSettings UNRIDDEN = new ConeSettings(45, 136, 1.6F, 8, 20, 0.3F, 56, 0.4F, 16, -16, 0.35F, 200F, 0F, 0.6F, Optional.empty(), Optional.empty());
    /** A level of vanilla's height, where the cavern is wider than the cone just under its roof. */
    static final RavineBounds VANILLA = new RavineBounds(-40, 80);
    /** The same cone with every disc drawn to hang. */
    static final ConeSettings HANGING = new ConeSettings(45, 136, 1.6F, 8, 26, 0.3F, 80, 0.4F, 14, 8, 1F, 200F, 0F, 0.6F, Optional.empty(), Optional.empty());

    private static RavineCell cell(int cx, int cz) {
        return RavineCells.at(7L, SETTINGS, cx, cz).orElseThrow();
    }

    private static List<Disc> discs(RavineSettings settings, ConeSettings cone, RavineCell c) {
        ConeDiscLayout layout = new ConeDiscLayout(settings, cone, BOUNDS, c);
        var discs = new ArrayList<Disc>();
        for (int layer = 0; layer < layout.layers(); layer++) {
            for (int slot = 0; slot < layout.slots(layer); slot++) {
                layout.at(layer, slot).ifPresent(discs::add);
            }
        }
        return discs;
    }

    @Test
    void aClearLineOfSightRunsStraightDownTheMiddleOfEveryCone() {
        assertAClearLineOfSight(SETTINGS, 6);
    }

    @Test
    void hangingDiscsLeaveTheLineOfSightClear() {
        assertAClearLineOfSight(withCone(HANGING), 3);
    }

    private static void assertAClearLineOfSight(RavineSettings settings, int cells) {
        for (int cx = -cells; cx < cells; cx++) {
            for (int cz = -cells; cz < cells; cz++) {
                RavineCell c = RavineCells.at(7L, settings, cx, cz).orElseThrow();
                for (int y = BOUNDS.floorY(); y <= BOUNDS.topY() + 20; y += 3) {
                    for (double r = 0; r < CONE.clearRadius(); r += 2) {
                        assertFalse(Carved.solid(settings, BOUNDS, c, c.centreX() + r, y, c.centreZ()), "blocked at y=" + y + " r=" + r + " in cell " + cx + "," + cz);
                    }
                }
            }
        }
    }

    @Test
    void theConeIsAsWideAsTheCavernAtTheFloorAndNarrowsToItsTopRadius() {
        RavineCell c = cell(0, 0);
        double floor = BOUNDS.floorY() + 1;
        assertTrue(Carved.distance(SETTINGS, BOUNDS, c, c.centreX() + 133, floor, c.centreZ()) < 0);
        assertTrue(Carved.distance(SETTINGS, BOUNDS, c, c.centreX() + 139, floor, c.centreZ()) > 0);
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
                        assertFalse(Carved.distance(SETTINGS, BOUNDS, c, x, y, z) < 0, "open beyond the reach");
                    }
                }
            }
        }
    }

    @Test
    void discsHaveFlatPlatformsAndStayOutsideTheClearCylinder() {
        int sampled = 0;
        int solid = 0;
        int flat = 0;
        for (int cz = 1; cz < 4; cz++) {
            RavineCell c = cell(1, cz);
            ConeDiscLayout layout = new ConeDiscLayout(SETTINGS, CONE, BOUNDS, c);
            int top = layout.layers() - 1;
            for (int slot = 0; slot < layout.slots(top); slot++) {
                var found = layout.at(top, slot);
                if (found.isEmpty()) {
                    continue;
                }
                Disc d = found.get();
                double centre = Math.hypot(d.x() - c.centreX(), d.z() - c.centreZ());
                assertTrue(centre - d.radius() >= CONE.clearRadius() - 1e-9, "outside the clear cylinder");
                // Points on the platform's inner side, which are inside the cone, at several distances from its axis.
                for (double share : new double[] {0.2, 0.5, 0.8}) {
                    double x = d.x() + (c.centreX() - d.x()) / centre * share * d.radius();
                    double z = d.z() + (c.centreZ() - d.z()) / centre * share * d.radius();
                    sampled++;
                    solid += Carved.solid(SETTINGS, BOUNDS, c, x, d.floor() - 0.5, z) ? 1 : 0;
                    flat += Carved.solid(SETTINGS, BOUNDS, c, x, d.floor() + 0.5, z) ? 0 : 1;
                }
            }
        }
        assertTrue(sampled > 10, "sampled " + sampled);
        assertEquals(sampled, solid, "the platform is solid right up to its flat top");
        // Neighbouring platforms in the same layer may overlap a point, so a flat top is only open above where nothing else is.
        assertTrue(flat > sampled * 0.7, flat + " of " + sampled + " are open just above the top");
    }

    @Test
    void aDiscNearTheWallCutsItsDomeIntoTheWallAndTheDomeStaysClear() {
        int checked = 0;
        for (int cz = 1; cz < 5; cz++) {
            RavineCell c = cell(1, cz);
            List<Disc> all = discs(SETTINGS, CONE, c);
            for (Disc d : all) {
                double wall = ConeShape.radiusAt(SETTINGS, CONE, BOUNDS, d.floor() + 3);
                double centre = Math.hypot(d.x() - c.centreX(), d.z() - c.centreZ());
                if (centre < wall + 4) {
                    continue;
                }
                // The centre is inside the rock past the wall: only the disc's dome opens it.
                double y = d.floor() + 3;
                boolean otherRock = false;
                for (Disc other : all) {
                    otherRock |= other != d && (other.platformDistance(SHAPE, d.x(), y, d.z()) < 0 || other.stemDistance(SHAPE, d.x(), y, d.z()) < 0);
                }
                if (otherRock) {
                    continue;
                }
                assertTrue(Carved.distance(SETTINGS, BOUNDS, c, d.x(), y, d.z()) < 0, "dome opens the wall at " + d);
                assertFalse(Carved.solid(SETTINGS, BOUNDS, c, d.x(), y, d.z()));
                checked++;
            }
        }
        assertTrue(checked > 3, "checked " + checked);
    }

    @Test
    void aStemRunsDownThroughTheCityDomeToTheFloorWithNothingBelowIt() {
        int checked = 0;
        for (int cz = 1; cz < 5; cz++) {
            RavineCell c = cell(1, cz);
            ConeDiscLayout layout = new ConeDiscLayout(SETTINGS, CONE, BOUNDS, c);
            for (Disc d : discs(SETTINGS, CONE, c)) {
                if (Discs.bottomOf(layout.discs(), SHAPE, d) != Double.NEGATIVE_INFINITY) {
                    continue;
                }
                // Inside the cavern's dome, where it used to be cut off.
                double y = BOUNDS.floorY() + 20;
                assertTrue(RavineShape.cavernContains(SETTINGS, BOUNDS, c, d.x(), y, d.z(), 0) || d.stemDistance(SHAPE, d.x(), y, d.z()) < 0);
                assertTrue(Carved.solid(SETTINGS, BOUNDS, c, d.x(), y, d.z()), "stem of " + d + " is solid at the floor of the cone");
                checked++;
            }
        }
        assertTrue(checked > 3, "checked " + checked);
    }

    @Test
    void aStackedDiscHasItsStemLandOnThePlatformBelowIt() {
        ConeSettings stacking = new ConeSettings(45, 136, 1.6F, 8, 26, 0F, 80, 1F, 14, 8, 0F, 200F, 0F, 0.6F, Optional.empty(), Optional.empty());
        RavineSettings settings = withCone(stacking);
        RavineCell c = RavineCells.at(7L, settings, 2, 2).orElseThrow();
        ConeDiscLayout layout = new ConeDiscLayout(settings, stacking, BOUNDS, c);
        int landed = 0;
        for (int layer = 1; layer < layout.layers(); layer++) {
            for (int slot = 0; slot < layout.slots(layer); slot++) {
                var upper = layout.at(layer, slot);
                var lower = layout.at(layer - 1, slot);
                if (upper.isEmpty() || lower.isEmpty()) {
                    continue;
                }
                double bottom = Discs.bottomOf(layout.discs(), SHAPE, upper.get());
                if (bottom > Double.NEGATIVE_INFINITY) {
                    assertTrue(bottom > lower.get().floor() - 40, "lands on a platform below");
                    landed++;
                }
            }
        }
        assertTrue(landed > 3, "landed " + landed);
    }

    @Test
    void theHeightOfTheWallIsTheInverseOfItsRadius() {
        for (double y = BOUNDS.floorY() + 1; y < BOUNDS.topY(); y += 7) {
            double radius = ConeShape.radiusAt(SETTINGS, CONE, BOUNDS, y);
            assertEquals(y, ConeShape.heightAt(SETTINGS, CONE, BOUNDS, radius).orElseThrow(), 1e-6);
        }
        assertTrue(ConeShape.heightAt(SETTINGS, CONE, BOUNDS, CONE.topRadius() - 1).isEmpty(), "open sky inside the top radius");
        assertEquals(BOUNDS.floorY(), ConeShape.heightAt(SETTINGS, CONE, BOUNDS, 140).orElseThrow(), "past the cavern the wall starts at the floor");
    }

    @Test
    void aDiscDrawnToHangHangsOnlyWhereItHasACeiling() {
        RavineSettings settings = withCone(HANGING);
        int underPlatforms = 0;
        int fromTheWall = 0;
        int standing = 0;
        for (int cz = 1; cz < 5; cz++) {
            RavineCell c = RavineCells.at(7L, settings, 1, cz).orElseThrow();
            List<Disc> all = new ConeDiscLayout(settings, HANGING, BOUNDS, c).discs();
            for (Disc d : all) {
                if (!(d.support() instanceof Disc.Support.Hanging root)) {
                    standing++;
                    continue;
                }
                assertTrue(root.anchor() - d.floor() >= SHAPE.minHeight() - 1e-9, "room to hang in");
                if (Discs.platformAbove(all, SHAPE, d).equals(OptionalDouble.of(root.anchor()))) {
                    assertEquals(root.anchor() + SHAPE.floorThickness() / 2, root.top(), 1e-9, "it runs on into the platform it hangs from");
                    underPlatforms++;
                } else {
                    double fromAxis = c.distanceToCentre(d.x(), d.z());
                    assertTrue(fromAxis > HANGING.topRadius(), "never under open sky");
                    assertTrue(ConeShape.radiusAt(settings, HANGING, BOUNDS, root.anchor()) <= fromAxis + 1e-6, "its axis has met the wall at the anchor");
                    assertTrue(root.anchor() <= BOUNDS.topY() - HANGING.ceilingMargin(), "not anchored in the last blocks under the surface");
                    fromTheWall++;
                }
            }
        }
        assertTrue(fromTheWall > 5, fromTheWall + " hang from the wall");
        assertTrue(underPlatforms + fromTheWall + standing > 20, "sampled " + (underPlatforms + fromTheWall + standing));
        assertTrue(standing > 0, "some have no ceiling and stand, " + underPlatforms + " hang under platforms");
    }

    @Test
    void noDiscHangsUnlessTheConeAsksForIt() {
        for (int cz = 1; cz < 5; cz++) {
            for (Disc d : new ConeDiscLayout(SETTINGS, CONE, BOUNDS, cell(1, cz)).discs()) {
                assertTrue(d.support() instanceof Disc.Support.Standing, "hangs with a chance of 0: " + d);
            }
        }
    }

    @Test
    void aRootIsSolidFromItsPlatformToItsAnchorAndTheDiscHasNoStem() {
        RavineSettings settings = withCone(HANGING);
        int checked = 0;
        for (int cz = 1; cz < 5; cz++) {
            RavineCell c = RavineCells.at(7L, settings, 1, cz).orElseThrow();
            for (Disc d : Carved.layout(settings, BOUNDS, c).discs()) {
                if (!(d.support() instanceof Disc.Support.Hanging root)) {
                    continue;
                }
                for (double y = d.floor() + 1; y < root.anchor(); y += 2) {
                    assertTrue(Carved.solid(settings, BOUNDS, c, d.x(), y, d.z()), "root of " + d + " is broken at y=" + y);
                }
                assertTrue(d.rockDistance(SHAPE, d.x(), d.floor() - SHAPE.floorThickness() - 3, d.z()) > 0, "a stem under " + d);
                checked++;
            }
        }
        assertTrue(checked > 5, "checked " + checked);
    }

    @Test
    void discsMayStandBelowTheCavernRoofButStayClearOfItsFloor() {
        ConeSettings high = new ConeSettings(45, 136, 1.6F, 8, 20, 0.3F, 56, 0.4F, 16, 4, 0.35F, 200F, 0.6F, 0.6F, Optional.empty(), Optional.empty());
        assertTrue(ConeDiscLayout.layers(LOW_SETTINGS, LOW, VANILLA) > ConeDiscLayout.layers(withCone(high, DiscTest.SLIM), high, VANILLA), "a lower start fits another layer");
        double roof = VANILLA.floorY() + LOW_SETTINGS.cavernHeight();
        int underTheRoof = 0;
        int all = 0;
        for (int cz = 1; cz < 5; cz++) {
            RavineCell c = RavineCells.at(7L, LOW_SETTINGS, 1, cz).orElseThrow();
            for (Disc d : new ConeDiscLayout(LOW_SETTINGS, LOW, VANILLA, c).discs()) {
                all++;
                underTheRoof += d.floor() < roof ? 1 : 0;
                assertTrue(d.floor() - DiscTest.SLIM.floorThickness() > VANILLA.floorY() + 30, "a platform down among the city's roofs: " + d);
            }
        }
        assertTrue(underTheRoof > 3, underTheRoof + " of " + all + " discs are below the cavern roof");
    }

    @Test
    void aDiscIsAsLargeAsItIsDrawnAndCarvesIntoTheRockAroundTheConeWhereItDoesNotFit() {
        RavineSettings settings = withCone(UNRIDDEN, DiscTest.VARIED);
        int widerThanTheCone = 0;
        int openedOutside = 0;
        double largest = 0;
        var bowls = new java.util.TreeSet<Long>();
        for (int cz = 1; cz < 6; cz++) {
            RavineCell c = RavineCells.at(7L, settings, 1, cz).orElseThrow();
            for (Disc d : new ConeDiscLayout(settings, UNRIDDEN, BOUNDS, c).discs()) {
                double fromAxis = c.distanceToCentre(d.x(), d.z());
                double wall = ConeShape.radiusAt(settings, UNRIDDEN, BOUNDS, d.floor());
                assertTrue(d.radius() <= DiscTest.VARIED.maxRadius() + 1e-6);
                assertTrue(fromAxis - d.radius() >= UNRIDDEN.clearRadius() - 1e-9, "its inner edge is outside the clear cylinder");
                assertTrue(fromAxis + d.radius() <= UNRIDDEN.outerRadius() + ConeDiscLayout.PLACING_SLACK, "and nothing of it is past the outer radius");
                largest = Math.max(largest, d.radius());
                bowls.add(Math.round(d.bowl() / DiscTest.SLIM.bowlDepthFor(d.radius(), 0) * 10));
                if (fromAxis + d.radius() > wall + 20) {
                    widerThanTheCone++;
                    // A point over the platform, well past the cone's wall: only this disc's dome can have opened it.
                    double out = fromAxis + 0.6 * d.radius();
                    double x = c.centreX() + (d.x() - c.centreX()) / fromAxis * out;
                    double z = c.centreZ() + (d.z() - c.centreZ()) / fromAxis * out;
                    double y = d.topAt(0.6 * d.radius()) + 2;
                    if (out > ConeShape.radiusAt(settings, UNRIDDEN, BOUNDS, y) + 4 && d.domeDistance(x, y, z) < 0) {
                        assertTrue(Carved.distance(settings, BOUNDS, c, x, y, z) < 0, "the dome of " + d + " did not open the rock outside the cone");
                        openedOutside++;
                    }
                }
            }
        }
        assertTrue(largest > 60, "largest radius " + largest);
        assertTrue(widerThanTheCone > 5, widerThanTheCone + " discs reach well past the cone's wall");
        assertTrue(openedOutside > 5, openedOutside + " domes checked outside the cone");
        assertTrue(bowls.size() > 3 && bowls.first() < 6 && bowls.last() > 8, "bowls come in different depths for their size: tenths " + bowls);
    }

    @Test
    void ridersStandOnLargerDiscsOutsideTheConeInsideTheirHostsDome() {
        RavineSettings settings = withCone(RIDDEN, DiscTest.VARIED);
        int riders = 0;
        int ringDiscs = 0;
        int ridersOfRiders = 0;
        for (int cz = 1; cz < 6; cz++) {
            RavineCell c = RavineCells.at(7L, settings, 1, cz).orElseThrow();
            List<Disc> all = new ConeDiscLayout(settings, RIDDEN, BOUNDS, c).discs();
            List<Disc> ring = new ConeDiscLayout(withCone(UNRIDDEN, DiscTest.VARIED), UNRIDDEN, BOUNDS, c).discs();
            ringDiscs += ring.size();
            assertTrue(all.size() <= ConeDiscLayout.MAX_DISCS);
            // The ring comes first and is the same with or without riders, apart from what each disc stands on or hangs from.
            for (int i = 0; i < ring.size(); i++) {
                assertEquals(ring.get(i).withSupport(Disc.Support.Standing.TO_THE_FLOOR), all.get(i).withSupport(Disc.Support.Standing.TO_THE_FLOOR));
            }
            for (Disc rider : all.subList(ring.size(), all.size())) {
                riders++;
                double fromAxis = c.distanceToCentre(rider.x(), rider.z());
                assertTrue(fromAxis > ConeShape.radiusAt(settings, RIDDEN, BOUNDS, rider.floor()) - 1, "a rider inside the cone: " + rider);
                assertTrue(fromAxis - rider.radius() >= RIDDEN.clearRadius() - 1e-9 && fromAxis + rider.radius() <= RIDDEN.outerRadius() + ConeDiscLayout.PLACING_SLACK);
                // Its host: a larger disc nearer the axis that has the rider in its dome, out towards its edge.
                Disc host = null;
                for (Disc other : all) {
                    boolean larger = other != rider && other.radius() * RIDDEN.riderScale() >= rider.radius() - 1e-9;
                    boolean nearerTheAxis = c.distanceToCentre(other.x(), other.z()) < fromAxis;
                    boolean towardsItsEdge = Math.hypot(rider.x() - other.x(), rider.z() - other.z()) > 0.35 * other.radius();
                    if (larger && nearerTheAxis && towardsItsEdge && other.domeDistance(rider.x(), rider.floor(), rider.z()) < 0.8) {
                        host = other;
                    }
                }
                assertTrue(host != null, "no larger disc nearer the axis has " + rider + " in the outer part of its dome");
                ridersOfRiders += all.indexOf(host) >= ring.size() ? 1 : 0;
                if (rider.support() instanceof Disc.Support.Standing stem) {
                    assertTrue(stem.bottom() > Double.NEGATIVE_INFINITY, "a standing rider's stem lands on a platform: " + rider);
                }
            }
        }
        assertTrue(riders > 10, riders + " riders on " + ringDiscs + " ring discs");
        assertTrue(ridersOfRiders > 0, "riders carry riders of their own");
    }

    @Test
    void nothingOpensBeyondTheReachWithDiscsOutsideTheCone() {
        RavineSettings settings = withCone(RIDDEN, DiscTest.VARIED);
        double reach = settings.maxReach() - settings.edgeFalloff() + 0.01;
        assertEquals(200 + ConeDiscLayout.PLACING_SLACK + settings.edgeFalloff(), settings.maxReach(), 1e-9, "the outer radius sets the reach when it is past the cavern");
        for (int cx = -3; cx < 3; cx++) {
            for (int cz = -3; cz < 3; cz++) {
                RavineCell c = RavineCells.at(7L, settings, cx, cz).orElseThrow();
                for (int y = BOUNDS.floorY(); y <= BOUNDS.topY() + 40; y += 6) {
                    for (int degrees = 0; degrees < 360; degrees += 10) {
                        double x = c.centreX() + reach * Math.cos(Math.toRadians(degrees));
                        double z = c.centreZ() + reach * Math.sin(Math.toRadians(degrees));
                        assertFalse(Carved.distance(settings, BOUNDS, c, x, y, z) < 0, "open beyond the reach");
                    }
                }
            }
        }
    }

    @Test
    void ridersLeaveTheLineOfSightClear() {
        assertAClearLineOfSight(withCone(RIDDEN, DiscTest.VARIED), 3);
    }

    @Test
    void anOuterRadiusWithNoRoomForADiscIsRejected() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, LOW_SETTINGS).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("cone").addProperty("outer_radius", 40);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void aLowPlatformIsRockWhereItStandsInTheCavernsAirspaceBeyondTheCone() {
        RavineCell c = RavineCells.at(7L, LOW_SETTINGS, 1, 1).orElseThrow();
        // A disc under the cavern roof, its centre near the cone's wall: its outer part is past the cone, in the cavern's air.
        Disc disc = Disc.placed(c.centreX() + 95, c.centreZ(), -3, 30, 14, 2);
        DiscLayout layout = () -> List.of(disc);
        // Far enough from the disc's axis that the flare under the platform does not reach the point below it.
        double x = c.centreX() + 102;
        double y = -4;
        double radial = c.distanceToCentre(x, c.centreZ());
        assertTrue(radial > ConeShape.radiusAt(LOW_SETTINGS, LOW, VANILLA, y), "the point is outside the cone");
        assertTrue(RavineShape.cavernDistance(LOW_SETTINGS, VANILLA, radial, y) < 0, "and in the cavern's air");
        assertTrue(disc.platformDistance(DiscTest.SLIM, x, y, c.centreZ()) < 0, "and in the platform");
        assertTrue(ConeShape.rockDistance(LOW_SETTINGS, LOW, VANILLA, c, layout, x, y, c.centreZ()) < 0, "so it is rock");
        assertTrue(ConeShape.rockDistance(LOW_SETTINGS, LOW, VANILLA, c, layout, x, y - 10, c.centreZ()) > 0, "and the air under the platform is not");
    }

    @Test
    void lowThinBowledDiscsLeaveTheLineOfSightClear() {
        assertAClearLineOfSight(LOW_SETTINGS, 3);
    }

    @Test
    void aConeWhoseLowestDiscsWouldReachTheCavernFloorIsRejected() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, LOW_SETTINGS).getOrThrow().getAsJsonObject();
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isSuccess());
        json.getAsJsonObject("cone").addProperty("base_clearance", -50);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void theConesBaseHasItsOwnRadiusApartFromTheCaverns() {
        ConeSettings wide = new ConeSettings(45, 150, 1.6F, 8, 20, 0.3F, 56, 0.4F, 16, -16, 0.35F, 200F, 0.8F, 0.6F, Optional.empty(), Optional.empty());
        RavineSettings settings = withCone(wide, DiscTest.VARIED);
        assertEquals(150, ConeShape.radiusAt(settings, wide, VANILLA, VANILLA.floorY()), 1e-9);
        assertEquals(45, ConeShape.radiusAt(settings, wide, VANILLA, VANILLA.topY()), 1e-9);
        assertTrue(ConeShape.radiusAt(settings, wide, VANILLA, 0) > ConeShape.radiusAt(LOW_SETTINGS, LOW, VANILLA, 0) + 5, "wider all the way up but for the top");
        RavineCell c = RavineCells.at(7L, settings, 1, 1).orElseThrow();
        // On the floor between the cavern's edge and the cone's: only the wider cone opens it.
        assertTrue(Carved.distance(settings, VANILLA, c, c.centreX() + 144, VANILLA.floorY() + 1, c.centreZ()) < 0, "the cone opens the ground past the cavern");
        assertTrue(Carved.distance(LOW_SETTINGS, VANILLA, RavineCells.at(7L, LOW_SETTINGS, 1, 1).orElseThrow(), c.centreX() + 144, VANILLA.floorY() + 1, c.centreZ()) > 0);
        assertEquals(136, settings.cavernRadius(), "the cavern keeps its own radius");
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, settings).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("cone").addProperty("base_radius", 40);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "a base narrower than the top");
    }

    @Test
    void theLogCanCountAConesLayersWithoutACell() {
        assertEquals(new ConeDiscLayout(SETTINGS, CONE, BOUNDS, cell(0, 0)).layers(), RavineShape.discRows(SETTINGS, BOUNDS));
        assertTrue(RavineShape.discRows(SETTINGS, BOUNDS) > 0);
    }

    @Test
    void theConeBlockRoundTripsAndRejectsAClearRadiusThatLeavesNoRoom() {
        assertEquals(CONE, ConeSettings.CODEC.parse(JsonOps.INSTANCE, ConeSettings.CODEC.encodeStart(JsonOps.INSTANCE, CONE).getOrThrow()).getOrThrow());
        var json = ConeSettings.CODEC.encodeStart(JsonOps.INSTANCE, CONE).getOrThrow().getAsJsonObject();
        json.addProperty("clear_radius", 50);
        assertTrue(ConeSettings.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }
}

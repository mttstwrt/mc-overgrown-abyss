package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.Test;

/** A cone whose top follows the ground: its lip, its layers, its domes under the ground, the bowl above the lip, the opening. */
class ConeRimTest {
    static {
        MinecraftBootstrap.init();
    }

    static final RimSettings.Collar COLLAR = new RimSettings.Collar(30, 34, 2, 2, 12);
    static final RimSettings RIM = new RimSettings(20, 3, 0.5F, 22, COLLAR);
    static final UpperSettings UPPER = new UpperSettings(20);
    /** The cone the mod ships. */
    static final ConeSettings CONE = new ConeSettings(
            45, 150, 1.6F, 8, 20, 0.3F, 56, 0.4F, 12, -16, 0F, 200F, 0.5F, 0.6F, Optional.of(RIM), Optional.of(UPPER));
    static final RavineSettings SETTINGS = ConeShapeTest.withCone(CONE, DiscTest.VARIED);
    /** A level of vanilla's height with the shipped floor and the highest a lip may be, 64 under the level's top. */
    static final RavineBounds LEVEL = new RavineBounds(-40, 255);
    private static final DiscShape SHAPE = DiscTest.VARIED;

    private static ConeSettings with(ConeSettings c, float ceilingMargin, float hangChance, Optional<RimSettings> rim, Optional<UpperSettings> upper) {
        return new ConeSettings(
                c.topRadius(), c.baseRadius(), c.flare(), c.clearRadius(), c.layerSpacing(), c.layerJitter(), c.spacing(), c.stackChance(),
                ceilingMargin, c.baseClearance(), hangChance, c.outerRadius(), c.riderChance(), c.riderScale(), rim, upper);
    }

    /** The shipped cone with its lip under the ground that this share of the mouth's edge lies below. */
    static RavineSettings withLowShare(float share) {
        RimSettings rim = new RimSettings(RIM.minAboveSea(), RIM.dip(), share, RIM.topRoom(), COLLAR);
        return ConeShapeTest.withCone(with(CONE, CONE.ceilingMargin(), CONE.hangChance(), Optional.of(rim), Optional.of(UPPER)), SHAPE);
    }

    private static RavineCell cell(int cx, int cz) {
        return RavineCells.at(7L, SETTINGS, cx, cz).orElseThrow();
    }

    private static RavineBounds lipAt(int lip) {
        return new RavineBounds(LEVEL.floorY(), lip);
    }

    @Test
    void onLevelGroundAtTheOldTopTheDiscsAreTheOnesOfAFixedTop() {
        // The shipped numbers before the rim: a ceiling margin of 16 under a top of 80, the top layer 24 under that.
        ConeSettings fixed = ConeShapeTest.LOW;
        ConeSettings following = with(fixed, 16, fixed.hangChance(), Optional.of(new RimSettings(0, 3, 0, 24, COLLAR)), Optional.empty());
        int discs = 0;
        for (int cz = 0; cz < 6; cz++) {
            RavineCell c = cell(1, cz);
            List<Disc> expected = new ConeDiscLayout(ConeShapeTest.LOW_SETTINGS, fixed, ConeShapeTest.VANILLA, c).discs();
            assertEquals(expected, new ConeDiscLayout(ConeShapeTest.withCone(following, SHAPE), following, ConeShapeTest.VANILLA, c).discs());
            discs += expected.size();
        }
        assertTrue(discs > 60, discs + " discs compared");
    }

    @Test
    void aHigherLipHoldsMoreLayersSpreadEvenlyUpToTheTopLayer() {
        int[] lips = {80, 96, 112, 128, 144, 160, 224, 255};
        int[] onVanillaHeight = {3, 4, 4, 5, 6, 7, 10, 12};
        // Larion's world bottom is at -128, which puts the floor at -104 and the lowest layer at -64.
        int[] onLarion = {6, 7, 8, 8, 9, 10, 13, 15};
        for (int i = 0; i < lips.length; i++) {
            assertEquals(onVanillaHeight[i], ConeDiscLayout.layers(SETTINGS, CONE, lipAt(lips[i])), "layers under a lip at " + lips[i]);
            assertEquals(onLarion[i], ConeDiscLayout.layers(SETTINGS, CONE, new RavineBounds(-104, lips[i])), "layers on Larion under a lip at " + lips[i]);
            ConeDiscLayout layout = new ConeDiscLayout(SETTINGS, CONE, lipAt(lips[i]), cell(0, 0));
            int top = layout.layers() - 1;
            assertEquals(0, layout.nominalFloor(0), 1e-9, "the lowest layer is where it was");
            assertEquals(lips[i] - CONE.ceilingMargin() - RIM.topRoom(), layout.nominalFloor(top), 1e-9, "the top layer has its room");
            double gap = layout.nominalFloor(1) - layout.nominalFloor(0);
            assertTrue(gap >= CONE.layerSpacing() && gap < 27, "layers " + gap + " apart under a lip at " + lips[i]);
            for (int layer = 1; layer <= top; layer++) {
                assertEquals(gap, layout.nominalFloor(layer) - layout.nominalFloor(layer - 1), 1e-9, "evenly spread");
            }
        }
    }

    @Test
    void underGroundAsHighAsTheLipNoDiscOfTheRingIsDropped() {
        for (int lip : new int[] {96, 117, 160}) {
            for (int cz = 0; cz < 4; cz++) {
                ConeDiscLayout layout = new ConeDiscLayout(SETTINGS, CONE, lipAt(lip), cell(2, cz));
                for (int layer = 0; layer < layout.layers(); layer++) {
                    for (int slot = 0; slot < layout.slots(layer); slot++) {
                        Optional<Disc> disc = layout.at(layer, slot);
                        assertTrue(disc.isPresent(), "layer " + layer + " slot " + slot + " under a lip at " + lip);
                        assertTrue(disc.get().floor() + disc.get().height() <= lip - CONE.ceilingMargin() + 1e-9, "stays under the ceiling margin");
                    }
                }
            }
        }
    }

    @Test
    void aTopRoomTooSmallForADomeIsRejected() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, SETTINGS).getOrThrow().getAsJsonObject();
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isSuccess());
        // The least dome is 14 and a platform may be 3 over its layer's height.
        json.getAsJsonObject("cone").getAsJsonObject("rim").addProperty("top_room", 16);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError());
        json.getAsJsonObject("cone").getAsJsonObject("rim").addProperty("top_room", 17);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isSuccess());
    }

    @Test
    void noDomeComesWithinTheCeilingMarginOfTheGroundOverIt() {
        int limited = 0;
        int dropped = 0;
        int all = 0;
        for (int cz = 0; cz < 5; cz++) {
            RavineCell c = cell(3, cz);
            // Level at 99 round the mouth, and falling away east of it by a block in two.
            SurfaceProbe ground = RavineSitesTest.ground((x, z) -> 99 - (int) Math.max(0, (x - c.centreX() - 45) / 2));
            RavineSites sites = new RavineSites(SETTINGS, LEVEL);
            sites.followGround(ground, RavineSitesTest.SEA);
            RavineBounds bounds = sites.boundsOf(c).orElseThrow();
            assertEquals(96, bounds.topY());
            List<Disc> discs = new ConeDiscLayout(SETTINGS, CONE, bounds, c, ground).discs();
            dropped += new ConeDiscLayout(SETTINGS, CONE, bounds, c).discs().size() - discs.size();
            for (Disc disc : discs) {
                all++;
                limited += disc.height() < Math.min(SHAPE.heightFor(disc.radius()), 96 - CONE.ceilingMargin() - disc.floor()) - 1e-9 ? 1 : 0;
                assertTrue(disc.height() >= SHAPE.minHeight(), "a dome too low to stand in: " + disc);
                for (double dx = -disc.radius(); dx <= disc.radius(); dx += 3) {
                    for (double dz = -disc.radius(); dz <= disc.radius(); dz += 3) {
                        double share = Math.hypot(dx, dz) / disc.radius();
                        if (share >= 1) {
                            continue;
                        }
                        double roof = disc.floor() + disc.height() * Math.sqrt(1 - share * share);
                        int over = ground.heightAt((int) Math.floor(disc.x() + dx), (int) Math.floor(disc.z() + dz), 400);
                        // The ground is read every 8 blocks, between which this ground falls by 4.
                        assertTrue(roof + CONE.ceilingMargin() <= over + 4.5, "the roof of " + disc + " at " + roof + " under ground at " + over);
                    }
                }
            }
        }
        // A dome has little between its full height and the least one, so most that the ground reaches are dropped, not lowered.
        assertTrue(limited + dropped > 5, limited + " of " + all + " domes were lowered under the falling ground and " + dropped + " discs dropped");
    }

    @Test
    void groundLowerThanAPlatformDropsTheDisc() {
        RavineCell c = cell(3, 0);
        // A cliff: everything further east of the mouth than 15 blocks is at sea level.
        SurfaceProbe cliff = RavineSitesTest.ground((x, z) -> x > c.centreX() + 60 ? 63 : 99);
        List<Disc> under = new ConeDiscLayout(SETTINGS, CONE, lipAt(96), c, cliff).discs();
        List<Disc> level = new ConeDiscLayout(SETTINGS, CONE, lipAt(96), c).discs();
        assertTrue(under.size() < level.size(), under.size() + " discs under the cliff, " + level.size() + " under level ground");
        for (Disc disc : under) {
            if (disc.x() - HoleGround.GRID > c.centreX() + 60) {
                assertTrue(disc.floor() + disc.height() <= 63 - CONE.ceilingMargin() + 1e-9, "a dome through the low ground: " + disc);
            }
        }
    }

    @Test
    void noRootIsAnchoredToAWallThatTheGroundDoesNotReach() {
        // Every disc drawn to hang, under a lip the ground east of the axis is 30 blocks lower than.
        ConeSettings hanging = with(CONE, 12, 1F, Optional.of(RIM), Optional.of(UPPER));
        RavineSettings settings = ConeShapeTest.withCone(hanging, SHAPE);
        int roots = 0;
        int underTheLowGround = 0;
        for (int cz = 0; cz < 6; cz++) {
            RavineCell c = cell(4, cz);
            SurfaceProbe ground = RavineSitesTest.ground((x, z) -> x > c.centreX() ? 80 : 140);
            for (Disc disc : new ConeDiscLayout(settings, hanging, lipAt(110), c, ground).discs()) {
                if (disc.support() instanceof Disc.Support.Hanging root) {
                    int over = ground.heightAt((int) Math.floor(disc.x()), (int) Math.floor(disc.z()), 110);
                    assertTrue(root.top() < over, "the root of " + disc + " rises out of ground at " + over);
                    roots++;
                    underTheLowGround += over == 80 ? 1 : 0;
                }
            }
        }
        assertTrue(roots > 40 && underTheLowGround > 10, roots + " roots, " + underTheLowGround + " of them under the low ground");
    }

    private static double carve(RavineBounds bounds, RavineCell c, double out, double y, double degrees) {
        double radial = CONE.topRadius() + out;
        double x = c.centreX() + radial * Math.cos(Math.toRadians(degrees));
        double z = c.centreZ() + radial * Math.sin(Math.toRadians(degrees));
        return Carved.distance(SETTINGS, bounds, c, x, y, z);
    }

    @Test
    void aboveItsLipAConeWithARimOpensAsABowlAndOneWithoutIsABore() {
        RavineBounds bounds = lipAt(117);
        RavineCell c = cell(0, 0);
        for (int degrees = 0; degrees < 360; degrees += 20) {
            assertTrue(carve(bounds, c, -1, 117 + 60, degrees) < 0, "open inside the mouth");
            assertTrue(carve(bounds, c, 5, 117 + 10, degrees) < 0, "open just outside the mouth, over the low inside of the bowl");
            assertTrue(carve(bounds, c, 25, 117 + 10, degrees) > 0, "solid under the bowl further out");
            assertTrue(carve(bounds, c, 25, 117 + 30, degrees) < 0, "open over it");
            assertTrue(carve(bounds, c, 60, 117 + 100, degrees) > 0, "the surface goes on rising past the collar's width");
            assertTrue(carve(bounds, c, 60, 117 + 140, degrees) < 0);
            assertTrue(carve(bounds, c, 2, 117 - 1, degrees) > 0, "under the lip the wall is the cone's");
        }
        ConeSettings bore = with(CONE, CONE.ceilingMargin(), CONE.hangChance(), Optional.empty(), Optional.empty());
        RavineSettings settings = ConeShapeTest.withCone(bore, SHAPE);
        assertTrue(Carved.distance(settings, bounds, c, c.centreX() + 44, 117 + 60, c.centreZ()) < 0);
        assertTrue(Carved.distance(settings, bounds, c, c.centreX() + 46, 117 + 60, c.centreZ()) > 0, "no bowl without a rim");
        assertTrue(Carved.distance(settings, bounds, c, c.centreX() + 46, 117 + 300, c.centreZ()) > 0);
    }

    // The lowest open block above the lip in a column that far out from the mouth.
    private static int bowlSurface(RavineBounds bounds, RavineCell c, double out, double degrees) {
        int y = bounds.topY() + 1;
        while (carve(bounds, c, out, y, degrees) > 0) {
            y++;
        }
        return y;
    }

    @Test
    void theBowlRisesAwayFromTheMouthWithoutAVerticalWall() {
        RavineBounds bounds = lipAt(117);
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(1, cz);
            for (int degrees = 0; degrees < 360; degrees += 45) {
                int previous = bowlSurface(bounds, c, 1, degrees);
                assertTrue(previous <= 117 + 1 + COLLAR.roughness() + 1, "the bowl starts at the lip, not " + (previous - 117) + " above it");
                // To the outer radius, 155 blocks out, where the collar is 900 blocks over the lip.
                for (int out = 2; out <= 154; out++) {
                    int surface = bowlSurface(bounds, c, out, degrees);
                    double rise = COLLAR.riseAt(out);
                    assertTrue(Math.abs(surface - (117 + rise)) <= COLLAR.roughness() + 1.5, "the surface " + out + " out is at " + surface + ", not near " + (117 + rise));
                    assertTrue(surface - previous <= COLLAR.slopeAt(out) + COLLAR.roughness() + 1.5, "a step of " + (surface - previous) + " blocks " + out + " out");
                    previous = surface;
                }
            }
        }
    }

    @Test
    void onLevelGroundTheBowlIsAShallowDipRoundTheMouth() {
        RavineBounds bounds = lipAt(117);
        RavineCell c = cell(0, 1);
        // Level ground is the dip above the lip. The collar reaches that height 9 blocks out, 12 where it is roughest.
        double ground = 117 + RIM.dip();
        for (int degrees = 0; degrees < 360; degrees += 5) {
            for (int out = 12; out <= 154; out += 2) {
                assertTrue(carve(bounds, c, out, ground, degrees) > 0, "level ground is cut " + out + " blocks out from the mouth");
            }
            assertTrue(carve(bounds, c, 3, ground, degrees) < 0, "and is opened right at the mouth");
        }
    }

    // A hole in a hillside rising to the east by a block for every block, as the shipped rim reads it.
    private static SurfaceProbe hillside(RavineCell c) {
        return RavineSitesTest.ground((x, z) -> 150 + (int) Math.floor(x - c.centreX()));
    }

    private static RavineBounds onTheHillside(RavineCell c) {
        RavineSites sites = new RavineSites(SETTINGS, LEVEL);
        sites.followGround(hillside(c), RavineSitesTest.SEA);
        return sites.boundsOf(c).orElseThrow();
    }

    // Blocks of a column's ground that the bowl takes away, the column being that far out from the mouth.
    private static int cutInto(SurfaceProbe ground, RavineBounds bounds, RavineCell c, double out, double degrees) {
        double radial = CONE.topRadius() + out;
        int x = (int) Math.floor(c.centreX() + radial * Math.cos(Math.toRadians(degrees)));
        int z = (int) Math.floor(c.centreZ() + radial * Math.sin(Math.toRadians(degrees)));
        int surface = ground.heightAt(x, z, 1000);
        int cut = 0;
        while (surface - cut > bounds.topY() && Carved.distance(SETTINGS, bounds, c, x, surface - cut, z) < 0) {
            cut++;
        }
        return cut;
    }

    @Test
    void onASlopeTheWallRunsUpToTheGroundOnTheUphillSideAndTheBowlStartsThere() {
        RavineCell c = cell(0, 2);
        RavineBounds bounds = onTheHillside(c);
        // The mouth spans 90 blocks of the slope: the lip is under the ground at the axis, the edge 40 over it to the east.
        assertEquals(150 - 3, bounds.topY(), 1.01);
        int lip = bounds.topY();
        assertEquals(lip + 43, bounds.edgeAt(1, 0), 2.01);
        assertEquals(lip, bounds.edgeAt(-1, 0), 1e-9);
        for (int y = lip + 2; y < lip + 38; y += 3) {
            assertTrue(carve(bounds, c, -2, y, 0) < 0, "open inside the mouth at y=" + y);
            assertTrue(carve(bounds, c, 2, y, 0) > 0, "the wall on the uphill side stands at y=" + y);
            assertTrue(carve(bounds, c, 2, y, 0) <= 2 + 1e-9, "and is no further from the air than from the mouth");
            assertTrue(carve(bounds, c, 20, y, 0) > 0, "with the hill behind it");
        }
        assertTrue(carve(bounds, c, 2, lip + 50, 0) < 0, "open over the edge");
        // On the downhill side the ground is under the lip, and so is the bowl's start.
        assertTrue(carve(bounds, c, 5, lip + 4, 180) < 0);
        int surface = bowlSurface(bounds, c, 1, 0);
        assertEquals(bounds.edgeAt(1, 0), surface, COLLAR.roughness() + 1.5, "the bowl starts at the edge on the uphill side");
    }

    @Test
    void theBowlTakesOnlyAShallowCutOutOfASlopeAllRoundTheMouth() {
        int deepest = 0;
        int deepestLevel = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(3, cz);
            SurfaceProbe ground = hillside(c);
            RavineBounds bounds = onTheHillside(c);
            // The same hole with a level mouth, as it would be if the bowl started from the lip all round.
            RavineBounds level = new RavineBounds(bounds.floorY(), bounds.topY());
            for (int degrees = 0; degrees < 360; degrees += 10) {
                // From far enough out for the whole column to be outside the mouth.
                for (int out = 3; out <= 150; out += 2) {
                    deepest = Math.max(deepest, cutInto(ground, bounds, c, out, degrees));
                    deepestLevel = Math.max(deepestLevel, cutInto(ground, level, c, out, degrees));
                }
            }
        }
        // 10 blocks where the slope and the bowl are furthest apart, and the bowl's roughness and the edge's blending on that.
        assertTrue(deepest >= 6 && deepest <= 15, "the bowl cuts " + deepest + " blocks into the slope");
        assertTrue(deepestLevel > 40, "started from the lip it would cut " + deepestLevel);
    }

    @Test
    void theBowlOpensNothingPastTheOuterRadius() {
        RavineBounds bounds = lipAt(96);
        assertEquals(ConeShapeTest.withCone(with(CONE, 12, 0.35F, Optional.empty(), Optional.empty()), SHAPE).maxReach(), SETTINGS.maxReach(), 1e-9, "a rim and an upper leave the reach alone");
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(0, cz);
            assertEquals(RavineCells.at(7L, ConeShapeTest.LOW_SETTINGS, 0, cz).orElseThrow().centreX(), c.centreX(), 1e-9, "and so leave every hole where it was");
            for (int degrees = 0; degrees < 360; degrees += 10) {
                for (int y = 97; y <= 96 + 2000; y += 50) {
                    assertFalse(carve(bounds, c, 155.5, y, degrees) < 0, "open past the outer radius at y=" + y);
                    assertFalse(carve(bounds, c, SETTINGS.maxReach() - 45, y, degrees) < 0, "open at the reach at y=" + y);
                }
            }
        }
    }

    @Test
    void theClearAirWidensFromTheFloorToTheLip() {
        RavineBounds bounds = lipAt(117);
        assertEquals(8, ConeShape.clearAt(CONE, bounds, bounds.floorY()), 1e-9);
        assertEquals(20, ConeShape.clearAt(CONE, bounds, 117), 1e-9);
        assertEquals(20, ConeShape.clearAt(CONE, bounds, 300), 1e-9, "and no further above it");
        assertEquals(14, ConeShape.clearAt(CONE, bounds, (bounds.floorY() + 117) / 2.0), 1e-9);
        ConeSettings cylinder = with(CONE, 12, 0.35F, Optional.of(RIM), Optional.empty());
        assertEquals(8, ConeShape.clearAt(cylinder, bounds, 117), 1e-9, "a cylinder without an upper");
    }

    @Test
    void noPlatformStemOrRootReachesIntoTheClearAir() {
        // Every disc drawn to hang, since a root runs up into the widening part.
        ConeSettings hanging = with(CONE, 12, 1F, Optional.of(RIM), Optional.of(UPPER));
        RavineSettings settings = ConeShapeTest.withCone(hanging, SHAPE);
        RavineBounds bounds = lipAt(128);
        int roots = 0;
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                RavineCell c = cell(cx, cz);
                DiscLayout layout = new ConeDiscLayout(settings, hanging, bounds, c);
                roots += (int) layout.discs().stream().filter(disc -> disc.support() instanceof Disc.Support.Hanging).count();
                for (double y = bounds.floorY(); y <= 128; y += 1.5) {
                    double clear = ConeShape.clearAt(hanging, bounds, y);
                    for (int degrees = 0; degrees < 360; degrees += 6) {
                        for (double r = clear; r > clear - 6 && r > 0; r -= 1.25) {
                            double x = c.centreX() + r * Math.cos(Math.toRadians(degrees));
                            double z = c.centreZ() + r * Math.sin(Math.toRadians(degrees));
                            assertTrue(Discs.rockDistance(layout, SHAPE, x, y, z) >= 0, "rock in the clear air at y=" + y + ", " + r + " from the axis");
                        }
                    }
                }
            }
        }
        assertTrue(roots > 30, roots + " hanging discs");
    }

    @Test
    void theWholeOfTheClearAirIsOpenFromTheFloorToTheSky() {
        RavineBounds bounds = lipAt(117);
        for (int cz = 0; cz < 4; cz++) {
            RavineCell c = cell(2, cz);
            for (int y = bounds.floorY(); y <= 117 + 200; y += 3) {
                double clear = ConeShape.clearAt(CONE, bounds, y);
                for (double r = 0; r < clear; r += 2) {
                    assertFalse(Carved.solid(SETTINGS, bounds, c, c.centreX() + r, y, c.centreZ()), "blocked at y=" + y + " r=" + r);
                    assertFalse(Carved.solid(SETTINGS, bounds, c, c.centreX(), y, c.centreZ() - r), "blocked at y=" + y + " r=" + r);
                }
            }
        }
    }

    @Test
    void anUpperClearRadiusOutsideTheConesOwnIsRejected() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, SETTINGS).getOrThrow().getAsJsonObject();
        json.getAsJsonObject("cone").getAsJsonObject("upper").addProperty("clear_radius", 6);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "narrower than at the floor");
        json.getAsJsonObject("cone").getAsJsonObject("upper").addProperty("clear_radius", 45);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "as wide as the mouth");
        json.getAsJsonObject("cone").getAsJsonObject("upper").addProperty("clear_radius", 44);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isSuccess());
        json.getAsJsonObject("cone").getAsJsonObject("rim").getAsJsonObject("collar").addProperty("profile", 0.5);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "a collar that leaves the lip vertically");
    }

    @Test
    void aCarveGivesEachCellTheTopItsGroundAllows() {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, SETTINGS).getOrThrow();
        RavineCarve carve = RavineCarve.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow().bind(7L, LEVEL);
        RavineCell high = cell(0, 0);
        RavineCell low = cell(1, 0);
        // The first cell along x is high ground, the next is low.
        carve.followGround(RavineSitesTest.ground((x, z) -> x < SETTINGS.cellSize() ? 130 : 70), RavineSitesTest.SEA);
        assertEquals(127, carve.boundsOf(high).orElseThrow().topY());
        assertTrue(carve.boundsOf(low).isEmpty());
        assertTrue(carve.isInFootprint((int) high.centreX(), (int) high.centreZ()));
        assertFalse(carve.isInFootprint((int) low.centreX(), (int) low.centreZ()), "no hole, so no city under it either");
        assertEquals(-1, carve.compute(new DensityFunction.SinglePointContext((int) high.centreX(), 100, (int) high.centreZ())), 1e-9);
        assertEquals(1, carve.compute(new DensityFunction.SinglePointContext((int) low.centreX(), 100, (int) low.centreZ())), 1e-9);
        // 20 blocks out from the mouth the bowl is 15 over the lip: open at 30 over it, solid at 5.
        int outside = (int) high.centreX() + 65;
        assertTrue(carve.compute(new DensityFunction.SinglePointContext(outside, 127 + 30, (int) high.centreZ())) < 0);
        assertTrue(carve.compute(new DensityFunction.SinglePointContext(outside, 127 + 5, (int) high.centreZ())) > 0);
        assertEquals(-1, carve.rock().compute(new DensityFunction.SinglePointContext((int) high.centreX(), 100, (int) high.centreZ())), 1e-9, "no rock in the clear air");
        assertTrue(carve.rock().isActive(high) && !carve.rock().isActive(low), "the rock shares what the carve knows of each cell");
    }
}

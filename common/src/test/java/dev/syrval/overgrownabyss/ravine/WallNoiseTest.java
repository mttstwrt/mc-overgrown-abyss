package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/** A cone's uneven wall: how far it is moved, that nothing is left floating, and what has to follow it. */
class WallNoiseTest {
    static {
        MinecraftBootstrap.init();
    }

    /** The layers the mod ships: wide lobes, runnels and a fine grain. */
    static final WallNoise SHIPPED = new WallNoise(List.of(
            new WallNoise.Layer(90, 3, 6), new WallNoise.Layer(16, 4, 2.5F), new WallNoise.Layer(6, 1.5F, 1)));
    private static final ConeSettings CONE = ConeRimTest.CONE;
    private static final DiscShape SHAPE = DiscTest.VARIED;
    private static final RavineSettings UNEVEN = ConeShapeTest.withCone(CONE, SHAPE, SHIPPED);
    private static final RavineSettings EVEN = ConeRimTest.SETTINGS;
    // The distance round the shipped cone where it is of middling width, which is what wavelengths are counted along.
    private static final double AROUND = Math.PI * (45 + 150);
    private static final DiscLayout NO_DISCS = () -> List.of();
    private static final RavineBounds BOUNDS = new RavineBounds(-40, 117);

    private static RavineCell cell(int cx, int cz) {
        return RavineCells.at(7L, UNEVEN, cx, cz).orElseThrow();
    }

    // The main cut alone, with no disc's dome in the wall.
    private static double cut(RavineSettings settings, RavineCell c, double radial, double y, double degrees) {
        double x = c.centreX() + radial * Math.cos(Math.toRadians(degrees));
        double z = c.centreZ() + radial * Math.sin(Math.toRadians(degrees));
        return ConeShape.signedDistance(settings, CONE, BOUNDS, c, NO_DISCS, x, y, z);
    }

    // How far from the axis the air ends along a line out from it, and how many times air and rock change places on the way.
    private record Crossing(double radius, int changes) {}

    private static Crossing crossing(RavineSettings settings, RavineCell c, double y, double degrees) {
        double first = Double.NaN;
        int changes = 0;
        boolean open = true;
        for (double radial = 0; radial <= settings.maxReach() + 4; radial += 0.5) {
            boolean here = cut(settings, c, radial, y, degrees) < 0;
            if (here != open) {
                changes++;
                first = changes == 1 ? radial : first;
                open = here;
            }
        }
        return new Crossing(first, changes);
    }

    @Test
    void theWallIsMovedByNoMoreThanItsLayersAmplitudesAndByMostOfThatSomewhere() {
        assertEquals(9.5, SHIPPED.maxDisplacement(), 1e-9);
        assertEquals(0, WallNoise.NONE.maxDisplacement(), 1e-9);
        assertEquals(0, WallNoise.NONE.offset(99L, AROUND, 0.3, 40), 1e-9);
        double furthestOut = 0;
        double furthestIn = 0;
        for (long hash = 1; hash <= 4; hash++) {
            for (double turn = 0; turn < 1; turn += 1 / 700.0) {
                for (double y = -40; y < 255; y += 3) {
                    double offset = SHIPPED.offset(hash * 0x9E3779B97F4A7C15L, AROUND, turn, y);
                    assertTrue(Math.abs(offset) <= 9.5, "moved by " + offset);
                    furthestOut = Math.max(furthestOut, offset);
                    furthestIn = Math.min(furthestIn, offset);
                }
            }
        }
        assertTrue(furthestOut > 5.5 && furthestIn < -5.5, "moved between " + furthestIn + " and " + furthestOut);
    }

    @Test
    void theWallClosesOnItselfAndChangesGraduallyFromBlockToBlock() {
        long hash = cell(0, 0).hash();
        for (double y = -40; y < 255; y += 0.7) {
            assertEquals(SHIPPED.offset(hash, AROUND, 0, y), SHIPPED.offset(hash, AROUND, 1 - 1e-9, y), 1e-6, "a seam at y=" + y);
            assertEquals(SHIPPED.offset(hash, AROUND, 0, y), SHIPPED.offset(hash, AROUND, 1, y), 1e-12);
        }
        // The steepest a layer can be is three times its amplitude over its wavelength.
        double block = 1 / AROUND;
        for (double turn = 0; turn < 1; turn += 37 * block) {
            for (double y = -40; y < 255; y += 1.3) {
                double here = SHIPPED.offset(hash, AROUND, turn, y);
                assertTrue(Math.abs(SHIPPED.offset(hash, AROUND, turn + block, y) - here) <= 1.3, "a step round the hole at turn " + turn + ", y=" + y);
                assertTrue(Math.abs(SHIPPED.offset(hash, AROUND, turn, y + 1) - here) <= 0.6, "a step up the wall at turn " + turn + ", y=" + y);
            }
        }
    }

    @Test
    void aStretchedLayerRunsUpTheWallAndItsBulgesDoNotLineUpInRows() {
        WallNoise runnels = new WallNoise(List.of(new WallNoise.Layer(16, 4, 2.5F)));
        long hash = cell(1, 0).hash();
        double block = 1 / AROUND;
        double round = 0;
        double up = 0;
        for (double turn = 0; turn < 1; turn += 5 * block) {
            for (double y = -40; y < 255; y += 5) {
                double here = runnels.offset(hash, AROUND, turn, y);
                round += Math.abs(runnels.offset(hash, AROUND, turn + block, y) - here);
                up += Math.abs(runnels.offset(hash, AROUND, turn, y + 1) - here);
            }
        }
        assertTrue(up < round / 2.5, "it changes by " + up + " up the wall against " + round + " round it");
        // A column of bulges is level at the heights of its drawn values, 64 blocks apart. Each column is slid up by an amount
        // of its own, or the whole wall would be level at those heights, all the way round.
        double atARow = 0;
        double between = 0;
        for (double turn = 0; turn < 1; turn += 5 * block) {
            atARow += Math.abs(runnels.offset(hash, AROUND, turn, 128.5) - runnels.offset(hash, AROUND, turn, 127.5));
            between += Math.abs(runnels.offset(hash, AROUND, turn, 160.5) - runnels.offset(hash, AROUND, turn, 159.5));
        }
        assertTrue(atARow > between / 3, "the wall changes by " + atARow + " over a block at a row's height and by " + between + " between two rows");
    }

    @Test
    void eachHoleHasAWallOfItsOwn() {
        int alike = 0;
        int all = 0;
        for (double turn = 0; turn < 1; turn += 0.013) {
            for (double y = -40; y < 255; y += 11) {
                double one = SHIPPED.offset(cell(0, 0).hash(), AROUND, turn, y);
                assertEquals(one, SHIPPED.offset(cell(0, 0).hash(), AROUND, turn, y), 0, "the same hole, the same wall");
                alike += Math.abs(one - SHIPPED.offset(cell(0, 1).hash(), AROUND, turn, y)) < 0.5 ? 1 : 0;
                all++;
            }
        }
        assertTrue(alike < all / 4, alike + " of " + all + " places are alike in two holes");
    }

    @Test
    void alongEveryLineOutFromTheAxisAirTurnsToRockExactlyOnce() {
        double most = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(2, cz);
            for (double y = BOUNDS.floorY() + 0.5; y <= 117; y += 3.7) {
                for (double degrees = 0; degrees < 360; degrees += 7) {
                    Crossing uneven = crossing(UNEVEN, c, y, degrees);
                    Crossing even = crossing(EVEN, c, y, degrees);
                    assertEquals(1, uneven.changes(), "air and rock change places " + uneven.changes() + " times at y=" + y + ", " + degrees + " degrees");
                    double moved = Math.abs(uneven.radius() - even.radius());
                    // Found to half a block either way.
                    assertTrue(moved <= 9.5 + 1, "the wall is moved by " + moved + " at y=" + y + ", " + degrees + " degrees");
                    most = Math.max(most, moved);
                }
            }
        }
        assertTrue(most > 5, "the wall is moved by " + most + " at most");
    }

    @Test
    void noRockOfAnUnevenBowlIsLeftFloating() {
        // Over the lip the bowl's surface has a roughness of its own, so a line out from the axis may cross it more than once.
        // What has to hold is that every block of rock has rock under it or behind it, further from the axis.
        int rock = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(2, cz);
            for (double degrees = 0; degrees < 360; degrees += 7) {
                for (int y = 118; y < 117 + 120; y++) {
                    for (int radial = 30; radial < 200; radial++) {
                        if (cut(UNEVEN, c, radial, y, degrees) < 0) {
                            continue;
                        }
                        rock++;
                        assertTrue(cut(UNEVEN, c, radial, y - 1, degrees) >= 0 || cut(UNEVEN, c, radial + 1, y, degrees) >= 0,
                                "rock in the air " + radial + " from the axis at y=" + y + ", " + degrees + " degrees");
                    }
                }
            }
        }
        assertTrue(rock > 100_000, rock + " blocks of rock looked at");
    }

    @Test
    void anUnevenWallOpensNothingPastTheReachAndLeavesTheClearAirOpen() {
        assertEquals(EVEN.maxReach(), UNEVEN.maxReach(), 1e-9, "unevenness leaves the reach alone");
        for (int cz = 0; cz < 3; cz++) {
            RavineCell c = cell(0, cz);
            for (double y = BOUNDS.floorY() + 0.5; y < 117 + 600; y += 7) {
                for (double degrees = 0; degrees < 360; degrees += 9) {
                    assertFalse(cut(UNEVEN, c, UNEVEN.maxReach(), y, degrees) < 0, "open at the reach at y=" + y);
                    assertFalse(cut(UNEVEN, c, 200.5, y, degrees) < 0 && y > 117, "the bowl is open past the outer radius at y=" + y);
                }
                double clear = ConeShape.clearAt(CONE, BOUNDS, y);
                for (double r = 0; r < clear; r += 2) {
                    assertFalse(Carved.solid(UNEVEN, BOUNDS, c, c.centreX() + r, y, c.centreZ()), "blocked at y=" + y + " r=" + r);
                    assertFalse(Carved.solid(UNEVEN, BOUNDS, c, c.centreX(), y, c.centreZ() - r), "blocked at y=" + y + " r=" + r);
                }
            }
        }
    }

    @Test
    void theWallOverAColumnIsTheLowestRockOverIt() {
        int found = 0;
        int moved = 0;
        for (int cz = 0; cz < 2; cz++) {
            RavineCell c = cell(1, cz);
            for (double radial = 30; radial <= 170; radial += 3.1) {
                for (double degrees = 0; degrees < 360; degrees += 11) {
                    double x = c.centreX() + radial * Math.cos(Math.toRadians(degrees));
                    double z = c.centreZ() + radial * Math.sin(Math.toRadians(degrees));
                    OptionalDouble smooth = ConeShape.wallOver(EVEN, CONE, BOUNDS, c, x, z);
                    assertEquals(ConeShape.heightAt(EVEN, CONE, BOUNDS, c.distanceToCentre(x, z)), smooth, "an even wall is where the cone is that wide");
                    OptionalDouble wall = ConeShape.wallOver(UNEVEN, CONE, BOUNDS, c, x, z);
                    if (wall.isEmpty()) {
                        assertTrue(radial < 45 + 9.5, "no wall over a column " + radial + " from the axis");
                        assertTrue(cut(UNEVEN, c, radial, 116.5, degrees) < 0, "and it is open to the top");
                        continue;
                    }
                    // Under the cavern's roof the cavern may be open where the cone is not.
                    if (wall.getAsDouble() <= BOUNDS.floorY() + UNEVEN.cavernHeight() + 1) {
                        continue;
                    }
                    assertTrue(cut(UNEVEN, c, radial, wall.getAsDouble(), degrees) >= 0, "rock at the wall, " + radial + " out");
                    assertTrue(cut(UNEVEN, c, radial, wall.getAsDouble() - 1, degrees) < 0, "and air under it, " + radial + " out");
                    found++;
                    moved += smooth.isPresent() && Math.abs(wall.getAsDouble() - smooth.getAsDouble()) > 3 ? 1 : 0;
                }
            }
        }
        assertTrue(found > 1000 && moved > found / 4, found + " columns under the wall, " + moved + " of them where it is moved by over 3 blocks");
    }

    @Test
    void aRootHungFromAnUnevenWallReachesIt() {
        ConeSettings hanging = new ConeSettings(
                CONE.topRadius(), CONE.baseRadius(), CONE.flare(), CONE.clearRadius(), CONE.layerSpacing(), CONE.layerJitter(), CONE.spacing(),
                CONE.stackChance(), CONE.ceilingMargin(), CONE.baseClearance(), 1F, CONE.outerRadius(), CONE.riderChance(), CONE.riderScale(),
                CONE.rim(), CONE.upper());
        RavineSettings settings = ConeShapeTest.withCone(hanging, SHAPE, SHIPPED);
        int fromTheWall = 0;
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 4; cz++) {
                RavineCell c = cell(cx, cz);
                List<Disc> discs = new ConeDiscLayout(settings, hanging, BOUNDS, c).discs();
                for (Disc disc : discs) {
                    if (!(disc.support() instanceof Disc.Support.Hanging root) || Discs.platformAbove(discs, SHAPE, disc).isPresent()) {
                        continue;
                    }
                    fromTheWall++;
                    boolean meetsRock = false;
                    for (double y = root.anchor(); y <= root.top() && !meetsRock; y += 0.5) {
                        meetsRock = ConeShape.signedDistance(settings, hanging, BOUNDS, c, NO_DISCS, disc.x(), y, disc.z()) >= 0;
                    }
                    assertTrue(meetsRock, "the root of " + disc + " ends in the air");
                    assertTrue(root.top() <= 117 - hanging.ceilingMargin() + SHAPE.rootRadiusFor(disc.radius()) + 1e-9, "and stays under the top");
                }
            }
        }
        assertTrue(fromTheWall > 25, fromTheWall + " roots hung from the wall");
    }

    @Test
    void theCavernsBiomeReachesAsFarAsTheWallMayBeMoved() {
        RavineCell c = cell(0, 0);
        double floor = BOUNDS.floorY() + 1;
        assertTrue(RavineShape.cavernContains(EVEN, BOUNDS, c, c.centreX() + 139, floor, c.centreZ(), 4));
        assertFalse(RavineShape.cavernContains(EVEN, BOUNDS, c, c.centreX() + 141, floor, c.centreZ(), 4));
        assertTrue(RavineShape.cavernContains(UNEVEN, BOUNDS, c, c.centreX() + 148, floor, c.centreZ(), 4));
        assertFalse(RavineShape.cavernContains(UNEVEN, BOUNDS, c, c.centreX() + 151, floor, c.centreZ(), 4));
        assertFalse(RavineShape.cavernContains(UNEVEN, BOUNDS, c, c.centreX(), BOUNDS.floorY() - 5, c.centreZ(), 4), "no further under the floor");
    }

    @Test
    void unevennessThatWouldReachTheClearAirOrPassTheOuterRadiusIsRejected() throws Exception {
        assertEquals(SHIPPED, DiscThemeTest.shipped().wallNoise(), "the shipped file has the layers these tests are run on");
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, UNEVEN).getOrThrow().getAsJsonObject();
        assertEquals(Optional.of(UNEVEN.wallNoise()), RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).result().map(RavineSettings::wallNoise));
        var layer = json.getAsJsonArray("wall_noise").get(0).getAsJsonObject();
        // The mouth is 45 from the axis and the clear air 20: the other two layers move the wall by 3.5.
        layer.addProperty("amplitude", 21.5);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "into the clear air at the top");
        layer.addProperty("amplitude", 21);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isSuccess());
        json.getAsJsonObject("cone").addProperty("outer_radius", 174);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "past the outer radius at the floor");
        json.getAsJsonObject("cone").addProperty("outer_radius", 175);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isSuccess());
        layer.addProperty("wavelength", 3);
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "narrower than blocks can show");
        // A ravine has no wall of this kind.
        var ravine = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, RavineShapeTest.SETTINGS).getOrThrow().getAsJsonObject();
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, ravine).isSuccess());
        ravine.add("wall_noise", RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, UNEVEN).getOrThrow().getAsJsonObject().get("wall_noise"));
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, ravine).isError());
    }
}

package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.InclusiveRange;
import org.junit.jupiter.api.Test;

/** A hole's falls: how many, where their still water lies and that it cannot leave, and what each comes down on. */
class DiscFallsTest {
    static {
        MinecraftBootstrap.init();
    }

    static final FallSettings MANY = new FallSettings(
            new InclusiveRange<>(4, 4), DiscTheme.Ramp.EVEN, 1, 10, 1, new FallSettings.Pool(3, 2), 1, 4,
            Optional.of(new FallSettings.Curtain(0.5F, new InclusiveRange<>(3, 5), 30)), Optional.empty());
    private static final int[][] BESIDE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** The mod's own hole with other falls, or none. */
    static RavineSettings with(Optional<FallSettings> falls) throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        return new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), base.floor(), base.top(), base.cavernRadius(), base.cavernHeight(),
                base.edgeFalloff(), base.discs(), base.discThemes(), base.environment(), base.wallNoise(), base.ravine(), base.cone(),
                new ConeDressing(base.roots(), falls));
    }

    private record Hole(RavineCell cell, CellDiscs discs) {}

    private static List<Hole> holes(RavineSettings settings) {
        var holes = new ArrayList<Hole>();
        for (int cz = 0; cz < 6; cz++) {
            RavineCell cell = RavineCells.at(11L, settings, 0, cz).orElseThrow();
            holes.add(new Hole(cell, CellDiscs.of(settings, DiscThemeTest.LEVEL, cell)));
        }
        return holes;
    }

    private record At(int disc, int x, int z) {}

    @Test
    void aHoleStartsNoMoreFallsThanItIsAllowedAndEachGoesOnOnlySoFar() throws Exception {
        RavineSettings settings = with(Optional.of(MANY));
        int started = 0;
        int wentOn = 0;
        int ontoDiscs = 0;
        int curtains = 0;
        for (Hole hole : holes(settings)) {
            List<DiscFalls.Fall> falls = hole.discs().falls().falls();
            var startedOn = new HashMap<Integer, Integer>();
            int first = 0;
            for (DiscFalls.Fall fall : falls) {
                assertTrue(fall.step() >= 0 && fall.step() < MANY.maxSteps(), "a fall " + fall.step() + " after its first");
                assertTrue(fall.drop() >= MANY.minDrop(), "a drop of " + fall.drop());
                assertTrue(fall.width() == 1 || fall.width() >= 3 && fall.width() <= 5, "a lip " + fall.width() + " wide");
                if (fall.step() == 0) {
                    first++;
                    assertTrue(startedOn.merge(fall.from(), 1, Integer::sum) <= MANY.maxPerDisc(), "two falls start on disc " + fall.from());
                    ontoDiscs += fall.onto().isPresent() ? 1 : 0;
                } else {
                    wentOn++;
                }
                curtains += fall.width() > 1 ? 1 : 0;
            }
            assertTrue(first <= 4, first + " falls started in one hole");
            started += first;
            assertEquals(falls, CellDiscs.of(settings, DiscThemeTest.LEVEL, hole.cell()).falls().falls(), "the same falls every time");
        }
        assertTrue(started >= 12, started + " falls started in six holes that may each have four");
        assertTrue(ontoDiscs >= 3, ontoDiscs + " of them land on a disc");
        assertTrue(wentOn >= 1, "with a chain of 1 none went on from its pool");
        assertTrue(curtains >= 1, "no curtain among them");
        for (Hole hole : holes(with(Optional.empty()))) {
            assertTrue(hole.discs().falls().falls().isEmpty() && hole.discs().falls().columns().isEmpty(), "falls without the setting");
        }
    }

    @Test
    void aFallsStillWaterHasGroundOrItsOwnWaterOnEverySideButALipsOpenOne() throws Exception {
        RavineSettings settings = with(Optional.of(MANY));
        DiscShape shape = settings.discs();
        int columns = 0;
        int lips = 0;
        int cut = 0;
        for (Hole hole : holes(settings)) {
            List<Disc> discs = hole.discs().layout().discs();
            Map<At, DiscFalls.Column> water = new HashMap<>();
            for (DiscFalls.Column column : hole.discs().falls().columns()) {
                assertTrue(water.put(new At(column.disc(), column.x(), column.z()), column) == null, "a column twice");
            }
            for (DiscFalls.Column column : water.values()) {
                Disc d = discs.get(column.disc());
                int ground = d.topBlockAt(Math.hypot(column.x() - d.x(), column.z() - d.z()));
                assertEquals(ground - column.level(), column.cut(), "what is cut away is the ground over the water");
                assertTrue(column.cut() >= 0 && column.cut() + column.depth() <= 3, column + " leaves no block of a platform 4 thick under it");
                assertTrue(d.platformDistance(shape, column.x(), column.level() - column.depth(), column.z()) < 0, "no bed under " + column);
                assertTrue(d.platformDistance(shape, column.x(), column.level(), column.z()) < 0, "water outside its platform: " + column);
                boolean open = false;
                Optional<DiscWater> own = hole.discs().themes().get(column.disc()).flatMap(DiscTheme::water);
                for (int[] step : BESIDE) {
                    int x = column.x() + step[0];
                    int z = column.z() + step[1];
                    double fromAxis = Math.hypot(x - d.x(), z - d.z());
                    DiscFalls.Column beside = water.get(new At(column.disc(), x, z));
                    if (beside != null) {
                        assertEquals(column.level(), beside.level(), "two levels of a fall's water side by side at " + column);
                    } else if (fromAxis >= d.radius()) {
                        open = true;
                    } else {
                        assertTrue(d.topBlockAt(fromAxis) >= column.level(), "lower ground beside " + column);
                        boolean wet = own.isPresent() && own.get().depthAt(d, hole.cell().hash(), column.disc(), x, z) > 0;
                        assertTrue(!wet || d.topBlockAt(fromAxis) == column.level(), "the disc's own water at another level beside " + column);
                    }
                }
                assertEquals(open, column.lip(), "a lip is the water with an open side: " + column);
                assertTrue(hole.discs().falls().holds(column.disc(), column.x(), column.z()));
                assertTrue(hole.discs().falls().isNear(column.x() + 2, column.level() + 1, column.z()), "the ground two blocks from a fall's water is its bank");
                assertEquals(0, hole.discs().grounds().get(column.disc()).riseAt(column.x() + 1, column.z() + 1), "a mound right beside a fall's water");
                columns++;
                lips += column.lip() ? 1 : 0;
                cut += column.cut() > 0 ? 1 : 0;
            }
        }
        assertTrue(columns > 200 && lips >= 12, columns + " columns, " + lips + " of them lips, " + cut + " cut into the ground");
    }

    @Test
    void aFallComesDownInAPoolOnADiscsTopOrOnTheFloor() throws Exception {
        RavineSettings settings = with(Optional.of(MANY));
        for (Hole hole : holes(settings)) {
            List<Disc> discs = hole.discs().layout().discs();
            DiscFalls falls = hole.discs().falls();
            for (DiscFalls.Fall fall : falls.falls()) {
                Disc from = discs.get(fall.from());
                assertTrue(Math.hypot(fall.x() - from.x(), fall.z() - from.z()) >= from.radius(), "a fall down through its own disc");
                assertTrue(falls.columns().stream().anyMatch(column -> column.disc() == fall.from() && column.lip() && column.level() == fall.level()
                        && Math.hypot(column.x() - fall.x(), column.z() - fall.z()) < 1.5), "no lip beside the column " + fall + " falls down");
                int landsOn = fall.level() - fall.drop() - 1;
                for (Root root : hole.discs().roots().roots()) {
                    for (Root.Knot knot : root.knots()) {
                        assertTrue(knot.y() < landsOn || knot.y() > fall.level() || Math.hypot(knot.x() - fall.x(), knot.z() - fall.z()) >= knot.radius() + 1,
                                fall + " comes down on a root at " + knot);
                    }
                }
                if (fall.onto().isEmpty()) {
                    assertEquals(DiscThemeTest.LEVEL.floorY() - 1, landsOn, "a fall that lands on no disc goes to the floor");
                    continue;
                }
                int onto = fall.onto().getAsInt();
                Disc under = discs.get(onto);
                double fromAxis = Math.hypot(fall.x() - under.x(), fall.z() - under.z());
                // A lip lets its water go down each open column beside it. Where those lie either side of a step of the bowl
                // under them, the fall is reckoned to the lower side.
                int top = under.topBlockAt(fromAxis);
                assertTrue(landsOn == top || landsOn == top - 1, fall + " lands at " + landsOn + ", and the top of the disc under it is at " + top);
                assertTrue(under.floor() < from.floor(), "and that disc is a lower one");
                List<DiscFalls.Column> pool = falls.columns().stream()
                        .filter(column -> column.disc() == onto && Math.hypot(column.x() - fall.x(), column.z() - fall.z()) <= 5.5).toList();
                assertTrue(pool.stream().anyMatch(column -> column.x() == fall.x() && column.z() == fall.z()), "no water where " + fall + " lands");
                assertTrue(pool.size() >= 12, "a pool of " + pool.size() + " columns");
                assertTrue(pool.stream().anyMatch(column -> column.depth() == 2), "a pool with no deeper middle");
                int bank = hole.discs().themes().get(onto).flatMap(DiscTheme::water).map(DiscWater::bank).orElse(3);
                for (DiscFalls.Column column : pool) {
                    // Only what leads on from a pool goes nearer its disc's rim than a pond may.
                    assertTrue(column.depth() == 1 || Math.hypot(column.x() - under.x(), column.z() - under.z()) <= under.radius() - bank, "a pool in the bank: " + column);
                }
            }
        }
    }

    @Test
    void aFallsWaterTakesThePlaceOfTheDiscsOwnAndNothingIsGrownInIt() throws Exception {
        RavineSettings settings = with(Optional.of(MANY));
        for (Hole hole : holes(settings).subList(0, 2)) {
            DiscFalls falls = hole.discs().falls();
            Set<At> written = new HashSet<>();
            Set<Long> chunks = new HashSet<>();
            for (DiscFalls.Column column : falls.columns()) {
                chunks.add(((long) Math.floorDiv(column.x(), 16) << 32) | (Math.floorDiv(column.z(), 16) & 0xFFFFFFFFL));
            }
            int[] grownBesideAFall = {0};
            // The same hole with no falls, to see what the discs' own water would have been.
            CellDiscs dry = CellDiscs.of(with(Optional.empty()), DiscThemeTest.LEVEL, hole.cell());
            Map<At, Integer> own = new HashMap<>();
            Map<At, Integer> ownWithoutFalls = new HashMap<>();
            for (long chunk : chunks) {
                int minX = (int) (chunk >> 32) * 16;
                int minZ = (int) chunk * 16;
                DiscBlocks.forEachFallWater(hole.discs(), minX, minZ, (x, level, z, depth, cut, lip) -> {
                    assertTrue(x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16, "outside its chunk");
                    DiscFalls.Column column = falls.columns().stream().filter(each -> each.x() == x && each.z() == z && each.level() == level).findFirst().orElseThrow();
                    assertTrue(written.add(new At(column.disc(), x, z)), "written twice");
                    assertEquals(column.depth(), depth);
                    assertEquals(column.cut(), cut);
                    assertEquals(column.lip(), lip);
                });
                DiscBlocks.forEachWater(hole.cell(), hole.discs(), minX, minZ, (x, surface, z, depth, fluid) -> own.merge(new At(surface, x, z), 1, Integer::sum));
                DiscBlocks.forEachWater(hole.cell(), dry, minX, minZ, (x, surface, z, depth, fluid) -> ownWithoutFalls.merge(new At(surface, x, z), 1, Integer::sum));
                DiscGrowth.forEach(settings, hole.cell(), hole.discs(), minX, minZ, (x, y, z, feature, on) -> {
                    for (DiscFalls.Column column : falls.columns()) {
                        // Under a disc is the one place a fall's water is nothing to what grows.
                        boolean itsOwn = on != DiscTheme.Surface.UNDERSIDE && Math.abs(y - column.level()) <= 4;
                        grownBesideAFall[0] += itsOwn && Math.hypot(x - column.x(), z - column.z()) <= 2 ? 1 : 0;
                    }
                });
            }
            assertEquals(falls.columns().size(), written.size(), "every column of a fall's water, once");
            // Discs of one layer run into one another, so a column may be two discs' at once: counted by height and column,
            // each of a disc's own columns that a fall has taken is one fewer than there would have been.
            List<Disc> discs = hole.discs().layout().discs();
            int replaced = 0;
            for (DiscFalls.Column column : falls.columns()) {
                Disc d = discs.get(column.disc());
                Optional<DiscWater> water = hole.discs().themes().get(column.disc()).flatMap(DiscTheme::water);
                if (water.isPresent() && water.get().depthAt(d, hole.cell().hash(), column.disc(), column.x(), column.z()) > 0) {
                    var at = new At(d.topBlockAt(Math.hypot(column.x() - d.x(), column.z() - d.z())), column.x(), column.z());
                    assertEquals(ownWithoutFalls.get(at) - 1, own.getOrDefault(at, 0), "a disc's own water where a fall's lies, at " + column);
                    replaced++;
                }
            }
            assertTrue(replaced > 0, "no fall starts in a disc's own water");
            assertEquals(ownWithoutFalls.values().stream().mapToInt(Integer::intValue).sum() - replaced, own.values().stream().mapToInt(Integer::intValue).sum(),
                    "and the rest of the discs' own water is as it was");
            assertEquals(0, grownBesideAFall[0], "things grown in a fall's water or on its bank");
        }
    }

    @Test
    void aSpringComesOutOfTheWallBehindADiscOverAPoolAtItsFoot() throws Exception {
        var springs = new FallSettings.Springs(new InclusiveRange<>(3, 3), new InclusiveRange<>(3, 7), DiscThemeTest.feature("overgrown_abyss:disc/wall_spring"));
        var falls = new FallSettings(MANY.count(), MANY.byHeight(), 1, 10, 1, MANY.pool(), 1, 4, Optional.empty(), Optional.of(springs));
        RavineSettings settings = with(Optional.of(falls));
        int found = 0;
        for (Hole hole : holes(settings)) {
            List<Disc> discs = hole.discs().layout().discs();
            DiscFalls water = hole.discs().falls();
            assertTrue(water.springs().size() <= 3, water.springs().size() + " springs in a hole that may have three");
            assertEquals(water.springs(), CellDiscs.of(settings, DiscThemeTest.LEVEL, hole.cell()).falls().springs(), "the same springs every time");
            for (DiscFalls.Spring spring : water.springs()) {
                Disc d = discs.get(spring.disc());
                DiscFalls.Place first = spring.tries().getFirst();
                assertTrue(ConeShape.airDistance(settings, settings.cone().orElseThrow(), DiscThemeTest.LEVEL, hole.cell(), hole.discs().layout(), first.x(), first.y(), first.z()) < 0,
                        "a spring in open air: " + spring);
                int over = first.y() - d.topBlockAt(d.radius());
                assertTrue(over >= 2 && over <= 8, "a spring " + over + " over the rim");
                // A spring wants rock under it and an open side, so it is no higher than its dome's wall is nearly upright.
                double leansIn = d.radius() * (Math.sqrt(1 - Math.pow((first.y() - 1 - d.floor()) / d.height(), 2)) - Math.sqrt(1 - Math.pow((first.y() - d.floor()) / d.height(), 2)));
                assertTrue(leansIn <= 0.5 + 1e-9, "a spring where the wall leans in by " + leansIn + " in a block's rise");
                assertTrue(spring.tries().size() >= 5 && spring.tries().stream().allMatch(place -> Math.abs(place.x() - first.x()) <= 1 && Math.abs(place.y() - first.y()) <= 2
                        && Math.abs(place.z() - first.z()) <= 1), "the places to try a spring at are the one reckoned and those beside it");
                // On the side of the disc that lies in the wall: away from the hole's middle.
                double out = (first.x() - d.x()) * (d.x() - hole.cell().centreX()) + (first.z() - d.z()) * (d.z() - hole.cell().centreZ());
                assertTrue(out > 0, "a spring on the side of the hole's middle: " + spring);
                List<DiscFalls.Column> pool = water.columns().stream()
                        .filter(column -> column.disc() == spring.disc() && Math.hypot(column.x() - spring.x(), column.z() - spring.z()) <= 3).toList();
                assertTrue(pool.stream().anyMatch(column -> column.x() == spring.x() && column.z() == spring.z() && column.level() == spring.level()),
                        "no water under " + spring);
                // The pool reaches the foot of the wall, so that the water comes down into it wherever along the wall the spring is.
                assertTrue(pool.stream().anyMatch(column -> Math.hypot(column.x() - d.x(), column.z() - d.z()) > d.radius() - 1.5), "a spring's pool short of the wall");
                for (DiscFalls.Column column : pool) {
                    assertTrue(!column.lip(), "a spring's pool with an open side");
                    for (int[] step : BESIDE) {
                        int x = column.x() + step[0];
                        int z = column.z() + step[1];
                        boolean offThePlatform = Math.hypot(x - d.x(), z - d.z()) >= d.radius();
                        assertTrue(!offThePlatform || ConeShape.airDistance(settings, settings.cone().orElseThrow(), DiscThemeTest.LEVEL, hole.cell(), hole.discs().layout(), x, column.level(), z) < 0,
                                "open air and not the wall beyond a spring's pool at " + column);
                    }
                }
                assertTrue(water.isNear(spring.x() + 5, spring.level() + 1, spring.z()), "the ground round where a spring's water comes down is kept clear");
                found++;
            }
        }
        assertTrue(found >= 8, found + " springs in six holes");
        for (Hole hole : holes(with(Optional.of(MANY)))) {
            assertTrue(hole.discs().falls().springs().isEmpty(), "springs without the setting");
        }
    }

    @Test
    void aDiscThatHoldsLavaStartsNoFallAndTakesNoPoolNorSpring() throws Exception {
        var springs = new FallSettings.Springs(new InclusiveRange<>(3, 3), new InclusiveRange<>(3, 7), DiscThemeTest.feature("overgrown_abyss:disc/wall_spring"));
        var falls = new FallSettings(MANY.count(), MANY.byHeight(), 1, 10, 1, MANY.pool(), 1, 4, Optional.empty(), Optional.of(springs));
        RavineSettings base = with(Optional.of(falls));
        // The mod's own swamp on every disc, and the same again with its ponds and streams turned to lava.
        DiscTheme swamp = base.discThemes().get(2);
        DiscWater water = swamp.water().orElseThrow();
        var found = new HashMap<DiscWater.Fluid, Integer>();
        for (DiscWater.Fluid fluid : DiscWater.Fluid.values()) {
            var held = new DiscWater(water.ponds(), water.pondSize(), water.streamWidth(), water.streamSpacing(), water.depth(), water.bank(), fluid, 0);
            var theme = new DiscTheme(swamp.biome(), swamp.inherits(), swamp.weight(), swamp.byHeight(), swamp.byDistance(), swamp.bySize(), swamp.only(), swamp.palette(),
                    Optional.of(held), swamp.growth(), swamp.wildness(), swamp.ground(), swamp.ruins(), swamp.troves(), swamp.rootWood());
            var settings = new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), base.floor(), base.top(), base.cavernRadius(), base.cavernHeight(),
                    base.edgeFalloff(), base.discs(), List.of(theme), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
            for (Hole hole : holes(settings)) {
                DiscFalls made = hole.discs().falls();
                found.merge(fluid, made.falls().size() + made.columns().size() + made.springs().size(), Integer::sum);
            }
        }
        assertTrue(found.get(DiscWater.Fluid.WATER) > 100, found + ": few falls and springs among discs of water");
        assertEquals(0, found.get(DiscWater.Fluid.LAVA), "falls, their water or springs among discs of lava");
        // And among the mod's own themes, where a lava disc has discs of water over it and under it.
        int lavaDiscs = 0;
        for (Hole hole : holes(base)) {
            List<Optional<DiscTheme>> themes = hole.discs().themes();
            lavaDiscs += (int) themes.stream().filter(theme -> theme.orElseThrow().holdsLava()).count();
            DiscFalls made = hole.discs().falls();
            for (DiscFalls.Fall fall : made.falls()) {
                assertTrue(!themes.get(fall.from()).orElseThrow().holdsLava(), "a fall starts on a lava disc: " + fall);
                assertTrue(fall.onto().isEmpty() || !themes.get(fall.onto().getAsInt()).orElseThrow().holdsLava(), "a fall lands on a lava disc: " + fall);
            }
            for (DiscFalls.Column column : made.columns()) {
                assertTrue(!themes.get(column.disc()).orElseThrow().holdsLava(), "a fall's water on a lava disc: " + column);
            }
            for (DiscFalls.Spring spring : made.springs()) {
                assertTrue(!themes.get(spring.disc()).orElseThrow().holdsLava(), "a spring over a lava disc: " + spring);
            }
        }
        assertTrue(lavaDiscs >= 6, lavaDiscs + " lava discs in six holes");
    }

}

package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.Test;

/** The ruins on discs: how they are written, which discs have them, and that each stands where it has the room. */
class DiscRuinsTest {
    static {
        MinecraftBootstrap.init();
    }

    private static final RavineBounds LEVEL = DiscThemeTest.LEVEL;
    private static final long SEED = 11L;
    private static final int CELLS = 4;

    private static ResourceKey<StructureTemplatePool> pool(String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath("overgrown_abyss", path));
    }

    private static DiscRuins.Kind kind(String pool, float weight, float radius, int height) {
        return new DiscRuins.Kind(pool(pool), weight, radius, height, 0);
    }

    private static DiscTheme ruined(Optional<DiscWater> water, DiscRuins ruins) {
        return new DiscTheme(
                Optional.empty(), Optional.empty(), 1, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Limits.NONE,
                DiscPalette.UNPAINTED, water, List.of(), Optional.of(ruins));
    }

    /** The mod's own hole with other themes. */
    private static RavineSettings with(DiscTheme... themes) throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        return new RavineSettings(
                base.salt(), base.cellSize(), 1F, base.sizeBias(), base.floor(), base.top(), base.cavernRadius(), base.cavernHeight(),
                base.edgeFalloff(), base.discs(), List.of(themes), base.environment(), base.wallNoise(), base.ravine(), base.cone());
    }

    /** One ruin as the tests look at it: its site, its kind, and the disc it was found a place on. */
    private record Standing(RuinSite site, DiscRuins.Kind kind, int index, Disc disc) {
        double fromAxis() {
            return Math.hypot(site.x() + 0.5 - disc.x(), site.z() + 0.5 - disc.z());
        }

        int ground() {
            return site.y() - 1 + kind.sink();
        }
    }

    /**
     * Every ruin of a cell with the disc it stands on: the one whose theme has ruins of that pool, whose top holds the ruin's
     * whole round and whose lowest ground under the round is where the ruin was stood.
     */
    private static List<Standing> standing(CellDiscs cellDiscs) {
        List<Disc> discs = cellDiscs.layout().discs();
        var found = new ArrayList<Standing>();
        for (RuinSite site : cellDiscs.ruins()) {
            var hosts = new ArrayList<Standing>();
            for (int i = 0; i < discs.size(); i++) {
                Disc disc = discs.get(i);
                Optional<DiscRuins.Kind> kind = cellDiscs.themes().get(i).flatMap(DiscTheme::ruins)
                        .flatMap(ruins -> ruins.kinds().stream().filter(each -> each.pool().equals(site.pool())).findFirst());
                if (kind.isEmpty()) {
                    continue;
                }
                var here = new Standing(site, kind.get(), i, disc);
                if (here.fromAxis() + kind.get().radius() <= disc.radius()
                        && disc.topBlockAt(Math.max(0, here.fromAxis() - kind.get().radius())) == here.ground()) {
                    hosts.add(here);
                }
            }
            assertFalse(hosts.isEmpty(), site + " stands on no disc whose theme has such ruins");
            found.add(hosts.getFirst());
        }
        return found;
    }

    @Test
    void ruinsAreWrittenWithTheirKindsAndCheckedWhenRead() {
        var json = JsonParser.parseString("""
                {"chance": 0.5, "kinds": [
                  {"pool": "overgrown_abyss:disc_ruins/camp", "weight": 2, "radius": 12.5, "height": 5},
                  {"pool": "overgrown_abyss:disc_ruins/hut", "weight": 1, "radius": 5, "height": 6, "sink": 1}]}""");
        DiscRuins ruins = DiscRuins.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(0.5F, ruins.chance());
        assertEquals(new DiscRuins.Kind(pool("disc_ruins/camp"), 2, 12.5F, 5, 0), ruins.kinds().get(0), "a piece stands on the ground unless it says otherwise");
        assertEquals(new DiscRuins.Kind(pool("disc_ruins/hut"), 1, 5, 6, 1), ruins.kinds().get(1));
        double largestTop = Math.PI * 256 * 256;
        assertTrue(ruins.every() > largestTop, "without every, no disc is large enough for a second ruin");
        assertTrue(DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"chance\": 0.5, \"kinds\": []}")).isError(), "no kinds");
        assertTrue(DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"chance\": 0.5, \"kinds\": [{\"pool\": \"minecraft:empty\", \"weight\": 0, \"radius\": 5, \"height\": 5}]}")).isError(), "no kind with a weight");
        assertTrue(DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"chance\": 2, \"kinds\": [{\"pool\": \"minecraft:empty\", \"weight\": 1, \"radius\": 5, \"height\": 5}]}")).isError(), "a chance above 1");
    }

    @Test
    void kindsAreTriedInAnOrderDrawnByWeight() {
        DiscRuins.Kind heavy = kind("heavy", 3, 5, 5);
        DiscRuins.Kind light = kind("light", 1, 5, 5);
        assertEquals(List.of(heavy, light), DiscRuinSites.inOrder(List.of(light, heavy), List.of(0.5, 0.5)), "on the same draw the heavier comes first");
        assertEquals(List.of(light, heavy), DiscRuinSites.inOrder(List.of(light, heavy), List.of(0.1, 0.9)), "but a low draw beats a high one");
        int trials = 20_000;
        int heavyFirst = 0;
        for (int i = 0; i < trials; i++) {
            List<Double> units = List.of(RavineCells.unit(99L, 2 * i), RavineCells.unit(99L, 2 * i + 1));
            heavyFirst += DiscRuinSites.inOrder(List.of(light, heavy), units).getFirst() == heavy ? 1 : 0;
        }
        assertEquals(0.75, heavyFirst / (double) trials, 0.02, "three times the weight is first three times as often");
    }

    /** The mod's own hole with a share of its discs hanging from roots, which it has none of as shipped. */
    private static RavineSettings withHangingDiscs() throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        ConeSettings cone = base.cone().orElseThrow();
        var hanging = new ConeSettings(
                cone.topRadius(), cone.baseRadius(), cone.flare(), cone.clearRadius(), cone.layerSpacing(), cone.layerJitter(), cone.spacing(),
                cone.stackChance(), cone.ceilingMargin(), cone.baseClearance(), 0.5F, cone.outerRadius(), cone.riderChance(), cone.riderScale(),
                cone.rim(), cone.upper());
        return new RavineSettings(
                base.salt(), base.cellSize(), 1F, base.sizeBias(), base.floor(), base.top(), base.cavernRadius(), base.cavernHeight(),
                base.edgeFalloff(), base.discs(), base.discThemes(), base.environment(), base.wallNoise(), base.ravine(), Optional.of(hanging));
    }

    @Test
    void everyRuinOfTheModsThemesStandsOnItsDiscWithRoom() throws Exception {
        assertRoomForEveryRuin(DiscThemeTest.shipped(), 60, 8);
    }

    @Test
    void noRuinStandsWhereARootComesDown() throws Exception {
        RavineSettings settings = withHangingDiscs();
        long roots = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            RavineCell cell = RavineCells.at(SEED, settings, 0, cz).orElseThrow();
            roots += CellDiscs.of(settings, LEVEL, cell).layout().discs().stream().filter(disc -> disc.support() instanceof Disc.Support.Hanging).count();
        }
        assertTrue(roots > 20, roots + " hanging discs");
        assertRoomForEveryRuin(settings, 40, 6);
    }

    private static void assertRoomForEveryRuin(RavineSettings settings, int atLeast, int kindsAtLeast) {
        DiscShape shape = settings.discs();
        int ruins = 0;
        Set<String> kinds = new HashSet<>();
        for (int cz = 0; cz < CELLS; cz++) {
            RavineCell cell = RavineCells.at(SEED, settings, 0, cz).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(settings, LEVEL, cell);
            List<Standing> all = standing(cellDiscs);
            for (Standing ruin : all) {
                Disc disc = ruin.disc();
                double radius = ruin.kind().radius();
                String what = ruin.site() + " on " + disc;
                assertTrue(ruin.fromAxis() + radius + DiscRuinSites.MARGIN <= disc.radius() + 1e-9, what + " reaches into the rim");
                assertTrue(disc.topBlockAt(ruin.fromAxis() + radius) - ruin.ground() <= DiscRuinSites.MAX_STEP, what + " is on ground that steps too far");
                // Open air from the third block over its ground to the highest block a piece of its kind reaches.
                for (int spoke = -1; spoke < 8; spoke++) {
                    double out = spoke < 0 ? 0 : radius;
                    int x = (int) Math.floor(ruin.site().x() + 0.5 + out * Math.cos(Math.PI * spoke / 4));
                    int z = (int) Math.floor(ruin.site().z() + 0.5 + out * Math.sin(Math.PI * spoke / 4));
                    for (int y = ruin.ground() + 3; y <= ruin.ground() + ruin.kind().height(); y++) {
                        assertFalse(Carved.solid(settings, LEVEL, cell, x, y, z), what + " meets rock at " + x + " " + y + " " + z);
                    }
                }
                for (Disc other : cellDiscs.layout().discs()) {
                    double apart = Math.hypot(ruin.site().x() + 0.5 - other.x(), ruin.site().z() + 0.5 - other.z());
                    if (other != disc && other.support() instanceof Disc.Support.Standing stem && apart < radius + shape.stemRadiusFor(other.radius())) {
                        assertTrue(other.undersideAt(shape, 0) <= ruin.ground() || stem.bottom() > ruin.ground() + ruin.kind().height(),
                                what + " has the stem of " + other + " through it");
                    }
                    if (other.support() instanceof Disc.Support.Hanging root && apart < radius + shape.rootRadiusFor(other.radius())) {
                        assertTrue(root.top() <= ruin.ground() || other.floor() > ruin.ground() + ruin.kind().height(),
                                what + " has the root of " + other + " through it");
                    }
                }
                for (Standing other : all) {
                    if (other != ruin && other.index() == ruin.index()) {
                        double apart = Math.hypot(ruin.site().x() - other.site().x(), ruin.site().z() - other.site().z());
                        assertTrue(apart >= radius + other.kind().radius() + DiscRuinSites.MARGIN, what + " overlaps " + other.site());
                    }
                }
                kinds.add(ruin.site().pool().location().getPath());
                ruins++;
            }
        }
        assertTrue(ruins > atLeast, ruins + " ruins in " + CELLS + " holes");
        assertTrue(kinds.size() >= kindsAtLeast, "kinds that found room: " + kinds);
    }

    @Test
    void notEveryDiscHasRuinsAndADiscHasTheSameOnesEveryTime() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        Map<String, int[]> byTheme = new HashMap<>();
        int large = 0;
        int severalOnLarge = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            RavineCell cell = RavineCells.at(SEED, settings, 0, cz).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(settings, LEVEL, cell);
            assertEquals(cellDiscs.ruins(), CellDiscs.of(settings, LEVEL, cell).ruins(), "the same ruins every time the cell's discs are built");
            List<Standing> all = standing(cellDiscs);
            List<Disc> discs = cellDiscs.layout().discs();
            for (int i = 0; i < discs.size(); i++) {
                int index = i;
                long here = all.stream().filter(ruin -> ruin.index() == index).count();
                String theme = cellDiscs.themes().get(i).flatMap(DiscTheme::biome).map(key -> key.location().getPath()).orElse("none");
                int[] counts = byTheme.computeIfAbsent(theme, key -> new int[2]);
                counts[0]++;
                counts[1] += here > 0 ? 1 : 0;
                assertTrue(here <= DiscRuinSites.MAX_PER_DISC, here + " ruins on one disc");
                if (theme.equals("disc_jungle") && discs.get(i).radius() > 45) {
                    large++;
                    severalOnLarge += here > 1 ? 1 : 0;
                }
            }
        }
        double jungle = byTheme.get("disc_jungle")[1] / (double) byTheme.get("disc_jungle")[0];
        double lush = byTheme.get("disc_lush")[1] / (double) byTheme.get("disc_lush")[0];
        assertTrue(jungle > 0.4 && jungle < 0.65, "the share of jungle discs with ruins is about the theme's chance: " + jungle);
        assertTrue(lush > 0.15 && lush < jungle, "fewer lush discs have them: " + lush);
        assertEquals(0, byTheme.get("disc_crystal")[1], "a theme without ruins has none");
        assertTrue(byTheme.get("disc_crystal")[0] > 5, "crystal discs looked at: " + byTheme.get("disc_crystal")[0]);
        assertTrue(severalOnLarge > 0, "of " + large + " large jungle discs, those with more than one ruin: " + severalOnLarge);

        RavineSettings plain = with(DiscThemeTest.COMMON);
        RavineCell cell = RavineCells.at(SEED, plain, 0, 0).orElseThrow();
        assertEquals(List.of(), CellDiscs.of(plain, LEVEL, cell).ruins(), "no theme has ruins");
    }

    @Test
    void aStreamMayRunUnderARuinButAPondMayNotLieUnderOne() throws Exception {
        var ruins = new DiscRuins(1, 1_000_000, List.of(kind("wide", 1, 10, 3)));
        RavineSettings dry = with(ruined(Optional.empty(), ruins));
        RavineSettings streams = with(ruined(Optional.of(new DiscWater(0, 12, 2, 44, 2, 3)), ruins));
        RavineSettings ponds = with(ruined(Optional.of(new DiscWater(0.9F, 12, 0, 40, 2, 3)), ruins));
        int onDry = 0;
        int overStreams = 0;
        int overPonds = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            // The three differ only in their themes, so they have the same holes and discs.
            onDry += CellDiscs.of(dry, LEVEL, RavineCells.at(SEED, dry, 0, cz).orElseThrow()).ruins().size();
            overStreams += CellDiscs.of(streams, LEVEL, RavineCells.at(SEED, streams, 0, cz).orElseThrow()).ruins().size();
            overPonds += CellDiscs.of(ponds, LEVEL, RavineCells.at(SEED, ponds, 0, cz).orElseThrow()).ruins().size();
        }
        assertTrue(onDry > 80, onDry + " ruins on dry discs");
        assertTrue(overStreams > 0.8 * onDry, overStreams + " ruins on discs with streams, " + onDry + " on dry ones");
        assertTrue(overPonds < 0.2 * onDry, overPonds + " ruins on discs that are mostly pond, " + onDry + " on dry ones");
    }

    @Test
    void aBoundCarveHandsOutEachRuinInTheChunkItsMiddleIsIn() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        RavineCarve carve = RavineCarve.MAP_CODEC.codec()
                .parse(JsonOps.INSTANCE, RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, settings).getOrThrow()).getOrThrow();
        RavineCell cell = RavineCells.at(SEED, settings, 0, 0).orElseThrow();
        int chunkX = Math.floorDiv((int) cell.centreX(), 16) * 16;
        int chunkZ = Math.floorDiv((int) cell.centreZ(), 16) * 16;
        assertEquals(List.of(), carve.ruinsIn(chunkX, chunkZ), "nothing before the carve is bound to a level");
        RavineCarve bound = carve.bind(SEED, LEVEL);
        var handed = new ArrayList<RuinSite>();
        int chunks = (int) Math.ceil(settings.maxReach() / 16) + 2;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                int minX = chunkX + dx * 16;
                int minZ = chunkZ + dz * 16;
                for (RuinSite site : bound.ruinsIn(minX, minZ)) {
                    assertTrue(site.x() >= minX && site.x() < minX + 16 && site.z() >= minZ && site.z() < minZ + 16, site + " handed to the chunk at " + minX + " " + minZ);
                    handed.add(site);
                }
            }
        }
        List<RuinSite> all = CellDiscs.of(settings, LEVEL, cell).ruins();
        assertEquals(new HashSet<>(all), new HashSet<>(handed), "every ruin of the hole");
        assertEquals(all.size(), handed.size(), "each of them once");
        assertTrue(all.size() > 10, all.size() + " ruins in the hole");
        int farX = Math.floorDiv((int) (cell.centreX() + settings.maxReach() + 64), 16) * 16;
        assertEquals(List.of(), bound.ruinsIn(farX, chunkZ), "none in a chunk the hole does not reach");
    }
}

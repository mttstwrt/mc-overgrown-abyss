package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.syrval.overgrownabyss.compat.JarRuinPieces;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;

/** The ruins on discs: how they are written, which discs have them, and that each stands where it has the room. */
class DiscRuinsTest {
    static {
        MinecraftBootstrap.init();
    }

    private static final RavineBounds LEVEL = DiscThemeTest.LEVEL;
    private static final long SEED = 11L;
    private static final int CELLS = 4;
    private static final DiscTheme.Ramp EVEN = DiscTheme.Ramp.EVEN;

    private static ResourceKey<StructureTemplatePool> pool(String path) {
        return ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.fromNamespaceAndPath("overgrown_abyss", path));
    }

    private static DiscRuins.Kind kind(String pool, float weight, DiscTheme.Ramp byHeight) {
        return new DiscRuins.Kind(pool(pool), weight, byHeight, 0);
    }

    private static DiscRuins ruins(float chance, int every, DiscTheme.Ramp byHeight, DiscRuins.Kind... kinds) {
        return new DiscRuins(chance, every, byHeight, DiscRuins.Loot.NONE, List.of(kinds), List.of());
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("overgrown_abyss", path));
    }

    private static RuinPieces.Piece piece(String pool, int element, int weight, double radius, int height, int sink) {
        return new RuinPieces.Piece(pool(pool), element, weight, radius, height, sink);
    }

    /** What a level has for one theme's ruins: for each of its kinds, in their order, the pieces given. */
    @SafeVarargs
    private static RuinPieces pieces(DiscRuins ruins, List<RuinPieces.Piece>... ofKinds) {
        var kinds = new ArrayList<RuinPieces.Kind>();
        for (int i = 0; i < ofKinds.length; i++) {
            kinds.add(new RuinPieces.Kind(ruins.kinds().get(i).weight(), ruins.kinds().get(i).byHeight(), false, ofKinds[i]));
        }
        return new RuinPieces(Map.of(ruins, kinds));
    }

    private static DiscTheme ruined(Optional<DiscWater> water, DiscRuins ruins) {
        return new DiscTheme(
                Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE,
                DiscPalette.UNPAINTED, water, List.of(), Optional.of(ruins));
    }

    /** The mod's own hole with other themes. */
    private static RavineSettings with(DiscTheme... themes) throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        return new RavineSettings(
                base.salt(), base.cellSize(), 1F, base.sizeBias(), base.floor(), base.top(), base.cavernRadius(), base.cavernHeight(),
                base.edgeFalloff(), base.discs(), List.of(themes), base.environment(), base.wallNoise(), base.ravine(), base.cone());
    }

    private static CellDiscs discs(RavineSettings settings, int cz, RuinPieces pieces) {
        return CellDiscs.of(settings, LEVEL, RavineCells.at(SEED, settings, 0, cz).orElseThrow(), SurfaceProbe.SOLID, pieces);
    }

    /** One ruin as the tests look at it: its site, the piece it is, and the disc it was found a place on. */
    private record Standing(RuinSite site, RuinPieces.Piece piece, int index, Disc disc, DiscTraits traits) {
        double fromAxis() {
            return Math.hypot(site.x() + 0.5 - disc.x(), site.z() + 0.5 - disc.z());
        }

        int ground() {
            return site.y() - 1 + piece.sink();
        }
    }

    /** Every ruin of a cell with the disc it stands on and the piece it is. */
    private static List<Standing> standing(RavineSettings settings, int cz, CellDiscs cellDiscs, RuinPieces pieces) {
        RavineCell cell = RavineCells.at(SEED, settings, 0, cz).orElseThrow();
        List<Disc> discs = cellDiscs.layout().discs();
        List<DiscTraits> traits = DiscTraits.of(settings, cell, discs);
        List<DiscRuinSites.Placed> placed = DiscRuinSites.onDiscs(settings, LEVEL, cell, cellDiscs.layout(), cellDiscs.themes(), traits, pieces);
        assertEquals(cellDiscs.ruins(), placed.stream().map(DiscRuinSites.Placed::site).toList(), "the cell's ruins are these");
        var found = new ArrayList<Standing>();
        for (DiscRuinSites.Placed each : placed) {
            RuinSite site = each.site();
            RuinPieces.Piece piece = cellDiscs.themes().get(each.disc()).flatMap(DiscTheme::ruins).stream()
                    .flatMap(ruins -> pieces.byRuins().getOrDefault(ruins, List.of()).stream())
                    .flatMap(kind -> kind.pieces().stream())
                    .filter(one -> one.pool().equals(site.pool()) && one.element() == site.element()).findFirst()
                    .orElseThrow(() -> new AssertionError(site + " is no piece of its disc's theme"));
            var here = new Standing(site, piece, each.disc(), discs.get(each.disc()), traits.get(each.disc()));
            assertEquals(here.ground(), discs.get(each.disc()).topBlockAt(Math.max(0, here.fromAxis() - piece.radius())),
                    site + " stands on the lowest ground of its round");
            found.add(here);
        }
        return found;
    }

    @Test
    void ruinsAreWrittenWithTheirKindsAndCheckedWhenRead() {
        var json = JsonParser.parseString("""
                {"chance": 0.5, "by_height": {"bottom": 2, "top": 0.5}, "kinds": [
                  {"pool": "overgrown_abyss:disc_ruins/camp", "weight": 2},
                  {"pool": "overgrown_abyss:disc_ruins/hut", "weight": 1, "sink": 1, "by_height": {"bottom": 0.5, "top": 3}}],
                 "structures": [{"tag": "#overgrown_abyss:on_discs/jungle", "weight": 4}]}""");
        DiscRuins ruins = DiscRuins.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(0.5F, ruins.chance());
        assertEquals(new DiscTheme.Ramp(2, 0.5F), ruins.byHeight());
        assertEquals(new DiscRuins.Kind(pool("disc_ruins/camp"), 2, EVEN, 0), ruins.kinds().get(0),
                "a piece stands on the ground, and its kind is as frequent at every height, unless it says otherwise");
        assertEquals(new DiscRuins.Kind(pool("disc_ruins/hut"), 1, new DiscTheme.Ramp(0.5F, 3), 1), ruins.kinds().get(1));
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("overgrown_abyss", "on_discs/jungle"));
        assertEquals(List.of(new DiscRuins.Borrowed(tag, 4, EVEN)), ruins.structures());
        assertEquals(ruins, DiscRuins.CODEC.parse(JsonOps.INSTANCE, DiscRuins.CODEC.encodeStart(JsonOps.INSTANCE, ruins).getOrThrow()).getOrThrow(),
                "written and read again");
        double largestTop = Math.PI * 256 * 256;
        assertTrue(ruins.every() > largestTop, "without every, no disc is large enough for a second ruin");

        DiscRuins plain = DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"chance\": 0.5, \"kinds\": [{\"pool\": \"minecraft:empty\", \"weight\": 1}]}")).getOrThrow();
        assertEquals(EVEN, plain.byHeight(), "as frequent at every height unless it says otherwise");
        assertEquals(List.of(), plain.structures());
        assertTrue(DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"chance\": 0.5, \"structures\": [{\"tag\": \"#minecraft:village\", \"weight\": 1}]}")).isSuccess(), "borrowed ruins alone");
        assertTrue(DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"chance\": 0.5, \"kinds\": []}")).isError(), "nothing to be");
        assertTrue(DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"chance\": 0.5, \"kinds\": [{\"pool\": \"minecraft:empty\", \"weight\": 0}],"
                        + " \"structures\": [{\"tag\": \"#minecraft:village\", \"weight\": 0}]}")).isError(), "nothing with a weight");
        assertTrue(DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"chance\": 2, \"kinds\": [{\"pool\": \"minecraft:empty\", \"weight\": 1}]}")).isError(), "a chance above 1");
    }

    private static DiscRuins.Loot loot(float depth, float distance, float fair, float rich) {
        return new DiscRuins.Loot(
                List.of(new DiscRuins.Loot.Tier(table("poor"), 0), new DiscRuins.Loot.Tier(table("fair"), fair), new DiscRuins.Loot.Tier(table("rich"), rich)),
                depth, distance);
    }

    private static boolean readsAsRuins(String json) {
        return DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isSuccess();
    }

    @Test
    void lootIsWrittenAsTablesFromThePoorestToTheRichest() {
        DiscRuins ruins = DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"chance": 0.5, "loot": {"depth": 3, "tables": [{"table": "overgrown_abyss:poor"}, {"table": "overgrown_abyss:rich", "from": 0.75}]},
                 "kinds": [{"pool": "minecraft:empty", "weight": 1}]}""")).getOrThrow();
        var expected = new DiscRuins.Loot(List.of(new DiscRuins.Loot.Tier(table("poor"), 0), new DiscRuins.Loot.Tier(table("rich"), 0.75F)), 3, 1);
        assertEquals(expected, ruins.loot(), "the first table is from the lowest rank and distance counts for 1, unless they say otherwise");
        assertEquals(ruins, DiscRuins.CODEC.parse(JsonOps.INSTANCE, DiscRuins.CODEC.encodeStart(JsonOps.INSTANCE, ruins).getOrThrow()).getOrThrow(),
                "written and read again");

        String kinds = "\"kinds\": [{\"pool\": \"minecraft:empty\", \"weight\": 1}]}";
        DiscRuins plain = DiscRuins.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"chance\": 0.5, " + kinds)).getOrThrow();
        assertEquals(Optional.empty(), plain.loot().tableFor(new DiscTraits(0, 1, 0.5)), "no tables, so a ruin's chests are left as its pool made them");
        assertFalse(DiscRuins.CODEC.encodeStart(JsonOps.INSTANCE, plain).getOrThrow().getAsJsonObject().has("loot"), "and nothing is written for it");
        assertFalse(readsAsRuins("{\"chance\": 0.5, \"loot\": {\"tables\": []}, " + kinds), "loot with no table");
        assertFalse(readsAsRuins("{\"chance\": 0.5, \"loot\": {\"tables\": [{\"table\": \"minecraft:empty\"}], \"depth\": 0, \"distance\": 0}, " + kinds),
                "neither depth nor distance counting");
        assertFalse(readsAsRuins("{\"chance\": 0.5, \"loot\": {\"tables\": [{\"table\": \"minecraft:a\", \"from\": 0.5}, {\"table\": \"minecraft:b\", \"from\": 0.5}]}, " + kinds),
                "two tables from the same rank, of which the first would never be held");
        assertFalse(readsAsRuins("{\"chance\": 0.5, \"loot\": {\"tables\": [{\"table\": \"minecraft:a\", \"from\": 0.5}, {\"table\": \"minecraft:b\"}]}, " + kinds),
                "tables out of order");
        assertFalse(readsAsRuins("{\"chance\": 0.5, \"loot\": {\"tables\": [{\"table\": \"minecraft:a\", \"from\": 1.5}]}, " + kinds), "a rank above 1");
    }

    @Test
    void aDiscsTableRisesWithItsDepthAndWithItsDistanceFromTheCentre() {
        DiscRuins.Loot loot = loot(2, 1, 0.4F, 0.8F);
        assertEquals(0, loot.rank(new DiscTraits(1, 0, 0.5)), 1e-9, "the highest disc, at the centre");
        assertEquals(1, loot.rank(new DiscTraits(0, 1, 0.5)), 1e-9, "the lowest, at the edge");
        assertEquals(2 / 3.0, loot.rank(new DiscTraits(0, 0, 0.5)), 1e-9, "depth counts twice what distance does");
        assertEquals(1 / 3.0, loot.rank(new DiscTraits(1, 1, 0.5)), 1e-9);
        assertEquals(table("poor"), loot.tableFor(new DiscTraits(1, 0, 0.5)).orElseThrow());
        assertEquals(table("rich"), loot.tableFor(new DiscTraits(0, 1, 0.5)).orElseThrow());
        assertEquals(table("fair"), loot.tableFor(new DiscTraits(0, 0, 0.5)).orElseThrow(), "the lowest disc is not the richest at the centre");
        assertEquals(table("poor"), loot.tableFor(new DiscTraits(1, 1, 0.5)).orElseThrow(), "nor the highest at the edge");
        assertEquals(table("fair"), loot.tableFor(new DiscTraits(0.7, 0.75, 0.5)).orElseThrow(), "a rank of 0.45 is past where the fair table begins");
        assertEquals(table("poor"), loot.tableFor(new DiscTraits(0.7, 0.45, 0.5)).orElseThrow(), "and one of 0.35 is not");
        assertEquals(loot.tableFor(new DiscTraits(0.3, 0.4, 0)), loot.tableFor(new DiscTraits(0.3, 0.4, 1)), "a disc's size does not count");

        DiscRuins.Loot byDepth = loot(1, 0, 0.4F, 0.8F);
        assertEquals(table("rich"), byDepth.tableFor(new DiscTraits(0.1, 0, 0.5)).orElseThrow(), "depth alone");
        assertEquals(table("poor"), byDepth.tableFor(new DiscTraits(0.9, 1, 0.5)).orElseThrow());
        DiscRuins.Loot byDistance = loot(0, 1, 0.4F, 0.8F);
        assertEquals(table("rich"), byDistance.tableFor(new DiscTraits(1, 0.9, 0.5)).orElseThrow(), "distance alone");
        assertEquals(table("poor"), byDistance.tableFor(new DiscTraits(0, 0.1, 0.5)).orElseThrow());

        var onlyTheRich = new DiscRuins.Loot(List.of(new DiscRuins.Loot.Tier(table("rich"), 0.8F)), 2, 1);
        assertEquals(Optional.of(table("rich")), onlyTheRich.tableFor(new DiscTraits(0, 1, 0.5)));
        assertEquals(Optional.empty(), onlyTheRich.tableFor(new DiscTraits(1, 0, 0.5)), "a disc whose rank reaches no table has none");
    }

    @Test
    void aRuinOfTheThemesOwnHoldsItsDiscsTableAndABorrowedOneKeepsItsOwn() throws Exception {
        DiscRuins.Loot loot = loot(2, 1, 0.5F, 0.8F);
        DiscRuins ruins = new DiscRuins(1, 1500, EVEN, loot, List.of(kind("own", 1, EVEN)), List.of());
        var pieces = new RuinPieces(Map.of(ruins, List.of(
                new RuinPieces.Kind(1, EVEN, false, List.of(piece("own", 0, 1, 5, 6, 0))),
                new RuinPieces.Kind(1, EVEN, true, List.of(piece("lent", 0, 1, 5, 6, 0))))));
        RavineSettings settings = with(ruined(Optional.empty(), ruins));
        var heights = new HashMap<ResourceKey<LootTable>, List<Double>>();
        int lent = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            for (Standing ruin : standing(settings, cz, discs(settings, cz, pieces), pieces)) {
                if (ruin.site().pool().equals(pool("lent"))) {
                    assertEquals(Optional.empty(), ruin.site().loot(), "a borrowed ruin is given no table");
                    lent++;
                } else {
                    assertEquals(loot.tableFor(ruin.traits()), ruin.site().loot(), "the table of the disc that " + ruin.site() + " stands on");
                    heights.computeIfAbsent(ruin.site().loot().orElseThrow(), table -> new ArrayList<>()).add(ruin.traits().height());
                }
            }
        }
        assertTrue(lent > 20, lent + " borrowed ruins");
        assertEquals(Set.of(table("poor"), table("fair"), table("rich")), heights.keySet(), "every table is some ruin's");
        assertTrue(heights.values().stream().allMatch(of -> of.size() > 10), "and each has its share: " + heights.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().location().getPath(), entry -> entry.getValue().size())));
        double poor = heights.get(table("poor")).stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double rich = heights.get(table("rich")).stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        assertTrue(rich < 0.3 && poor > 0.5, "the rich table lies low (" + rich + ") and the poor one high (" + poor + ")");
    }

    @Test
    void thingsAreTriedInAnOrderDrawnByWeight() {
        record Thing(String name, double weight) {}
        var heavy = new Thing("heavy", 3);
        var light = new Thing("light", 1);
        assertEquals(List.of(heavy, light), DiscRuinSites.inOrder(List.of(light, heavy), Thing::weight, thing -> 0.5), "on the same draw the heavier comes first");
        assertEquals(List.of(light, heavy), DiscRuinSites.inOrder(List.of(light, heavy), Thing::weight, thing -> thing == light ? 0.1 : 0.9),
                "but a low draw beats a high one");
        int trials = 20_000;
        int heavyFirst = 0;
        for (int i = 0; i < trials; i++) {
            int trial = i;
            heavyFirst += DiscRuinSites.inOrder(List.of(light, heavy), Thing::weight, thing -> RavineCells.unit(99L, 2 * trial + (thing == light ? 0 : 1)))
                    .getFirst() == heavy ? 1 : 0;
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
        RavineSettings settings = DiscThemeTest.shipped();
        List<Standing> all = assertRoomForEveryRuin(settings, JarRuinPieces.of(settings));
        Set<String> kinds = all.stream().map(ruin -> ruin.site().pool().location().getPath()).collect(Collectors.toSet());
        assertTrue(all.size() > 60, all.size() + " ruins in " + CELLS + " holes");
        assertTrue(kinds.size() >= 8, "kinds that found room: " + kinds);
        assertTrue(all.stream().anyMatch(ruin -> ruin.piece().sink() > 0), "pieces with a layer in the ground among them");
    }

    @Test
    void noRuinStandsWhereARootComesDown() throws Exception {
        RavineSettings settings = withHangingDiscs();
        long roots = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            roots += discs(settings, cz, RuinPieces.NONE).layout().discs().stream().filter(disc -> disc.support() instanceof Disc.Support.Hanging).count();
        }
        assertTrue(roots > 20, roots + " hanging discs");
        List<Standing> all = assertRoomForEveryRuin(settings, JarRuinPieces.of(settings));
        assertTrue(all.size() > 40, all.size() + " ruins in " + CELLS + " holes");
    }

    /** Every ruin of the first holes, each seen to have the room its piece needs. */
    private static List<Standing> assertRoomForEveryRuin(RavineSettings settings, RuinPieces pieces) {
        DiscShape shape = settings.discs();
        var ruins = new ArrayList<Standing>();
        for (int cz = 0; cz < CELLS; cz++) {
            RavineCell cell = RavineCells.at(SEED, settings, 0, cz).orElseThrow();
            CellDiscs cellDiscs = discs(settings, cz, pieces);
            List<Standing> all = standing(settings, cz, cellDiscs, pieces);
            for (Standing ruin : all) {
                Disc disc = ruin.disc();
                double radius = ruin.piece().radius();
                int height = ruin.piece().height();
                String what = ruin.site() + " on " + disc;
                assertTrue(ruin.fromAxis() + radius + DiscRuinSites.MARGIN <= disc.radius() + 1e-9, what + " reaches into the rim");
                assertTrue(disc.topBlockAt(ruin.fromAxis() + radius) - ruin.ground() <= DiscRuinSites.MAX_STEP, what + " is on ground that steps too far");
                for (int spoke = -1; spoke < 8; spoke++) {
                    double out = spoke < 0 ? 0 : radius;
                    int x = (int) Math.floor(ruin.site().x() + 0.5 + out * Math.cos(Math.PI * spoke / 4));
                    int z = (int) Math.floor(ruin.site().z() + 0.5 + out * Math.sin(Math.PI * spoke / 4));
                    // Open air from the third block over its ground to the highest block the piece reaches.
                    for (int y = ruin.ground() + 3; y <= ruin.ground() + height; y++) {
                        assertFalse(Carved.solid(settings, LEVEL, cell, x, y, z), what + " meets rock at " + x + " " + y + " " + z);
                    }
                    // Rock at every layer of the piece that lies in the ground.
                    for (int y = ruin.ground() - ruin.piece().sink(); y < ruin.ground(); y++) {
                        assertTrue(Carved.solid(settings, LEVEL, cell, x, y, z), what + " has no rock at " + x + " " + y + " " + z);
                    }
                }
                for (Disc other : cellDiscs.layout().discs()) {
                    double apart = Math.hypot(ruin.site().x() + 0.5 - other.x(), ruin.site().z() + 0.5 - other.z());
                    if (other != disc && other.support() instanceof Disc.Support.Standing stem && apart < radius + shape.stemRadiusFor(other.radius())) {
                        assertTrue(other.undersideAt(shape, 0) <= ruin.ground() || stem.bottom() > ruin.ground() + height,
                                what + " has the stem of " + other + " through it");
                    }
                    if (other.support() instanceof Disc.Support.Hanging root && apart < radius + shape.rootRadiusFor(other.radius())) {
                        assertTrue(root.top() <= ruin.ground() || other.floor() > ruin.ground() + height,
                                what + " has the root of " + other + " through it");
                    }
                }
                for (Standing other : all) {
                    // Of its own disc, or of another whose platform runs into this one: wherever the two pieces share a height.
                    boolean level = other.site().y() <= ruin.ground() + Math.max(height, 3) && other.ground() + Math.max(other.piece().height(), 3) >= ruin.site().y();
                    if (other != ruin && (other.index() == ruin.index() || level)) {
                        double apart = Math.hypot(ruin.site().x() - other.site().x(), ruin.site().z() - other.site().z());
                        assertTrue(apart >= radius + other.piece().radius() + DiscRuinSites.MARGIN, what + " overlaps " + other.site());
                    }
                }
            }
            ruins.addAll(all);
        }
        return ruins;
    }

    @Test
    void notEveryDiscHasRuinsAndADiscHasTheSameOnesEveryTime() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        RuinPieces pieces = JarRuinPieces.of(settings);
        Map<String, int[]> byTheme = new HashMap<>();
        int large = 0;
        int severalOnLarge = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            CellDiscs cellDiscs = discs(settings, cz, pieces);
            assertEquals(cellDiscs.ruins(), discs(settings, cz, pieces).ruins(), "the same ruins every time the cell's discs are built");
            List<Standing> all = standing(settings, cz, cellDiscs, pieces);
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
        assertTrue(jungle > 0.4 && jungle < 0.75, "the share of jungle discs with ruins: " + jungle);
        assertTrue(lush > 0.15 && lush < jungle, "fewer lush discs have them: " + lush);
        assertEquals(0, byTheme.get("disc_crystal")[1], "a theme without ruins has none");
        assertTrue(byTheme.get("disc_crystal")[0] > 5, "crystal discs looked at: " + byTheme.get("disc_crystal")[0]);
        assertTrue(severalOnLarge > 0, "of " + large + " large jungle discs, those with more than one ruin: " + severalOnLarge);

        assertEquals(List.of(), discs(settings, 0, RuinPieces.NONE).ruins(), "a level that has no pieces for them has no ruins");
        RavineSettings plain = with(DiscThemeTest.COMMON);
        assertEquals(List.of(), discs(plain, 0, pieces).ruins(), "no theme has ruins");
    }

    // A small low piece, which finds room on nearly every disc, so that what the tests below count is the draw and not the room.
    private static List<RuinPieces.Piece> small(String pool) {
        return List.of(piece(pool, 0, 1, 5, 4, 0));
    }

    /** For each third of a hole's height, from the lowest: its discs, those of them with ruins, and its ruins. */
    private static int[][] byThird(RavineSettings settings, RuinPieces pieces) {
        int[][] counts = new int[3][3];
        for (int cz = 0; cz < CELLS; cz++) {
            CellDiscs cellDiscs = discs(settings, cz, pieces);
            List<Standing> all = standing(settings, cz, cellDiscs, pieces);
            List<DiscTraits> traits = DiscTraits.of(settings, RavineCells.at(SEED, settings, 0, cz).orElseThrow(), cellDiscs.layout().discs());
            for (int i = 0; i < traits.size(); i++) {
                int index = i;
                int here = (int) all.stream().filter(ruin -> ruin.index() == index).count();
                int third = Math.min(2, (int) (traits.get(i).height() * 3));
                counts[third][0]++;
                counts[third][1] += here > 0 ? 1 : 0;
                counts[third][2] += here;
            }
        }
        return counts;
    }

    @Test
    void ruinsAreMoreFrequentTowardTheBottomWhereTheirThemeSaysSo() throws Exception {
        DiscRuins even = ruins(0.5F, 1500, EVEN, kind("small", 1, EVEN));
        DiscRuins deeper = ruins(0.5F, 1500, new DiscTheme.Ramp(2, 0.2F), kind("small", 1, EVEN));
        int[][] flat = byThird(with(ruined(Optional.empty(), even)), pieces(even, small("small")));
        int[][] ramped = byThird(with(ruined(Optional.empty(), deeper)), pieces(deeper, small("small")));
        String counts = "discs, discs with ruins and ruins in the lowest third " + Arrays.toString(ramped[0]) + " and the highest " + Arrays.toString(ramped[2])
                + "; with no ramp " + Arrays.toString(flat[0]) + " and " + Arrays.toString(flat[2]);
        assertTrue(Math.min(flat[0][0], flat[2][0]) > 40, counts);
        assertEquals(flat[0][1] / (double) flat[0][0], flat[2][1] / (double) flat[2][0], 0.15, "with no ramp, about the same share of discs high and low: " + counts);
        // The ramp runs from 2 to 1.4 over the lowest third and from 0.8 to 0.2 over the highest, on a chance of a half.
        assertTrue(ramped[0][1] / (double) ramped[0][0] > 0.7, "most low discs have ruins: " + counts);
        assertTrue(ramped[2][1] / (double) ramped[2][0] < 0.35, "few high discs have ruins: " + counts);
        // Lower discs are larger and hold more ruins anyway, so a disc with ruins is set beside one at its height with no ramp.
        assertTrue(ramped[0][2] / (double) ramped[0][1] > 1.15 * flat[0][2] / flat[0][1], "a low disc that has ruins has more of them: " + counts);
        assertTrue(ramped[2][2] / (double) ramped[2][1] < 0.85 * flat[2][2] / flat[2][1], "and a high one fewer: " + counts);
    }

    @Test
    void aKindGathersWhereItsOwnWeightIsLarger() throws Exception {
        DiscRuins ruins = ruins(1, 1500, EVEN, kind("deep", 1, new DiscTheme.Ramp(8, 0)), kind("high", 1, new DiscTheme.Ramp(0, 8)));
        RuinPieces pieces = pieces(ruins, small("deep"), small("high"));
        List<Standing> all = assertRoomForEveryRuin(with(ruined(Optional.empty(), ruins)), pieces);
        List<Standing> deep = all.stream().filter(ruin -> ruin.site().pool().equals(pool("deep"))).toList();
        List<Standing> high = all.stream().filter(ruin -> ruin.site().pool().equals(pool("high"))).toList();
        assertTrue(deep.size() > 50 && high.size() > 50, deep.size() + " of the deep kind and " + high.size() + " of the high one");
        double deepAt = deep.stream().mapToDouble(ruin -> ruin.traits().height()).average().orElseThrow();
        double highAt = high.stream().mapToDouble(ruin -> ruin.traits().height()).average().orElseThrow();
        // Two thirds apart if discs were as many at every height; there are more of them low down.
        assertTrue(deepAt < 0.35 && highAt > 0.55, "the deep kind stands at " + deepAt + " of its hole's height on average and the high one at " + highAt);
        assertTrue(deep.stream().noneMatch(ruin -> ruin.traits().height() == 1), "a kind with no weight at the top is not on the highest disc");
        assertTrue(high.stream().noneMatch(ruin -> ruin.traits().height() == 0), "nor one with none at the bottom on the lowest");
    }

    private static List<Standing> withSink(int sink) throws Exception {
        DiscRuins ruins = ruins(1, 1500, EVEN, new DiscRuins.Kind(pool("cellar"), 1, EVEN, sink));
        return assertRoomForEveryRuin(with(ruined(Optional.empty(), ruins)), pieces(ruins, List.of(piece("cellar", 0, 1, 6, 5, sink))));
    }

    @Test
    void aPieceWithLayersInTheGroundStandsOnlyWhereTheRockIsThatDeep() throws Exception {
        List<Standing> onTheGround = withSink(0);
        List<Standing> oneLayerIn = withSink(1);
        List<Standing> twoLayersIn = withSink(2);
        List<Standing> withACellar = withSink(5);
        long groundOffTheStem = onTheGround.stream().filter(ruin -> ruin.fromAxis() >= 1).count();
        long cellarOffTheStem = withACellar.stream().filter(ruin -> ruin.fromAxis() >= 1).count();
        String counts = onTheGround.size() + " on the ground (" + groundOffTheStem + " of them away from the middle of their disc), " + oneLayerIn.size()
                + " a layer in it, " + twoLayersIn.size() + " two layers, " + withACellar.size() + " five (" + cellarOffTheStem + ")";
        assertTrue(onTheGround.size() > 150, counts);
        assertEquals(onTheGround.stream().map(ruin -> List.of(ruin.site().x(), ruin.ground(), ruin.site().z())).toList(),
                oneLayerIn.stream().map(ruin -> List.of(ruin.site().x(), ruin.ground(), ruin.site().z())).toList(),
                "a platform always has rock under a piece's one layer in it");
        assertTrue(twoLayersIn.size() > 0.8 * onTheGround.size(), "nearly always under two: " + counts);
        // A platform is 4 blocks thick, so five layers only have rock where the stem flares out under it, which is where such a
        // piece is tried first, or where the disc lies in the hole's wall, as much of many discs does. That each of these has
        // the rock was seen above, column by column.
        assertTrue(cellarOffTheStem > 0 && cellarOffTheStem < 0.6 * groundOffTheStem, "away from the stem fewer places are that deep: " + counts);
        assertTrue(withACellar.size() - cellarOffTheStem > 100, "over the stem most discs are: " + counts);
        assertTrue(onTheGround.size() - groundOffTheStem < 20, "which a piece on the ground is not tried at first: " + counts);
    }

    @Test
    void aPieceStandsOnlyWhereItFitsAndAnotherOfItsKindTakesItsPlace() throws Exception {
        DiscRuins ruins = ruins(1, 1500, EVEN, kind("mixed", 1, EVEN));
        RuinPieces.Piece giant = piece("mixed", 0, 100, 30, 200, 0);
        RuinPieces.Piece small = piece("mixed", 1, 1, 5, 4, 0);
        RavineSettings settings = with(ruined(Optional.empty(), ruins));
        List<RuinSite> withBoth = new ArrayList<>();
        List<RuinSite> smallAlone = new ArrayList<>();
        for (int cz = 0; cz < CELLS; cz++) {
            withBoth.addAll(discs(settings, cz, pieces(ruins, List.of(giant, small))).ruins());
            smallAlone.addAll(discs(settings, cz, pieces(ruins, List.of(small))).ruins());
        }
        assertTrue(smallAlone.size() > 150, smallAlone.size() + " ruins");
        assertEquals(smallAlone, withBoth, "a piece far too tall for any disc, drawn first a hundred times in a hundred and one, leaves every place to the other");

        DiscRuins twoKinds = ruins(1, 1500, EVEN, kind("none", 1000, EVEN), kind("mixed", 1, EVEN));
        RavineSettings two = with(ruined(Optional.empty(), twoKinds));
        List<RuinSite> besideAnEmptyKind = new ArrayList<>();
        for (int cz = 0; cz < CELLS; cz++) {
            besideAnEmptyKind.addAll(discs(two, cz, pieces(twoKinds, List.of(), List.of(small))).ruins());
        }
        assertEquals(smallAlone, besideAnEmptyKind, "a kind the level has no pieces for is passed over");
    }

    @Test
    void aStreamMayRunUnderARuinButAPondMayNotLieUnderOne() throws Exception {
        DiscRuins ruins = ruins(1, 1_000_000, EVEN, kind("wide", 1, EVEN));
        RuinPieces pieces = pieces(ruins, List.of(piece("wide", 0, 1, 10, 3, 0)));
        RavineSettings dry = with(ruined(Optional.empty(), ruins));
        RavineSettings streams = with(ruined(Optional.of(new DiscWater(0, 12, 2, 44, 2, 3)), ruins));
        RavineSettings ponds = with(ruined(Optional.of(new DiscWater(0.9F, 12, 0, 40, 2, 3)), ruins));
        int onDry = 0;
        int overStreams = 0;
        int overPonds = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            // The three differ only in their themes, so they have the same holes and discs.
            onDry += discs(dry, cz, pieces).ruins().size();
            overStreams += discs(streams, cz, pieces).ruins().size();
            overPonds += discs(ponds, cz, pieces).ruins().size();
        }
        assertTrue(onDry > 80, onDry + " ruins on dry discs");
        assertTrue(overStreams > 0.8 * onDry, overStreams + " ruins on discs with streams, " + onDry + " on dry ones");
        assertTrue(overPonds < 0.2 * onDry, overPonds + " ruins on discs that are mostly pond, " + onDry + " on dry ones");
    }

    @Test
    void aBoundCarveHandsOutEachRuinInTheChunkItsMiddleIsIn() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        RuinPieces pieces = JarRuinPieces.of(settings);
        RavineCarve carve = RavineCarve.MAP_CODEC.codec()
                .parse(JsonOps.INSTANCE, RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, settings).getOrThrow()).getOrThrow();
        RavineCell cell = RavineCells.at(SEED, settings, 0, 0).orElseThrow();
        int chunkX = Math.floorDiv((int) cell.centreX(), 16) * 16;
        int chunkZ = Math.floorDiv((int) cell.centreZ(), 16) * 16;
        assertEquals(List.of(), carve.ruinsIn(chunkX, chunkZ), "nothing before the carve is bound to a level");
        RavineCarve bound = carve.bind(SEED, LEVEL);
        int chunks = (int) Math.ceil(settings.maxReach() / 16) + 2;
        assertEquals(List.of(), ruinsRound(bound, chunkX, chunkZ, chunks), "nor before the level's pieces are known");
        bound.furnishRuins(pieces);
        List<RuinSite> handed = ruinsRound(bound, chunkX, chunkZ, chunks);
        List<RuinSite> all = discs(settings, 0, pieces).ruins();
        assertEquals(new HashSet<>(all), new HashSet<>(handed), "every ruin of the hole");
        assertEquals(all.size(), handed.size(), "each of them once");
        assertTrue(all.size() > 10, all.size() + " ruins in the hole");
        int farX = Math.floorDiv((int) (cell.centreX() + settings.maxReach() + 64), 16) * 16;
        assertEquals(List.of(), bound.ruinsIn(farX, chunkZ), "none in a chunk the hole does not reach");
    }

    // What the chunks round one hand out, each ruin seen to be in the chunk that handed it out.
    private static List<RuinSite> ruinsRound(RavineCarve carve, int chunkX, int chunkZ, int chunks) {
        var handed = new ArrayList<RuinSite>();
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                int minX = chunkX + dx * 16;
                int minZ = chunkZ + dz * 16;
                for (RuinSite site : carve.ruinsIn(minX, minZ)) {
                    assertTrue(site.x() >= minX && site.x() < minX + 16 && site.z() >= minZ && site.z() < minZ + 16, site + " handed to the chunk at " + minX + " " + minZ);
                    handed.add(site);
                }
            }
        }
        return handed;
    }
}

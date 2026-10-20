package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.syrval.overgrownabyss.compat.JarRuinPieces;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

/**
 * The ruins on discs: how they are written, which discs have them, and that each stands where it has the room. And the troves,
 * which are a second list of the same kind on a theme.
 */
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
        return new DiscRuins.Kind(pool(pool), weight, byHeight, 0, List.of());
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
        return ruined(water, ruins, Optional.empty());
    }

    private static DiscTheme ruined(Optional<DiscWater> water, DiscRuins ruins, Optional<DiscRuins> troves) {
        return new DiscTheme(
                Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE,
                DiscPalette.UNPAINTED, water, List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.of(ruins), troves, Optional.empty());
    }

    /** The mod's own hole with other themes. */
    private static RavineSettings with(DiscTheme... themes) throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        return new RavineSettings(
                base.salt(), base.cellSize(), 1F, base.sizeBias(), base.floor(), base.top(), base.cavernRadius(), base.cavernHeight(),
                base.edgeFalloff(), base.discs(), List.of(themes), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
    }

    private static CellDiscs discs(RavineSettings settings, int cz, RuinPieces pieces) {
        return CellDiscs.of(settings, LEVEL, RavineCells.at(SEED, settings, 0, cz).orElseThrow(), SurfaceProbe.SOLID, pieces);
    }

    /** One ruin or trove as the tests look at it: its site, the piece it is, and the disc it was found a place on. */
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
        return standing(DiscSites.RUINS, settings, cz, cellDiscs, pieces);
    }

    /** The same of either of a cell's two lists. */
    private static List<Standing> standing(DiscSites which, RavineSettings settings, int cz, CellDiscs cellDiscs, RuinPieces pieces) {
        RavineCell cell = RavineCells.at(SEED, settings, 0, cz).orElseThrow();
        List<Disc> discs = cellDiscs.layout().discs();
        List<DiscTraits> traits = DiscTraits.of(settings, cell, discs);
        DiscRuinSites.Stood stood = DiscRuinSites.onDiscs(settings, LEVEL, cell, cellDiscs.layout(), cellDiscs.themes(), traits, pieces, cellDiscs.roots());
        List<DiscRuinSites.Placed> placed = which == DiscSites.RUINS ? stood.ruins() : stood.troves();
        assertEquals(cellDiscs.sites(which), placed.stream().map(DiscRuinSites.Placed::site).toList(), "the cell's " + which.getSerializedName() + " are these");
        var found = new ArrayList<Standing>();
        for (DiscRuinSites.Placed each : placed) {
            RuinSite site = each.site();
            RuinPieces.Piece piece = cellDiscs.themes().get(each.disc()).flatMap(which::of).stream()
                    .flatMap(ruins -> pieces.byRuins().getOrDefault(ruins, List.of()).stream())
                    .flatMap(kind -> kind.pieces().stream())
                    .filter(one -> one.pool().equals(site.pool()) && one.element() == site.element()).findFirst()
                    .orElseThrow(() -> new AssertionError(site + " is no piece of its disc's theme"));
            assertEquals(piece.radius(), each.radius(), site + " is given the round of ground its piece asks for");
            var here = new Standing(site, piece, each.disc(), discs.get(each.disc()), traits.get(each.disc()));
            assertEquals(here.ground(), discs.get(each.disc()).topBlockAt(Math.max(0, here.fromAxis() - piece.radius())),
                    site + " stands on the lowest ground of its round");
            found.add(here);
        }
        return found;
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
                base.edgeFalloff(), base.discs(), base.discThemes(), base.environment(), base.wallNoise(), base.ravine(), Optional.of(hanging), base.dressing());
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
        return assertRoomForEverything(settings, pieces).get(DiscSites.RUINS);
    }

    /** Every ruin and every trove of the first holes, each seen to have the room its piece needs and to keep clear of all the others. */
    private static Map<DiscSites, List<Standing>> assertRoomForEverything(RavineSettings settings, RuinPieces pieces) {
        DiscShape shape = settings.discs();
        var ruins = new ArrayList<Standing>();
        var troves = new ArrayList<Standing>();
        for (int cz = 0; cz < CELLS; cz++) {
            RavineCell cell = RavineCells.at(SEED, settings, 0, cz).orElseThrow();
            CellDiscs cellDiscs = discs(settings, cz, pieces);
            List<Standing> ofRuins = standing(DiscSites.RUINS, settings, cz, cellDiscs, pieces);
            List<Standing> ofTroves = standing(DiscSites.TROVES, settings, cz, cellDiscs, pieces);
            var all = new ArrayList<>(ofRuins);
            all.addAll(ofTroves);
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
            ruins.addAll(ofRuins);
            troves.addAll(ofTroves);
        }
        return Map.of(DiscSites.RUINS, ruins, DiscSites.TROVES, troves);
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


    private static List<Standing> withSink(int sink) throws Exception {
        DiscRuins ruins = ruins(1, 1500, EVEN, new DiscRuins.Kind(pool("cellar"), 1, EVEN, sink, List.of()));
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
        RavineSettings streams = with(ruined(Optional.of(new DiscWater(0, 12, 2, 44, 2, 3, DiscWater.Fluid.WATER, 0)), ruins));
        RavineSettings ponds = with(ruined(Optional.of(new DiscWater(0.9F, 12, 0, 40, 2, 3, DiscWater.Fluid.WATER, 0)), ruins));
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
        assertEquals(List.of(), carve.sitesIn(DiscSites.RUINS, chunkX, chunkZ), "nothing before the carve is bound to a level");
        RavineCarve bound = carve.bind(SEED, LEVEL);
        int chunks = (int) Math.ceil(settings.maxReach() / 16) + 2;
        assertEquals(List.of(), sitesRound(DiscSites.RUINS, bound, chunkX, chunkZ, chunks), "nor before the level's pieces are known");
        bound.furnishRuins(pieces);
        List<RuinSite> handed = sitesRound(DiscSites.RUINS, bound, chunkX, chunkZ, chunks);
        List<RuinSite> all = discs(settings, 0, pieces).ruins();
        assertEquals(new HashSet<>(all), new HashSet<>(handed), "every ruin of the hole");
        assertEquals(all.size(), handed.size(), "each of them once");
        assertTrue(all.size() > 10, all.size() + " ruins in the hole");
        int farX = Math.floorDiv((int) (cell.centreX() + settings.maxReach() + 64), 16) * 16;
        assertEquals(List.of(), bound.sitesIn(DiscSites.RUINS, farX, chunkZ), "none in a chunk the hole does not reach");

        List<RuinSite> troves = discs(settings, 0, pieces).troves();
        List<RuinSite> handedAsTroves = sitesRound(DiscSites.TROVES, bound, chunkX, chunkZ, chunks);
        assertEquals(new HashSet<>(troves), new HashSet<>(handedAsTroves), "every trove of the hole, to the structure that builds troves");
        assertEquals(troves.size(), handedAsTroves.size(), "each of them once");
        assertTrue(troves.size() > 10, troves.size() + " troves in the hole");
        assertTrue(Collections.disjoint(handed, handedAsTroves), "and none of them to the one that builds ruins");
    }

    // A piece the size of a trove, which finds room nearly anywhere.
    private static List<RuinPieces.Piece> tiny(String pool) {
        return List.of(piece(pool, 0, 1, 1.5, 3, 0));
    }

    // A theme with troves and no ruins.
    private static DiscTheme troved(DiscRuins troves) {
        return new DiscTheme(
                Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE,
                DiscPalette.UNPAINTED, Optional.empty(), List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.of(troves), Optional.empty());
    }

    private static RuinPieces piecesOf(DiscRuins ruins, List<RuinPieces.Piece> ofRuins, DiscRuins troves, List<RuinPieces.Piece> ofTroves) {
        var both = new HashMap<>(pieces(ruins, ofRuins).byRuins());
        both.putAll(pieces(troves, ofTroves).byRuins());
        return new RuinPieces(both);
    }


    @Test
    void aThemeGivenTrovesKeepsEveryRuinWhereItWasAndItsTrovesStandClearOfThem() throws Exception {
        DiscRuins ruins = ruins(1, 1500, EVEN, kind("small", 1, EVEN));
        DiscRuins troves = ruins(1, 800, EVEN, kind("tiny", 1, EVEN));
        RuinPieces pieces = piecesOf(ruins, small("small"), troves, tiny("tiny"));
        RavineSettings alone = with(ruined(Optional.empty(), ruins));
        RavineSettings both = with(ruined(Optional.empty(), ruins, Optional.of(troves)));
        int found = 0;
        int kept = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            CellDiscs withTroves = discs(both, cz, pieces);
            CellDiscs without = discs(alone, cz, pieces);
            assertEquals(without.ruins(), withTroves.ruins(), "the same ruins with troves as without");
            assertEquals(List.of(), without.troves(), "a theme that names none has none");
            assertTrue(Collections.disjoint(withTroves.ruins(), withTroves.troves()), "a trove is not among the ruins");
            found += withTroves.troves().size();
            kept += withTroves.ruins().size();
        }
        assertTrue(found > 300 && kept > 150, found + " troves beside " + kept + " ruins");
        // That each keeps the margin from every ruin and every other trove, with the room its piece needs.
        Map<DiscSites, List<Standing>> stood = assertRoomForEverything(both, pieces);
        assertEquals(found, stood.get(DiscSites.TROVES).size());
        // Drawn apart from the ruins: the very same list stood as a theme's troves comes to other places than it does as its ruins.
        RavineSettings asTroves = with(troved(ruins));
        RuinPieces same = pieces(ruins, small("small"));
        var places = new HashSet<List<Integer>>();
        int asTroveSites = 0;
        int shared = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            for (RuinSite site : discs(alone, cz, same).ruins()) {
                places.add(List.of(cz, site.x(), site.z()));
            }
            assertEquals(List.of(), discs(asTroves, cz, same).ruins());
            for (RuinSite site : discs(asTroves, cz, same).troves()) {
                asTroveSites++;
                shared += places.contains(List.of(cz, site.x(), site.z())) ? 1 : 0;
            }
        }
        assertTrue(asTroveSites > 150, asTroveSites + " stood as troves");
        assertTrue(shared < 0.05 * asTroveSites, shared + " of " + asTroveSites + " stand as troves where they stand as ruins");
    }

    @Test
    void aTroveHoldsTheTableThatItsOwnTiersGiveItsDisc() throws Exception {
        DiscRuins.Loot ofRuins = loot(2, 1, 0.5F, 0.8F);
        var behind = new DiscRuins.Loot(
                List.of(new DiscRuins.Loot.Tier(table("stash"), 0), new DiscRuins.Loot.Tier(table("poor"), 0.5F), new DiscRuins.Loot.Tier(table("fair"), 0.8F)), 2, 1);
        DiscRuins ruins = new DiscRuins(1, 1500, EVEN, ofRuins, List.of(kind("small", 1, EVEN)), List.of());
        DiscRuins troves = new DiscRuins(1, 800, EVEN, behind, List.of(kind("tiny", 1, EVEN)), List.of());
        RuinPieces pieces = piecesOf(ruins, small("small"), troves, tiny("tiny"));
        RavineSettings settings = with(ruined(Optional.empty(), ruins, Optional.of(troves)));
        var held = new HashMap<ResourceKey<LootTable>, Integer>();
        for (int cz = 0; cz < CELLS; cz++) {
            CellDiscs cellDiscs = discs(settings, cz, pieces);
            for (Standing ruin : standing(DiscSites.RUINS, settings, cz, cellDiscs, pieces)) {
                assertEquals(ofRuins.tableFor(ruin.traits()), ruin.site().loot(), "a ruin still holds its own list's table");
            }
            for (Standing trove : standing(DiscSites.TROVES, settings, cz, cellDiscs, pieces)) {
                assertEquals(behind.tableFor(trove.traits()), trove.site().loot(), "the table of the disc that the trove " + trove.site() + " stands on");
                held.merge(trove.site().loot().orElseThrow(), 1, Integer::sum);
            }
        }
        assertEquals(Set.of(table("stash"), table("poor"), table("fair")), held.keySet(), "every table of the troves is some trove's, and the ruins' richest is none's");
        assertTrue(held.values().stream().allMatch(count -> count > 10), "each has its share: " + held);
    }

    // Whether a root comes within a margin of the round of ground a piece stands on, at the heights the piece is kept room for.
    private static boolean meets(RootLayout roots, Standing piece, double margin) {
        for (Root root : roots.roots()) {
            for (Root.Knot knot : root.knots()) {
                boolean level = knot.y() + knot.radius() >= piece.site().y() && knot.y() - knot.radius() <= piece.ground() + Math.max(piece.piece().height(), 3);
                double apart = Math.hypot(knot.x() - piece.site().x() - 0.5, knot.z() - piece.site().z() - 0.5);
                if (level && apart < piece.piece().radius() + knot.radius() + margin) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void noTroveStandsWhereARootOfTheHolePassesThoughARuinMay() throws Exception {
        RavineSettings settings = DiscThemeTest.shipped();
        RuinPieces pieces = JarRuinPieces.of(settings);
        int knots = 0;
        int troves = 0;
        int ruins = 0;
        int ruinsMet = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            CellDiscs cellDiscs = discs(settings, cz, pieces);
            RootLayout roots = cellDiscs.roots();
            knots += roots.roots().stream().mapToInt(root -> root.knots().size()).sum();
            for (Standing trove : standing(DiscSites.TROVES, settings, cz, cellDiscs, pieces)) {
                // No piece replaces a root's wood, and a trove is four or five blocks: a root through it would leave nothing.
                assertFalse(meets(roots, trove, DiscRuinSites.MARGIN), "the trove " + trove.site() + " is within the margin of a root");
                troves++;
            }
            for (Standing ruin : standing(DiscSites.RUINS, settings, cz, cellDiscs, pieces)) {
                ruins++;
                ruinsMet += meets(roots, ruin, 0) ? 1 : 0;
            }
        }
        assertTrue(knots > 1000 && troves > 100, knots + " knots of root and " + troves + " troves in " + CELLS + " holes");
        assertTrue(ruinsMet > 0, "roots do come through where pieces stand: of " + ruins + " ruins, which are built round them, " + ruinsMet + " are met by one");
    }

    // A trove stands on the platform, as a ruin does, so a mound it was put in is cut back from it and does not bury it.
    private static void assertTheGroundLiesLevelRound(RavineSettings settings, RuinPieces pieces) {
        int looked = 0;
        int inAMound = 0;
        for (int cz = 0; cz < CELLS; cz++) {
            CellDiscs cellDiscs = discs(settings, cz, pieces);
            for (Standing trove : standing(DiscSites.TROVES, settings, cz, cellDiscs, pieces)) {
                DiscGround ground = cellDiscs.grounds().get(trove.index());
                double kept = trove.piece().radius() + DiscRuinSites.MARGIN;
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (Math.hypot(dx - 0.5, dz - 0.5) < kept + 1) {
                            assertEquals(0, ground.riseAt(trove.site().x() + dx, trove.site().z() + dz), "ground over the platform beside the trove " + trove.site());
                        }
                    }
                }
                var left = new DiscGround(ground.disc(), ground.hash(), ground.index(), ground.relief(), ground.water(), List.of());
                looked++;
                inAMound += left.riseAt(trove.site().x(), trove.site().z()) > 0 ? 1 : 0;
            }
        }
        assertTrue(inAMound > 10, "of " + looked + " troves, those where a mound or an outcrop would have stood: " + inAMound);
    }


    // What the chunks round one hand out of one of the two lists, each site seen to be in the chunk that handed it out.
    private static List<RuinSite> sitesRound(DiscSites which, RavineCarve carve, int chunkX, int chunkZ, int chunks) {
        var handed = new ArrayList<RuinSite>();
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                int minX = chunkX + dx * 16;
                int minZ = chunkZ + dz * 16;
                for (RuinSite site : carve.sitesIn(which, minX, minZ)) {
                    assertTrue(site.x() >= minX && site.x() < minX + 16 && site.z() >= minZ && site.z() < minZ + 16, site + " handed to the chunk at " + minX + " " + minZ);
                    handed.add(site);
                }
            }
        }
        return handed;
    }
}

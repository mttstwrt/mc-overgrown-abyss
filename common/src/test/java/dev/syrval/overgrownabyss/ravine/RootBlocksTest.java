package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.Test;

/** Which blocks of a chunk are root, and of which wood. */
class RootBlocksTest {
    static {
        MinecraftBootstrap.init();
    }

    static final BlockStateProvider BARK = BlockStateProvider.simple(Blocks.JUNGLE_WOOD);
    static final BlockStateProvider CORE = BlockStateProvider.simple(Blocks.PACKED_MUD);
    static final RootWood OWN = new RootWood(BARK, Optional.of(CORE));
    static final RootWood JUNGLE = new RootWood(BlockStateProvider.simple(Blocks.JUNGLE_WOOD), Optional.empty());
    static final RootWood MANGROVE = new RootWood(BlockStateProvider.simple(Blocks.MANGROVE_WOOD), Optional.empty());
    static final RootSettings ROOTS = new RootSettings(
            RootLayoutTest.ROOTS.great(), RootLayoutTest.ROOTS.crossing(), RootLayoutTest.ROOTS.branches(), RootLayoutTest.ROOTS.links(),
            60, 0.6F, 1.5F, OWN, 48, 16, List.of());
    static final RavineSettings SETTINGS = RootLayoutTest.rooted(ConeShapeTest.LOW_SETTINGS, ROOTS);
    static final RavineBounds BOUNDS = RootLayoutTest.VANILLA;

    private record Block(int x, int y, int z) {}

    private static RavineCell cell(int cz) {
        return RavineCells.at(7L, SETTINGS, 1, cz).orElseThrow();
    }

    /** What the chunks touching a box of columns end up with: each block once, since a chunk gives each of its blocks once. */
    private static Map<Block, BlockStateProvider> paint(RavineCell cell, CellDiscs discs, double fromX, double toX, double fromZ, double toZ) {
        var painted = new HashMap<Block, BlockStateProvider>();
        for (int chunkX = Math.floorDiv((int) Math.floor(fromX), 16); chunkX <= Math.floorDiv((int) Math.floor(toX), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(fromZ), 16); chunkZ <= Math.floorDiv((int) Math.floor(toZ), 16); chunkZ++) {
                int minX = chunkX * 16;
                int minZ = chunkZ * 16;
                RootBlocks.forEach(SETTINGS, cell, discs, minX, minZ, -128, 320, (x, y, z, block) -> {
                    assertTrue(x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16 && y >= -128 && y < 320, "outside its chunk");
                    assertTrue(painted.put(new Block(x, y, z), block) == null, "a block given twice");
                });
            }
        }
        return painted;
    }

    private static Map<Block, BlockStateProvider> paintAll(RavineCell cell, CellDiscs discs) {
        double reach = SETTINGS.maxReach();
        return paint(cell, discs, cell.centreX() - reach, cell.centreX() + reach, cell.centreZ() - reach, cell.centreZ() + reach);
    }

    // How far inside a root a block is: its radius there, less how far the block is from the line down its middle.
    private static double inside(Root root, int x, int y, int z) {
        double deepest = Double.NEGATIVE_INFINITY;
        List<Root.Knot> knots = root.knots();
        for (int k = 0; k + 1 < knots.size(); k++) {
            Root.Knot from = knots.get(k);
            Root.Knot to = knots.get(k + 1);
            double ax = to.x() - from.x();
            double ay = to.y() - from.y();
            double az = to.z() - from.z();
            double length = ax * ax + ay * ay + az * az;
            double along = length < 1e-12 ? 0 : Math.clamp(((x - from.x()) * ax + (y - from.y()) * ay + (z - from.z()) * az) / length, 0, 1);
            double away = Math.sqrt(Math.pow(x - from.x() - along * ax, 2) + Math.pow(y - from.y() - along * ay, 2) + Math.pow(z - from.z() - along * az, 2));
            deepest = Math.max(deepest, from.radius() + along * (to.radius() - from.radius()) - away);
        }
        return deepest;
    }

    @Test
    void aRootIsEveryBlockWithinItsRadiusOfTheLineDownItsMiddleAndNoOther() {
        RavineCell c = cell(0);
        CellDiscs discs = CellDiscs.of(SETTINGS, BOUNDS, c);
        Map<Block, BlockStateProvider> painted = paintAll(c, discs);
        List<Root> roots = discs.roots().roots();
        int checked = 0;
        // Round every fortieth knot of every root, a box a block wider than the root: in it, each block is root or is not.
        for (Root root : roots) {
            for (int k = 0; k < root.knots().size(); k += 40) {
                Root.Knot knot = root.knots().get(k);
                int reach = (int) Math.ceil(knot.radius()) + 1;
                for (int x = (int) Math.floor(knot.x()) - reach; x <= Math.floor(knot.x()) + reach; x++) {
                    for (int y = (int) Math.floor(knot.y()) - reach; y <= Math.floor(knot.y()) + reach; y++) {
                        for (int z = (int) Math.floor(knot.z()) - reach; z <= Math.floor(knot.z()) + reach; z++) {
                            double deepest = Double.NEGATIVE_INFINITY;
                            for (Root each : roots) {
                                deepest = Math.max(deepest, inside(each, x, y, z));
                            }
                            // Blocks on the very surface may fall either way to rounding.
                            if (Math.abs(deepest) > 1e-4) {
                                assertEquals(deepest > 0, painted.containsKey(new Block(x, y, z)), "at " + x + "," + y + "," + z + " " + deepest + " inside");
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 5_000, checked + " blocks checked");
        assertTrue(painted.size() > 50_000, painted.size() + " blocks of root");
    }

    @Test
    void aRootsOutermostBlockIsBarkAndItsHeartIsCore() {
        RavineCell c = cell(1);
        CellDiscs discs = CellDiscs.of(SETTINGS, BOUNDS, c);
        Map<Block, BlockStateProvider> painted = paintAll(c, discs);
        int bark = 0;
        int core = 0;
        for (var entry : painted.entrySet()) {
            Block b = entry.getKey();
            boolean onTheSurface = !painted.containsKey(new Block(b.x() + 1, b.y(), b.z())) || !painted.containsKey(new Block(b.x() - 1, b.y(), b.z()))
                    || !painted.containsKey(new Block(b.x(), b.y() + 1, b.z())) || !painted.containsKey(new Block(b.x(), b.y() - 1, b.z()))
                    || !painted.containsKey(new Block(b.x(), b.y(), b.z() + 1)) || !painted.containsKey(new Block(b.x(), b.y(), b.z() - 1));
            if (onTheSurface) {
                assertSame(BARK, entry.getValue(), "a block with air beside it is bark");
                bark++;
            } else {
                core += entry.getValue() == CORE ? 1 : 0;
            }
        }
        assertTrue(bark > 20_000 && core > 5_000, bark + " blocks of bark and " + core + " of core");
        Root great = discs.roots().roots().getFirst();
        Root.Knot thick = great.knots().get(20);
        assertSame(CORE, painted.get(new Block((int) Math.round(thick.x()), (int) Math.round(thick.y()), (int) Math.round(thick.z()))), "the middle of a great root");
    }

    @Test
    void theSameBlocksInTheSameOrderEveryTime() {
        RavineCell c = cell(2);
        CellDiscs discs = CellDiscs.of(SETTINGS, BOUNDS, c);
        RootLayout.Stretch somewhere = new RootLayout.Stretch(0, discs.roots().roots().getFirst().knots().size() / 2);
        Root.Knot knot = discs.roots().roots().getFirst().knots().get(somewhere.knot());
        int minX = Math.floorDiv((int) Math.floor(knot.x()), 16) * 16;
        int minZ = Math.floorDiv((int) Math.floor(knot.z()), 16) * 16;
        var first = new ArrayList<Block>();
        var second = new ArrayList<Block>();
        RootBlocks.forEach(SETTINGS, c, discs, minX, minZ, -128, 320, (x, y, z, block) -> first.add(new Block(x, y, z)));
        RootBlocks.forEach(SETTINGS, c, CellDiscs.of(SETTINGS, BOUNDS, c), minX, minZ, -128, 320, (x, y, z, block) -> second.add(new Block(x, y, z)));
        assertTrue(first.size() > 50, first.size() + " blocks");
        assertEquals(first, second);
        var low = new ArrayList<Block>();
        RootBlocks.forEach(SETTINGS, c, discs, minX, minZ, -128, (int) Math.floor(knot.y()), (x, y, z, block) -> low.add(new Block(x, y, z)));
        assertEquals(first.stream().filter(block -> block.y() < Math.floor(knot.y())).toList(), low, "and only between the heights asked for");
    }

    // Two discs far apart, one of a theme with mangrove roots and one with jungle, and a third whose theme names no wood.
    private static CellDiscs twoWoods() {
        List<Disc> discs = List.of(new Disc(0, 0, 0, 30, 14), new Disc(400, 0, 0, 30, 14), new Disc(0, 400, 0, 30, 14));
        DiscLayout layout = () -> discs;
        return new CellDiscs(
                layout, List.of(Optional.of(theme(Optional.of(MANGROVE))), Optional.of(theme(Optional.of(JUNGLE))), Optional.of(theme(Optional.empty()))),
                List.of(), List.of(), List.of(), RootLayout.NONE, DiscFalls.NONE);
    }

    private static DiscTheme theme(Optional<RootWood> wood) {
        return new DiscTheme(Optional.empty(), Optional.empty(), 1, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Ramp.EVEN, DiscTheme.Limits.NONE,
                DiscPalette.UNPAINTED, Optional.empty(), List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty(), wood);
    }

    @Test
    void aRootTakesTheWoodOfTheDiscNearestToIt() {
        CellDiscs discs = twoWoods();
        RootBlocks.Mix onTheMangrove = RootBlocks.mixAt(SETTINGS, discs, 5, 6, 5);
        assertSame(MANGROVE, onTheMangrove.first());
        assertEquals(1, onTheMangrove.share(), 1e-9, "all of it, on the disc and in its dome");
        assertEquals(1, RootBlocks.mixAt(SETTINGS, discs, 30 + 20, 0, 0).share(), 1e-9, "and for some way beside it");
        RootBlocks.Mix onTheJungle = RootBlocks.mixAt(SETTINGS, discs, 400, -10, 0);
        assertSame(JUNGLE, onTheJungle.first());
        assertEquals(1, onTheJungle.share(), 1e-9, "under a disc as well as over it");
        RootBlocks.Mix bare = RootBlocks.mixAt(SETTINGS, discs, 0, 5, 400);
        assertSame(OWN, bare.first(), "a theme with no wood of its own has no say");
        assertEquals(1, bare.share(), 1e-9);
    }

    @Test
    void awayFromEveryDiscARootIsOfTheSettingsOwnWoodAndBetweenTheTwoItIsShared() {
        CellDiscs discs = twoWoods();
        RootBlocks.Mix far = RootBlocks.mixAt(SETTINGS, discs, 200, 0, 0);
        assertSame(OWN, far.first());
        assertEquals(1, far.share(), 1e-9);
        // 40 from the mangrove disc's rim: inside the reach of 48, so mangrove has it, but the settings' own wood is only 8 further.
        RootBlocks.Mix near = RootBlocks.mixAt(SETTINGS, discs, 30 + 40, 0, 0);
        assertSame(MANGROVE, near.first());
        assertSame(OWN, near.second());
        assertEquals(0.75, near.share(), 1e-9);
        // 56 from it: outside the reach, so the settings' wood has it, and mangrove is 8 further.
        RootBlocks.Mix past = RootBlocks.mixAt(SETTINGS, discs, 30 + 56, 0, 0);
        assertSame(OWN, past.first());
        assertSame(MANGROVE, past.second());
        assertEquals(0.75, past.share(), 1e-9);
        // Two discs of different woods as near as each other share the place evenly.
        List<Disc> close = List.of(new Disc(0, 0, 0, 30, 14), new Disc(80, 0, 0, 30, 14));
        DiscLayout layout = () -> close;
        var both = new CellDiscs(layout, List.of(Optional.of(theme(Optional.of(MANGROVE))), Optional.of(theme(Optional.of(JUNGLE)))), List.of(), List.of(), List.of(), RootLayout.NONE, DiscFalls.NONE);
        assertEquals(0.5, RootBlocks.mixAt(SETTINGS, both, 40, 0, 0).share(), 1e-9);
        RootBlocks.Mix nearer = RootBlocks.mixAt(SETTINGS, both, 36, 0, 0);
        assertSame(MANGROVE, nearer.first());
        assertSame(JUNGLE, nearer.second());
        assertEquals(0.75, nearer.share(), 1e-9);
    }

    @Test
    void rootsBetweenTwoWoodsAreOfBothInPatchesAlongThem() {
        // Every disc of the mangrove theme or the jungle one, by an even draw, so a hole's roots pass discs of both.
        RavineSettings base = RootLayoutTest.rooted(ConeShapeTest.LOW_SETTINGS, ROOTS);
        RavineSettings settings = new RavineSettings(base.salt(), base.cellSize(), base.chance(), base.sizeBias(), base.floor(), base.top(), base.cavernRadius(),
                base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(theme(Optional.of(MANGROVE)), theme(Optional.of(JUNGLE))), base.environment(),
                base.wallNoise(), base.ravine(), base.cone(), base.dressing());
        RavineCell c = RavineCells.at(7L, settings, 1, 0).orElseThrow();
        CellDiscs discs = CellDiscs.of(settings, BOUNDS, c);
        int mangrove = 0;
        int jungle = 0;
        int own = 0;
        double reach = settings.maxReach();
        for (int minX = Math.floorDiv((int) (c.centreX() - reach), 16) * 16; minX <= c.centreX() + reach; minX += 16) {
            for (int minZ = Math.floorDiv((int) (c.centreZ() - reach), 16) * 16; minZ <= c.centreZ() + reach; minZ += 16) {
                int[] counts = new int[3];
                RootBlocks.forEach(settings, c, discs, minX, minZ, -128, 320, (x, y, z, block) -> {
                    counts[block == MANGROVE.bark() ? 0 : block == JUNGLE.bark() ? 1 : 2]++;
                });
                mangrove += counts[0];
                jungle += counts[1];
                own += counts[2];
            }
        }
        assertTrue(mangrove > 10_000 && jungle > 10_000, mangrove + " blocks of mangrove and " + jungle + " of jungle");
        assertTrue(own > 0, "and the settings' own wood where no disc is near, such as on the floor: " + own);
    }

    private static final ResourceKey<ConfiguredFeature<?, ?>> LICHEN = feature("disc/glow_lichen_high");
    private static final ResourceKey<ConfiguredFeature<?, ?>> VINE = feature("disc/glow_berries_over_drop");

    private static ResourceKey<ConfiguredFeature<?, ?>> feature(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath("overgrown_abyss", path));
    }

    // The same roots, with things hung under them.
    private static RavineSettings growing(RootSettings.Growth... growth) {
        return RootLayoutTest.rooted(ConeShapeTest.LOW_SETTINGS, new RootSettings(
                ROOTS.great(), ROOTS.crossing(), ROOTS.branches(), ROOTS.links(), ROOTS.floorRun(), ROOTS.winding(), ROOTS.minRadius(), ROOTS.wood(),
                ROOTS.woodReach(), ROOTS.woodBlend(), List.of(growth)));
    }

    private record Place(Block at, ResourceKey<ConfiguredFeature<?, ?>> feature) {}

    /** Every place under the roots of a hole where something grows, chunk by chunk from one corner or from the other. */
    private static List<Place> grown(RavineSettings settings, RavineCell cell, boolean backwards) {
        CellDiscs discs = CellDiscs.of(settings, BOUNDS, cell);
        double reach = settings.maxReach();
        var chunks = new ArrayList<int[]>();
        for (int chunkX = Math.floorDiv((int) Math.floor(cell.centreX() - reach), 16); chunkX <= Math.floorDiv((int) Math.floor(cell.centreX() + reach), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(cell.centreZ() - reach), 16); chunkZ <= Math.floorDiv((int) Math.floor(cell.centreZ() + reach), 16); chunkZ++) {
                chunks.add(new int[] {chunkX * 16, chunkZ * 16});
            }
        }
        var places = new ArrayList<Place>();
        for (int[] chunk : backwards ? chunks.reversed() : chunks) {
            RootBlocks.forEachGrowth(settings, cell, discs, chunk[0], chunk[1], -128, 320, (x, y, z, feature, on) -> {
                assertTrue(x >= chunk[0] && x < chunk[0] + 16 && z >= chunk[1] && z < chunk[1] + 16, "outside its chunk");
                assertSame(DiscTheme.Surface.UNDERSIDE, on);
                places.add(new Place(new Block(x, y, z), feature));
            });
        }
        return places;
    }

    @Test
    void whatHangsUnderARootStartsInTheBlockUnderItsLowestPart() {
        RavineCell c = cell(2);
        Map<Block, BlockStateProvider> roots = paintAll(c, CellDiscs.of(SETTINGS, BOUNDS, c));
        List<Place> places = grown(growing(new RootSettings.Growth(LICHEN, 1, 0)), c, false);
        assertTrue(places.size() > 200, places.size() + " places");
        assertEquals(places.size(), new HashSet<>(places).size(), "a place given twice");
        for (Place place : places) {
            Block at = place.at();
            assertTrue(roots.containsKey(new Block(at.x(), at.y() + 1, at.z())), "no root over " + at);
            assertTrue(!roots.containsKey(at), "inside a root: " + at);
            assertSame(LICHEN, place.feature());
        }
        // A root's flanks have blocks with no root under them too, and a light there would shine onto its top.
        long lowest = roots.keySet().stream().filter(block -> !roots.containsKey(new Block(block.x(), block.y() - 1, block.z()))).count();
        assertTrue(places.size() < lowest * 0.9 && places.size() > lowest * 0.25, places.size() + " places under " + lowest + " blocks with no root below");
        assertTrue(grown(SETTINGS, c, false).isEmpty(), "and nothing where the roots grow nothing");
    }

    @Test
    void oneGrowsForEverySoManyBlocksOfUndersideAndOnlyUnderRootsThickEnough() {
        RavineCell c = cell(2);
        var all = new HashSet<>(grown(growing(new RootSettings.Growth(LICHEN, 1, 0)), c, false));
        List<Place> few = grown(growing(new RootSettings.Growth(LICHEN, 8, 0)), c, false);
        assertTrue(all.containsAll(few), "a larger `every` takes some of the same places away and adds none");
        assertEquals(all.size() / 8.0, few.size(), all.size() / 8.0 * 0.25, "one for every 8");
        // A great root is 4.5 at the wall and 3.5 at the floor; its branches and the links are thinner.
        List<Place> thick = grown(growing(new RootSettings.Growth(LICHEN, 1, 3.6F)), c, false);
        assertTrue(!thick.isEmpty() && thick.size() < all.size() / 2 && all.containsAll(thick), thick.size() + " of " + all.size() + " under roots of 3.6 or more");
        assertTrue(grown(growing(new RootSettings.Growth(LICHEN, 1, 16)), c, false).isEmpty(), "no root is that thick");
    }

    @Test
    void thePlacesAreTheSameInAnyOrderOfChunksAndGrowthsComeInTheOrderListed() {
        RavineCell c = cell(2);
        RavineSettings two = growing(new RootSettings.Growth(LICHEN, 3, 0), new RootSettings.Growth(VINE, 5, 2.5F));
        List<Place> forwards = grown(two, c, false);
        List<Place> backwards = grown(two, c, true);
        assertEquals(new HashSet<>(forwards), new HashSet<>(backwards));
        assertEquals(forwards.size(), backwards.size());
        assertTrue(forwards.stream().anyMatch(place -> place.feature() == VINE) && forwards.stream().anyMatch(place -> place.feature() == LICHEN));
        // Each growth has draws of its own, so a block may be given to both; within a chunk the first listed comes first.
        CellDiscs discs = CellDiscs.of(two, BOUNDS, c);
        Root.Knot knot = discs.roots().roots().getFirst().knots().get(discs.roots().roots().getFirst().knots().size() / 2);
        var inOne = new ArrayList<ResourceKey<ConfiguredFeature<?, ?>>>();
        RootBlocks.forEachGrowth(two, c, discs, Math.floorDiv((int) Math.floor(knot.x()), 16) * 16, Math.floorDiv((int) Math.floor(knot.z()), 16) * 16, -128, 320,
                (x, y, z, feature, on) -> inOne.add(feature));
        assertTrue(inOne.contains(LICHEN), "the chunk has places");
        assertTrue(inOne.lastIndexOf(LICHEN) < (inOne.contains(VINE) ? inOne.indexOf(VINE) : inOne.size()), "lichen before vines");
    }

    @Test
    void aCarveHasRootBlocksOnlyOnceBoundAndOnlyWithRootsInItsSettings() {
        RavineCell c = cell(0);
        Root.Knot knot = CellDiscs.of(SETTINGS, BOUNDS, c).roots().roots().getFirst().knots().get(40);
        int minX = Math.floorDiv((int) Math.floor(knot.x()), 16) * 16;
        int minZ = Math.floorDiv((int) Math.floor(knot.z()), 16) * 16;
        assertEquals(0, count(carve(SETTINGS), minX, minZ), "an unbound carve has none");
        assertTrue(count(carve(SETTINGS).bind(7L, BOUNDS), minX, minZ) > 50, "a bound carve with roots has them");
        assertEquals(0, count(carve(ConeShapeTest.LOW_SETTINGS).bind(7L, BOUNDS), minX, minZ), "no roots in the settings");
        int[] places = {0};
        carve(growing(new RootSettings.Growth(LICHEN, 1, 0))).bind(7L, BOUNDS).forEachRootGrowth(minX, minZ, -128, 320, (x, y, z, feature, on) -> places[0]++);
        assertTrue(places[0] > 0, "and a bound carve whose roots grow something has places for it");
        carve(SETTINGS).bind(7L, BOUNDS).forEachRootGrowth(minX, minZ, -128, 320, (x, y, z, feature, on) -> places[0] = -1);
        assertTrue(places[0] > 0, "which roots that grow nothing have none of");
    }

    private static RavineCarve carve(RavineSettings settings) {
        var json = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, settings).getOrThrow();
        return RavineCarve.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static int count(RavineCarve carve, int minX, int minZ) {
        int[] count = {0};
        carve.forEachRootBlock(minX, minZ, -128, 320, (x, y, z, block) -> count[0]++);
        return count[0];
    }
}

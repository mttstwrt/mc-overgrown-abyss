package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.junit.jupiter.api.Test;

/** A disc's theme: how it is written, which one a disc gets, where its biome reaches and where it grows things. */
class DiscThemeTest {
    static {
        MinecraftBootstrap.init();
    }

    static final RavineBounds VANILLA = new RavineBounds(-40, 80);
    /**
     * What the mod's own settings resolve to in a level of vanilla's height. Their top follows the ground, and until a level's
     * ground is installed every hole's top is the level's.
     */
    static final RavineBounds LEVEL = new RavineBounds(-40, 160);

    static ResourceKey<Biome> biome(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("overgrown_abyss", path));
    }

    static ResourceKey<ConfiguredFeature<?, ?>> feature(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.parse(id));
    }

    static DiscTheme theme(String biome, float weight, DiscTheme.Ramp byHeight, DiscTheme.Ramp byDistance, DiscTheme.Ramp bySize) {
        return new DiscTheme(Optional.of(biome(biome)), Optional.empty(), weight, byHeight, byDistance, bySize, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty(), Optional.empty());
    }

    static final DiscTheme.Ramp EVEN = DiscTheme.Ramp.EVEN;
    static final DiscTheme COMMON = theme("common", 4, EVEN, EVEN, EVEN);
    static final DiscTheme CENTRAL = theme("central", 4, EVEN, new DiscTheme.Ramp(3, 0.4F), EVEN);
    static final DiscTheme RARE = theme("rare", 1, new DiscTheme.Ramp(3, 0.3F), new DiscTheme.Ramp(0.3F, 3), new DiscTheme.Ramp(3, 0.2F));

    /** The mod's own settings, with a hole in every cell so that tests can ask for any. */
    static RavineSettings shipped() throws Exception {
        try (var in = DiscThemeTest.class.getResourceAsStream("/data/overgrown_abyss/worldgen/density_function/ravine/carve.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("type");
            json.addProperty("chance", 1);
            return RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        }
    }

    /** The mod's own hole with one theme on every disc, which grows what is given and nothing else. */
    static RavineSettings growing(DiscTheme.Wildness wildness, DiscTheme.Growth... growth) throws Exception {
        RavineSettings base = shipped();
        var theme = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(growth), wildness, DiscRelief.FLAT, Optional.empty(), Optional.empty(), Optional.empty());
        return new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(theme), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
    }

    record Place(int x, int y, int z, DiscTheme.Surface on) {}

    /** Every place where something grows in the chunks that hold a disc and the columns right outside it. */
    static List<Place> placesAround(RavineSettings settings, RavineCell cell, CellDiscs cellDiscs, Disc d) {
        var places = new ArrayList<Place>();
        double reach = d.radius() + 1;
        for (int chunkX = Math.floorDiv((int) Math.floor(d.x() - reach), 16); chunkX <= Math.floorDiv((int) Math.floor(d.x() + reach), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(d.z() - reach), 16); chunkZ <= Math.floorDiv((int) Math.floor(d.z() + reach), 16); chunkZ++) {
                DiscGrowth.forEach(settings, cell, cellDiscs, chunkX * 16, chunkZ * 16, (x, y, z, feature, on) -> places.add(new Place(x, y, z, on)));
            }
        }
        return places;
    }

    /** Whether a place is the open block over a column of this disc's own top. */
    static boolean isOnTopOf(Disc d, Place place) {
        double fromAxis = Math.hypot(place.x() - d.x(), place.z() - d.z());
        return place.on() == DiscTheme.Surface.TOP && fromAxis <= d.radius() && place.y() == d.topBlockAt(fromAxis) + 1;
    }

    static Disc largest(List<Disc> discs) {
        return discs.stream().max((a, b) -> Double.compare(a.radius(), b.radius())).orElseThrow();
    }

    @Test
    void aDiscsTraitsAreMeasuredAgainstTheDiscsOfItsOwnHole() throws Exception {
        RavineSettings settings = shipped();
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        List<Disc> discs = CellDiscs.of(settings, VANILLA, cell).layout().discs();
        List<DiscTraits> traits = DiscTraits.of(settings, cell, discs);
        assertEquals(0, traits.stream().mapToDouble(DiscTraits::height).min().orElseThrow(), 1e-9, "the lowest disc is at the bottom of the ramp");
        assertEquals(1, traits.stream().mapToDouble(DiscTraits::height).max().orElseThrow(), 1e-9, "and the highest at its top");
        assertEquals(1, traits.stream().mapToDouble(DiscTraits::distance).max().orElseThrow(), 1e-9, "the outermost disc is at the edge");
        for (int i = 0; i < discs.size(); i++) {
            DiscTraits t = traits.get(i);
            assertTrue(t.height() >= 0 && t.height() <= 1 && t.distance() >= 0 && t.distance() <= 1 && t.size() >= 0 && t.size() <= 1, t.toString());
            for (int j = 0; j < discs.size(); j++) {
                assertTrue(discs.get(i).floor() <= discs.get(j).floor() || t.height() >= traits.get(j).height(), "a higher disc has no lower trait");
            }
        }
        assertEquals(List.of(new DiscTraits(0, 0, 0)), DiscTraits.of(settings, cell, List.of(new Disc(cell.centreX(), cell.centreZ(), 5, 20, 14))), "one disc alone");
    }

    @Test
    void aWeightFollowsADiscsTraitsAlongEachRamp() {
        assertEquals(4, COMMON.weightFor(new DiscTraits(0.2, 0.9, 0.5)), 1e-9, "even ramps leave the weight alone");
        assertEquals(12, CENTRAL.weightFor(new DiscTraits(0.5, 0, 0.5)), 1e-6, "three times as likely in the very middle");
        assertEquals(1.6, CENTRAL.weightFor(new DiscTraits(0.5, 1, 0.5)), 1e-6);
        assertEquals(6.8, CENTRAL.weightFor(new DiscTraits(0.5, 0.5, 0.5)), 1e-6, "and steadily in between");
        assertEquals(27, RARE.weightFor(new DiscTraits(0, 1, 0)), 1e-5, "the ramps multiply: a small, low, outlying disc");
        assertEquals(0.018, RARE.weightFor(new DiscTraits(1, 0, 1)), 1e-6, "a large, high, central one");
        assertEquals(RARE.weightFor(new DiscTraits(0, 1, 0)), RARE.weightFor(new DiscTraits(-5, 7, -1)), 1e-9, "traits are held to their ends");
    }

    @Test
    void aDiscGetsAThemeByWeightAndNoneIfNoneHasAny() {
        List<DiscTheme> themes = List.of(COMMON, RARE);
        var middling = new DiscTraits(0.5, 0.5, 0.5);
        double share = COMMON.weightFor(middling) / (COMMON.weightFor(middling) + RARE.weightFor(middling));
        assertEquals(OptionalInt.of(0), DiscThemes.pick(themes, middling, 0));
        assertEquals(OptionalInt.of(0), DiscThemes.pick(themes, middling, share - 1e-6));
        assertEquals(OptionalInt.of(1), DiscThemes.pick(themes, middling, share + 1e-6));
        assertEquals(OptionalInt.of(1), DiscThemes.pick(themes, middling, 0.999999));
        assertEquals(OptionalInt.empty(), DiscThemes.pick(List.of(), middling, 0.5));
        DiscTheme never = theme("never", 0, EVEN, EVEN, EVEN);
        assertEquals(OptionalInt.empty(), DiscThemes.pick(List.of(never), middling, 0.5), "a theme with no weight is never given");
        assertEquals(OptionalInt.of(1), DiscThemes.pick(List.of(never, COMMON, never), middling, 0.999999), "nor is it the fallback at the end of the draw");
        DiscTheme lowOnly = theme("low", 1, new DiscTheme.Ramp(1, 0), EVEN, EVEN);
        assertEquals(OptionalInt.empty(), DiscThemes.pick(List.of(lowOnly), new DiscTraits(1, 0.5, 0.5), 0.5), "a ramp can rule a theme out at one end");
    }

    @Test
    void aThemesLimitsRuleItOutWhereARampWouldOnlyMakeItUnlikely() {
        var smallAndFar = new DiscTheme.Limits(DiscTheme.Span.ALL, new DiscTheme.Span(0.55F, 1), new DiscTheme.Span(0, 0.4F));
        var kept = new DiscTheme(Optional.of(biome("kept")), Optional.empty(), 100, EVEN, EVEN, EVEN, smallAndFar, DiscPalette.UNPAINTED, Optional.empty(), List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(100, kept.weightFor(new DiscTraits(0.5, 0.8, 0.2)), 1e-9, "inside its limits it has its weight");
        assertEquals(0, kept.weightFor(new DiscTraits(0.5, 0.5, 0.2)), 1e-9, "too near the centre");
        assertEquals(0, kept.weightFor(new DiscTraits(0.5, 0.8, 0.5)), 1e-9, "too large");
        // However heavy it is, a disc outside its limits goes to another theme, or to none.
        assertEquals(OptionalInt.of(1), DiscThemes.pick(List.of(kept, COMMON), new DiscTraits(0.5, 0.2, 0.2), 0.0001));
        assertEquals(OptionalInt.empty(), DiscThemes.pick(List.of(kept), new DiscTraits(0.5, 0.2, 0.2), 0.5));
        var json = JsonParser.parseString("{\"weight\": 1, \"only\": {\"distance\": {\"min\": 0.55}, \"size\": {\"max\": 0.4}}}");
        assertEquals(smallAndFar, DiscTheme.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().only(), "a span's missing end is the end of the trait");
        var backwards = JsonParser.parseString("{\"weight\": 1, \"only\": {\"size\": {\"min\": 0.6, \"max\": 0.4}}}");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, backwards).isError());
    }


    private static double share(Map<String, Integer> counts, String name) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return total == 0 ? 0 : counts.getOrDefault(name, 0) / (double) total;
    }

    @Test
    void aDiscsBiomeCoversItsDomeAndPlatformAndALittleRoundThem() {
        Disc d = new Disc(0, 0, 20, 40, 18, 3, Disc.Support.Standing.TO_THE_FLOOR);
        DiscShape shape = DiscTest.SHAPE;
        assertTrue(d.biomeContains(shape, 0, 30, 0, 4), "in the dome");
        assertTrue(d.biomeContains(shape, 30, 21, 0, 4), "in the platform under the bowl");
        assertTrue(d.biomeContains(shape, 0, 17, 0, 4), "in the platform's middle");
        assertTrue(d.biomeContains(shape, 0, 13, 0, 4), "just under the platform, where things hang");
        assertTrue(!d.biomeContains(shape, 0, 11, 0, 4), "not well under it");
        assertTrue(d.biomeContains(shape, 0, 41, 0, 4), "just over the roof");
        assertTrue(!d.biomeContains(shape, 0, 43, 0, 4), "not well over it");
        assertTrue(d.biomeContains(shape, 43, 24, 0, 4), "just outside the rim");
        assertTrue(!d.biomeContains(shape, 45, 24, 0, 4), "not well outside it");
        assertTrue(!d.biomeContains(shape, 38, 36, 0, 4), "nor above the roof where it has curved down towards the rim");
    }

    @Test
    void aPointTakesTheBiomeOfTheHighestDiscItBelongsTo() throws Exception {
        RavineSettings settings = shipped();
        RavineCarve carve = RavineCarve.MAP_CODEC.codec()
                .parse(JsonOps.INSTANCE, RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, settings).getOrThrow()).getOrThrow();
        assertEquals(Optional.empty(), carve.discBiomeAt(0, 0, 0), "nothing before the carve is bound to a level");
        RavineCarve bound = carve.bind(11L, LEVEL);
        int inDomes = 0;
        int overridden = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell cell = RavineCells.at(11L, settings, 0, cz).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(settings, LEVEL, cell);
            List<Disc> all = cellDiscs.layout().discs();
            for (int i = 0; i < all.size(); i++) {
                Disc d = all.get(i);
                int x = (int) Math.floor(d.x());
                int y = (int) d.floor() + 2;
                int z = (int) Math.floor(d.z());
                // The highest disc whose space holds the point: this one, unless a higher disc's space reaches down here too.
                Disc highest = d;
                for (Disc other : all) {
                    if (other.floor() > highest.floor() && other.biomeContains(settings.discs(), x, y, z, 4)) {
                        highest = other;
                    }
                }
                overridden += highest != d ? 1 : 0;
                assertEquals(cellDiscs.themes().get(all.indexOf(highest)).orElseThrow().biome(), bound.discBiomeAt(x, y, z), "over the middle of " + d);
                inDomes++;
            }
            assertEquals(Optional.empty(), bound.discBiomeAt((int) cell.centreX(), VANILLA.floorY() + 2, (int) cell.centreZ()), "the city's floor is no disc's");
            assertEquals(Optional.empty(), bound.discBiomeAt((int) cell.centreX() + 600, 20, (int) cell.centreZ()), "nor is the ground far outside the hole");
        }
        assertTrue(inDomes > 40, inDomes + " discs checked, " + overridden + " of them under a higher disc's space");
    }


    @Test
    void aThemeGrowsWhatItGivesPatchesOnlyInThosePatches() throws Exception {
        RavineSettings base = shipped();
        var patches = new DiscTheme.Patches(8, 0.3F);
        var inPatches = new DiscTheme.Growth(feature("minecraft:mangrove"), 6, DiscTheme.Surface.TOP, Optional.of(patches), Optional.empty());
        var growing = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(inPatches), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty(), Optional.empty());
        RavineSettings settings = new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(growing), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
        Disc d = cellDiscs.layout().discs().get(0);
        int[] places = {0};
        for (int chunkX = Math.floorDiv((int) Math.floor(d.x() - d.radius()), 16); chunkX <= Math.floorDiv((int) Math.floor(d.x() + d.radius()), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(d.z() - d.radius()), 16); chunkZ <= Math.floorDiv((int) Math.floor(d.z() + d.radius()), 16); chunkZ++) {
                DiscGrowth.forEach(settings, cell, cellDiscs, chunkX * 16, chunkZ * 16, (x, y, z, feature, on) -> {
                    double fromAxis = Math.hypot(x - d.x(), z - d.z());
                    // Other discs grow things in these chunks too; this disc's are the ones on its own top.
                    if (fromAxis <= d.radius() && y == d.topBlockAt(fromAxis) + 1) {
                        assertTrue(patches.weightAt(cell.hash(), 0, x, z) > 0, "grown outside its patches at " + x + ", " + z);
                        places[0]++;
                    }
                });
            }
        }
        // A disc holds only a few dozen patches, so its own average strays from the one asked for; what each column was given is exact.
        double expected = 0;
        for (int x = (int) Math.floor(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
            for (int z = (int) Math.floor(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                // Nothing grows within a block and a half of the rim (DiscGrowth.EDGE_CLEAR).
                expected += Math.hypot(x - d.x(), z - d.z()) <= d.radius() - 1.5 ? Math.min(1, patches.weightAt(cell.hash(), 0, x, z) / 6) : 0;
            }
        }
        assertTrue(expected > 50, expected + " places expected");
        assertEquals(expected, places[0], 0.25 * expected, "places on the top, one in 6 asked for where the patches are as thick as on average");
    }

    @Test
    void aThemeWithWaterNeedsAPaletteForTheGroundThatHoldsIt() {
        String water = "\"water\": {\"ponds\": 0.3}";
        String grass = "{\"thickness\": 1, \"block\": {\"type\": \"minecraft:simple_state_provider\", \"state\": {\"Name\": \"minecraft:grass_block\"}}}";
        String dirt = "{\"thickness\": 2, \"block\": {\"type\": \"minecraft:simple_state_provider\", \"state\": {\"Name\": \"minecraft:dirt\"}}}";
        String stone = "\"body\": {\"type\": \"minecraft:simple_state_provider\", \"state\": {\"Name\": \"minecraft:stone\"}}";
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"weight\": 1, " + water + "}")).isError(),
                "left as the terrain's rock, the ground round a pond may be a cave");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"weight\": 1, \"palette\": {\"top\": [" + grass + "]}, " + water + "}")).isError(),
                "one block of ground under water two deep");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"weight\": 1, \"palette\": {\"top\": [" + grass + ", " + dirt + "]}, " + water + "}")).isSuccess(),
                "three blocks: two of water and its bed");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"weight\": 1, \"palette\": {" + stone + "}, " + water + "}")).isSuccess(),
                "a body is ground all the way down");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"weight\": 1, \"palette\": {\"top\": [" + grass + ", " + grass + "]}, \"water\": {\"ponds\": 0.3, \"depth\": 1}}")).isSuccess(), "shallower water needs less");
    }

    @Test
    void waterNeedsAPlatformThickEnoughToHaveABed() throws Exception {
        try (var in = DiscThemeTest.class.getResourceAsStream("/data/overgrown_abyss/worldgen/density_function/ravine/carve.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("type");
            assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isSuccess());
            json.getAsJsonObject("discs").addProperty("floor_thickness", 2);
            assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).isError(), "water two deep in a platform two thick");
        }
    }

    @Test
    void whatGrowsInWaterStartsOnTheBedOfAPondAndNowhereElse() throws Exception {
        RavineSettings base = shipped();
        var water = new DiscWater(0.4F, 12, 2, 40, 2, 3, DiscWater.Fluid.WATER, 0);
        var inWater = new DiscTheme.Growth(feature("minecraft:mangrove"), 4, DiscTheme.Surface.WATER, Optional.empty(), Optional.empty());
        var stone = new DiscPalette(List.of(), List.of(), base.discThemes().get(0).palette().body(), DiscPalette.UNPAINTED.stem());
        var swamp = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, stone, Optional.of(water), List.of(inWater), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty(), Optional.empty());
        RavineSettings settings = new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(swamp), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
        Disc d = cellDiscs.layout().discs().get(0);
        int[] places = {0};
        int wet = 0;
        for (int chunkX = Math.floorDiv((int) Math.floor(d.x() - d.radius()), 16); chunkX <= Math.floorDiv((int) Math.floor(d.x() + d.radius()), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(d.z() - d.radius()), 16); chunkZ <= Math.floorDiv((int) Math.floor(d.z() + d.radius()), 16); chunkZ++) {
                DiscGrowth.forEach(settings, cell, cellDiscs, chunkX * 16, chunkZ * 16, (x, y, z, feature, on) -> {
                    double fromAxis = Math.hypot(x - d.x(), z - d.z());
                    int depth = water.depthAt(d, cell.hash(), 0, x, z);
                    // Other discs grow things in these chunks too; this disc's are the ones in its own water.
                    if (fromAxis <= d.radius() && depth > 0 && y == d.topBlockAt(fromAxis) - depth + 1) {
                        places[0]++;
                    }
                    assertEquals(DiscTheme.Surface.WATER, on);
                });
            }
        }
        for (int x = (int) Math.floor(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
            for (int z = (int) Math.floor(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                wet += water.depthAt(d, cell.hash(), 0, x, z) > 0 ? 1 : 0;
            }
        }
        assertTrue(wet > 100, wet + " columns of water");
        assertTrue(places[0] > 0.5 * wet / 4 && places[0] < 1.6 * wet / 4, places[0] + " places in " + wet + " columns of water, one in 4 asked for");
    }

    @Test
    void whatGrowsOverLavaStartsInTheOpenBlockAboveItAndNowhereElse() throws Exception {
        RavineSettings base = shipped();
        var lava = new DiscWater(0.4F, 12, 0, 40, 1, 3, DiscWater.Fluid.LAVA, 0);
        var overLava = new DiscTheme.Growth(feature("overgrown_abyss:disc/obsidian_tree"), 4, DiscTheme.Surface.LAVA, Optional.empty(), Optional.empty());
        // Neither finds what it grows in: the one wants water where there is lava, the other lava where there is water.
        var inWater = new DiscTheme.Growth(feature("minecraft:mangrove"), 4, DiscTheme.Surface.WATER, Optional.empty(), Optional.empty());
        var stone = new DiscPalette(List.of(), List.of(), base.discThemes().get(0).palette().body(), DiscPalette.UNPAINTED.stem());
        for (boolean molten : new boolean[] {true, false}) {
            var held = molten ? lava : new DiscWater(0.4F, 12, 0, 40, 1, 3, DiscWater.Fluid.WATER, 0);
            var theme = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, stone, Optional.of(held),
                    List.of(molten ? inWater : overLava, molten ? overLava : inWater), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty(), Optional.empty());
            assertEquals(molten, theme.holdsLava());
            RavineSettings settings = new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                    base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(theme), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
            RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
            Disc d = cellDiscs.layout().discs().get(0);
            int[] places = {0};
            int pond = 0;
            for (int chunkX = Math.floorDiv((int) Math.floor(d.x() - d.radius()), 16); chunkX <= Math.floorDiv((int) Math.floor(d.x() + d.radius()), 16); chunkX++) {
                for (int chunkZ = Math.floorDiv((int) Math.floor(d.z() - d.radius()), 16); chunkZ <= Math.floorDiv((int) Math.floor(d.z() + d.radius()), 16); chunkZ++) {
                    DiscGrowth.forEach(settings, cell, cellDiscs, chunkX * 16, chunkZ * 16, (x, y, z, feature, on) -> {
                        assertEquals(molten ? DiscTheme.Surface.LAVA : DiscTheme.Surface.WATER, on);
                        double fromAxis = Math.hypot(x - d.x(), z - d.z());
                        // Other discs grow things in these chunks too; this disc's are the ones at its own pond's height.
                        if (fromAxis <= d.radius() && held.depthAt(d, cell.hash(), 0, x, z) > 0 && y == d.topBlockAt(fromAxis) + (molten ? 1 : 0)) {
                            places[0]++;
                        }
                    });
                }
            }
            for (int x = (int) Math.floor(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
                for (int z = (int) Math.floor(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                    pond += held.depthAt(d, cell.hash(), 0, x, z) > 0 ? 1 : 0;
                }
            }
            assertTrue(pond > 100, pond + " columns of pond");
            assertTrue(places[0] > 0.5 * pond / 4 && places[0] < 1.6 * pond / 4, places[0] + " places in " + pond + " columns of pond, one in 4 asked for");
        }
    }

    @Test
    void aThemeInheritsVegetationFromItsParentUnlessToldOtherwise() {
        var json = JsonParser.parseString("{\"biome\": \"overgrown_abyss:disc_jungle\", \"weight\": 1, \"inherits\": {\"biome\": \"minecraft:jungle\"}}");
        DiscTheme.Inherits inherits = DiscTheme.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().inherits().orElseThrow();
        assertEquals(ResourceKey.create(Registries.BIOME, ResourceLocation.parse("minecraft:jungle")), inherits.biome());
        assertEquals(List.of(GenerationStep.Decoration.VEGETAL_DECORATION), inherits.stages(), "vegetation only, which leaves out lakes, ores and springs");
        assertTrue(inherits.withoutFeatures().isEmpty() && inherits.withoutSpawns().isEmpty());
        assertEquals(json, DiscTheme.CODEC.encodeStart(JsonOps.INSTANCE, DiscTheme.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()).getOrThrow());
        var more = JsonParser.parseString("""
                {"biome": "overgrown_abyss:disc_lush", "weight": 1, "inherits": {
                  "biome": "minecraft:lush_caves",
                  "stages": ["underground_ores", "vegetal_decoration"],
                  "without_features": ["minecraft:lush_caves_clay"],
                  "without_spawns": ["minecraft:witch", "othermod:not_installed"]}}
                """);
        DiscTheme.Inherits picked = DiscTheme.CODEC.parse(JsonOps.INSTANCE, more).getOrThrow().inherits().orElseThrow();
        assertEquals(List.of(GenerationStep.Decoration.UNDERGROUND_ORES, GenerationStep.Decoration.VEGETAL_DECORATION), picked.stages());
        assertEquals("minecraft:lush_caves_clay", picked.withoutFeatures().get(0).location().toString());
        assertEquals(List.of(ResourceLocation.parse("minecraft:witch"), ResourceLocation.parse("othermod:not_installed")), picked.withoutSpawns(),
                "mobs are named by id, so one from a mod that is not installed still loads");
        var noBiomeOfItsOwn = JsonParser.parseString("{\"weight\": 1, \"inherits\": {\"biome\": \"minecraft:jungle\"}}");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, noBiomeOfItsOwn).isError(), "what is inherited is given to the theme's own biome");
        var fromItself = JsonParser.parseString("{\"biome\": \"minecraft:jungle\", \"weight\": 1, \"inherits\": {\"biome\": \"minecraft:jungle\"}}");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, fromItself).isError());
    }

    @Test
    void anInheritingDiscIsAPlotWithItsOwnGroundAndItsOwnBlocks() throws Exception {
        RavineSettings settings = shipped();
        RavineCarve carve = RavineCarve.MAP_CODEC.codec()
                .parse(JsonOps.INSTANCE, RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, settings).getOrThrow()).getOrThrow();
        int[] unbound = {0};
        carve.forEachInheritingDisc(0, 0, (inherits, plot) -> unbound[0]++);
        assertEquals(0, unbound[0], "nothing before the carve is bound to a level");
        RavineCarve bound = carve.bind(11L, LEVEL);
        RavineSettings live = bound.settings();
        int plots = 0;
        int yieldedToAHigherDisc = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell cell = RavineCells.at(11L, live, 0, cz).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(live, LEVEL, cell);
            List<Disc> all = cellDiscs.layout().discs();
            for (int i = 0; i < all.size(); i++) {
                Disc d = all.get(i);
                Optional<DiscTheme.Inherits> inherits = cellDiscs.themes().get(i).orElseThrow().inherits();
                int x = (int) Math.floor(d.x());
                int z = (int) Math.floor(d.z());
                int minX = Math.floorDiv(x, 16) * 16;
                int minZ = Math.floorDiv(z, 16) * 16;
                // The chunk under the disc's middle holds this disc's plot exactly when its theme inherits. Its ground there is
                // the open block over the platform, or over a mound if one stands on it.
                int open = cellDiscs.grounds().get(i).topBlockAt(x, z) + 1;
                var found = new ArrayList<DiscPlot>();
                bound.forEachInheritingDisc(minX, minZ, (what, plot) -> {
                    if (plot.groundAt(x, z).isPresent() && plot.groundAt(x, z).getAsInt() == open
                            && plot.groundAt((int) (d.x() + d.radius() + 2), z).isEmpty() && plot.groundAt((int) (d.x() - d.radius() - 2), z).isEmpty()
                            && what.equals(inherits.orElse(null))) {
                        found.add(plot);
                    }
                });
                if (inherits.isEmpty()) {
                    continue;
                }
                assertTrue(!found.isEmpty(), "no plot for " + d);
                DiscPlot plot = found.get(0);
                plots++;
                int ground = plot.groundAt(x, z).getAsInt();
                boolean ownsItsGround = plot.owns(x, ground, z);
                boolean higherDiscThere = false;
                for (Disc other : all) {
                    higherDiscThere |= other.floor() > d.floor() && other.biomeContains(live.discs(), x, ground, z, 4);
                }
                assertEquals(!higherDiscThere, ownsItsGround, "a disc owns the block over its middle unless a higher disc's space holds it: " + d);
                yieldedToAHigherDisc += higherDiscThere ? 1 : 0;
                assertTrue(!plot.owns(x, (int) (d.floor() + d.height() + 12), z), "nothing far over its dome is its own");
                assertTrue(!plot.owns((int) (d.x() + d.radius() + 12), ground, z), "nor far beside it");
            }
        }
        assertTrue(plots > 30, plots + " plots, " + yieldedToAHigherDisc + " of them under a higher disc at their middle");
    }


    @Test
    void whatGrowsOnARimStartsBesideItsTopBlockAllTheWayRound() throws Exception {
        RavineSettings settings = growing(DiscTheme.Wildness.EVEN, new DiscTheme.Growth(feature("minecraft:vines"), 5, DiscTheme.Surface.RIM, Optional.empty(), Optional.empty()));
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
        List<Disc> discs = cellDiscs.layout().discs();
        Disc d = largest(discs);
        List<Place> places = placesAround(settings, cell, cellDiscs, d);
        assertEquals(places, placesAround(settings, cell, cellDiscs, d), "the same places every time");
        int[][] beside = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int own = 0;
        var octants = new HashSet<Integer>();
        for (Place place : places) {
            assertEquals(DiscTheme.Surface.RIM, place.on());
            boolean besideARim = false;
            for (Disc disc : discs) {
                // Outside the platform, with the top block of one of the platform's columns right beside it.
                boolean outside = Math.hypot(place.x() - disc.x(), place.z() - disc.z()) >= disc.radius();
                boolean atATop = false;
                for (int[] step : beside) {
                    double fromAxis = Math.hypot(place.x() + step[0] - disc.x(), place.z() + step[1] - disc.z());
                    atATop |= fromAxis < disc.radius() && disc.topBlockAt(fromAxis) == place.y();
                }
                besideARim |= outside && atATop;
                if (disc == d && outside && atATop) {
                    own++;
                    octants.add((int) Math.floor((Math.atan2(place.z() - d.z(), place.x() - d.x()) + Math.PI) / (Math.PI / 4)) % 8);
                }
            }
            assertTrue(besideARim, "not beside any disc's rim: " + place);
        }
        int rim = 0;
        for (int x = (int) Math.floor(d.x() - d.radius()) - 1; x <= d.x() + d.radius() + 1; x++) {
            for (int z = (int) Math.floor(d.z() - d.radius()) - 1; z <= d.z() + d.radius() + 1; z++) {
                boolean inside = false;
                for (int[] step : beside) {
                    inside |= Math.hypot(x + step[0] - d.x(), z + step[1] - d.z()) < d.radius();
                }
                rim += inside && Math.hypot(x - d.x(), z - d.z()) >= d.radius() ? 1 : 0;
            }
        }
        double around = 2 * Math.PI * d.radius();
        assertTrue(rim > 0.8 * around && rim < 1.4 * around, rim + " columns right outside a rim " + Math.round(around) + " blocks round");
        assertEquals(rim / 5.0, own, 0.35 * rim / 5.0, "places on the rim, one in 5 asked for");
        assertEquals(8, octants.size(), "on every side of the disc");
    }

    @Test
    void aGrowthThinsOrGathersOverTheLastBlocksBeforeARim() {
        var disc = new Disc(0, 0, 60, 40, 18);
        var thinning = new DiscTheme.RimFade(10, 0.2F, 0, 1);
        assertEquals(1, thinning.weightAt(77L, 3, disc, 0, 0), 1e-9, "the middle is left alone");
        assertEquals(1, thinning.weightAt(77L, 3, disc, 30, 0), 1e-9, "and so is everything further in than its width");
        assertEquals(0.6, thinning.weightAt(77L, 3, disc, 35, 0), 1e-6, "halfway through the width");
        assertEquals(0.2, thinning.weightAt(77L, 3, disc, 0, -40), 1e-6, "at the rim");
        assertEquals(0.2, thinning.weightAt(77L, 3, disc, 45, 0), 1e-6, "and no less beyond it");
        var gathering = new DiscTheme.RimFade(6, 1.5F, 0, 1);
        assertEquals(1.25, gathering.weightAt(77L, 3, disc, 0, 37), 1e-6, "a fade to more than 1 gathers the growth at the rim");
        assertEquals(1.5, gathering.weightAt(77L, 3, disc, -40, 0), 1e-6);

        // On a disc 40 blocks in radius, a tenth of the radius is 4 blocks: the fade ends there whatever its width.
        var held = new DiscTheme.RimFade(10, 0.2F, 0, 0.1F);
        assertEquals(1, held.weightAt(77L, 3, disc, 35, 0), 1e-9, "past its reach the growth is left alone");
        assertEquals(0.6, held.weightAt(77L, 3, disc, 38, 0), 1e-6, "halfway through what its reach leaves of its width");
        assertEquals(10, thinning.widthAt(77L, 3, new Disc(0, 0, 60, 20, 14), 20, 0), 1e-9, "with no reach given a small disc is faded as far in as a large one");
        for (int step = 0; step < 360; step++) {
            assertTrue(widthRound(new DiscTheme.RimFade(10, 0.2F, 0.8F, 0.25F), disc, 3, step, 360) <= 10 + 1e-9, "a width that wanders past its reach");
        }

        var wandering = new DiscTheme.RimFade(10, 0.2F, 0.8F, 1);
        int steps = 1440;
        double narrowest = Double.MAX_VALUE;
        double widest = 0;
        boolean differs = false;
        for (int step = 0; step < steps; step++) {
            double width = widthRound(wandering, disc, 3, step, steps);
            assertTrue(width >= 2 - 1e-6 && width <= 18 + 1e-6, "a width of " + width);
            // Round the whole rim and back to where it began, with no jump anywhere, the join included.
            assertEquals(width, widthRound(wandering, disc, 3, (step + 1) % steps, steps), 1.5, "a jump at step " + step);
            narrowest = Math.min(narrowest, width);
            widest = Math.max(widest, width);
            differs |= Math.abs(width - widthRound(wandering, disc, 4, step, steps)) > 1;
        }
        assertTrue(narrowest < 5 && widest > 15, "the width only runs from " + narrowest + " to " + widest);
        assertTrue(differs, "every disc wanders alike");
    }

    private static double widthRound(DiscTheme.RimFade fade, Disc disc, int index, int step, int steps) {
        double angle = 2 * Math.PI * step / steps;
        return fade.widthAt(77L, index, disc, (int) Math.round(disc.radius() * Math.cos(angle)), (int) Math.round(disc.radius() * Math.sin(angle)));
    }

    @Test
    void aThemeGrowsLessOfWhatFadesTheNearerTheRim() throws Exception {
        var fading = new DiscTheme.Growth(feature("minecraft:jungle_tree"), 3, DiscTheme.Surface.TOP, Optional.empty(), Optional.of(new DiscTheme.RimFade(12, 0, 0, 1)));
        RavineSettings settings = growing(DiscTheme.Wildness.EVEN, fading);
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
        Disc d = largest(cellDiscs.layout().discs());
        assertTrue(d.radius() >= 30, "a disc of " + d.radius());
        int[] grown = new int[2];
        for (Place place : placesAround(settings, cell, cellDiscs, d)) {
            double fromRim = d.radius() - Math.hypot(place.x() - d.x(), place.z() - d.z());
            if (isOnTopOf(d, place)) {
                grown[0] += fromRim < 6 ? 1 : 0;
                grown[1] += fromRim >= 12 ? 1 : 0;
            }
        }
        int[] columns = new int[2];
        for (int x = (int) Math.floor(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
            for (int z = (int) Math.floor(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                double fromRim = d.radius() - Math.hypot(x - d.x(), z - d.z());
                columns[0] += fromRim >= 1.5 && fromRim < 6 ? 1 : 0;
                columns[1] += fromRim >= 12 ? 1 : 0;
            }
        }
        double nearTheRim = grown[0] / (double) columns[0];
        double inTheMiddle = grown[1] / (double) columns[1];
        assertEquals(1 / 3.0, inTheMiddle, 0.06, "in the middle, one in 3 asked for");
        // From a block and a half to six blocks in, a fade to nothing over twelve leaves about a third.
        assertTrue(nearTheRim > 0.15 * inTheMiddle && nearTheRim < 0.5 * inTheMiddle, nearTheRim + " near the rim against " + inTheMiddle);
    }


    private static boolean isGiant(DiscTheme.Growth growth) {
        String name = growth.feature().location().getPath();
        return name.startsWith("disc/giant_") || name.startsWith("disc/glow_bulb");
    }


    @Test
    void everyFeatureFileOfADiscIsWholeAndNamesOnlyFeaturesThatExist() throws Exception {
        Path features = Path.of(DiscThemeTest.class.getResource("/data/overgrown_abyss/worldgen/configured_feature/disc").toURI());
        int files = 0;
        try (var paths = Files.list(features)) {
            for (Path file : paths.toList()) {
                String text = Files.readString(file);
                assertTrue(JsonParser.parseString(text).getAsJsonObject().has("type"), file + " has no type");
                var named = Pattern.compile("\"overgrown_abyss:(disc/[a-z_]+)\"").matcher(text);
                while (named.find()) {
                    assertTrue(Files.exists(features.getParent().resolve(named.group(1) + ".json")), file.getFileName() + " names " + named.group(1));
                }
                files++;
            }
        }
        assertTrue(files >= 12, files + " feature files");
    }
}

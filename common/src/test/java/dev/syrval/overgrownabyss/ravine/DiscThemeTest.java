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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
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

    static ResourceKey<Biome> biome(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("overgrown_abyss", path));
    }

    static ResourceKey<ConfiguredFeature<?, ?>> feature(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.parse(id));
    }

    static DiscTheme theme(String biome, float weight, DiscTheme.Ramp byHeight, DiscTheme.Ramp byDistance, DiscTheme.Ramp bySize) {
        return new DiscTheme(Optional.of(biome(biome)), Optional.empty(), weight, byHeight, byDistance, bySize, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of());
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
        var kept = new DiscTheme(Optional.of(biome("kept")), Optional.empty(), 100, EVEN, EVEN, EVEN, smallAndFar, DiscPalette.UNPAINTED, Optional.empty(), List.of());
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

    @Test
    void theModsOwnThemesGatherWhereTheirRampsAndLimitsSay() throws Exception {
        RavineSettings settings = shipped();
        DiscTheme crystal = settings.discThemes().stream().filter(t -> t.biome().orElseThrow().location().getPath().equals("disc_crystal")).findFirst().orElseThrow();
        var all = new HashMap<String, Integer>();
        var central = new HashMap<String, Integer>();
        var outlying = new HashMap<String, Integer>();
        var wideAndLow = new HashMap<String, Integer>();
        int crystalBehindALargerDisc = 0;
        for (int cx = -4; cx < 4; cx++) {
            for (int cz = -4; cz < 4; cz++) {
                RavineCell cell = RavineCells.at(11L, settings, cx, cz).orElseThrow();
                CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
                assertEquals(cellDiscs.themes(), CellDiscs.of(settings, VANILLA, cell).themes(), "the same themes every time");
                List<Disc> discs = cellDiscs.layout().discs();
                List<DiscTraits> traits = DiscTraits.of(settings, cell, discs);
                for (int i = 0; i < discs.size(); i++) {
                    DiscTheme theme = cellDiscs.themes().get(i).orElseThrow();
                    String name = theme.biome().orElseThrow().location().getPath();
                    DiscTraits t = traits.get(i);
                    all.merge(name, 1, Integer::sum);
                    if (t.distance() < 0.35) {
                        central.merge(name, 1, Integer::sum);
                    } else if (t.distance() > 0.6 && t.size() < 0.4) {
                        outlying.merge(name, 1, Integer::sum);
                    }
                    if (t.size() > 0.6 && t.height() < 0.4) {
                        wideAndLow.merge(name, 1, Integer::sum);
                    }
                    if (theme == crystal) {
                        assertTrue(crystal.only().allow(t), "a crystal disc outside its limits: " + t);
                        Disc d = discs.get(i);
                        double fromAxis = cell.distanceToCentre(d.x(), d.z());
                        // Seen from the middle, is a larger disc of another theme in front of it, between it and the axis?
                        for (int j = 0; j < discs.size(); j++) {
                            Disc other = discs.get(j);
                            if (cellDiscs.themes().get(j).orElseThrow() != crystal && other.radius() > d.radius()
                                    && cell.distanceToCentre(other.x(), other.z()) < fromAxis
                                    && Math.hypot(other.x() - d.x(), other.z() - d.z()) < other.radius() + d.radius()) {
                                crystalBehindALargerDisc++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        String counts = "all " + all + ", central " + central + ", small and outlying " + outlying + ", wide and low " + wideAndLow;
        int crystals = all.getOrDefault("disc_crystal", 0);
        assertEquals(4, all.size(), "all four themes turn up: " + counts);
        assertTrue(share(central, "disc_jungle") > share(outlying, "disc_jungle") + 0.15, "jungle should gather in the middle: " + counts);
        assertEquals(0, central.getOrDefault("disc_crystal", 0), "no crystal in the middle: " + counts);
        assertTrue(share(all, "disc_crystal") > 0.04 && share(all, "disc_crystal") < 0.15, "crystal stays rare over all: " + counts);
        assertTrue(share(outlying, "disc_crystal") > 0.12, "and is found on the small outlying discs: " + counts);
        assertTrue(share(wideAndLow, "disc_mangrove") > share(all, "disc_mangrove") + 0.1, "mangrove takes the wide low discs: " + counts);
        assertTrue(crystalBehindALargerDisc > 0.75 * crystals, crystalBehindALargerDisc + " of " + crystals + " crystal discs stand behind a larger disc of another theme: " + counts);
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
        RavineCarve bound = carve.bind(11L, VANILLA);
        int inDomes = 0;
        int overridden = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell cell = RavineCells.at(11L, settings, 0, cz).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
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
    void aThemeGrowsThingsOnItsSurfacesAboutAsOftenAsItSays() throws Exception {
        RavineSettings base = shipped();
        var onTop = new DiscTheme.Growth(feature("minecraft:jungle_tree"), 50, DiscTheme.Surface.TOP);
        var under = new DiscTheme.Growth(feature("minecraft:cave_vine"), 20, DiscTheme.Surface.UNDERSIDE);
        var growing = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(onTop, under));
        RavineSettings settings = new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(growing), base.environment(), base.ravine(), base.cone());
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
        Disc d = cellDiscs.layout().discs().get(0);
        record Place(int x, int y, int z, DiscTheme.Surface on) {}
        var places = new ArrayList<Place>();
        var again = new ArrayList<Place>();
        for (int chunkX = Math.floorDiv((int) Math.floor(d.x() - d.radius()), 16); chunkX <= Math.floorDiv((int) Math.floor(d.x() + d.radius()), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv((int) Math.floor(d.z() - d.radius()), 16); chunkZ <= Math.floorDiv((int) Math.floor(d.z() + d.radius()), 16); chunkZ++) {
                int minX = chunkX * 16;
                int minZ = chunkZ * 16;
                DiscGrowth.forEach(settings, cell, cellDiscs, minX, minZ, (x, y, z, feature, on) -> {
                    assertTrue(x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16, "outside its chunk");
                    assertEquals(on == DiscTheme.Surface.TOP ? onTop.feature() : under.feature(), feature);
                    places.add(new Place(x, y, z, on));
                });
                DiscGrowth.forEach(settings, cell, cellDiscs, minX, minZ, (x, y, z, feature, on) -> again.add(new Place(x, y, z, on)));
            }
        }
        assertEquals(places, again, "the same places every time");
        int tops = 0;
        int undersides = 0;
        int inner = 0;
        for (Place place : places) {
            // Other discs of the cell grow things in these chunks too; count the ones on this disc.
            double fromAxis = Math.hypot(place.x() - d.x(), place.z() - d.z());
            if (fromAxis > d.radius()) {
                continue;
            }
            if (place.on() == DiscTheme.Surface.TOP && place.y() == (int) Math.ceil(d.topAt(fromAxis))) {
                assertTrue(d.platformDistance(settings.discs(), place.x(), place.y() - 1, place.z()) < 0, "the block under the place is the platform's top");
                assertTrue(d.platformDistance(settings.discs(), place.x(), place.y(), place.z()) >= 0, "and the place itself is over it");
                tops++;
            } else if (place.on() == DiscTheme.Surface.UNDERSIDE && place.y() == d.hangBlockAt(settings.discs(), fromAxis).orElse(Integer.MIN_VALUE)) {
                // Under the middle of a standing disc the lowest rock is the stem's flare, not the platform.
                assertTrue(d.rockDistance(settings.discs(), place.x(), place.y() + 1, place.z()) < 0, "the block over the place is the disc's rock");
                assertTrue(d.rockDistance(settings.discs(), place.x(), place.y(), place.z()) >= 0, "and the place itself is open");
                undersides++;
                inner += fromAxis < d.radius() / 2 ? 1 : 0;
            }
        }
        double area = Math.PI * d.radius() * d.radius();
        assertTrue(tops > 0.5 * area / 50 && tops < 1.6 * area / 50, tops + " places on a top of " + Math.round(area) + " blocks, one in 50 asked for");
        assertTrue(undersides > 0.5 * area / 20 && undersides < 1.6 * area / 20, undersides + " places under it, one in 20 asked for");
        assertTrue(inner > 0.5 * undersides / 4, inner + " of them under the inner half of the radius, a quarter of the area");
    }

    @Test
    void aThemeIsWrittenAsItsBiomeWeightsPaletteAndGrowth() {
        var json = JsonParser.parseString("""
                {
                  "biome": "overgrown_abyss:disc_crystal",
                  "weight": 1.0,
                  "by_height": {"bottom": 3.0, "top": 0.5},
                  "by_distance": {"centre": 0.5, "edge": 3.0},
                  "palette": {
                    "body": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:calcite"}}
                  },
                  "water": {"ponds": 0.25, "pond_size": 14.0, "stream_width": 2.0, "stream_spacing": 36.0, "depth": 1, "bank": 4},
                  "growth": [
                    {"feature": "overgrown_abyss:disc/amethyst_cluster", "every": 7},
                    {"feature": "minecraft:cave_vine", "every": 30, "on": "underside"},
                    {"feature": "minecraft:mangrove", "every": 12, "on": "water"}
                  ]
                }
                """);
        DiscTheme parsed = DiscTheme.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(Optional.of(biome("disc_crystal")), parsed.biome());
        assertEquals(new DiscTheme.Ramp(3, 0.5F), parsed.byHeight());
        assertEquals(DiscTheme.Ramp.EVEN, parsed.bySize(), "a ramp left out is even");
        assertTrue(parsed.palette().body().isPresent() && parsed.palette().top().isEmpty());
        assertEquals(DiscTheme.Surface.TOP, parsed.growth().get(0).on(), "things grow on the top unless told otherwise");
        assertEquals(new DiscTheme.Growth(feature("minecraft:cave_vine"), 30, DiscTheme.Surface.UNDERSIDE), parsed.growth().get(1));
        assertEquals(DiscTheme.Surface.WATER, parsed.growth().get(2).on());
        assertEquals(Optional.of(new DiscWater(0.25F, 14, 2, 36, 1, 4)), parsed.water());
        assertEquals(json, DiscTheme.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow(), "and is written back the same");
        var onlyAWeight = JsonParser.parseString("{\"weight\": 2}");
        DiscTheme bare = DiscTheme.CODEC.parse(JsonOps.INSTANCE, onlyAWeight).getOrThrow();
        assertTrue(bare.biome().isEmpty() && bare.growth().isEmpty() && bare.water().isEmpty() && bare.palette().equals(DiscPalette.UNPAINTED),
                "everything but the weight is optional");
        var bad = json.getAsJsonObject().deepCopy();
        bad.getAsJsonArray("growth").get(0).getAsJsonObject().addProperty("every", 0);
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, bad).isError(), "growth on every block of none");
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
        var water = new DiscWater(0.4F, 12, 2, 40, 2, 3);
        var inWater = new DiscTheme.Growth(feature("minecraft:mangrove"), 4, DiscTheme.Surface.WATER);
        var stone = new DiscPalette(List.of(), List.of(), base.discThemes().get(0).palette().body(), DiscPalette.UNPAINTED.stem());
        var swamp = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, stone, Optional.of(water), List.of(inWater));
        RavineSettings settings = new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(swamp), base.environment(), base.ravine(), base.cone());
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
        RavineCarve bound = carve.bind(11L, VANILLA);
        RavineSettings live = bound.settings();
        int plots = 0;
        int yieldedToAHigherDisc = 0;
        for (int cz = 0; cz < 3; cz++) {
            RavineCell cell = RavineCells.at(11L, live, 0, cz).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(live, VANILLA, cell);
            List<Disc> all = cellDiscs.layout().discs();
            for (int i = 0; i < all.size(); i++) {
                Disc d = all.get(i);
                Optional<DiscTheme.Inherits> inherits = cellDiscs.themes().get(i).orElseThrow().inherits();
                int x = (int) Math.floor(d.x());
                int z = (int) Math.floor(d.z());
                int minX = Math.floorDiv(x, 16) * 16;
                int minZ = Math.floorDiv(z, 16) * 16;
                // The chunk under the disc's middle holds this disc's plot exactly when its theme inherits.
                var found = new ArrayList<DiscPlot>();
                bound.forEachInheritingDisc(minX, minZ, (what, plot) -> {
                    if (plot.groundAt(x, z).isPresent() && plot.groundAt(x, z).getAsInt() == (int) Math.ceil(d.topAt(Math.hypot(x - d.x(), z - d.z())))
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
    void theModsOwnThemesInheritTheirBiomesAndGrowWhatTheirParentsMayNot() throws Exception {
        List<DiscTheme> themes = shipped().discThemes();
        assertEquals(4, themes.size());
        var parents = new HashMap<String, String>();
        var leftOut = new HashMap<String, List<String>>();
        for (DiscTheme theme : themes) {
            String name = theme.biome().orElseThrow().location().getPath();
            assertTrue(DiscThemeTest.class.getResource("/data/overgrown_abyss/worldgen/biome/" + name + ".json") != null, "no biome file for " + name);
            String overworld = new String(DiscThemeTest.class.getResourceAsStream("/data/minecraft/tags/worldgen/biome/is_overworld.json").readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(overworld.contains("overgrown_abyss:" + name), name + " is not tagged as an overworld biome");
            theme.inherits().ifPresent(inherits -> {
                parents.put(name, inherits.biome().location().toString());
                leftOut.put(name, inherits.withoutFeatures().stream().map(key -> key.location().toString()).toList());
            });
            String biomeFile = new String(DiscThemeTest.class.getResourceAsStream("/data/overgrown_abyss/worldgen/biome/" + name + ".json").readAllBytes(), StandardCharsets.UTF_8);
            int ownSpawns = 0;
            for (var category : JsonParser.parseString(biomeFile).getAsJsonObject().getAsJsonObject("spawners").entrySet()) {
                ownSpawns += category.getValue().getAsJsonArray().size();
            }
            // The file's spawns are added to what is inherited, so a copy of the parent's list there would pin every entry.
            assertEquals(theme.inherits().isPresent(), ownSpawns == 0, name + ": spawns of its own only where it inherits none");
            assertTrue(theme.palette().blockAt(new DiscPoint.Platform(0.5, 3.5)).isPresent(), name + " leaves its platform to the terrain, cave holes and all");
            for (DiscTheme.Growth growth : theme.growth()) {
                // Under the mod's own ids, so that a pack which restyles vanilla's trees does not change a disc.
                assertEquals("overgrown_abyss", growth.feature().location().getNamespace(), name + " grows " + growth.feature().location());
                String file = "/data/overgrown_abyss/worldgen/configured_feature/" + growth.feature().location().getPath() + ".json";
                assertTrue(DiscThemeTest.class.getResource(file) != null, "no feature file " + file);
                assertTrue(growth.on() != DiscTheme.Surface.WATER || theme.water().isPresent(), name + " grows something in water it does not have");
            }
        }
        assertEquals(Map.of("disc_lush", "minecraft:lush_caves", "disc_jungle", "minecraft:jungle", "disc_mangrove", "minecraft:mangrove_swamp"), parents);
        // A disc grows its own trees, since another pack may give the parent trees that cannot grow on one; the parent's are
        // left out so that a disc is not twice as thick with trees where they can.
        assertEquals(Map.of("disc_lush", List.of(), "disc_jungle", List.of("minecraft:trees_jungle"), "disc_mangrove", List.of("minecraft:trees_mangrove")), leftOut);
        DiscTheme lush = themes.get(0);
        assertTrue(lush.growth().isEmpty() && lush.water().isEmpty(), "the lush disc is its parent and nothing more");
        for (DiscTheme wet : List.of(themes.get(1), themes.get(2))) {
            assertTrue(wet.water().orElseThrow().depth() <= 2, "water deeper than two blocks");
            assertTrue(wet.growth().stream().anyMatch(growth -> growth.on() == DiscTheme.Surface.UNDERSIDE), "nothing hangs under the disc");
        }
        assertTrue(themes.get(2).growth().stream().anyMatch(growth -> growth.on() == DiscTheme.Surface.WATER), "no mangrove stands in the water");
        assertTrue(themes.get(2).water().orElseThrow().ponds() > themes.get(1).water().orElseThrow().ponds(), "a swamp is wetter than a jungle");
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

package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
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
        return new DiscTheme(Optional.of(biome(biome)), weight, byHeight, byDistance, bySize, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, List.of());
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
        var kept = new DiscTheme(Optional.of(biome("kept")), 100, EVEN, EVEN, EVEN, smallAndFar, DiscPalette.UNPAINTED, List.of());
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
        var growing = new DiscTheme(Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, List.of(onTop, under));
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
            } else if (place.on() == DiscTheme.Surface.UNDERSIDE && place.y() == (int) Math.floor(d.undersideAt(settings.discs(), fromAxis))) {
                assertTrue(d.platformDistance(settings.discs(), place.x(), place.y() + 1, place.z()) < 0, "the block over the place is the platform's underside");
                undersides++;
            }
        }
        double area = Math.PI * d.radius() * d.radius();
        assertTrue(tops > 0.5 * area / 50 && tops < 1.6 * area / 50, tops + " places on a top of " + Math.round(area) + " blocks, one in 50 asked for");
        assertTrue(undersides > 0.5 * area / 20 && undersides < 1.6 * area / 20, undersides + " places under it, one in 20 asked for");
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
                  "growth": [
                    {"feature": "overgrown_abyss:disc/amethyst_cluster", "every": 7},
                    {"feature": "minecraft:cave_vine", "every": 30, "on": "underside"}
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
        assertEquals(json, DiscTheme.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow(), "and is written back the same");
        var onlyAWeight = JsonParser.parseString("{\"weight\": 2}");
        DiscTheme bare = DiscTheme.CODEC.parse(JsonOps.INSTANCE, onlyAWeight).getOrThrow();
        assertTrue(bare.biome().isEmpty() && bare.growth().isEmpty() && bare.palette().equals(DiscPalette.UNPAINTED), "everything but the weight is optional");
        var bad = json.getAsJsonObject().deepCopy();
        bad.getAsJsonArray("growth").get(0).getAsJsonObject().addProperty("every", 0);
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, bad).isError(), "growth on every block of none");
    }

    @Test
    void theModsOwnThemesEachHaveABiomeAMaterialForEveryPartAndSomethingGrowing() throws Exception {
        List<DiscTheme> themes = shipped().discThemes();
        assertEquals(4, themes.size());
        for (DiscTheme theme : themes) {
            String name = theme.biome().orElseThrow().location().toString();
            assertTrue(name.startsWith("overgrown_abyss:disc_"), name);
            assertTrue(DiscThemeTest.class.getResource("/data/overgrown_abyss/worldgen/biome/" + theme.biome().get().location().getPath() + ".json") != null, "no biome file for " + name);
            assertTrue(theme.palette().blockAt(new DiscPoint.Platform(0.5, 3.5)).isPresent(), name + " top");
            assertTrue(theme.palette().blockAt(new DiscPoint.Platform(3.5, 0.5)).isPresent(), name + " underside");
            assertTrue(theme.palette().blockAt(new DiscPoint.Platform(20, 20)).isPresent(), name + " body");
            assertTrue(theme.palette().blockAt(new DiscPoint.Stem(0.5)).isPresent() && theme.palette().blockAt(new DiscPoint.Stem(20)).isPresent(), name + " stem");
            assertTrue(!theme.growth().isEmpty(), name + " grows nothing");
            for (DiscTheme.Growth growth : theme.growth()) {
                if (growth.feature().location().getNamespace().equals("overgrown_abyss")) {
                    String file = "/data/overgrown_abyss/worldgen/configured_feature/" + growth.feature().location().getPath() + ".json";
                    assertTrue(DiscThemeTest.class.getResource(file) != null, "no feature file " + file);
                }
            }
        }
    }
}

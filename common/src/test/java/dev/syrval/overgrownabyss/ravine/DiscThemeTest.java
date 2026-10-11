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
        return new DiscTheme(Optional.of(biome(biome)), Optional.empty(), weight, byHeight, byDistance, bySize, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty());
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
        var theme = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(growth), wildness, DiscRelief.FLAT, Optional.empty(), Optional.empty());
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
        var kept = new DiscTheme(Optional.of(biome("kept")), Optional.empty(), 100, EVEN, EVEN, EVEN, smallAndFar, DiscPalette.UNPAINTED, Optional.empty(), List.of(), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty());
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
        DiscTheme lava = settings.discThemes().stream().filter(DiscTheme::holdsLava).findFirst().orElseThrow();
        var all = new HashMap<String, Integer>();
        var central = new HashMap<String, Integer>();
        var outlying = new HashMap<String, Integer>();
        var wideAndLow = new HashMap<String, Integer>();
        var low = new HashMap<String, Integer>();
        var high = new HashMap<String, Integer>();
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
                    if (t.height() < 0.3) {
                        low.merge(name, 1, Integer::sum);
                    } else if (t.height() > 0.7) {
                        high.merge(name, 1, Integer::sum);
                    }
                    if (theme == lava) {
                        assertTrue(lava.only().allow(t), "a lava disc outside its limits: " + t);
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
        String counts = "all " + all + ", central " + central + ", small and outlying " + outlying + ", wide and low " + wideAndLow
                + ", low " + low + ", high " + high;
        int crystals = all.getOrDefault("disc_crystal", 0);
        assertEquals(5, all.size(), "all five themes turn up: " + counts);
        assertTrue(share(central, "disc_jungle") > share(outlying, "disc_jungle") + 0.15, "jungle should gather in the middle: " + counts);
        assertEquals(0, central.getOrDefault("disc_crystal", 0), "no crystal in the middle: " + counts);
        assertTrue(share(all, "disc_crystal") > 0.04 && share(all, "disc_crystal") < 0.15, "crystal stays rare over all: " + counts);
        assertTrue(share(outlying, "disc_crystal") > 0.12, "and is found on the small outlying discs: " + counts);
        assertTrue(share(wideAndLow, "disc_mangrove") > share(all, "disc_mangrove") + 0.1, "mangrove takes the wide low discs: " + counts);
        assertTrue(share(all, "disc_lava") > 0.01 && share(all, "disc_lava") < 0.05, "lava is a rare find over all: " + counts);
        assertTrue(lava.only().height().max() <= 0.4, "and is kept to the lower levels of a hole: " + lava.only());
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
    void aThemeGrowsThingsOnItsSurfacesAboutAsOftenAsItSays() throws Exception {
        RavineSettings base = shipped();
        var onTop = new DiscTheme.Growth(feature("minecraft:jungle_tree"), 50, DiscTheme.Surface.TOP, Optional.empty(), Optional.empty());
        var under = new DiscTheme.Growth(feature("minecraft:cave_vine"), 20, DiscTheme.Surface.UNDERSIDE, Optional.empty(), Optional.empty());
        var growing = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(onTop, under), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty());
        RavineSettings settings = new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), VerticalAnchor.absolute(-40), VerticalAnchor.absolute(80),
                base.cavernRadius(), base.cavernHeight(), base.edgeFalloff(), base.discs(), List.of(growing), base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
        RavineCell cell = RavineCells.at(11L, settings, 0, 0).orElseThrow();
        CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
        Disc d = cellDiscs.layout().discs().get(0);
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
                    {"feature": "minecraft:mangrove", "every": 12, "on": "water", "patches": {"size": 32.0, "cover": 0.25}},
                    {"feature": "minecraft:jungle_tree", "every": 45, "rim_fade": {"width": 10.0, "to": 0.25, "wander": 0.75, "reach": 0.25}},
                    {"feature": "minecraft:vines", "every": 12, "on": "rim"}
                  ],
                  "wildness": {"min": 0.5, "max": 1.5},
                  "ground": {
                    "mounds": {"height": 3, "size": 22.0, "cover": 0.25},
                    "outcrops": {"height": 5, "size": 8.0, "cover": 0.5, "block": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:andesite"}}}
                  }
                }
                """);
        DiscTheme parsed = DiscTheme.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(Optional.of(biome("disc_crystal")), parsed.biome());
        assertEquals(new DiscTheme.Ramp(3, 0.5F), parsed.byHeight());
        assertEquals(DiscTheme.Ramp.EVEN, parsed.bySize(), "a ramp left out is even");
        assertTrue(parsed.palette().body().isPresent() && parsed.palette().top().isEmpty());
        assertEquals(DiscTheme.Surface.TOP, parsed.growth().get(0).on(), "things grow on the top unless told otherwise");
        assertEquals(new DiscTheme.Growth(feature("minecraft:cave_vine"), 30, DiscTheme.Surface.UNDERSIDE, Optional.empty(), Optional.empty()), parsed.growth().get(1));
        assertEquals(DiscTheme.Surface.WATER, parsed.growth().get(2).on());
        assertTrue(parsed.growth().get(0).patches().isEmpty(), "spread evenly unless given patches");
        assertEquals(Optional.of(new DiscTheme.Patches(32, 0.25F)), parsed.growth().get(2).patches());
        assertEquals(Optional.of(new DiscWater(0.25F, 14, 2, 36, 1, 4, DiscWater.Fluid.WATER, 0)), parsed.water());
        assertEquals(Optional.of(new DiscTheme.RimFade(10, 0.25F, 0.75F, 0.25F)), parsed.growth().get(3).rimFade());
        assertTrue(parsed.growth().get(0).rimFade().isEmpty(), "as thick at the rim as anywhere unless given a fade");
        assertEquals(DiscTheme.Surface.RIM, parsed.growth().get(4).on());
        assertEquals(new DiscTheme.Wildness(0.5F, 1.5F), parsed.wildness());
        assertEquals(Optional.of(new DiscRelief.Mounds(3, 22, 0.25F)), parsed.ground().mounds());
        assertEquals(5, parsed.ground().outcrops().orElseThrow().height());
        assertEquals(json, DiscTheme.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow(), "and is written back the same");
        var onlyAWeight = JsonParser.parseString("{\"weight\": 2}");
        DiscTheme bare = DiscTheme.CODEC.parse(JsonOps.INSTANCE, onlyAWeight).getOrThrow();
        assertTrue(bare.biome().isEmpty() && bare.growth().isEmpty() && bare.water().isEmpty() && bare.palette().equals(DiscPalette.UNPAINTED),
                "everything but the weight is optional");
        assertEquals(DiscTheme.Wildness.EVEN, bare.wildness(), "every disc as overgrown as the next unless told otherwise");
        assertEquals(DiscRelief.FLAT, bare.ground(), "and the platform is the ground unless told otherwise");
        var moundsOfNothing = json.getAsJsonObject().deepCopy();
        moundsOfNothing.remove("palette");
        moundsOfNothing.remove("water");
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, moundsOfNothing).isError(), "mounds with no palette to make them of");
        var fadingOnTheRim = json.getAsJsonObject().deepCopy();
        fadingOnTheRim.getAsJsonArray("growth").get(4).getAsJsonObject().add("rim_fade", JsonParser.parseString("{\"width\": 4, \"to\": 0}"));
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, fadingOnTheRim).isError(), "a fade towards the rim for what grows on the rim");
        var wilderAtLeast = json.getAsJsonObject().deepCopy();
        wilderAtLeast.getAsJsonObject("wildness").addProperty("min", 2);
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, wilderAtLeast).isError(), "a least wildness over the most");
        var bad = json.getAsJsonObject().deepCopy();
        bad.getAsJsonArray("growth").get(0).getAsJsonObject().addProperty("every", 0);
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, bad).isError(), "growth on every block of none");
        var coversNothing = json.getAsJsonObject().deepCopy();
        coversNothing.getAsJsonArray("growth").get(2).getAsJsonObject().getAsJsonObject("patches").addProperty("cover", 0);
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, coversNothing).isError(), "patches that cover nothing");
    }

    @Test
    void aGrowthInPatchesKeepsItsAverageButLeavesTheRestOfTheSurfaceBare() {
        var groves = new DiscTheme.Patches(8, 0.3F);
        var edges = new DiscTheme.Patches(8, 0.6F);
        var other = new DiscTheme.Patches(12, 0.3F);
        double sum = 0;
        int bare = 0;
        int apart = 0;
        int ownOnly = 0;
        int all = 0;
        for (int x = 0; x < 800; x++) {
            for (int z = 0; z < 800; z++) {
                double weight = groves.weightAt(77L, 3, x, z);
                assertTrue(weight >= 0 && weight <= 2 / 0.3 + 1e-6, "a weight of " + weight);
                assertTrue(weight == 0 || edges.weightAt(77L, 3, x, z) > 0, "patches of the same size are the same patches, the smaller cover inside the larger");
                sum += weight;
                bare += weight == 0 ? 1 : 0;
                apart += weight > 0 && other.weightAt(77L, 3, x, z) == 0 ? 1 : 0;
                ownOnly += weight > 0 && groves.weightAt(77L, 4, x, z) == 0 ? 1 : 0;
                all++;
            }
        }
        assertEquals(1, sum / all, 0.06, "on average as often as asked for");
        assertEquals(0.7, bare / (double) all, 0.03, "and none of it in the seven tenths the patches do not cover");
        assertTrue(apart > 0.5 * 0.3 * 0.7 * all, "patches of another size lie elsewhere");
        assertTrue(ownOnly > 0.5 * 0.3 * 0.7 * all, "each disc has patches of its own");
    }

    @Test
    void aThemeGrowsWhatItGivesPatchesOnlyInThosePatches() throws Exception {
        RavineSettings base = shipped();
        var patches = new DiscTheme.Patches(8, 0.3F);
        var inPatches = new DiscTheme.Growth(feature("minecraft:mangrove"), 6, DiscTheme.Surface.TOP, Optional.of(patches), Optional.empty());
        var growing = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, DiscPalette.UNPAINTED, Optional.empty(), List.of(inPatches), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty());
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
        var swamp = new DiscTheme(Optional.empty(), Optional.empty(), 1, EVEN, EVEN, EVEN, DiscTheme.Limits.NONE, stone, Optional.of(water), List.of(inWater), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty());
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
                    List.of(molten ? inWater : overLava, molten ? overLava : inWater), DiscTheme.Wildness.EVEN, DiscRelief.FLAT, Optional.empty(), Optional.empty());
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
    void theModsOwnThemesInheritTheirBiomesAndGrowWhatTheirParentsMayNot() throws Exception {
        List<DiscTheme> themes = shipped().discThemes();
        assertEquals(5, themes.size());
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
                assertTrue(growth.on() != DiscTheme.Surface.WATER || theme.water().isPresent() && !theme.holdsLava(), name + " grows something in water it does not have");
                assertTrue(growth.on() != DiscTheme.Surface.LAVA || theme.holdsLava(), name + " grows something over lava it does not have");
            }
        }
        assertEquals(Map.of("disc_lush", "minecraft:lush_caves", "disc_jungle", "minecraft:jungle", "disc_mangrove", "minecraft:mangrove_swamp"), parents);
        // A disc grows its own trees, since another pack may give the parent trees that cannot grow on one; the parent's are
        // left out so that a disc is not twice as thick with trees where they can.
        assertEquals(Map.of("disc_lush", List.of(), "disc_jungle", List.of("minecraft:trees_jungle"), "disc_mangrove", List.of("minecraft:trees_mangrove")), leftOut);
        DiscTheme lush = themes.get(0);
        assertTrue(lush.water().isEmpty() && lush.palette().top().isEmpty(), "the lush disc's ground is its parent's and nothing more");
        for (DiscTheme wet : List.of(themes.get(1), themes.get(2))) {
            assertTrue(wet.water().orElseThrow().depth() <= 2, "water deeper than two blocks");
            assertTrue(wet.growth().stream().anyMatch(growth -> growth.on() == DiscTheme.Surface.UNDERSIDE), "nothing hangs under the disc");
            // A small disc keeps the trees of its middle.
            assertTrue(wet.growth().stream().flatMap(growth -> growth.rimFade().stream()).filter(fade -> fade.to() < 1).allMatch(fade -> fade.reach() <= 0.3),
                    "a fade that thins trees may take a small disc's whole radius");
            // The thickest disc has to be one a player can still walk through.
            assertTrue(wet.wildness().min() < 1 && wet.wildness().max() > 1 && wet.wildness().max() <= 1.5, "wildness " + wet.wildness());
        }
        for (DiscTheme theme : themes) {
            // Outcrops are in a player's way; mounds are not, but ground a player has to climb all the time is no path either.
            assertTrue(theme.ground().mounds().isPresent() || theme.ground().outcrops().isPresent(), "a top as smooth as a plate");
            assertTrue(theme.ground().outcrops().map(DiscRelief.Outcrops::cover).orElse(0F) <= 0.1F, "outcrops over much of a disc");
            assertTrue(theme.ground().mounds().map(DiscRelief.Mounds::cover).orElse(0F) <= 0.5F, "mounds over most of a disc");
        }
        assertEquals(1, themes.get(2).ground().mounds().orElseThrow().height(), "a swamp's ground is hummocks, not hills");
        for (DiscTheme green : themes.subList(0, 3)) {
            assertTrue(green.growth().stream().anyMatch(growth -> growth.on() == DiscTheme.Surface.RIM), "nothing hangs over the rim");
            List<DiscTheme.Growth> giants = green.growth().stream().filter(DiscThemeTest::isGiant).toList();
            // A few to a disc, and listed before the trees: a cap needs the air it fills, and a tree grows round one.
            assertTrue(!giants.isEmpty() && giants.stream().allMatch(growth -> growth.every() >= 300), "giants: " + giants);
            assertEquals(giants, green.growth().subList(0, giants.size()), "something is grown before the giants");
        }
        assertTrue(themes.get(1).growth().stream().anyMatch(growth -> growth.rimFade().filter(fade -> fade.to() < 1 && fade.wander() > 0).isPresent()),
                "the jungle's trees stand as thick at the rim as in the middle, the same all the way round");
        // A mangrove's crown reaches out over the rim by itself, and thinning the trees there took a third of that away.
        assertTrue(themes.get(2).growth().stream().flatMap(growth -> growth.rimFade().stream()).noneMatch(fade -> fade.to() < 1),
                "the mangrove's trees are thinned towards the rim");
        assertTrue(themes.get(2).growth().stream().anyMatch(growth -> growth.on() == DiscTheme.Surface.WATER), "no mangrove stands in the water");
        assertTrue(themes.get(2).water().orElseThrow().ponds() > themes.get(1).water().orElseThrow().ponds(), "a swamp is wetter than a jungle");
        DiscTheme lava = themes.get(4);
        assertTrue(lava.holdsLava() && lava.water().orElseThrow().streamWidth() == 0, "the lava disc's ponds are of lava, and it has no streams");
        // The owner took the obsidian trees out for now; their feature file stays, for putting them back.
        assertTrue(lava.growth().isEmpty(), "something grows on the lava disc: " + lava.growth());
        // Lava lights what burns three blocks from it, and a crown stands out past its trunk.
        assertTrue(lava.water().orElseThrow().clear() >= 5 && lava.water().orElseThrow().bank() >= 5, "lava within reach of the disc next to it");
        assertTrue(lava.rootWood().isEmpty(), "roots keep the wood of the hole: the lava keeps clear of them");
        assertTrue(themes.subList(0, 4).stream().noneMatch(DiscTheme::holdsLava), "lava on another disc");
        // The game spawns a magma cube in any light, where a cave's monsters want the dark that a pond of lava takes away.
        String lavaBiome = new String(DiscThemeTest.class.getResourceAsStream("/data/overgrown_abyss/worldgen/biome/disc_lava.json").readAllBytes(), StandardCharsets.UTF_8);
        MobSpawnSettings lavaSpawns = MobSpawnSettings.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(lavaBiome)).getOrThrow();
        assertTrue(lavaSpawns.getMobs(MobCategory.MONSTER).unwrap().stream().anyMatch(mob -> mob.type == EntityType.MAGMA_CUBE), "no magma cubes on the lava disc");
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

    @Test
    void eachDiscOfAThemeIsAsOvergrownAsItsOwnDrawSays() throws Exception {
        var uneven = new DiscTheme.Wildness(0.5F, 1.5F);
        double sum = 0;
        var seen = new HashSet<Double>();
        for (int index = 0; index < 400; index++) {
            double factor = uneven.factorFor(77L, index);
            assertTrue(factor >= 0.5 && factor <= 1.5, "a factor of " + factor);
            assertEquals(factor, uneven.factorFor(77L, index), "a disc keeps its factor");
            sum += factor;
            seen.add(factor);
        }
        assertEquals(1, sum / 400, 0.05, "spread evenly between the two");
        assertTrue(seen.size() > 390, "each disc has a draw of its own");
        assertEquals(1, DiscTheme.Wildness.EVEN.factorFor(77L, 9), 1e-9);

        var tree = new DiscTheme.Growth(feature("minecraft:jungle_tree"), 40, DiscTheme.Surface.TOP, Optional.empty(), Optional.empty());
        RavineCell cell = RavineCells.at(11L, growing(DiscTheme.Wildness.EVEN, tree), 0, 0).orElseThrow();
        var counts = new HashMap<DiscTheme.Wildness, Integer>();
        var all = new HashMap<DiscTheme.Wildness, List<Place>>();
        Disc d = null;
        int index = -1;
        for (DiscTheme.Wildness wildness : List.of(DiscTheme.Wildness.EVEN, new DiscTheme.Wildness(2, 2), new DiscTheme.Wildness(0, 0), uneven)) {
            RavineSettings settings = growing(wildness, tree);
            CellDiscs cellDiscs = CellDiscs.of(settings, VANILLA, cell);
            d = largest(cellDiscs.layout().discs());
            index = cellDiscs.layout().discs().indexOf(d);
            List<Place> places = placesAround(settings, cell, cellDiscs, d);
            Disc disc = d;
            counts.put(wildness, (int) places.stream().filter(place -> isOnTopOf(disc, place)).count());
            all.put(wildness, places);
        }
        int even = counts.get(DiscTheme.Wildness.EVEN);
        assertTrue(even > 40, even + " places on the disc");
        assertTrue(all.get(new DiscTheme.Wildness(2, 2)).containsAll(all.get(DiscTheme.Wildness.EVEN)), "a wilder disc grows all that a tamer one does");
        assertEquals(2 * even, counts.get(new DiscTheme.Wildness(2, 2)), 0.25 * 2 * even, "and twice as wild is twice as much");
        assertTrue(all.get(new DiscTheme.Wildness(0, 0)).isEmpty(), "a factor of nothing grows nothing");
        double own = uneven.factorFor(cell.hash(), index);
        assertEquals(own * even, counts.get(uneven), 0.3 * own * even + 5, "the disc's own factor of " + own);
    }

    private static boolean isGiant(DiscTheme.Growth growth) {
        String name = growth.feature().location().getPath();
        return name.startsWith("disc/giant_") || name.startsWith("disc/glow_bulb");
    }

    @Test
    void theJungleDiscsLayersLieInOneOrderAllTheWayRoundARim() throws Exception {
        DiscPalette jungle = shipped().discThemes().stream()
                .filter(t -> t.biome().orElseThrow().location().getPath().equals("disc_jungle")).findFirst().orElseThrow().palette();
        // Soil, then what water left on it, then what was built there: three of these under the ground, never out of this order.
        List<Block> order = List.of(Blocks.DIRT, Blocks.MUD, Blocks.CLAY, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE);
        var found = new HashSet<Block>();
        var random = RandomSource.create(0);
        var pos = new BlockPos.MutableBlockPos();
        for (int x = -2000; x <= 2000; x += 11) {
            for (int z = -2000; z <= 2000; z += 11) {
                for (int ground = -30; ground <= 150; ground += 60) {
                    int[] places = new int[3];
                    for (int below = 1; below <= 3; below++) {
                        // As the painter asks: the layer by how deep the block is, the block by where it is.
                        Block block = jungle.blockAt(new DiscPoint.Platform(below + 0.5, Double.MAX_VALUE)).orElseThrow()
                                .getState(random, pos.set(x, ground - below, z)).getBlock();
                        places[below - 1] = order.indexOf(block);
                        found.add(block);
                    }
                    String where = "under " + x + ", " + ground + ", " + z + ": " + places[0] + ", " + places[1] + ", " + places[2];
                    assertTrue(places[0] >= 0 && places[0] <= places[1] && places[1] <= places[2], "out of order " + where);
                    assertTrue(places[0] < places[2], "one block three deep " + where);
                }
            }
        }
        assertEquals(Set.copyOf(order), found, "every one of the five shows somewhere");
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

package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

/**
 * The troves the mod's own disc themes name, against the files they rest on. Their templates are the mod's own, and binary,
 * so this is where each is held to what a trove is: a few blocks of masonry with one container at its foot that names a loot
 * table, no air and nothing that gives off light.
 */
class DiscTroveFilesTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String DATA = "/data/overgrown_abyss/";
    private static final String CARVE = DATA + "worldgen/density_function/ravine/carve.json";
    private static final String WEATHERED = "overgrown_abyss:disc_troves/weathered";
    private static final Set<String> CONTAINERS = Set.of("minecraft:chest", "minecraft:barrel");

    private static JsonObject json(String resource) throws IOException {
        try (var in = DiscTroveFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** The files of one of the mod's folders, each as {@code prefix} and its name without its ending. */
    private static Set<String> files(String folder, String prefix) throws IOException {
        try {
            Path path = Path.of(DiscTroveFilesTest.class.getResource(DATA + folder).toURI());
            try (var files = Files.list(path)) {
                return files.map(file -> prefix + file.getFileName().toString().replaceFirst("\\.[a-z]+$", "")).collect(Collectors.toSet());
            }
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /** The kinds of every theme's troves, as the mod's settings list them. */
    private static List<JsonObject> kinds() throws IOException {
        var kinds = new ArrayList<JsonObject>();
        for (JsonElement theme : json(CARVE).getAsJsonArray("disc_themes")) {
            JsonObject troves = theme.getAsJsonObject().getAsJsonObject("troves");
            if (troves != null) {
                assertFalse(troves.has("structures"), "the mod's troves are all its own");
                troves.getAsJsonArray("kinds").forEach(kind -> kinds.add(kind.getAsJsonObject()));
            }
        }
        return kinds;
    }

    @Test
    void everyTroveIsAFewBlocksOfMasonryWithOneContainerAtItsFoot() throws IOException {
        Set<String> pools = new HashSet<>();
        Set<String> templates = new HashSet<>();
        for (JsonObject kind : kinds()) {
            ResourceLocation pool = ResourceLocation.parse(kind.get("pool").getAsString());
            assertEquals("overgrown_abyss", pool.getNamespace(), "the mod's themes name the mod's own pools as kinds");
            assertFalse(kind.has("sink"), "a trove stands on the ground, with none of it let in: " + kind);
            pools.add(pool.getPath());
            for (JsonObject entry : JarRuinPieces.elements(pool)) {
                JsonObject element = entry.getAsJsonObject("element");
                assertEquals("minecraft:single_pool_element", element.get("element_type").getAsString(), pool + " holds single templates");
                assertEquals("rigid", element.get("projection").getAsString(), "a piece stands where its site is, not on the level's surface");
                assertEquals(WEATHERED, element.get("processors").getAsString(), "which keep every block of a trove");
                String location = element.get("location").getAsString();
                if (templates.add(location)) {
                    assertIsATrove(location);
                }
            }
        }
        assertTrue(pools.size() >= 2, "kinds of trove: " + pools);
        assertEquals(files("worldgen/template_pool/disc_troves", "disc_troves/"), pools, "every pool of troves is named by a theme");
        assertEquals(files("structure/trove", "overgrown_abyss:trove/"), templates, "every trove template is in a pool");
    }

    private static void assertIsATrove(String location) {
        CompoundTag template = JarRuinPieces.template(ResourceLocation.parse(location)).orElseThrow(() -> new AssertionError(location + " is missing"));
        DiscRuinPieces.Measure measure = DiscRuinPieces.Measure.of(template).orElseThrow(() -> new AssertionError(location + " builds nothing"));
        assertEquals(0, measure.emptied(), location + " holds no air, which would empty whatever stood where it is put");
        assertTrue(measure.sizeX() <= 2 && measure.sizeZ() <= 2 && measure.built() <= 4, location + " is small: " + measure);
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        int masonry = 0;
        int containers = 0;
        for (Tag each : template.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) each;
            CompoundTag saved = palette.getCompound(block.getInt("state"));
            String name = saved.getString("Name");
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), saved);
            // A name the game does not know is read as air.
            assertFalse(state.isAir(), location + " names a block the game has not: " + name);
            assertEquals(saved.getCompound("Properties").size(), state.getProperties().size(), location + " writes every property of " + name);
            assertEquals(0, state.getLightEmission(), location + " gives off no light, but has " + name);
            if (!CONTAINERS.contains(name)) {
                assertFalse(block.contains("nbt"), name + " in " + location + " carries no data");
                masonry++;
                continue;
            }
            containers++;
            assertEquals(0, block.getList("pos", Tag.TAG_INT).getInt(1), "the container of " + location + " is at its foot");
            CompoundTag data = block.getCompound("nbt");
            assertEquals(name, data.getString("id"), "the container of " + location + " is saved as what it is");
            // The table it holds wherever a theme gives its troves no loot; with none it would be left empty, table and all.
            ResourceLocation table = ResourceLocation.parse(data.getString("LootTable"));
            assertNotNull(DiscTroveFilesTest.class.getResource("/data/" + table.getNamespace() + "/loot_table/" + table.getPath() + ".json"),
                    location + " names the loot table " + table + ", which is no file of the mod's");
        }
        assertEquals(1, containers, "containers in " + location);
        assertTrue(masonry >= 2 && masonry <= 4, masonry + " blocks of masonry in " + location);
    }

    @Test
    void weatheringChangesOnlyTheStoneAndTakesNothingAway() {
        JsonArray processors = JarRuinPieces.processors(ResourceLocation.parse(WEATHERED)).getAsJsonArray("processors");
        // A rot would take blocks away at random, the container among them.
        assertEquals(List.of("overgrown_abyss:swap_blocks", "minecraft:protected_blocks"),
                processors.asList().stream().map(each -> each.getAsJsonObject().get("processor_type").getAsString()).toList());
        assertFalse(DiscRuinPieces.leavesAirOut(processors), "a trove has no air to leave out");
        assertEquals("#overgrown_abyss:ruins_cannot_replace", processors.get(1).getAsJsonObject().get("value").getAsString(),
                "a trove leaves alone what a ruin does: a root of the hole's, bedrock");
        int swaps = 0;
        for (JsonElement each : processors.get(0).getAsJsonObject().getAsJsonArray("swaps")) {
            Block from = block(each.getAsJsonObject().get("from").getAsString());
            assertFalse(CONTAINERS.contains(BuiltInRegistries.BLOCK.getKey(from).toString()), "a container stays what it is");
            for (JsonElement to : each.getAsJsonObject().getAsJsonArray("to")) {
                Block into = block(to.getAsJsonObject().get("block").getAsString());
                assertEquals(Set.copyOf(from.defaultBlockState().getProperties()), Set.copyOf(into.defaultBlockState().getProperties()),
                        from + " is swapped for a block of its own shape, not " + into);
                assertEquals(0, into.defaultBlockState().getLightEmission(), into + " gives off no light");
            }
            swaps++;
        }
        assertTrue(swaps >= 6, swaps + " swaps");
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new AssertionError(id + " is no block of the game's"));
    }

    @Test
    void theTrovesAreAStructureOfTheirOwnBuiltFromTheTroveSites() throws IOException {
        JsonObject structure = json(DATA + "worldgen/structure/disc_troves.json");
        assertEquals("overgrown_abyss:disc_ruins", structure.get("type").getAsString(), "built as the ruins are");
        assertEquals("troves", structure.get("sites").getAsString());
        // Bearding would have the terrain shaped round pieces that stand on rock the mod itself puts there.
        assertEquals("none", structure.get("terrain_adaptation").getAsString());
        assertFalse(json(DATA + "worldgen/structure/disc_ruins.json").has("sites"), "the ruins' structure builds the ruins without saying so");

        JsonObject set = json(DATA + "worldgen/structure_set/disc_troves.json");
        JsonObject placement = set.getAsJsonObject("placement");
        assertEquals("overgrown_abyss:disc_ruins", placement.get("type").getAsString());
        assertEquals(List.of("overgrown_abyss:disc_troves"), set.getAsJsonArray("structures").asList().stream()
                .map(entry -> entry.getAsJsonObject().get("structure").getAsString()).toList());
        JsonObject ofRuins = json(DATA + "worldgen/structure_set/disc_ruins.json").getAsJsonObject("placement");
        assertNotEquals(ofRuins.get("salt").getAsInt(), placement.get("salt").getAsInt(), "a salt of its own");

        // Read by the mod's own codecs: which sites a file names is kept, and one that names none is for the ruins.
        assertEquals("troves", written(placement).get("sites").getAsString());
        assertFalse(written(ofRuins).has("sites"), "the ruins' placement is as it was");
        // A structure's biomes are a tag, which only a level's registries can read; the rest of the file is read here.
        structure.add("biomes", new JsonArray());
        JsonElement again = DiscRuinsStructure.CODEC.codec().encodeStart(
                JsonOps.INSTANCE, DiscRuinsStructure.CODEC.codec().parse(JsonOps.INSTANCE, structure).getOrThrow()).getOrThrow();
        assertEquals("troves", again.getAsJsonObject().get("sites").getAsString());
    }

    // A placement as the mod's codec reads it and writes it again.
    private static JsonObject written(JsonObject placement) {
        return DiscRuinsPlacement.CODEC.codec().encodeStart(
                JsonOps.INSTANCE, DiscRuinsPlacement.CODEC.codec().parse(JsonOps.INSTANCE, placement).getOrThrow()).getOrThrow().getAsJsonObject();
    }
}

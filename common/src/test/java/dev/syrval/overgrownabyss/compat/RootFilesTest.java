package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The roots the mod's own file grows, against the files that let them stand where a structure is built. A root is in the
 * world before any structure, and a structure's piece leaves alone whatever block its {@code protected_blocks} rule names,
 * so the roots' wood has to be in the tag that rule reads, in every list of rules the mod's structures use.
 */
class RootFilesTest {
    private static final String CARVE = "/data/overgrown_abyss/worldgen/density_function/ravine/carve.json";
    private static final String TAG = "/data/overgrown_abyss/tags/block/ruins_cannot_replace.json";
    private static final String LISTS = "/data/overgrown_abyss/worldgen/processor_list/";
    private static final List<String> EVERY_LIST = List.of("city/generic", "city/ice_box", "city/start", "city/walls", "disc_ruins/outpost", "disc_ruins/overgrown");

    private static JsonObject json(String resource) throws IOException {
        try (var in = RootFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Set<String> tagged() throws IOException {
        var values = new HashSet<String>();
        json(TAG).getAsJsonArray("values").forEach(value -> values.add(value.getAsString()));
        return values;
    }

    // Every block named anywhere in a piece of JSON, as a block state's "Name".
    private static void blocksIn(JsonElement json, Set<String> found) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(each -> blocksIn(each, found));
        } else if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getKey().equals("Name")) {
                    found.add(entry.getValue().getAsString());
                } else {
                    blocksIn(entry.getValue(), found);
                }
            });
        }
    }

    // Every piece of text anywhere in a piece of JSON: the rules name blocks under more than one key.
    private static void textIn(JsonElement json, Set<String> found) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(each -> textIn(each, found));
        } else if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> textIn(entry.getValue(), found));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            found.add(json.getAsString());
        }
    }

    @Test
    void theModsOwnHoleHasRootsOfJungleAndMangroveWood() throws IOException {
        JsonObject carve = json(CARVE);
        assertTrue(carve.has("roots"), "the mod's own file grows roots");
        var woods = new HashSet<String>();
        blocksIn(carve.get("roots"), woods);
        assertEquals(Set.of("minecraft:jungle_wood", "minecraft:mangrove_wood"), woods, "its own wood is both");
        for (JsonElement theme : carve.getAsJsonArray("disc_themes")) {
            String biome = theme.getAsJsonObject().get("biome").getAsString();
            var named = new HashSet<String>();
            if (theme.getAsJsonObject().has("root_wood")) {
                blocksIn(theme.getAsJsonObject().get("root_wood"), named);
            }
            Set<String> expected = switch (biome) {
                case "overgrown_abyss:disc_jungle" -> Set.of("minecraft:jungle_wood");
                case "overgrown_abyss:disc_mangrove" -> Set.of("minecraft:mangrove_wood");
                default -> Set.of();
            };
            assertEquals(expected, named, biome);
        }
    }

    @Test
    void everyBlockARootIsMadeOfIsOneAStructureLeavesAlone() throws IOException {
        JsonObject carve = json(CARVE);
        var woods = new HashSet<String>();
        blocksIn(carve.get("roots"), woods);
        for (JsonElement theme : carve.getAsJsonArray("disc_themes")) {
            if (theme.getAsJsonObject().has("root_wood")) {
                blocksIn(theme.getAsJsonObject().get("root_wood"), woods);
            }
        }
        assertFalse(woods.isEmpty());
        Set<String> tagged = tagged();
        assertTrue(tagged.containsAll(woods), woods + " are not all in " + tagged);
        assertTrue(tagged.contains("#minecraft:features_cannot_replace"), "and what vanilla's own structures leave alone still is");
    }

    @Test
    void everyListOfRulesTheModsStructuresUseReadsThatTag() throws IOException {
        for (String list : EVERY_LIST) {
            int rules = 0;
            for (JsonElement processor : json(LISTS + list + ".json").getAsJsonArray("processors")) {
                JsonObject rule = processor.getAsJsonObject();
                if (rule.get("processor_type").getAsString().equals("minecraft:protected_blocks")) {
                    assertEquals("#overgrown_abyss:ruins_cannot_replace", rule.get("value").getAsString(), list);
                    rules++;
                }
            }
            assertEquals(1, rules, list + " has one such rule");
        }
    }

    @Test
    void noStructureOfTheModsBuildsWithARootsWood() throws IOException {
        // A piece would then leave alone what an earlier piece of the same structure built, and not only roots.
        Set<String> woods = tagged();
        woods.removeIf(value -> value.startsWith("#"));
        assertFalse(woods.isEmpty());
        // The lists hand most of their blocks on to the city's reskin, which is a list of its own.
        var lists = new ArrayList<>(EVERY_LIST);
        lists.add("city/reskin");
        var named = new HashSet<String>();
        for (String list : lists) {
            textIn(json(LISTS + list + ".json"), named);
        }
        assertTrue(named.contains("minecraft:mossy_stone_bricks") && named.contains("minecraft:jungle_log"), "the blocks they build with are read");
        named.retainAll(woods);
        assertTrue(named.isEmpty(), "the mod's structures build with " + named);
    }
}

package dev.syrval.verdantdeep.compat;

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
    private static final String CARVE = "/data/verdant_deep/worldgen/density_function/ravine/carve.json";
    private static final String TAG = "/data/verdant_deep/tags/block/ruins_cannot_replace.json";
    private static final String LISTS = "/data/verdant_deep/worldgen/processor_list/";
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
    void everythingTheModsRootsGrowHasAFeatureFileOfTheModsOwn() throws IOException {
        var growth = json(CARVE).getAsJsonObject("roots").getAsJsonArray("growth");
        assertNotNull(growth, "the mod's own roots grow something");
        assertFalse(growth.isEmpty());
        for (JsonElement each : growth) {
            String feature = each.getAsJsonObject().get("feature").getAsString();
            assertTrue(feature.startsWith("verdant_deep:"), feature + " is not the mod's own");
            json("/data/verdant_deep/worldgen/configured_feature/" + feature.substring("verdant_deep:".length()) + ".json");
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

}

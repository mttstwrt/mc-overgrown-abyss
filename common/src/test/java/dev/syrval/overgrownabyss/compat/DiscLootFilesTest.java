package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.Test;

/**
 * The loot tables the mod's disc themes name for their ruins and their troves: each is a file of the mod's and reads as the
 * game reads it. A table that names an item or a function the pinned version does not have would only be found out in a
 * game, as a chest that comes up empty.
 */
class DiscLootFilesTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String CARVE = "/data/overgrown_abyss/worldgen/density_function/ravine/carve.json";

    private static JsonElement file(String resource) throws IOException {
        try (var in = DiscLootFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not among the mod's files");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * The tables of each theme that has any for its {@code ruins} or its {@code troves}, by the theme's biome, as the mod's own
     * settings file lists them.
     */
    private static Map<String, List<ResourceLocation>> tablesByTheme(String which) throws IOException {
        var found = new LinkedHashMap<String, List<ResourceLocation>>();
        for (JsonElement each : file(CARVE).getAsJsonObject().getAsJsonArray("disc_themes")) {
            JsonObject theme = each.getAsJsonObject();
            if (theme.has(which) && theme.getAsJsonObject(which).has("loot")) {
                var tables = new ArrayList<ResourceLocation>();
                theme.getAsJsonObject(which).getAsJsonObject("loot").getAsJsonArray("tables")
                        .forEach(table -> tables.add(ResourceLocation.parse(table.getAsJsonObject().get("table").getAsString())));
                found.put(theme.get("biome").getAsString(), tables);
            }
        }
        return found;
    }

    /**
     * What a loot table is read with: vanilla's enchantments and their tags, which a table's enchanting functions choose from.
     * Items and potions are the game's built-in ones.
     */
    private static DynamicOps<JsonElement> ops() {
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource()))) {
            RegistryAccess.Frozen loaded = RegistryDataLoader.load(
                    manager, RegistryLayer.createRegistryAccess().getAccessForLoading(RegistryLayer.WORLDGEN), RegistryDataLoader.WORLDGEN_REGISTRIES);
            Registry<Enchantment> enchantments = loaded.registryOrThrow(Registries.ENCHANTMENT);
            var tags = new TagLoader<Holder<Enchantment>>(enchantments::getHolder, Registries.tagsDirPath(Registries.ENCHANTMENT)).loadAndBuild(manager);
            enchantments.bindTags(tags.entrySet().stream().collect(Collectors.toMap(
                    entry -> TagKey.create(Registries.ENCHANTMENT, entry.getKey()), entry -> List.copyOf(entry.getValue()))));
            return loaded.createSerializationContext(JsonOps.INSTANCE);
        }
    }

    @Test
    void everyThemeWithRuinsOfItsOwnNamesTablesOfItsOwn() throws IOException {
        Map<String, List<ResourceLocation>> tables = tablesByTheme("ruins");
        assertEquals(List.of("overgrown_abyss:disc_lush", "overgrown_abyss:disc_jungle", "overgrown_abyss:disc_mangrove"), List.copyOf(tables.keySet()));
        var all = new HashSet<ResourceLocation>();
        for (var theme : tables.entrySet()) {
            assertEquals(3, theme.getValue().size(), theme.getKey() + " has a poor, a middling and a rich table");
            for (ResourceLocation table : theme.getValue()) {
                assertEquals("overgrown_abyss", table.getNamespace(), "a table of the mod's own, so that a pack can pick the disc ruins out by it");
                assertTrue(all.add(table), table + " is named once, by one theme");
            }
        }
    }

    @Test
    void aThemesTrovesHoldAStashOfTheirOwnAndThenItsRuinsTablesOneBehind() throws IOException {
        Map<String, List<ResourceLocation>> ruins = tablesByTheme("ruins");
        Map<String, List<ResourceLocation>> troves = tablesByTheme("troves");
        assertEquals(ruins.keySet(), troves.keySet(), "the themes with ruins of their own have troves");
        var stashes = new HashSet<ResourceLocation>();
        for (var theme : troves.entrySet()) {
            List<ResourceLocation> ofRuins = ruins.get(theme.getKey());
            assertEquals(3, theme.getValue().size(), theme.getKey() + " has three tables for its troves");
            ResourceLocation stash = theme.getValue().getFirst();
            assertEquals("overgrown_abyss", stash.getNamespace(), "a table of the mod's own, so that a pack can pick the troves out by it");
            assertFalse(ofRuins.contains(stash), stash + " is the troves' own, poorer than any of the ruins'");
            assertTrue(stashes.add(stash), stash + " is named once, by one theme");
            assertEquals(ofRuins.subList(0, 2), theme.getValue().subList(1, 3),
                    "where a ruin of " + theme.getKey() + " holds its second and third tables a trove holds its first and second");
        }
    }

    @Test
    void everyTableReadsAsTheGameReadsIt() throws IOException {
        DynamicOps<JsonElement> ops = ops();
        var named = new ArrayList<>(tablesByTheme("ruins").values());
        named.addAll(tablesByTheme("troves").values());
        for (List<ResourceLocation> tables : named) {
            for (ResourceLocation table : tables) {
                JsonElement json = file("/data/" + table.getNamespace() + "/loot_table/" + table.getPath() + ".json");
                var read = LootTable.DIRECT_CODEC.parse(ops, json);
                assertTrue(read.isSuccess(), table + ": " + read.error().map(Object::toString).orElse(""));
                assertEquals("minecraft:chest", json.getAsJsonObject().get("type").getAsString(), table + " is rolled for a chest");
            }
        }
    }
}

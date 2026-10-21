package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
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


    private static final Set<String> LIT = Set.of("minecraft:magma_block");
    private static final List<String> STYLES = List.of("crystal_", "lava_");

    private static String spawnerMob(String file) {
        return file.startsWith("crystal_") ? "minecraft:vex" : file.startsWith("lava_") ? "minecraft:wither_skeleton" : "minecraft:cave_spider";
    }

    /** The kind of a trove from its file, as {@code trove/<style>_<kind>_<n>}; a marker is as small as a cairn. */
    private static TroveTemplates.Kind kindOf(String file) {
        String kind = file.replaceFirst("_\\d+$", "");
        for (String style : STYLES) {
            kind = kind.startsWith(style) ? kind.substring(style.length()) : kind;
        }
        return kind.equals("marker") ? TroveTemplates.Kind.CAIRN : TroveTemplates.Kind.valueOf(kind.toUpperCase());
    }

    private static void assertIsATrove(String location) {
        String file = location.substring(location.indexOf('/') + 1);
        TroveTemplates.Kind kind = kindOf(file);
        boolean nest = TroveTemplates.hasSpawner(kind);
        int floor = nest ? TroveTemplates.NEST_FLOOR : 0;
        CompoundTag template = JarRuinPieces.template(ResourceLocation.parse(location)).orElseThrow(() -> new AssertionError(location + " is missing"));
        DiscRuinPieces.Measure measure = DiscRuinPieces.Measure.of(template).orElseThrow(() -> new AssertionError(location + " builds nothing"));
        assertEquals(0, measure.emptied(), location + " holds no air, which would empty whatever stood where it is put");
        int footprint = switch (kind) {
            case CAIRN -> 2;
            case NEST -> 3;
            case HOARD, HOARD_NEST -> STYLES.stream().anyMatch(file::startsWith) ? 3 : 4;
        };
        assertTrue(measure.sizeX() <= footprint && measure.sizeZ() <= footprint && measure.built() - floor <= 4, location + " is small: " + measure);
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        var states = new java.util.HashMap<List<Integer>, BlockState>();
        var names = new java.util.HashMap<List<Integer>, String>();
        var datas = new java.util.HashMap<List<Integer>, CompoundTag>();
        for (Tag each : template.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) each;
            CompoundTag saved = palette.getCompound(block.getInt("state"));
            String name = saved.getString("Name");
            ListTag at = block.getList("pos", Tag.TAG_INT);
            List<Integer> pos = List.of(at.getInt(0), at.getInt(1), at.getInt(2));
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), saved);
            // A name the game does not know is read as air.
            assertFalse(state.isAir(), location + " names a block the game has not: " + name);
            assertEquals(saved.getCompound("Properties").size(), state.getProperties().size(), location + " writes every property of " + name);
            assertTrue(state.getLightEmission() == 0 || LIT.contains(name), location + " gives off no light, but has " + name);
            states.put(pos, state);
            names.put(pos, name);
            if (block.contains("nbt")) {
                datas.put(pos, block.getCompound("nbt"));
            }
        }
        int masonry = 0;
        int containers = 0;
        int spawners = 0;
        for (var entry : names.entrySet()) {
            String name = entry.getValue();
            List<Integer> pos = entry.getKey();
            CompoundTag data = datas.get(pos);
            if (name.equals(BuiltInRegistries.BLOCK.getKey(Blocks.SPAWNER).toString())) {
                spawners++;
                assertEquals(floor - 1, pos.get(1), "the spawner of " + location + " is 1 block under the floor, so it spawns on it");
                assertEquals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(BlockEntityType.MOB_SPAWNER).toString(), data.getString("id"), location);
                assertEquals(spawnerMob(file), data.getCompound("SpawnData").getCompound("entity").getString("id"), "the mob of " + location);
                assertEquals(16, data.getShort("RequiredPlayerRange"), location);
                int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] step : around) {
                    List<Integer> next = List.of(pos.get(0) + step[0], pos.get(1) + step[1], pos.get(2) + step[2]);
                    BlockState beside = states.get(next);
                    assertNotNull(beside, "the spawner of " + location + " is walled in, not open at " + next);
                    assertTrue(beside.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !datas.containsKey(next),
                            "the spawner of " + location + " has a solid block, the cap above it a full non-container one, at " + next);
                }
            } else if (CONTAINERS.contains(name)) {
                containers++;
                assertEquals(floor, pos.get(1), "the container of " + location + " is on the floor");
                assertEquals(name, data.getString("id"), "the container of " + location + " is saved as what it is");
                // The table it holds wherever a theme gives its troves no loot; with none it would be left empty, table and all.
                ResourceLocation table = ResourceLocation.parse(data.getString("LootTable"));
                assertNotNull(DiscTroveFilesTest.class.getResource("/data/" + table.getNamespace() + "/loot_table/" + table.getPath() + ".json"),
                        location + " names the loot table " + table + ", which is no file of the mod's");
            } else {
                assertNull(data, name + " in " + location + " carries no data");
                if (pos.get(1) >= floor) {
                    masonry++;
                }
            }
        }
        assertEquals(nest ? 1 : 0, spawners, "spawners in " + location);
        switch (kind) {
            case CAIRN, NEST -> {
                assertEquals(1, containers, "containers in " + location);
                assertTrue(masonry >= 2 && masonry <= 4, masonry + " blocks of masonry in " + location);
            }
            case HOARD, HOARD_NEST -> {
                assertTrue(containers >= 2 && containers <= 3, containers + " containers in " + location);
                assertTrue(masonry >= 2 && masonry <= 8, masonry + " blocks of masonry in " + location);
            }
        }
    }

    @Test
    void allTroveTemplatesObeyTheirInvariants() throws IOException {
        Set<String> troves = files("structure/trove", "overgrown_abyss:trove/");
        assertTrue(troves.size() >= 6, "the troves are found: " + troves);
        troves.stream().sorted().forEach(DiscTroveFilesTest::assertIsATrove);
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


    // A placement as the mod's codec reads it and writes it again.
    private static JsonObject written(JsonObject placement) {
        return DiscRuinsPlacement.CODEC.codec().encodeStart(
                JsonOps.INSTANCE, DiscRuinsPlacement.CODEC.codec().parse(JsonOps.INSTANCE, placement).getOrThrow()).getOrThrow().getAsJsonObject();
    }
}

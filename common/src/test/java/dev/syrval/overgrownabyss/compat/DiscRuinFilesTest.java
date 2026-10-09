package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The ruins the mod's own disc themes name, against the files they rest on. A kind says how much ground and air its pieces
 * need, and a place is found for it by those numbers alone; the pieces are vanilla's templates, read here from the game's
 * jar, so a number that no longer covers a template shows up here and not as a ruin through a disc's rim or a stem.
 */
class DiscRuinFilesTest {
    private static final String CARVE = "/data/overgrown_abyss/worldgen/density_function/ravine/carve.json";
    private static final String POOLS = "/data/overgrown_abyss/worldgen/template_pool/";
    // What a template holds that is never built: gaps, and the marker blocks a pool element leaves out.
    private static final Set<String> NOT_BUILT = Set.of("minecraft:air", "minecraft:structure_void", "minecraft:structure_block");

    private static JsonObject json(String resource) throws IOException {
        try (var in = DiscRuinFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static CompoundTag template(ResourceLocation id) throws IOException {
        String resource = "/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
        try (var in = DiscRuinFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }

    // The highest layer of a template that holds a block which is built.
    private static int highestLayer(CompoundTag template) {
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        int highest = -1;
        for (Tag each : template.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) each;
            if (!NOT_BUILT.contains(palette.getCompound(block.getInt("state")).getString("Name"))) {
                highest = Math.max(highest, block.getList("pos", Tag.TAG_INT).getInt(1));
            }
        }
        return highest;
    }

    @Test
    void everyPieceFitsTheGroundAndAirItsKindAsksFor() throws IOException {
        Set<String> named = new HashSet<>();
        int pieces = 0;
        for (JsonElement theme : json(CARVE).getAsJsonArray("disc_themes")) {
            if (!theme.getAsJsonObject().has("ruins")) {
                continue;
            }
            for (JsonElement each : theme.getAsJsonObject().getAsJsonObject("ruins").getAsJsonArray("kinds")) {
                JsonObject kind = each.getAsJsonObject();
                ResourceLocation pool = ResourceLocation.parse(kind.get("pool").getAsString());
                assertEquals("overgrown_abyss", pool.getNamespace(), "the mod's themes use the mod's own pools");
                named.add(pool.getPath());
                double radius = kind.get("radius").getAsDouble();
                int height = kind.get("height").getAsInt();
                int sink = kind.has("sink") ? kind.get("sink").getAsInt() : 0;
                for (JsonElement entry : json(POOLS + pool.getPath() + ".json").getAsJsonArray("elements")) {
                    JsonObject element = entry.getAsJsonObject().getAsJsonObject("element");
                    assertEquals("minecraft:single_pool_element", element.get("element_type").getAsString(), pool + " holds single templates");
                    assertEquals("rigid", element.get("projection").getAsString(), "a piece stands where its site is, not on the level's surface");
                    ResourceLocation location = ResourceLocation.parse(element.get("location").getAsString());
                    CompoundTag template = template(location);
                    ListTag size = template.getList("size", Tag.TAG_INT);
                    // A piece is turned about its middle, so its furthest corner may come to lie anywhere on this round.
                    double corner = Math.hypot(size.getInt(0), size.getInt(2)) / 2;
                    assertTrue(corner <= radius, location + " reaches " + corner + " from its middle, and " + pool + " keeps " + radius);
                    int over = highestLayer(template) + 1 - sink;
                    assertTrue(over <= height, location + " stands " + over + " over the ground, and " + pool + " keeps " + height);
                    assertTrue(over >= 1, location + " would be wholly in the ground");
                    pieces++;
                }
            }
        }
        assertTrue(pieces >= 50, pieces + " pieces looked at");
        assertEquals(poolFiles(), named, "every pool of ruins is named by a theme");
    }

    private static Set<String> poolFiles() throws IOException {
        try {
            Path folder = Path.of(DiscRuinFilesTest.class.getResource(POOLS + "disc_ruins").toURI());
            try (var files = Files.list(folder)) {
                return files.map(file -> "disc_ruins/" + file.getFileName().toString().replaceFirst("\\.json$", "")).collect(Collectors.toSet());
            }
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    @Test
    void theRuinsAreAStructureOfTheirOwnWithItsOwnPlacement() throws IOException {
        JsonObject structure = json("/data/overgrown_abyss/worldgen/structure/disc_ruins.json");
        assertEquals("overgrown_abyss:disc_ruins", structure.get("type").getAsString());
        // Bearding would have the terrain shaped round pieces that stand on rock the mod itself puts there.
        assertEquals("none", structure.get("terrain_adaptation").getAsString());
        JsonObject set = json("/data/overgrown_abyss/worldgen/structure_set/disc_ruins.json");
        assertEquals("overgrown_abyss:disc_ruins", set.getAsJsonObject("placement").get("type").getAsString());
        assertEquals(List.of("overgrown_abyss:disc_ruins"), set.getAsJsonArray("structures").asList().stream()
                .map(entry -> entry.getAsJsonObject().get("structure").getAsString()).toList());
    }
}

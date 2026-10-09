package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The ruins the mod's own disc themes name, against the files they rest on. How much ground and air a piece needs is measured
 * from its template when a level loads; the templates are vanilla's, read here from the game's jar, so this is where the
 * measure itself is checked and where a pool that could not be measured shows up.
 */
class DiscRuinFilesTest {
    private static final String CARVE = "/data/overgrown_abyss/worldgen/density_function/ravine/carve.json";
    private static final String POOLS = "/data/overgrown_abyss/worldgen/template_pool/";
    private static final String BORROWED = "/data/overgrown_abyss/tags/worldgen/structure/on_discs/jungle.json";

    private static JsonObject json(String resource) throws IOException {
        try (var in = DiscRuinFilesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static DiscRuinPieces.Measure measure(String template) {
        return JarRuinPieces.template(ResourceLocation.parse(template)).flatMap(DiscRuinPieces.Measure::of).orElseThrow();
    }

    @Test
    void aTemplateIsMeasuredByItsFootprintAndTheLayersItBuildsAndEmpties() {
        // Each of the city's tall ruins is saved in a box 23 high, whatever is left standing in it, with no air in it.
        assertEquals(new DiscRuinPieces.Measure(17, 17, 19, 0), measure("minecraft:ancient_city/structures/tall_ruin_1"));
        assertEquals(new DiscRuinPieces.Measure(17, 17, 13, 0), measure("minecraft:ancient_city/structures/tall_ruin_2"));
        assertEquals(new DiscRuinPieces.Measure(17, 17, 3, 0), measure("minecraft:ancient_city/structures/camp_3"));
        assertEquals(new DiscRuinPieces.Measure(19, 15, 10, 0), measure("minecraft:ancient_city/structures/chamber_1"));
        // The ocean's ruins and an outpost's plate are saved with the air of their whole box.
        assertEquals(new DiscRuinPieces.Measure(6, 7, 6, 7), measure("minecraft:underwater_ruin/brick_1"));
        assertEquals(new DiscRuinPieces.Measure(16, 16, 7, 16), measure("minecraft:underwater_ruin/big_mossy_1"));
        DiscRuinPieces.Measure plate = measure("minecraft:pillager_outpost/base_plate");
        assertEquals(new DiscRuinPieces.Measure(16, 16, 2, 30), plate);
        assertEquals(30, plate.layers(true), "placed with its air, a piece needs the room it empties");
        assertEquals(2, plate.layers(false), "placed without, only what it builds");
        assertEquals(19, measure("minecraft:ancient_city/structures/tall_ruin_1").layers(true), "and never less than that");
        // A piece is turned about its middle, so its furthest corner may come to lie anywhere on this round.
        assertEquals(Math.hypot(19, 15) / 2, measure("minecraft:ancient_city/structures/chamber_1").radius(), 1e-9);
    }

    @Test
    void aPoolsProcessorsSayWhetherItsPiecesPlaceTheirAir() {
        assertTrue(DiscRuinPieces.leavesAirOut(JarRuinPieces.processors(ResourceLocation.parse("overgrown_abyss:disc_ruins/overgrown"))),
                "the mod's ocean ruins stand in the disc's own ground and air");
        assertFalse(DiscRuinPieces.leavesAirOut(JarRuinPieces.processors(ResourceLocation.parse("overgrown_abyss:disc_ruins/outpost"))));
        assertFalse(DiscRuinPieces.leavesAirOut(JarRuinPieces.processors(ResourceLocation.parse("minecraft:mossify_20_percent"))));
        assertTrue(DiscRuinPieces.leavesAirOut(JsonParser.parseString("""
                [{"processor_type": "minecraft:block_rot", "integrity": 0.9},
                 {"processor_type": "overgrown_abyss:processor_list", "processors": {"processors": [
                   {"processor_type": "minecraft:block_ignore", "blocks": [{"Name": "minecraft:stone"}, {"Name": "minecraft:air"}]}]}}]""")),
                "in a list within a list");
        assertFalse(DiscRuinPieces.leavesAirOut(JsonParser.parseString("""
                {"processors": [{"processor_type": "minecraft:block_ignore", "blocks": [{"Name": "minecraft:gravel"}]}]}""")), "leaving out something else");
        assertFalse(DiscRuinPieces.leavesAirOut(JsonParser.parseString("\"minecraft:empty\"")), "a list that is only named says nothing by itself");
    }

    private static CompoundTag block(int state, int y) {
        var block = new CompoundTag();
        block.putInt("state", state);
        var pos = new ListTag();
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(0));
        block.put("pos", pos);
        return block;
    }

    private static CompoundTag template(String paletteField, ListTag palette, CompoundTag... blocks) {
        var template = new CompoundTag();
        var size = new ListTag();
        size.add(IntTag.valueOf(3));
        size.add(IntTag.valueOf(9));
        size.add(IntTag.valueOf(5));
        template.put("size", size);
        template.put(paletteField, palette);
        var list = new ListTag();
        list.addAll(List.of(blocks));
        template.put("blocks", list);
        return template;
    }

    private static ListTag palette(String... names) {
        var palette = new ListTag();
        for (String name : names) {
            var state = new CompoundTag();
            state.putString("Name", name);
            palette.add(state);
        }
        return palette;
    }

    @Test
    void whatATemplateNeverBuildsIsNotMeasured() {
        ListTag palette = palette("minecraft:stone", "minecraft:air", "minecraft:structure_void", "minecraft:structure_block");
        assertEquals(Optional.of(new DiscRuinPieces.Measure(3, 5, 3, 5)),
                DiscRuinPieces.Measure.of(template("palette", palette, block(0, 0), block(0, 2), block(1, 4), block(2, 6), block(3, 8))),
                "gaps and marker blocks are never placed, and air is counted apart from what is built");
        assertEquals(Optional.empty(), DiscRuinPieces.Measure.of(template("palette", palette, block(1, 0), block(3, 1))), "a template that builds nothing");
        var several = new ListTag();
        several.add(palette);
        several.add(palette("minecraft:cobblestone", "minecraft:air", "minecraft:structure_void", "minecraft:structure_block"));
        assertEquals(Optional.of(new DiscRuinPieces.Measure(3, 5, 6, 8)),
                DiscRuinPieces.Measure.of(template("palettes", several, block(0, 5), block(1, 7))), "a template with several palettes");
        assertEquals(Optional.empty(), DiscRuinPieces.Measure.of(new CompoundTag()), "not a template at all");
    }

    @Test
    void everyPieceOfTheModsOwnPoolsCanBeMeasuredAndStandsOverTheGround() throws IOException {
        Set<String> named = new HashSet<>();
        int pieces = 0;
        for (JsonElement theme : json(CARVE).getAsJsonArray("disc_themes")) {
            if (!theme.getAsJsonObject().has("ruins")) {
                continue;
            }
            for (JsonElement each : theme.getAsJsonObject().getAsJsonObject("ruins").getAsJsonArray("kinds")) {
                JsonObject kind = each.getAsJsonObject();
                assertFalse(kind.has("radius") || kind.has("height"), "the room a piece needs is measured, not written: " + kind);
                ResourceLocation pool = ResourceLocation.parse(kind.get("pool").getAsString());
                assertEquals("overgrown_abyss", pool.getNamespace(), "the mod's themes name the mod's own pools as kinds");
                named.add(pool.getPath());
                int sink = kind.has("sink") ? kind.get("sink").getAsInt() : 0;
                for (JsonObject entry : JarRuinPieces.elements(pool)) {
                    JsonObject element = entry.getAsJsonObject("element");
                    assertEquals("minecraft:single_pool_element", element.get("element_type").getAsString(), pool + " holds single templates");
                    assertEquals("rigid", element.get("projection").getAsString(), "a piece stands where its site is, not on the level's surface");
                    String location = element.get("location").getAsString();
                    Optional<DiscRuinPieces.Measure> measure = JarRuinPieces.template(ResourceLocation.parse(location)).flatMap(DiscRuinPieces.Measure::of);
                    assertTrue(measure.isPresent(), location + " is missing or builds nothing");
                    assertTrue(measure.get().built() > sink, location + " would be wholly in the ground");
                    assertEquals(measure.get().built(), JarRuinPieces.piece(null, 0, entry, 0).orElseThrow().height(),
                            location + " empties nothing above what it builds: its template has no air, or its processors leave air out");
                    pieces++;
                }
            }
        }
        assertTrue(pieces >= 50, pieces + " pieces looked at");
        assertEquals(poolFiles(), named, "every pool of ruins is named by a theme");
    }

    @Test
    void theStructuresTheModBorrowsFromOtherModsAreAllOptional() throws IOException {
        JsonObject tag = json(BORROWED);
        assertFalse(tag.has("replace") && tag.get("replace").getAsBoolean(), "packs add to the tag");
        assertFalse(tag.getAsJsonArray("values").isEmpty());
        for (JsonElement value : tag.getAsJsonArray("values")) {
            assertTrue(value.isJsonObject() && !value.getAsJsonObject().get("required").getAsBoolean(),
                    value + " must be optional: the mod loads without the mods whose structures it borrows");
            assertFalse(value.getAsJsonObject().get("id").getAsString().startsWith("minecraft:"), value + " is not another mod's");
        }
        Set<String> tags = new HashSet<>();
        for (JsonElement theme : json(CARVE).getAsJsonArray("disc_themes")) {
            JsonObject ruins = theme.getAsJsonObject().getAsJsonObject("ruins");
            if (ruins != null && ruins.has("structures")) {
                ruins.getAsJsonArray("structures").forEach(entry -> tags.add(entry.getAsJsonObject().get("tag").getAsString()));
            }
        }
        assertEquals(Set.of("#overgrown_abyss:on_discs/jungle"), tags, "the tags the mod's themes borrow from");
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

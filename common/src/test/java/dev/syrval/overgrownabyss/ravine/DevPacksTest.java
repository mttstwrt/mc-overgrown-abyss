package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A testing datapack replaces the whole carve file, so each one has to keep up with the settings' schema. */
class DevPacksTest {
    static {
        MinecraftBootstrap.init();
    }

    private static final Path PACKS = Path.of("..", "dev-datapacks");
    private static final Path CARVE = Path.of("data", "overgrown_abyss", "worldgen", "density_function", "ravine", "carve.json");

    @Test
    void everyTestingDatapackLoadsWithTheCurrentSettings() throws IOException {
        assumeTrue(Files.isDirectory(PACKS), "the testing datapacks are not next to this module");
        int packs = 0;
        try (var folders = Files.newDirectoryStream(PACKS, Files::isDirectory)) {
            for (Path pack : folders) {
                var json = JsonParser.parseString(Files.readString(pack.resolve(CARVE))).getAsJsonObject();
                json.remove("type");
                var parsed = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json);
                assertTrue(parsed.isSuccess(), pack.getFileName() + ": " + parsed.error().map(Object::toString).orElse(""));
                packs++;
            }
        }
        assertTrue(packs >= 6, "found " + packs + " packs");
    }

    @Test
    void theRavineKeptForLaterStillHasNoRavineShorterThanAHundred() throws IOException {
        assumeTrue(Files.isDirectory(PACKS), "the testing datapacks are not next to this module");
        var json = JsonParser.parseString(Files.readString(PACKS.resolve("painted-ravine").resolve(CARVE))).getAsJsonObject();
        json.remove("type");
        RavineSettings ravine = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(ravine.ravine().isPresent() && ravine.cone().isEmpty());
        assertEquals(100, ravine.length().minInclusive());
    }
}

package dev.syrval.verdantdeep.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The long ravine is set aside, not gone. The settings the mod shipped before the cone are kept beside the tests, so that
 * a ravine's own part of the settings still has a file that is read.
 */
class KeptRavineTest {
    static {
        MinecraftBootstrap.init();
    }

    @Test
    void theRavineKeptForLaterStillLoadsAndHasNoRavineShorterThanAHundred() throws Exception {
        try (var in = KeptRavineTest.class.getResourceAsStream("/ravine-settings.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("type");
            RavineSettings ravine = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();

            assertTrue(ravine.ravine().isPresent() && ravine.cone().isEmpty());
            assertEquals(100, ravine.length().minInclusive());
        }
    }
}

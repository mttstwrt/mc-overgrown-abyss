package dev.syrval.verdantdeep.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.syrval.verdantdeep.ravine.Dials;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dials' file in the config folder: made when it is missing, filled in when it lacks a name, left alone when it is broken. */
class DialsFileTest {
    private static final Set<String> GROWTH = Set.of("lights", "rim");
    private static final Set<String> THEMES = Set.of("verdant_deep:disc_lava");

    private static JsonObject written(Path folder) throws IOException {
        return JsonParser.parseString(Files.readString(folder.resolve(DialsFile.NAME))).getAsJsonObject();
    }

    @Test
    void aFolderWithoutTheFileGetsOneThatListsEveryDial(@TempDir Path folder) throws IOException {
        Dials read = DialsFile.read(folder.resolve("config"), GROWTH, THEMES);
        JsonObject file = written(folder.resolve("config"));

        assertTrue(read.isNeutral());
        assertEquals(
                List.of("about", "holes", "roots", "falls", "springs", "ruins", "troves", "hoards", "nests", "rough_ground", "haze", "growth", "themes"),
                List.copyOf(file.keySet()));
        assertEquals(GROWTH, file.getAsJsonObject("growth").keySet());
        assertEquals(THEMES, file.getAsJsonObject("themes").keySet());
        assertEquals(1F, file.get("roots").getAsFloat());
        assertTrue(DialsFile.read(folder.resolve("config"), GROWTH, THEMES).isNeutral());
    }

    @Test
    void aFileThatLacksNamesIsFilledInAndKeepsWhatItHad(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(DialsFile.NAME), "{\"roots\": 0, \"growth\": {\"lights\": 2, \"of_another_pack\": 3}}");

        Dials read = DialsFile.read(folder, GROWTH, THEMES);
        JsonObject file = written(folder);

        assertEquals(0F, read.roots());
        assertEquals(2F, read.growth().get("lights"));
        assertEquals(0F, file.get("roots").getAsFloat());
        assertEquals(1F, file.get("falls").getAsFloat());
        assertEquals(Set.of("lights", "rim", "of_another_pack"), file.getAsJsonObject("growth").keySet());
        assertEquals(2F, file.getAsJsonObject("growth").get("lights").getAsFloat());
        assertEquals(3F, file.getAsJsonObject("growth").get("of_another_pack").getAsFloat());
        assertEquals(1F, file.getAsJsonObject("growth").get("rim").getAsFloat());
        assertEquals(THEMES, file.getAsJsonObject("themes").keySet());
    }


    @Test
    void aFileThatCannotBeReadIsLeftAloneAndEveryDialIsAtOne(@TempDir Path folder) throws IOException {
        for (String broken : List.of("{ \"roots\": ", "", "[]", "{\"roots\": 11}", "{\"holes\": \"few\"}", "{\"growth\": {\"lights\": -1}}")) {
            Files.writeString(folder.resolve(DialsFile.NAME), broken);

            assertTrue(DialsFile.read(folder, GROWTH, THEMES).isNeutral(), broken);
            assertEquals(broken, Files.readString(folder.resolve(DialsFile.NAME)));
        }
    }
}

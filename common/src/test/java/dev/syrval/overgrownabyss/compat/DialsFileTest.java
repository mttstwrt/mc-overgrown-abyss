package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.syrval.overgrownabyss.ravine.Dials;
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
    private static final Set<String> THEMES = Set.of("overgrown_abyss:disc_lava");

    private static JsonObject written(Path folder) throws IOException {
        return JsonParser.parseString(Files.readString(folder.resolve(DialsFile.NAME))).getAsJsonObject();
    }

    @Test
    void aFolderWithoutTheFileGetsOneThatListsEveryDial(@TempDir Path folder) throws IOException {
        Dials read = DialsFile.read(folder.resolve("config"), GROWTH, THEMES);
        JsonObject file = written(folder.resolve("config"));

        assertTrue(read.isNeutral());
        assertEquals(
                List.of("about", "holes", "roots", "falls", "springs", "ruins", "troves", "rough_ground", "haze", "growth", "themes"),
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
    void aDialTheFileLacksIsWrittenInThoughEveryGroupAndThemeIsThere(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve(DialsFile.NAME), "{\"holes\":1,\"roots\":3,\"falls\":1,\"springs\":1,\"ruins\":1,\"troves\":1,"
                + "\"rough_ground\":1,\"growth\":{\"lights\":1,\"rim\":1},\"themes\":{\"overgrown_abyss:disc_lava\":1}}");

        Dials read = DialsFile.read(folder, GROWTH, THEMES);
        JsonObject file = written(folder);

        assertEquals(1F, read.haze());
        assertEquals(1F, file.get("haze").getAsFloat());
        assertEquals(3F, file.get("roots").getAsFloat());
    }

    @Test
    void aFileThatListsEveryNameIsNotWrittenAgain(@TempDir Path folder) throws IOException {
        String asTheOwnerWroteIt = "{\"holes\":1,\"roots\":3,\"falls\":1,\"springs\":1,\"ruins\":1,\"troves\":1,\"rough_ground\":1,\"haze\":1,"
                + "\"growth\":{\"lights\":1,\"rim\":0},\"themes\":{\"overgrown_abyss:disc_lava\":1}}";
        Files.writeString(folder.resolve(DialsFile.NAME), asTheOwnerWroteIt);

        Dials read = DialsFile.read(folder, GROWTH, THEMES);

        assertEquals(3F, read.roots());
        assertEquals(0F, read.growth().get("rim"));
        assertEquals(asTheOwnerWroteIt, Files.readString(folder.resolve(DialsFile.NAME)));
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

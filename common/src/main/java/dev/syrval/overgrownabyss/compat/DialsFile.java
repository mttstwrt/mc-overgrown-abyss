package dev.syrval.overgrownabyss.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import dev.syrval.overgrownabyss.OvergrownAbyss;
import dev.syrval.overgrownabyss.ravine.Dials;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The file the owner's dials are kept in: {@code overgrown_abyss.json} in the game's config folder. It is read each time a
 * level loads, so a change is tried by leaving the world and coming back, and only chunks made after that show it.
 *
 * <p>The file lists every dial there is: one it lacks is written in at 1, with the values already there kept. A file that
 * cannot be read is left as it is, for the owner to mend, and the level loads with every dial at 1.
 */
final class DialsFile {
    static final String NAME = OvergrownAbyss.MOD_ID + ".json";
    private static final String ABOUT = "0 is none, 1 is as the mod ships, 2 is twice as much (holes: 0 to 1). "
            + "Read when a world loads; only chunks made after that change.";
    // The codec writes its fields in an order of its own. The file has the dials in the order they are described in.
    private static final List<String> ORDER = List.of("holes", "roots", "falls", "springs", "ruins", "troves", "rough_ground", "haze", "growth", "themes");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DialsFile() {}

    /**
     * The dials in {@code folder}'s file, which is written if it is not there and filled in if it lacks a name.
     *
     * @param growthNames the groups of growth the level has, each of which has a dial
     * @param themeNames  the same of its themes
     */
    static Dials read(Path folder, Set<String> growthNames, Set<String> themeNames) {
        Path file = folder.resolve(NAME);
        boolean there = Files.exists(file);
        Dials given = Dials.NEUTRAL;
        // A dial left out reads as 1, so one the file lacks, a new one say, is told by the names the file has.
        boolean lacksADial = !there;
        if (there) {
            Optional<JsonElement> json = readJson(file);
            Optional<Dials> parsed = json.flatMap(found -> parse(file, found));
            if (parsed.isEmpty()) {
                return Dials.NEUTRAL;
            }
            given = parsed.get();
            lacksADial = !ORDER.stream().allMatch(json.get().getAsJsonObject()::has);
        }
        warnOfNamesThatTurnNothing(given, growthNames, themeNames);
        Dials full = given.withNames(growthNames, themeNames);
        if (lacksADial || !full.equals(given)) {
            write(file, full);
        }
        return given;
    }

    private static Optional<JsonElement> readJson(Path file) {
        try {
            return Optional.of(JsonParser.parseString(Files.readString(file)));
        } catch (IOException | JsonParseException e) {
            OvergrownAbyss.LOGGER.error("{} could not be read ({}); every dial is at 1 and the file is left as it is", file, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<Dials> parse(Path file, JsonElement json) {
        DataResult<Dials> parsed = Dials.CODEC.parse(JsonOps.INSTANCE, json);
        if (parsed.isError()) {
            OvergrownAbyss.LOGGER.error(
                    "{} is not what the dials can read ({}); every dial is at 1 and the file is left as it is",
                    file, parsed.error().map(DataResult.Error::message).orElse(""));
        }
        return parsed.result();
    }

    // A name is kept in the file whatever it is: it may be for a pack or a mod that is not there this time.
    private static void warnOfNamesThatTurnNothing(Dials given, Set<String> growthNames, Set<String> themeNames) {
        for (String name : given.growth().keySet()) {
            if (!growthNames.contains(name)) {
                OvergrownAbyss.LOGGER.warn("Dial growth.{} turns nothing: this level has no tag #{}:dial/{}", name, OvergrownAbyss.MOD_ID, name);
            }
        }
        for (String name : given.themes().keySet()) {
            if (!themeNames.contains(name)) {
                OvergrownAbyss.LOGGER.warn("Dial themes.{} turns nothing: no disc theme of this level has that biome", name);
            }
        }
    }

    private static void write(Path file, Dials dials) {
        var json = new JsonObject();
        json.addProperty("about", ABOUT);
        JsonObject written = Dials.CODEC.encodeStart(JsonOps.INSTANCE, dials).getOrThrow().getAsJsonObject();
        for (String name : ORDER) {
            if (written.has(name)) {
                json.add(name, written.remove(name));
            }
        }
        // A dial this list has not been told of is still written, after the rest.
        for (var entry : written.entrySet()) {
            json.add(entry.getKey(), entry.getValue());
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json) + "\n");
        } catch (IOException e) {
            OvergrownAbyss.LOGGER.warn("{} could not be written ({}); the dials are as they were read", file, e.getMessage());
        }
    }
}

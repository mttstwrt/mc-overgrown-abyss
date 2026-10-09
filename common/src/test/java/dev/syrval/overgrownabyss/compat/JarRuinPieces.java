package dev.syrval.overgrownabyss.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.syrval.overgrownabyss.ravine.DiscRuins;
import dev.syrval.overgrownabyss.ravine.DiscTheme;
import dev.syrval.overgrownabyss.ravine.RavineSettings;
import dev.syrval.overgrownabyss.ravine.RuinPieces;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * The pieces a level has for the ruins of some settings when its templates are vanilla's and no other mod lends any: each
 * kind's pool read from its file and each template from the game's jar, measured by the mod's own rule. For tests that need
 * the mod's real pieces without a level to load them in; the pools are read as files because the mod's own name processors
 * that only the running mod registers.
 */
public final class JarRuinPieces {
    private JarRuinPieces() {}

    /** A template's saved form from the test classpath, which holds vanilla's data; empty if there is no such file. */
    static Optional<CompoundTag> template(ResourceLocation id) {
        String resource = "/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt";
        try (var in = JarRuinPieces.class.getResourceAsStream(resource)) {
            return in == null ? Optional.empty() : Optional.of(NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The elements of a pool as its file lists them. */
    static List<JsonObject> elements(ResourceLocation pool) {
        String resource = "/data/" + pool.getNamespace() + "/worldgen/template_pool/" + pool.getPath() + ".json";
        try (var in = JarRuinPieces.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException(resource + " is missing");
            }
            JsonObject file = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            return file.getAsJsonArray("elements").asList().stream().map(JsonElement::getAsJsonObject).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A list of processors as its file has it, from the test classpath. */
    static JsonObject processors(ResourceLocation id) {
        String resource = "/data/" + id.getNamespace() + "/worldgen/processor_list/" + id.getPath() + ".json";
        try (var in = JarRuinPieces.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException(resource + " is missing");
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The piece an entry of a pool's file comes to, measured by the mod's own rule from the files alone; empty for an entry
     * that is not one template, or builds nothing over the ground.
     */
    static Optional<RuinPieces.Piece> piece(ResourceKey<StructureTemplatePool> pool, int index, JsonObject entry, int sink) {
        JsonObject element = entry.getAsJsonObject("element");
        if (!element.has("location")) {
            return Optional.empty();
        }
        Optional<DiscRuinPieces.Measure> measure = template(ResourceLocation.parse(element.get("location").getAsString())).flatMap(DiscRuinPieces.Measure::of);
        JsonElement processors = element.get("processors");
        boolean placesAir = !DiscRuinPieces.leavesAirOut(processors.isJsonPrimitive() ? processors(ResourceLocation.parse(processors.getAsString())) : processors);
        return measure.filter(found -> found.built() > sink)
                .map(found -> new RuinPieces.Piece(pool, index, entry.get("weight").getAsInt(), found.radius(), found.layers(placesAir) - sink, sink));
    }

    public static RuinPieces of(RavineSettings settings) {
        var byRuins = new HashMap<DiscRuins, List<RuinPieces.Kind>>();
        for (DiscTheme theme : settings.discThemes()) {
            theme.ruins().ifPresent(ruins -> byRuins.put(ruins, ruins.kinds().stream().map(JarRuinPieces::kind).toList()));
        }
        return new RuinPieces(byRuins);
    }

    private static RuinPieces.Kind kind(DiscRuins.Kind kind) {
        List<JsonObject> elements = elements(kind.pool().location());
        var pieces = new ArrayList<RuinPieces.Piece>();
        for (int i = 0; i < elements.size(); i++) {
            pieces.add(piece(kind.pool(), i, elements.get(i), kind.sink()).orElseThrow());
        }
        return new RuinPieces.Kind(kind.weight(), kind.byHeight(), false, pieces);
    }
}

package dev.syrval.verdantdeep.ravine;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.syrval.verdantdeep.compat.JarRuinPieces;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Prints what the chasm of a seed holds, without a level. Skipped unless {@code STATS_SEED} is set, so ordinary test runs do not
 * notice it. {@code STATS_X} and {@code STATS_Z} (default 0) name a block; the cell containing it is reported. The result is
 * approximate: flat ground and no ocean check, so a cone's rim and the cells a level leaves without a hole are not modelled.
 * Run: {@code STATS_SEED=123 STATS_X=0 STATS_Z=0 bin/mcg verdant-deep :common:test --tests '*ChasmStatsReport' -i}.
 */
class ChasmStatsReport {
    // The overworld's heights: above_bottom 24 and below_top 64 of carve.json in a level from -64 to 320.
    private static final RavineBounds OVERWORLD = new RavineBounds(-40, 256);

    @Test
    void report() throws Exception {
        String seedText = System.getenv("STATS_SEED");
        assumeTrue(seedText != null && !seedText.isBlank(), "STATS_SEED is not set");
        long seed = Long.parseLong(seedText.trim());
        int x = Integer.parseInt(System.getenv().getOrDefault("STATS_X", "0").trim());
        int z = Integer.parseInt(System.getenv().getOrDefault("STATS_Z", "0").trim());
        MinecraftBootstrap.init();

        JsonObject json;
        try (InputStream in = ChasmStatsReport.class.getResourceAsStream("/data/verdant_deep/worldgen/density_function/ravine/carve.json")) {
            json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        json.remove("type");
        RavineSettings settings = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        Optional<RavineCell> cell = RavineCells.containing(seed, settings, x, z);
        if (cell.isEmpty()) {
            System.out.println("[stats] seed " + seed + " at " + x + ", " + z + ": no chasm in this cell");
            return;
        }
        RavineSites sites = new RavineSites(settings, OVERWORLD);
        sites.furnishRuins(JarRuinPieces.of(settings));
        ChasmStats stats = ChasmStats.of(settings, sites.discsOf(cell.get(), OVERWORLD), ChasmStatsReport::containersOf);
        System.out.println("[stats] seed " + seed + " at " + x + ", " + z + " (approximate, flat ground), cell centre "
                + Math.round(cell.get().centreX()) + ", " + Math.round(cell.get().centreZ()));
        stats.format().forEach(line -> System.out.println("[stats] " + line));
    }

    private static final Map<String, ChasmStats.Containers> BY_ELEMENT = new HashMap<>();

    // The offline pieces number a pool's elements as its file lists them.
    private static ChasmStats.Containers containersOf(RuinSite site) {
        return BY_ELEMENT.computeIfAbsent(site.pool().location() + "#" + site.element(), key -> {
            ResourceLocation pool = site.pool().location();
            JsonObject file = JsonParser.parseString(read("/data/" + pool.getNamespace() + "/worldgen/template_pool/" + pool.getPath() + ".json")).getAsJsonObject();
            JsonObject element = file.getAsJsonArray("elements").get(site.element()).getAsJsonObject().getAsJsonObject("element");
            ResourceLocation id = ResourceLocation.parse(element.get("location").getAsString());
            try (InputStream in = ChasmStatsReport.class.getResourceAsStream("/data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt")) {
                CompoundTag template = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                return ChasmStats.Containers.ofTemplate(template);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private static String read(String resource) {
        try (InputStream in = ChasmStatsReport.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}

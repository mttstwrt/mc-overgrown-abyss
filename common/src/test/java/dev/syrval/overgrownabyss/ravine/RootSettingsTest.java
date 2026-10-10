package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.Test;

/** The JSON a hole's roots are written in, and what a root's wood gives for a block. */
class RootSettingsTest {
    static {
        MinecraftBootstrap.init();
    }

    static final String JSON = """
            {
              "great": {"count": {"min_inclusive": 3, "max_inclusive": 5}, "radius": 4.5, "end_radius": 3.5, "fall": 0.5, "touch_chance": 0.5},
              "crossing": {"count": {"min_inclusive": 1, "max_inclusive": 2}, "radius": 3.5},
              "branches": {"every": 150, "by_height": {"bottom": 3, "top": 0.5}, "shrink": 0.65, "depth": 2, "reach": 60},
              "links": {"chance": 0.5, "radius": 2, "max_slope": 0.6},
              "floor_run": 60,
              "winding": 0.6,
              "min_radius": 1.5,
              "wood": {
                "bark": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:jungle_wood"}},
                "core": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:mangrove_wood"}}
              },
              "wood_reach": 48,
              "wood_blend": 16
            }
            """;

    private static JsonObject json() {
        return JsonParser.parseString(JSON).getAsJsonObject();
    }

    @Test
    void rootsAreReadFromJsonAndWrittenBackTheSame() {
        RootSettings roots = RootSettings.CODEC.parse(JsonOps.INSTANCE, json()).getOrThrow();
        assertEquals(3, roots.great().count().minInclusive());
        assertEquals(5, roots.great().count().maxInclusive());
        assertEquals(4.5F, roots.great().radius());
        assertEquals(2, roots.crossing().count().maxInclusive());
        assertEquals(3, roots.branches().byHeight().at(0), 1e-6);
        assertEquals(0.5, roots.branches().byHeight().at(1), 1e-6);
        assertEquals(0.6F, roots.links().maxSlope());
        assertEquals(48F, roots.woodReach());
        var written = RootSettings.CODEC.encodeStart(JsonOps.INSTANCE, roots).getOrThrow();
        assertEquals(json().keySet(), written.getAsJsonObject().keySet());
        RootSettings again = RootSettings.CODEC.parse(JsonOps.INSTANCE, written).getOrThrow();
        assertEquals(roots.great(), again.great());
        assertEquals(roots.crossing(), again.crossing());
        assertEquals(roots.branches(), again.branches());
        assertEquals(roots.links(), again.links());
        assertEquals(RootSettings.CODEC.encodeStart(JsonOps.INSTANCE, again).getOrThrow(), written);
    }

    @Test
    void branchesComeEvenlyWhereNoRampIsGiven() {
        JsonObject json = json();
        json.getAsJsonObject("branches").remove("by_height");
        RootSettings roots = RootSettings.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1, roots.branches().byHeight().at(0), 1e-6);
        assertEquals(1, roots.branches().byHeight().at(1), 1e-6);
    }

    @Test
    void aRootTooThinToHoldTogetherIsRejected() {
        JsonObject json = json();
        json.getAsJsonObject("links").addProperty("radius", 0.5);
        assertTrue(RootSettings.CODEC.parse(JsonOps.INSTANCE, json).isError());
        JsonObject steep = json();
        steep.getAsJsonObject("links").addProperty("max_slope", 1.5);
        assertTrue(RootSettings.CODEC.parse(JsonOps.INSTANCE, steep).isError(), "nor a link too steep to walk");
        JsonObject backwards = json();
        backwards.getAsJsonObject("great").getAsJsonObject("count").addProperty("min_inclusive", 9);
        assertTrue(RootSettings.CODEC.parse(JsonOps.INSTANCE, backwards).isError(), "nor more at the least than at the most");
    }

    @Test
    void rootsAreOnlyForACone() {
        RavineSettings ravine = RavineCellsTest.SETTINGS;
        var encoded = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, ravine).getOrThrow().getAsJsonObject();
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, encoded).isSuccess());
        encoded.add("roots", json());
        var parsed = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, encoded);
        assertTrue(parsed.isError() && parsed.error().orElseThrow().message().contains("only for a cone"));
        var cone = RavineSettings.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, ConeShapeTest.SETTINGS).getOrThrow().getAsJsonObject();
        cone.add("roots", json());
        assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, cone).getOrThrow().roots().isPresent());
    }

    @Test
    void aThemeMayNameTheWoodOfTheRootsNearItsDiscs() {
        var theme = JsonParser.parseString("""
                {"weight": 1, "root_wood": {"bark": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:mangrove_wood"}}}}
                """);
        DiscTheme parsed = DiscTheme.CODEC.parse(JsonOps.INSTANCE, theme).getOrThrow();
        assertTrue(parsed.rootWood().isPresent() && parsed.rootWood().get().core().isEmpty());
        var plain = JsonParser.parseString("""
                {"weight": 1}
                """);
        assertTrue(DiscTheme.CODEC.parse(JsonOps.INSTANCE, plain).getOrThrow().rootWood().isEmpty());
    }

    @Test
    void aRootsOutermostBlockIsBarkAndTheRestItsCore() {
        BlockStateProvider bark = BlockStateProvider.simple(Blocks.JUNGLE_WOOD);
        BlockStateProvider core = BlockStateProvider.simple(Blocks.PACKED_MUD);
        var wood = new RootWood(bark, Optional.of(core));
        assertSame(bark, wood.blockAt(0.2));
        assertSame(bark, wood.blockAt(1));
        assertSame(core, wood.blockAt(1.01));
        assertSame(bark, new RootWood(bark, Optional.empty()).blockAt(3), "bark all through where no core is given");
    }
}

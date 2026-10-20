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

package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.Test;

/** What a disc is made of: which material a point takes, and the JSON a palette is written in. */
class DiscPaletteTest {
    static {
        MinecraftBootstrap.init();
    }

    static final BlockStateProvider MOSS = BlockStateProvider.simple(Blocks.MOSS_BLOCK);
    static final BlockStateProvider DIRT = BlockStateProvider.simple(Blocks.ROOTED_DIRT);
    static final BlockStateProvider CALCITE = BlockStateProvider.simple(Blocks.CALCITE);
    static final BlockStateProvider STONE = BlockStateProvider.simple(Blocks.STONE);
    static final BlockStateProvider BARK = BlockStateProvider.simple(Blocks.MUD_BRICKS);
    static final BlockStateProvider CORE = BlockStateProvider.simple(Blocks.PACKED_MUD);
    static final DiscPalette PALETTE = new DiscPalette(
            List.of(new DiscPalette.Layer(1, MOSS), new DiscPalette.Layer(2, DIRT)),
            List.of(new DiscPalette.Layer(1, CALCITE)),
            Optional.of(STONE),
            new DiscPalette.Stem(List.of(new DiscPalette.Layer(1, BARK)), Optional.of(CORE)));

    private static BlockStateProvider at(DiscPalette palette, DiscPoint point) {
        return palette.blockAt(point).orElse(null);
    }

    @Test
    void layersCountInFromEachSurfaceAndTheBodyIsTheRest() {
        assertSame(MOSS, at(PALETTE, new DiscPoint.Platform(1, 9)), "the outermost block");
        assertSame(DIRT, at(PALETTE, new DiscPoint.Platform(1.01, 9)));
        assertSame(DIRT, at(PALETTE, new DiscPoint.Platform(3, 7)), "the second layer is two thick");
        assertSame(STONE, at(PALETTE, new DiscPoint.Platform(3.5, 6.5)));
        assertSame(CALCITE, at(PALETTE, new DiscPoint.Platform(9, 1)), "the bottom face");
        assertSame(STONE, at(PALETTE, new DiscPoint.Platform(8, 2)));
        assertSame(MOSS, at(PALETTE, new DiscPoint.Platform(1, 1)), "the top wins where a thin platform has room for one only");
        assertSame(BARK, at(PALETTE, new DiscPoint.Stem(0.4)));
        assertSame(CORE, at(PALETTE, new DiscPoint.Stem(1.5)));
    }

    @Test
    void aPartThePaletteLeavesOutKeepsTheTerrainsRock() {
        var capOnly = new DiscPalette(List.of(new DiscPalette.Layer(1, MOSS)), List.of(), Optional.empty(), DiscPalette.Stem.UNPAINTED);
        assertSame(MOSS, at(capOnly, new DiscPoint.Platform(0.5, 3.5)));
        assertTrue(capOnly.blockAt(new DiscPoint.Platform(2, 2)).isEmpty(), "no body");
        assertTrue(capOnly.blockAt(new DiscPoint.Stem(1)).isEmpty(), "no stem");
        var barkOnly = new DiscPalette(List.of(), List.of(), Optional.empty(), new DiscPalette.Stem(List.of(new DiscPalette.Layer(1, BARK)), Optional.empty()));
        assertSame(BARK, at(barkOnly, new DiscPoint.Stem(1)));
        assertTrue(barkOnly.blockAt(new DiscPoint.Stem(1.2)).isEmpty(), "no core");
    }

    @Test
    void aPaletteIsWrittenWithVanillaBlockStateProviders() {
        var json = JsonParser.parseString("""
                {
                  "top": [
                    {"thickness": 1, "block": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:moss_block"}}}
                  ],
                  "body": {
                    "type": "minecraft:weighted_state_provider",
                    "entries": [
                      {"data": {"Name": "minecraft:stone"}, "weight": 3},
                      {"data": {"Name": "minecraft:andesite"}, "weight": 1}
                    ]
                  },
                  "stem": {"core": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:calcite"}}}
                }
                """);
        DiscPalette parsed = DiscPalette.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1, parsed.top().size());
        assertTrue(parsed.underside().isEmpty(), "a part left out is empty");
        assertTrue(parsed.body().isPresent());
        assertTrue(parsed.stem().surface().isEmpty());
        assertTrue(parsed.stem().core().isPresent());
        assertEquals(json, DiscPalette.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow(), "and is written back the same");
        var bad = json.getAsJsonObject().deepCopy();
        bad.getAsJsonArray("top").get(0).getAsJsonObject().addProperty("thickness", 0);
        assertTrue(DiscPalette.CODEC.parse(JsonOps.INSTANCE, bad).isError(), "a layer of no thickness");
    }
}

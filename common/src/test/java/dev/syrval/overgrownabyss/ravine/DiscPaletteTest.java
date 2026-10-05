package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.Test;

/** What a disc is made of: which material a point takes, which palette a disc gets, and the JSON a palette is written in. */
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
            1,
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
        var capOnly = new DiscPalette(1, List.of(new DiscPalette.Layer(1, MOSS)), List.of(), Optional.empty(), DiscPalette.Stem.UNPAINTED);
        assertSame(MOSS, at(capOnly, new DiscPoint.Platform(0.5, 3.5)));
        assertTrue(capOnly.blockAt(new DiscPoint.Platform(2, 2)).isEmpty(), "no body");
        assertTrue(capOnly.blockAt(new DiscPoint.Stem(1)).isEmpty(), "no stem");
        var barkOnly = new DiscPalette(1, List.of(), List.of(), Optional.empty(), new DiscPalette.Stem(List.of(new DiscPalette.Layer(1, BARK)), Optional.empty()));
        assertSame(BARK, at(barkOnly, new DiscPoint.Stem(1)));
        assertTrue(barkOnly.blockAt(new DiscPoint.Stem(1.2)).isEmpty(), "no core");
    }

    @Test
    void aDiscGetsAPaletteByWeight() {
        var rare = new DiscPalette(1, List.of(), List.of(), Optional.of(MOSS), DiscPalette.Stem.UNPAINTED);
        var common = new DiscPalette(3, List.of(), List.of(), Optional.of(STONE), DiscPalette.Stem.UNPAINTED);
        List<DiscPalette> palettes = List.of(rare, common);
        assertEquals(0, DiscPalette.pick(palettes, 0));
        assertEquals(0, DiscPalette.pick(palettes, 0.2499));
        assertEquals(1, DiscPalette.pick(palettes, 0.25));
        assertEquals(1, DiscPalette.pick(palettes, 0.999999));
        assertEquals(0, DiscPalette.pick(List.of(rare), 0.999999));
    }

    @Test
    void aPaletteIsWrittenWithVanillaBlockStateProviders() {
        var json = JsonParser.parseString("""
                {
                  "weight": 3,
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
        assertEquals(3, parsed.weight());
        assertEquals(1, parsed.top().size());
        assertTrue(parsed.underside().isEmpty(), "a part left out is empty");
        assertTrue(parsed.body().isPresent());
        assertTrue(parsed.stem().surface().isEmpty());
        assertTrue(parsed.stem().core().isPresent());
        assertEquals(json, DiscPalette.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow(), "and is written back the same");
        var bad = json.getAsJsonObject().deepCopy();
        bad.addProperty("weight", 0);
        assertTrue(DiscPalette.CODEC.parse(JsonOps.INSTANCE, bad).isError(), "a palette no disc could get");
    }

    @Test
    void theModsOwnCarveFileGivesEveryPartOfADiscAMaterialAndPalettesAreOptional() throws Exception {
        try (var in = DiscPaletteTest.class.getResourceAsStream("/data/overgrown_abyss/worldgen/density_function/ravine/carve.json")) {
            var json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            json.remove("type");
            List<DiscPalette> shipped = RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow().discPalettes();
            assertTrue(!shipped.isEmpty(), "the mod's own discs have a material");
            for (DiscPalette palette : shipped) {
                assertTrue(palette.blockAt(new DiscPoint.Platform(0.5, 3.5)).isPresent(), "top");
                assertTrue(palette.blockAt(new DiscPoint.Platform(3.5, 0.5)).isPresent(), "underside");
                assertTrue(palette.blockAt(new DiscPoint.Platform(20, 20)).isPresent(), "body");
                assertTrue(palette.blockAt(new DiscPoint.Stem(0.5)).isPresent(), "stem surface");
                assertTrue(palette.blockAt(new DiscPoint.Stem(20)).isPresent(), "stem core");
            }
            json.remove("disc_palettes");
            assertTrue(RavineSettings.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow().discPalettes().isEmpty(), "none unless given");
        }
    }
}

package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * What a disc is made of. The terrain makes every disc out of its own rock; a palette replaces that, part by part, with
 * blocks of its own, and any part it leaves out keeps the terrain's rock. Blocks are vanilla block-state providers, so a part
 * can be one block, a weighted mix, or patches following a noise.
 *
 * @param weight    how often a disc gets this palette, against the weights of the others
 * @param top       layers counted down from the platform's flat top, the first one outermost
 * @param underside layers counted up from the platform's bottom face; where the platform is too thin for both, the top's win
 * @param body      the rest of the platform
 * @param stem      the stem, or the root of a hanging disc
 */
public record DiscPalette(int weight, List<Layer> top, List<Layer> underside, Optional<BlockStateProvider> body, Stem stem) {

    /** {@code thickness} blocks of one material. */
    public record Layer(int thickness, BlockStateProvider block) {
        static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 64).fieldOf("thickness").forGetter(Layer::thickness),
                BlockStateProvider.CODEC.fieldOf("block").forGetter(Layer::block)
        ).apply(i, Layer::new));
    }

    /**
     * @param surface layers counted in from the stem's side, the first one outermost
     * @param core    the rest of the stem
     */
    public record Stem(List<Layer> surface, Optional<BlockStateProvider> core) {
        static final Stem UNPAINTED = new Stem(List.of(), Optional.empty());
        static final Codec<Stem> CODEC = RecordCodecBuilder.create(i -> i.group(
                Layer.CODEC.listOf().optionalFieldOf("surface", List.of()).forGetter(Stem::surface),
                BlockStateProvider.CODEC.optionalFieldOf("core").forGetter(Stem::core)
        ).apply(i, Stem::new));

        public Stem {
            surface = List.copyOf(surface);
        }
    }

    public static final Codec<DiscPalette> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1000).fieldOf("weight").forGetter(DiscPalette::weight),
            Layer.CODEC.listOf().optionalFieldOf("top", List.of()).forGetter(DiscPalette::top),
            Layer.CODEC.listOf().optionalFieldOf("underside", List.of()).forGetter(DiscPalette::underside),
            BlockStateProvider.CODEC.optionalFieldOf("body").forGetter(DiscPalette::body),
            Stem.CODEC.optionalFieldOf("stem", Stem.UNPAINTED).forGetter(DiscPalette::stem)
    ).apply(i, DiscPalette::new));

    public DiscPalette {
        top = List.copyOf(top);
        underside = List.copyOf(underside);
    }

    /** The material at a point of a disc, or empty where this palette leaves the terrain's own rock. */
    Optional<BlockStateProvider> blockAt(DiscPoint point) {
        return switch (point) {
            case DiscPoint.Platform platform -> layerAt(top, platform.belowTop())
                    .or(() -> layerAt(underside, platform.aboveUnderside()))
                    .or(() -> body);
            case DiscPoint.Stem inStem -> layerAt(stem.surface(), inStem.inside()).or(stem::core);
        };
    }

    // A block counts as being in a layer if its centre-line depth is within it, so a thickness of 1 is exactly the outermost block.
    private static Optional<BlockStateProvider> layerAt(List<Layer> layers, double depth) {
        double reach = 0;
        for (Layer layer : layers) {
            reach += layer.thickness();
            if (depth <= reach) {
                return Optional.of(layer.block());
            }
        }
        return Optional.empty();
    }

    /** Which of the palettes a uniform draw in [0, 1) picks, by weight. */
    static int pick(List<DiscPalette> palettes, double unit) {
        int total = 0;
        for (DiscPalette palette : palettes) {
            total += palette.weight();
        }
        double left = unit * total;
        for (int i = 0; i < palettes.size(); i++) {
            left -= palettes.get(i).weight();
            if (left < 0) {
                return i;
            }
        }
        return palettes.size() - 1;
    }
}

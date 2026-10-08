package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Unevenness of a hole's own wall, so that it is not an exact surface of revolution: each layer moves the wall towards or
 * away from the axis, by a different amount at each angle round the axis and each height. Wide layers make the hole something
 * other than round, narrow ones stretched upwards make runnels down the wall. The wall is only ever moved along the line out
 * from the axis, so along any such line air still turns to rock exactly once and nothing is left floating. Each hole's wall
 * is drawn from its own hash, so no two are alike.
 *
 * <p>The discs, their domes, stems and roots are not touched: what they are made of, what grows on them and their biomes are
 * all read from their exact shapes.
 */
public record WallNoise(List<Layer> layers) {
    public static final WallNoise NONE = new WallNoise(List.of());
    public static final Codec<WallNoise> CODEC = Layer.CODEC.listOf().xmap(WallNoise::new, WallNoise::layers);
    // The cell hash's indices for the layers: two for each, its values and how far each column of them is slid up.
    private static final int HASH_BASE = 800_000;

    public WallNoise {
        layers = List.copyOf(layers);
    }

    /**
     * One layer of unevenness.
     *
     * @param wavelength      blocks round the hole from one bulge to the next, counted where the hole is of middling width:
     *                        there are as many bulges round the hole at every height, so they are narrower than this at the
     *                        mouth and wider at the floor, and each runs up the wall without sliding sideways
     * @param verticalStretch how many times further apart the bulges are up the wall than round it: 1 is evenly rough, above
     *                        1 they are drawn out into runnels
     * @param amplitude       blocks the wall is moved by, at most, towards or away from the axis
     */
    public record Layer(float wavelength, float verticalStretch, float amplitude) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                // The carve is worked out for each block, so nothing narrower than a few blocks can show.
                Codec.floatRange(4, 1024).fieldOf("wavelength").forGetter(Layer::wavelength),
                Codec.floatRange(0.25F, 16).fieldOf("vertical_stretch").forGetter(Layer::verticalStretch),
                Codec.floatRange(0, 64).fieldOf("amplitude").forGetter(Layer::amplitude)
        ).apply(i, Layer::new));
    }

    /** The furthest the wall is moved, in blocks towards or away from the axis. */
    public double maxDisplacement() {
        return layers.stream().mapToDouble(Layer::amplitude).sum();
    }

    /**
     * How far outwards the wall of the hole with this hash is moved, at {@code turn} of the way round its axis (0 to 1) and at
     * height {@code y}. {@code around} is the distance round the hole that wavelengths are counted along.
     */
    double offset(long hash, double around, double turn, double y) {
        double offset = 0;
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            int bulges = Math.max(3, (int) Math.round(around / layer.wavelength()));
            double value = RavineCells.smoothRound(hash, HASH_BASE + 2 * i, turn * bulges, bulges, y / (layer.wavelength() * layer.verticalStretch()));
            offset += (value * 2 - 1) * layer.amplitude();
        }
        return offset;
    }
}

package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The gas a hole gives off besides what its discs' themes do (see {@link DiscTheme.Gas}): a fog through the whole of its air,
 * where each fall lands and along its drop, where each spring's water comes down, and over the cavern's floor. Gas is named by
 * its id among the gas library's media; without that library none of it is sent.
 *
 * @param fog     the haze that fills the hole's air
 * @param falls   gas at each fall
 * @param springs gas where each spring's water comes down on its disc, resting on the pool there
 * @param cavern  gas spread over the cavern's floor, each list entry with draws of its own
 */
public record GasSettings(Optional<Fog> fog, Optional<Falls> falls, Optional<Source> springs, List<Spread> cavern) {
    /** The most density a source may give each cell a second, as the gas library allows. */
    static final float MOST_RATE = 64;
    /** The most density a fog may have, which is thicker than anything worth seeing through. */
    static final float MOST_DENSITY = 4;
    public static final Codec<GasSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Fog.CODEC.optionalFieldOf("fog").forGetter(GasSettings::fog),
            Falls.CODEC.optionalFieldOf("falls").forGetter(GasSettings::falls),
            Source.CODEC.optionalFieldOf("springs").forGetter(GasSettings::springs),
            Spread.CODEC.listOf().optionalFieldOf("cavern", List.of()).forGetter(GasSettings::cavern)
    ).apply(i, GasSettings::new));

    public GasSettings {
        cavern = List.copyOf(cavern);
    }

    /**
     * A haze through the whole of a hole's air: one of the gas library's fogs, which are not simulated, cost next to nothing
     * and are drawn at any distance, so the discs above loom out of it and the floor is lost in it from the rim. It is
     * {@code bottom_density} thick at the hole's floor and thins by the same share each block up to {@code top_density} at its
     * top. Of a cone, it fills one from round the cavern and the cone's base at the floor to round its mouth at the top, which
     * holds the whole of the hole; what of it lies in the rock round the hole is never seen, since nothing is seen through
     * rock. Of a ravine, it fills the cavern alone. Over its outermost {@code edge} blocks it thins to nothing; it reaches that
     * far past the hole's walls and below its floor, so that it is at its full thickness where they are. A density is as the
     * library has it: light through {@code n} blocks of it is dimmed to {@code exp(-opacity * density * n)}, the opacity being
     * the medium's.
     *
     * @param medium how it looks: the appearance of one of the gas library's media, whose simulation plays no part
     */
    public record Fog(ResourceLocation medium, float bottomDensity, float topDensity, float edge) {
        static final Codec<Fog> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("medium").forGetter(Fog::medium),
                Codec.floatRange(0, MOST_DENSITY).fieldOf("bottom_density").forGetter(Fog::bottomDensity),
                Codec.floatRange(0, MOST_DENSITY).fieldOf("top_density").forGetter(Fog::topDensity),
                Codec.floatRange(0, 64).optionalFieldOf("edge", 16F).forGetter(Fog::edge)
        ).apply(i, Fog::new));
    }

    /** One source of a gas of a given shape and rate; where it stands is up to what uses it. */
    public record Source(ResourceLocation medium, GasShape shape, float rate) {
        static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("medium").forGetter(Source::medium),
                GasShape.CODEC.fieldOf("shape").forGetter(Source::shape),
                Codec.floatRange(0, MOST_RATE).fieldOf("rate").forGetter(Source::rate)
        ).apply(i, Source::new));
    }

    /**
     * Gas at a fall: {@code spray} rests on the water it lands in, and {@code curtain} fills a cylinder up the whole drop, as
     * wide as the fall and {@code margin} blocks more on every side, since the falling water itself holds none.
     */
    public record Falls(Optional<Source> spray, Optional<Curtain> curtain) {
        static final Codec<Falls> CODEC = RecordCodecBuilder.create(i -> i.group(
                Source.CODEC.optionalFieldOf("spray").forGetter(Falls::spray),
                Curtain.CODEC.optionalFieldOf("curtain").forGetter(Falls::curtain)
        ).apply(i, Falls::new));
    }

    /** The gas round a fall's drop. */
    public record Curtain(ResourceLocation medium, float margin, float rate) {
        static final Codec<Curtain> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("medium").forGetter(Curtain::medium),
                Codec.floatRange(0.5F, 16).optionalFieldOf("margin", 1.5F).forGetter(Curtain::margin),
                Codec.floatRange(0, MOST_RATE).fieldOf("rate").forGetter(Curtain::rate)
        ).apply(i, Curtain::new));
    }

    /** Sources resting on a floor, on average one for every {@code every} blocks of it. */
    public record Spread(ResourceLocation medium, int every, GasShape shape, float rate) {
        static final Codec<Spread> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("medium").forGetter(Spread::medium),
                Codec.intRange(1, DiscTheme.Growth.MOST_EVERY).fieldOf("every").forGetter(Spread::every),
                GasShape.CODEC.fieldOf("shape").forGetter(Spread::shape),
                Codec.floatRange(0, MOST_RATE).fieldOf("rate").forGetter(Spread::rate)
        ).apply(i, Spread::new));
    }
}

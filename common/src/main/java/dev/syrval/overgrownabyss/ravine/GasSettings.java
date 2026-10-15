package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The gas a hole gives off besides what its discs' themes do (see {@link DiscTheme.Gas}): a haze through the whole of its air,
 * where each fall lands and along its drop, where each spring's water comes down, and over the cavern's floor. Gas is named by
 * its id among the gas library's media; without that library none of it is sent.
 *
 * @param haze    thin gas throughout the hole's air
 * @param falls   gas at each fall
 * @param springs gas where each spring's water comes down on its disc, resting on the pool there
 * @param cavern  gas spread over the cavern's floor, each list entry with draws of its own
 */
public record GasSettings(Optional<Haze> haze, Optional<Falls> falls, Optional<Source> springs, List<Spread> cavern) {
    /** The most density a source may give each cell a second, as the gas library allows. */
    static final float MOST_RATE = 64;
    public static final Codec<GasSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Haze.CODEC.optionalFieldOf("haze").forGetter(GasSettings::haze),
            Falls.CODEC.optionalFieldOf("falls").forGetter(GasSettings::falls),
            Source.CODEC.optionalFieldOf("springs").forGetter(GasSettings::springs),
            Spread.CODEC.listOf().optionalFieldOf("cavern", List.of()).forGetter(GasSettings::cavern)
    ).apply(i, GasSettings::new));

    public GasSettings {
        cavern = List.copyOf(cavern);
    }

    /**
     * Sources hung all through a hole's air, from its floor to its top, one in each box of a lattice {@code spacing} blocks
     * across and {@code layer_spacing} blocks high, put anywhere in its box. A source is kept only where it lies in the hole's
     * open air, its discs' domes included, so the haze fills the hole and not the ground round it. Gas given off sparsely like
     * this and left to spread is the only way to a thin haze: the gas library drops any cell that gains less than a trace in
     * one of its steps, so a source as large as the hole with a rate low enough for a haze would give off nothing at all.
     */
    public record Haze(ResourceLocation medium, int spacing, int layerSpacing, GasShape shape, float rate) {
        static final Codec<Haze> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("medium").forGetter(Haze::medium),
                Codec.intRange(4, 256).fieldOf("spacing").forGetter(Haze::spacing),
                Codec.intRange(4, 256).fieldOf("layer_spacing").forGetter(Haze::layerSpacing),
                GasShape.CODEC.fieldOf("shape").forGetter(Haze::shape),
                Codec.floatRange(0, MOST_RATE).fieldOf("rate").forGetter(Haze::rate)
        ).apply(i, Haze::new));
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

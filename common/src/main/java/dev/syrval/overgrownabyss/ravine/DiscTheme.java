package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * One kind of disc: a small biome of its own. Each disc is given one theme, drawn by weight, and the weight of a theme
 * depends on where the disc is and how large, so that some themes gather near the middle and others are rare finds on the
 * small, low, outlying discs.
 *
 * @param biome      the biome stamped over the disc's dome and platform. It sets the colour of grass, leaves and water there
 *                   and what spawns, and it keeps the surrounding biome's features off the disc. Without one the disc keeps
 *                   the biome it lies in
 * @param inherits   another biome whose features and spawns the disc's biome takes on, as they are when the level loads,
 *                   with whatever other mods have added to that biome
 * @param weight     how often a disc gets this theme, against the weights of the others, before the three ramps below
 * @param byHeight   how the weight changes from the hole's lowest disc to its highest
 * @param byDistance from the hole's centre to its outermost disc
 * @param bySize     from the smallest radius a disc may have to the largest
 * @param only       the discs the theme may be given to at all, by the same three traits; outside these it has no weight
 * @param palette    what the disc is made of; a part it leaves out keeps the terrain's own rock
 * @param growth     what grows on the disc
 */
public record DiscTheme(
        Optional<ResourceKey<Biome>> biome, Optional<Inherits> inherits, float weight, Ramp byHeight, Ramp byDistance, Ramp bySize,
        Limits only, DiscPalette palette, List<Growth> growth) {

    /** A multiplier that changes steadily across one trait of a disc: {@code from} where the trait is 0, {@code to} where it is 1. */
    public record Ramp(float from, float to) {
        static final Ramp EVEN = new Ramp(1, 1);

        // Each trait names its two ends in its own words, such as "bottom" and "top".
        static Codec<Ramp> codec(String fromName, String toName) {
            return RecordCodecBuilder.create(i -> i.group(
                    Codec.floatRange(0, 1000).fieldOf(fromName).forGetter(Ramp::from),
                    Codec.floatRange(0, 1000).fieldOf(toName).forGetter(Ramp::to)
            ).apply(i, Ramp::new));
        }

        double at(double trait) {
            return from + (to - from) * Math.clamp(trait, 0, 1);
        }
    }

    /** The part of one trait, which runs from 0 to 1, that a theme is kept to. */
    public record Span(float min, float max) {
        static final Span ALL = new Span(0, 1);
        static final Codec<Span> CODEC = RecordCodecBuilder.<Span>create(i -> i.group(
                Codec.floatRange(0, 1).optionalFieldOf("min", 0F).forGetter(Span::min),
                Codec.floatRange(0, 1).optionalFieldOf("max", 1F).forGetter(Span::max)
        ).apply(i, Span::new)).validate(span -> span.min > span.max ? DataResult.error(() -> "min must not exceed max") : DataResult.success(span));

        // A trait is held to its ends first, as it is along a ramp.
        boolean holds(double trait) {
            double held = Math.clamp(trait, 0, 1);
            return held >= min && held <= max;
        }
    }

    /**
     * Where a theme may be given at all. A ramp only makes a theme more or less likely; a limit rules it out, so that a
     * theme can be kept to, say, the small discs far from the centre.
     */
    public record Limits(Span height, Span distance, Span size) {
        static final Limits NONE = new Limits(Span.ALL, Span.ALL, Span.ALL);
        static final Codec<Limits> CODEC = RecordCodecBuilder.create(i -> i.group(
                Span.CODEC.optionalFieldOf("height", Span.ALL).forGetter(Limits::height),
                Span.CODEC.optionalFieldOf("distance", Span.ALL).forGetter(Limits::distance),
                Span.CODEC.optionalFieldOf("size", Span.ALL).forGetter(Limits::size)
        ).apply(i, Limits::new));

        boolean allow(DiscTraits traits) {
            return height.holds(traits.height()) && distance.holds(traits.distance()) && size.holds(traits.size());
        }
    }

    /**
     * What a disc's biome takes from another biome. Its spawn lists are the other biome's. Its features are grown on the disc
     * by the other biome's own placement rules, which find the disc's top where they would look for the ground.
     *
     * @param biome   the biome inherited from
     * @param stages  the stages of decoration whose features are grown; by default only vegetation, which leaves out lakes,
     *                geodes, monster rooms, ores and springs
     * @param without placed features of those stages that are left out
     */
    public record Inherits(ResourceKey<Biome> biome, List<GenerationStep.Decoration> stages, List<ResourceKey<PlacedFeature>> without) {
        static final List<GenerationStep.Decoration> VEGETATION = List.of(GenerationStep.Decoration.VEGETAL_DECORATION);
        static final Codec<Inherits> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(Inherits::biome),
                GenerationStep.Decoration.CODEC.listOf().optionalFieldOf("stages", VEGETATION).forGetter(Inherits::stages),
                ResourceKey.codec(Registries.PLACED_FEATURE).listOf().optionalFieldOf("without", List.of()).forGetter(Inherits::without)
        ).apply(i, Inherits::new));

        public Inherits {
            stages = List.copyOf(stages);
            without = List.copyOf(without);
        }
    }

    /** The surface of a platform that something grows on. */
    public enum Surface implements StringRepresentable {
        TOP("top"),
        UNDERSIDE("underside");

        static final Codec<Surface> CODEC = StringRepresentable.fromEnum(Surface::values);
        private final String name;

        Surface(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * A feature grown on one surface of the platform: on average one for every {@code every} blocks of that surface. The feature
     * is placed in the open block on the surface (over the top, or under the underside), so it must be one that grows from there.
     */
    public record Growth(ResourceKey<ConfiguredFeature<?, ?>> feature, int every, Surface on) {
        static final Codec<Growth> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(Growth::feature),
                Codec.intRange(1, 100_000).fieldOf("every").forGetter(Growth::every),
                Surface.CODEC.optionalFieldOf("on", Surface.TOP).forGetter(Growth::on)
        ).apply(i, Growth::new));
    }

    public static final Codec<DiscTheme> CODEC = RecordCodecBuilder.<DiscTheme>create(i -> i.group(
            ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome").forGetter(DiscTheme::biome),
            Inherits.CODEC.optionalFieldOf("inherits").forGetter(DiscTheme::inherits),
            Codec.floatRange(0, 1000).fieldOf("weight").forGetter(DiscTheme::weight),
            Ramp.codec("bottom", "top").optionalFieldOf("by_height", Ramp.EVEN).forGetter(DiscTheme::byHeight),
            Ramp.codec("centre", "edge").optionalFieldOf("by_distance", Ramp.EVEN).forGetter(DiscTheme::byDistance),
            Ramp.codec("small", "large").optionalFieldOf("by_size", Ramp.EVEN).forGetter(DiscTheme::bySize),
            Limits.CODEC.optionalFieldOf("only", Limits.NONE).forGetter(DiscTheme::only),
            DiscPalette.CODEC.optionalFieldOf("palette", DiscPalette.UNPAINTED).forGetter(DiscTheme::palette),
            Growth.CODEC.listOf().optionalFieldOf("growth", List.of()).forGetter(DiscTheme::growth)
    ).apply(i, DiscTheme::new)).validate(DiscTheme::validate);

    // What is inherited is given to the theme's own biome, and grown where that biome is stamped.
    private static DataResult<DiscTheme> validate(DiscTheme theme) {
        if (theme.inherits.isPresent() && theme.biome.isEmpty()) {
            return DataResult.error(() -> "a theme that inherits from a biome needs a biome of its own");
        }
        if (theme.inherits.isPresent() && theme.inherits.get().biome().equals(theme.biome.get())) {
            return DataResult.error(() -> "a theme's biome cannot inherit from itself");
        }
        return DataResult.success(theme);
    }

    public DiscTheme {
        growth = List.copyOf(growth);
    }

    /** This theme's weight for a disc with these traits: none outside its limits. */
    double weightFor(DiscTraits traits) {
        if (!only.allow(traits)) {
            return 0;
        }
        return weight * byHeight.at(traits.height()) * byDistance.at(traits.distance()) * bySize.at(traits.size());
    }
}

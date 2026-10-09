package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
 * @param water      ponds and streams in the disc's top
 * @param growth     what grows on the disc, in the order listed and before what it inherits
 * @param ruins      the ruins that stand on some of the theme's discs
 */
public record DiscTheme(
        Optional<ResourceKey<Biome>> biome, Optional<Inherits> inherits, float weight, Ramp byHeight, Ramp byDistance, Ramp bySize,
        Limits only, DiscPalette palette, Optional<DiscWater> water, List<Growth> growth, Optional<DiscRuins> ruins) {

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
     * What a disc's biome takes from another biome. Its features are grown on the disc by the other biome's own placement
     * rules, which find the disc's top where they would look for the ground. Its spawns are the other biome's, changed by the
     * disc biome's own file: what that file lists is added, and replaces the other biome's entry for the same mob.
     *
     * @param biome           the biome inherited from
     * @param stages          the stages of decoration whose features are grown; by default only vegetation, which leaves out
     *                        lakes, geodes, monster rooms, ores and springs
     * @param withoutFeatures placed features of those stages that are left out
     * @param withoutSpawns   mobs that are left out of the spawns inherited, by id
     */
    public record Inherits(
            ResourceKey<Biome> biome, List<GenerationStep.Decoration> stages, List<ResourceKey<PlacedFeature>> withoutFeatures,
            List<ResourceLocation> withoutSpawns) {
        static final List<GenerationStep.Decoration> VEGETATION = List.of(GenerationStep.Decoration.VEGETAL_DECORATION);
        static final Codec<Inherits> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(Inherits::biome),
                GenerationStep.Decoration.CODEC.listOf().optionalFieldOf("stages", VEGETATION).forGetter(Inherits::stages),
                ResourceKey.codec(Registries.PLACED_FEATURE).listOf().optionalFieldOf("without_features", List.of()).forGetter(Inherits::withoutFeatures),
                // Ids rather than mobs, so that a pack naming another mod's mob still loads without that mod.
                ResourceLocation.CODEC.listOf().optionalFieldOf("without_spawns", List.of()).forGetter(Inherits::withoutSpawns)
        ).apply(i, Inherits::new));

        public Inherits {
            stages = List.copyOf(stages);
            withoutFeatures = List.copyOf(withoutFeatures);
            withoutSpawns = List.copyOf(withoutSpawns);
        }
    }

    /** Where on a platform something grows: on the dry ground of its top, in the water of its top, or under it. */
    public enum Surface implements StringRepresentable {
        TOP("top"),
        WATER("water"),
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
     * is placed in the open block on the surface (over dry ground, or under the disc's lowest rock), so it must be one that
     * grows from there. In water it is placed in the lowest block of the water, as vanilla starts a tree that stands in water
     * on its bed. Without {@code patches} it is spread evenly.
     */
    public record Growth(ResourceKey<ConfiguredFeature<?, ?>> feature, int every, Surface on, Optional<Patches> patches) {
        static final Codec<Growth> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter(Growth::feature),
                Codec.intRange(1, 100_000).fieldOf("every").forGetter(Growth::every),
                Surface.CODEC.optionalFieldOf("on", Surface.TOP).forGetter(Growth::on),
                Patches.CODEC.optionalFieldOf("patches").forGetter(Growth::patches)
        ).apply(i, Growth::new));

        /** How many times its average rate this grows in one column of the {@code index}-th disc of a hole. */
        double weightAt(long hash, int index, int x, int z) {
            return patches.map(found -> found.weightAt(hash, index, x, z)).orElse(1.0);
        }
    }

    /**
     * Where on a disc a growth gathers: in patches about {@code size} blocks across that take up {@code cover} of the surface,
     * thickest in their middles and thinning to nothing at their edges, with none of it between them. The growth keeps its
     * average over the whole disc, so the less it covers the thicker its patches: twice the average in the middle of patches
     * that cover everything, ten times in those that cover a fifth.
     *
     * <p>Growths with patches of the same size share them on a disc, so trees given different covers stand in the same groves,
     * the one with the least cover at their hearts. Each disc has patches of its own.
     */
    public record Patches(float size, float cover) {
        private static final int HASH_BASE = 500_000;
        static final Codec<Patches> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(4, 256).fieldOf("size").forGetter(Patches::size),
                Codec.floatRange(0.05F, 1).fieldOf("cover").forGetter(Patches::cover)
        ).apply(i, Patches::new));

        // A smooth value's share is spread evenly from 0 to 1, so a ramp over the top cover of it averages a half of its height.
        double weightAt(long hash, int index, int x, int z) {
            double share = RavineCells.shareBelow(RavineCells.smoothOver(hash, HASH_BASE + index, x, z, size));
            return 2 * Math.max(0, share - (1 - cover)) / (cover * cover);
        }
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
            DiscWater.CODEC.optionalFieldOf("water").forGetter(DiscTheme::water),
            Growth.CODEC.listOf().optionalFieldOf("growth", List.of()).forGetter(DiscTheme::growth),
            DiscRuins.CODEC.optionalFieldOf("ruins").forGetter(DiscTheme::ruins)
    ).apply(i, DiscTheme::new)).validate(DiscTheme::validate);

    // What is inherited is given to the theme's own biome, and grown where that biome is stamped.
    private static DataResult<DiscTheme> validate(DiscTheme theme) {
        if (theme.inherits.isPresent() && theme.biome.isEmpty()) {
            return DataResult.error(() -> "a theme that inherits from a biome needs a biome of its own");
        }
        if (theme.inherits.isPresent() && theme.inherits.get().biome().equals(theme.biome.get())) {
            return DataResult.error(() -> "a theme's biome cannot inherit from itself");
        }
        // Left as the terrain's own rock, the ground around the water could be a cave, and the water would drain into it.
        if (theme.water.isPresent() && !theme.palette.coversTop(theme.water.get().depth() + 1)) {
            return DataResult.error(() -> "a theme with water needs a palette that gives its platform's top "
                    + (theme.water.get().depth() + 1) + " blocks of material: the water's sides and its bed");
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

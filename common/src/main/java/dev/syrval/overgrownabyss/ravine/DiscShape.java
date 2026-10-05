package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a disc is, the same whatever the ground it is placed in (a ravine's walls or a cone's hollow): a flat platform of
 * rock, a clear dome of air above it, and a stem under it or, if it hangs, a root above it. Where discs go is up to the layout of each geometry
 * ({@link RavineDiscLayout}, {@link ConeDiscLayout}); the shapes below are shared, and so is the code that carves them
 * ({@link Disc}, {@link Discs}).
 *
 * @param minRadius      smallest platform radius in blocks
 * @param maxRadius      largest platform radius in blocks
 * @param heightRatio    dome height as a fraction of the radius
 * @param minHeight      lowest a dome may be in blocks; a disc that cannot reach this is not made
 * @param floorThickness blocks of solid rock in the platform, below its flat top
 * @param stemFraction   the stem's radius as a fraction of its disc's radius, within the two limits below
 * @param minStemRadius  the least radius a stem may have; 1 makes it 2 blocks across
 * @param maxStemRadius  the most; 3 makes it 6 blocks across
 * @param funnelScale    how quickly the platform's underside narrows into the stem: the radius is the disc's times
 *                       {@code funnelScale / (depth + funnelScale)} until it reaches the stem's radius, so it halves
 *                       {@code funnelScale} blocks below the platform
 * @param rootSpread     how wide a hanging disc's root is where it meets the ceiling, in stem radii (never wider than the disc)
 * @param rootScale      how quickly the root narrows away from the ceiling: it is down to half that width {@code rootScale}
 *                       blocks from it, and never thinner than the stem
 * @param minBowlDepth   how far the rim of the smallest disc stands above the middle of its top, which makes the disc a
 *                       shallow bowl; 0 is flat
 * @param maxBowlDepth   the same for the largest disc; in between it grows with the radius
 * @param bowlVariation  how much flatter than that a disc may come out, each by its own draw: 0 gives every disc the full
 *                       depth for its size, 1 anything from that down to flat
 */
public record DiscShape(
        float minRadius, float maxRadius, float heightRatio, float minHeight, float floorThickness,
        float stemFraction, float minStemRadius, float maxStemRadius, float funnelScale, float rootSpread, float rootScale,
        float minBowlDepth, float maxBowlDepth, float bowlVariation) {

    public static final MapCodec<DiscShape> MAP_CODEC = RecordCodecBuilder.<DiscShape>mapCodec(i -> i.group(
            Codec.floatRange(4, 256).fieldOf("min_radius").forGetter(DiscShape::minRadius),
            Codec.floatRange(4, 256).fieldOf("max_radius").forGetter(DiscShape::maxRadius),
            Codec.floatRange(0.1F, 2).fieldOf("height_ratio").forGetter(DiscShape::heightRatio),
            Codec.floatRange(4, 128).fieldOf("min_height").forGetter(DiscShape::minHeight),
            Codec.floatRange(2, 32).fieldOf("floor_thickness").forGetter(DiscShape::floorThickness),
            Codec.floatRange(0.01F, 1).fieldOf("stem_fraction").forGetter(DiscShape::stemFraction),
            // Blocks are sampled at whole coordinates around a centre halfway between them, so a radius under 0.75 would hold none.
            Codec.floatRange(0.75F, 32).fieldOf("min_stem_radius").forGetter(DiscShape::minStemRadius),
            Codec.floatRange(0.75F, 32).fieldOf("max_stem_radius").forGetter(DiscShape::maxStemRadius),
            Codec.floatRange(1, 64).fieldOf("funnel_scale").forGetter(DiscShape::funnelScale),
            Codec.floatRange(1, 8).fieldOf("root_spread").forGetter(DiscShape::rootSpread),
            Codec.floatRange(1, 64).fieldOf("root_scale").forGetter(DiscShape::rootScale),
            Codec.floatRange(0, 16).fieldOf("min_bowl_depth").forGetter(DiscShape::minBowlDepth),
            Codec.floatRange(0, 16).fieldOf("max_bowl_depth").forGetter(DiscShape::maxBowlDepth),
            Codec.floatRange(0, 1).fieldOf("bowl_variation").forGetter(DiscShape::bowlVariation)
    ).apply(i, DiscShape::new)).validate(DiscShape::validate);
    public static final Codec<DiscShape> CODEC = MAP_CODEC.codec();

    /** The radius for a uniform draw in [0, 1]. Raising the draw to a power keeps most discs modest and a few large. */
    public double radiusFor(double unit) {
        return minRadius + Math.pow(unit, 1.3) * (maxRadius - minRadius);
    }

    public double heightFor(double radius) {
        return Math.max(heightRatio * radius, minHeight);
    }

    /** Tallest a dome can be, which bounds how many rows or layers below a point can still hold it. */
    public double maxDomeHeight() {
        return heightFor(maxRadius);
    }

    /** A stem's radius: its share of the disc's radius, kept between the least and the most a stem may be. */
    public double stemRadiusFor(double radius) {
        return Math.min(Math.clamp(stemFraction * radius, minStemRadius, maxStemRadius), radius);
    }

    /**
     * How far a disc's rim stands above the middle of its top, for the disc's uniform draw in [0, 1]: the depth for its size
     * (discs under the smallest size get the least), made flatter by the draw's share of the variation.
     */
    public double bowlDepthFor(double radius, double unit) {
        double largeness = maxRadius > minRadius ? Math.clamp((radius - minRadius) / (maxRadius - minRadius), 0, 1) : 0;
        return (minBowlDepth + largeness * (maxBowlDepth - minBowlDepth)) * (1 - bowlVariation * unit);
    }

    /** A root's radius where it meets the ceiling. */
    public double rootRadiusFor(double radius) {
        return Math.min(radius, rootSpread * stemRadiusFor(radius));
    }

    private static DataResult<DiscShape> validate(DiscShape d) {
        if (d.minRadius > d.maxRadius) {
            return DataResult.error(() -> "min_radius must not exceed max_radius");
        }
        if (d.minStemRadius > d.maxStemRadius) {
            return DataResult.error(() -> "min_stem_radius must not exceed max_stem_radius");
        }
        if (d.minBowlDepth > d.maxBowlDepth) {
            return DataResult.error(() -> "min_bowl_depth must not exceed max_bowl_depth");
        }
        return DataResult.success(d);
    }
}

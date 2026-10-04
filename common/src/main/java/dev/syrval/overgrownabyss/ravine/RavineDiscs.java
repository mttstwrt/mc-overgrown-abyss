package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Round, flat-floored rooms with a domed roof cut sideways into the wall from the chasm, one family of them per
 * boundary between {@link RavineTiers levels}. Each is its own circle in plan, so it reads as a separate chamber opening
 * onto the chasm; neighbours on one boundary overlap because their floors sit a few blocks apart. They are sized for
 * ruins to be built inside, and are the intended home of a micro-biome.
 *
 * @param spacing       blocks of ravine length per disc on each boundary, 0 for none
 * @param minSize       smallest ravine size (0 to 1) that gets any
 * @param minRadius     smallest floor radius in blocks
 * @param maxRadius     largest floor radius in blocks
 * @param heightRatio   dome height as a fraction of the radius
 * @param minHeight     lowest a dome may be in blocks; a disc that cannot reach this is not made
 * @param ceilingMargin blocks kept between a dome's top and the top of the ravine, so domes stay under the surface
 * @param yJitter       most a floor may sit above or below its boundary, in blocks
 * @param minOffset     how far a disc's centre sits inside the wall, as a fraction of its radius, at the least
 * @param maxOffset     and at the most; larger values give a narrower mouth
 */
public record RavineDiscs(
        float spacing, float minSize, float minRadius, float maxRadius, float heightRatio, float minHeight,
        float ceilingMargin, float yJitter, float minOffset, float maxOffset) {

    public static final RavineDiscs NONE = new RavineDiscs(0, 0, 8, 8, 0.5F, 8, 12, 0, 0.5F, 0.5F);
    public static final MapCodec<RavineDiscs> MAP_CODEC = RecordCodecBuilder.<RavineDiscs>mapCodec(i -> i.group(
            Codec.floatRange(0, 1024).fieldOf("spacing").forGetter(RavineDiscs::spacing),
            Codec.floatRange(0, 1).fieldOf("min_size").forGetter(RavineDiscs::minSize),
            Codec.floatRange(4, 256).fieldOf("min_radius").forGetter(RavineDiscs::minRadius),
            Codec.floatRange(4, 256).fieldOf("max_radius").forGetter(RavineDiscs::maxRadius),
            Codec.floatRange(0.1F, 2).fieldOf("height_ratio").forGetter(RavineDiscs::heightRatio),
            Codec.floatRange(4, 128).fieldOf("min_height").forGetter(RavineDiscs::minHeight),
            Codec.floatRange(0, 128).fieldOf("ceiling_margin").forGetter(RavineDiscs::ceilingMargin),
            Codec.floatRange(0, 32).fieldOf("y_jitter").forGetter(RavineDiscs::yJitter),
            Codec.floatRange(0, 1).fieldOf("min_offset").forGetter(RavineDiscs::minOffset),
            Codec.floatRange(0, 1).fieldOf("max_offset").forGetter(RavineDiscs::maxOffset)
    ).apply(i, RavineDiscs::new)).validate(RavineDiscs::validate);
    public static final Codec<RavineDiscs> CODEC = MAP_CODEC.codec();

    /** Furthest a disc can reach past the wall it opens from, in blocks; ravine reach must allow for it. */
    public double extraReach() {
        return spacing == 0 ? 0 : maxRadius * (1 + maxOffset);
    }

    private static DataResult<RavineDiscs> validate(RavineDiscs d) {
        if (d.minRadius > d.maxRadius) {
            return DataResult.error(() -> "min_radius must not exceed max_radius");
        }
        return d.minOffset > d.maxOffset ? DataResult.error(() -> "min_offset must not exceed max_offset") : DataResult.success(d);
    }
}

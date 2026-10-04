package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Round rock plates that grow out of the shelves where {@link RavineTiers levels} meet. Each has a level top and a
 * domed underside, is rooted in the wall and reaches into the opening, and several along one boundary overlap a little
 * because their heights differ by a few blocks.
 *
 * @param spacing   blocks of ravine length per disc on each boundary, 0 for none
 * @param minSize   smallest ravine size (0 to 1) that gets any
 * @param minRadius smallest disc radius in blocks
 * @param maxRadius largest disc radius in blocks
 * @param thickness thickness at the middle of a disc in blocks
 * @param yJitter   most a disc's top may sit above or below the boundary, in blocks
 * @param maxReach  furthest a disc may reach into the opening, as a fraction of its width
 * @param inset     blocks of a disc's radius that stay inside the wall, so wall noise cannot leave it floating
 */
public record RavineDiscs(float spacing, float minSize, float minRadius, float maxRadius, float thickness, float yJitter, float maxReach, float inset) {

    public static final RavineDiscs NONE = new RavineDiscs(0, 0, 8, 8, 4, 0, 0.4F, 4);
    public static final MapCodec<RavineDiscs> MAP_CODEC = RecordCodecBuilder.<RavineDiscs>mapCodec(i -> i.group(
            Codec.floatRange(0, 1024).fieldOf("spacing").forGetter(RavineDiscs::spacing),
            Codec.floatRange(0, 1).fieldOf("min_size").forGetter(RavineDiscs::minSize),
            Codec.floatRange(2, 256).fieldOf("min_radius").forGetter(RavineDiscs::minRadius),
            Codec.floatRange(2, 256).fieldOf("max_radius").forGetter(RavineDiscs::maxRadius),
            Codec.floatRange(1, 64).fieldOf("thickness").forGetter(RavineDiscs::thickness),
            Codec.floatRange(0, 32).fieldOf("y_jitter").forGetter(RavineDiscs::yJitter),
            Codec.floatRange(0, 0.6F).fieldOf("max_reach").forGetter(RavineDiscs::maxReach),
            Codec.floatRange(0, 64).fieldOf("inset").forGetter(RavineDiscs::inset)
    ).apply(i, RavineDiscs::new)).validate(RavineDiscs::validate);
    public static final Codec<RavineDiscs> CODEC = MAP_CODEC.codec();

    private static DataResult<RavineDiscs> validate(RavineDiscs d) {
        return d.minRadius > d.maxRadius ? DataResult.error(() -> "min_radius must not exceed max_radius") : DataResult.success(d);
    }
}

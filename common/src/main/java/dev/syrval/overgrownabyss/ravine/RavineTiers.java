package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Splits the shaft above the cavern into a few stacked levels. Each level slides the whole opening sideways and
 * widens or narrows it, so walking from one level to the next the opening zigzags and flat shelves and ceilings appear
 * where the levels meet. Sideways shifts are fractions of the local half width, so small ravines get small steps.
 *
 * @param maxCount number of level changes in the tallest case, 0 for none
 * @param minSize  smallest ravine size (0 to 1) that gets any
 * @param maxShift furthest a level may sit from the centre line, as a fraction of the half width
 * @param minWidth narrowest a level may be, as a fraction of the normal half width
 * @param maxWidth widest a level may be
 * @param ramp     blocks over which one level blends into the next; small values give flat shelves
 */
public record RavineTiers(int maxCount, float minSize, float maxShift, float minWidth, float maxWidth, float ramp) {

    public static final RavineTiers NONE = new RavineTiers(0, 0, 0, 1, 1, 1);
    public static final MapCodec<RavineTiers> MAP_CODEC = RecordCodecBuilder.<RavineTiers>mapCodec(i -> i.group(
            Codec.intRange(0, 8).fieldOf("max_count").forGetter(RavineTiers::maxCount),
            Codec.floatRange(0, 1).fieldOf("min_size").forGetter(RavineTiers::minSize),
            Codec.floatRange(0, 1.5F).fieldOf("max_shift").forGetter(RavineTiers::maxShift),
            Codec.floatRange(0.2F, 1).fieldOf("min_width").forGetter(RavineTiers::minWidth),
            Codec.floatRange(1, 2).fieldOf("max_width").forGetter(RavineTiers::maxWidth),
            Codec.floatRange(0.5F, 32).fieldOf("ramp").forGetter(RavineTiers::ramp)
    ).apply(i, RavineTiers::new)).validate(RavineTiers::validate);
    public static final Codec<RavineTiers> CODEC = MAP_CODEC.codec();

    /** Furthest a wall can sit from the centre line, as a multiple of the normal half width. */
    public double maxExtent() {
        return maxCount == 0 ? 1 : Math.max(1, maxShift + maxWidth);
    }

    private static DataResult<RavineTiers> validate(RavineTiers t) {
        return t.minWidth > t.maxWidth ? DataResult.error(() -> "min_width must not exceed max_width") : DataResult.success(t);
    }
}

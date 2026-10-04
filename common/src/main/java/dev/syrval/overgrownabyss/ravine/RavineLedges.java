package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Flat rock shelves that stand out from the walls into the open shaft, each with a level top, thicker where it meets
 * the wall than at its lip. Turned a little off the wall's direction they overlap and cross one another at different
 * heights. Bridges always land on a pair of them. They are flat and wide enough to build on.
 *
 * @param density     shelves per 100 blocks of ravine length
 * @param minSize     smallest ravine (0 to 1 of the way from the smallest to the largest) that gets free-standing
 *                    shelves, so a small hole stays clean
 * @param minHeight   lowest shelf, as a fraction of the span from the cavern roof to just below the rim
 * @param maxHeight   highest shelf, same scale; keep it below the terrain surface or the shelf sits in open air, where
 *                    the carve cannot add rock
 * @param maxYaw      largest turn away from the wall's direction, in degrees
 */
public record RavineLedges(
        float density,
        float minSize,
        float minHeight,
        float maxHeight,
        int minLength,
        int maxLength,
        int minDepth,
        int maxDepth,
        int thickness,
        float maxYaw) {

    public static final MapCodec<RavineLedges> MAP_CODEC = RecordCodecBuilder.<RavineLedges>mapCodec(i -> i.group(
            Codec.floatRange(0, 32).fieldOf("density").forGetter(RavineLedges::density),
            Codec.floatRange(0, 1).fieldOf("min_size").forGetter(RavineLedges::minSize),
            Codec.floatRange(0, 1).fieldOf("min_height").forGetter(RavineLedges::minHeight),
            Codec.floatRange(0, 1).fieldOf("max_height").forGetter(RavineLedges::maxHeight),
            Codec.intRange(1, 128).fieldOf("min_length").forGetter(RavineLedges::minLength),
            Codec.intRange(1, 128).fieldOf("max_length").forGetter(RavineLedges::maxLength),
            Codec.intRange(1, 64).fieldOf("min_depth").forGetter(RavineLedges::minDepth),
            Codec.intRange(1, 64).fieldOf("max_depth").forGetter(RavineLedges::maxDepth),
            Codec.intRange(1, 32).fieldOf("thickness").forGetter(RavineLedges::thickness),
            Codec.floatRange(0, 80).fieldOf("max_yaw").forGetter(RavineLedges::maxYaw)
    ).apply(i, RavineLedges::new)).validate(RavineLedges::validate);
    public static final Codec<RavineLedges> CODEC = MAP_CODEC.codec();

    private static DataResult<RavineLedges> validate(RavineLedges ledges) {
        if (ledges.minHeight > ledges.maxHeight || ledges.minLength > ledges.maxLength || ledges.minDepth > ledges.maxDepth) {
            return DataResult.error(() -> "each minimum must not exceed its maximum");
        }
        return DataResult.success(ledges);
    }
}

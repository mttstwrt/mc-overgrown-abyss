package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Where a ravine puts its discs: on a jittered grid along both walls, rows alternating by half a slot, from the cavern roof up
 * to just under the top of the ravine, each one cut sideways into the wall so it opens onto the shaft through a wide mouth.
 * What a disc is comes from {@link DiscShape}; this only places them (see {@link RavineDiscLayout}).
 *
 * @param spacing        blocks of ravine length per disc in a row
 * @param rowSpacing     blocks between the floors of successive rows
 * @param ceilingMargin  blocks kept between a dome's top and the top of the ravine, so domes stay under the surface
 * @param minOffset      how far a disc's centre sits inside the wall, as a fraction of its radius, at the least
 * @param maxOffset      and at the most; larger values give a narrower mouth
 * @param rowJitter      how far a floor may drift from its row's height, as a fraction of {@code rowSpacing}; the floors of
 *                       neighbouring rows on one wall are always at least {@code (1 - rowJitter) * rowSpacing} apart
 * @param largeOffsetBonus how much further back into the wall the largest disc sits than the smallest, as a fraction of its
 *                       radius; in between it grows with the radius
 * @param maxOvershoot   furthest a disc may reach past the middle of the chasm, in blocks. A disc that would reach further is
 *                       set back into the wall until it does not, so it stays a full round disc and the middle of the chasm
 *                       is never walled off from one side
 * @param sideStagger    how far the rows on the second wall are raised above those on the first, as a fraction of
 *                       {@code rowSpacing}; 0.5 puts a row on one wall halfway between two rows on the other, so overhangs
 *                       alternate from side to side instead of meeting in the middle
 */
public record RavinePlacement(
        float spacing, float rowSpacing, float ceilingMargin, float minOffset, float maxOffset, float rowJitter,
        float largeOffsetBonus, float maxOvershoot, float sideStagger) {

    public static final MapCodec<RavinePlacement> MAP_CODEC = RecordCodecBuilder.<RavinePlacement>mapCodec(i -> i.group(
            Codec.floatRange(8, 1024).fieldOf("spacing").forGetter(RavinePlacement::spacing),
            Codec.floatRange(2, 128).fieldOf("row_spacing").forGetter(RavinePlacement::rowSpacing),
            Codec.floatRange(0, 128).fieldOf("ceiling_margin").forGetter(RavinePlacement::ceilingMargin),
            Codec.floatRange(0, 1).fieldOf("min_offset").forGetter(RavinePlacement::minOffset),
            Codec.floatRange(0, 1).fieldOf("max_offset").forGetter(RavinePlacement::maxOffset),
            Codec.floatRange(0, 0.9F).fieldOf("row_jitter").forGetter(RavinePlacement::rowJitter),
            Codec.floatRange(0, 0.9F).fieldOf("large_offset_bonus").forGetter(RavinePlacement::largeOffsetBonus),
            Codec.floatRange(0, 256).fieldOf("max_overshoot").forGetter(RavinePlacement::maxOvershoot),
            Codec.floatRange(0, 1).fieldOf("side_stagger").forGetter(RavinePlacement::sideStagger)
    ).apply(i, RavinePlacement::new)).validate(RavinePlacement::validate);
    public static final Codec<RavinePlacement> CODEC = MAP_CODEC.codec();

    /**
     * Furthest a disc can reach behind the wall it opens from, in blocks; ravine reach must allow for it. A disc's centre
     * is inside its wall by less than its radius, so it reaches less than two radii back.
     */
    public double extraReach(DiscShape shape) {
        return 2 * shape.maxRadius();
    }

    private static DataResult<RavinePlacement> validate(RavinePlacement p) {
        if (p.minOffset > p.maxOffset) {
            return DataResult.error(() -> "min_offset must not exceed max_offset");
        }
        // An offset of 1 would leave a disc with no reach into the shaft at all.
        if (p.maxOffset + p.largeOffsetBonus > 0.95F) {
            return DataResult.error(() -> "max_offset plus large_offset_bonus must not exceed 0.95");
        }
        return DataResult.success(p);
    }
}

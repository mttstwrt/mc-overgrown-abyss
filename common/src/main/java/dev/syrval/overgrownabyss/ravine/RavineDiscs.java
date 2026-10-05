package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The chasm walls are made of overlapping round rooms ("discs"): each has a flat floor, a domed roof like the cavern's,
 * and is cut sideways into the wall so it opens onto the shaft through a wide mouth. They are laid out on a jittered
 * grid along both walls, rows alternating by half a slot, from the cavern roof up to just under the top of the ravine,
 * so neighbours overlap and the wall reads as a stack of round bays. The rooms are sized for ruins to be built inside.
 *
 * @param spacing       blocks of ravine length per disc in a row
 * @param rowSpacing    blocks between the floors of successive rows
 * @param minRadius     smallest floor radius in blocks
 * @param maxRadius     largest floor radius in blocks
 * @param heightRatio   dome height as a fraction of the radius
 * @param minHeight     lowest a dome may be in blocks; a disc that cannot reach this is not made
 * @param ceilingMargin blocks kept between a dome's top and the top of the ravine, so domes stay under the surface
 * @param minOffset     how far a disc's centre sits inside the wall, as a fraction of its radius, at the least
 * @param maxOffset     and at the most; larger values give a narrower mouth
 * @param floorThickness blocks of solid rock under each room's floor; it also stops the domes of lower rooms cutting up into it
 * @param rowJitter     how far a floor may drift from its row's height, as a fraction of {@code rowSpacing}; the floors of
 *                      neighbouring rows on one wall are always at least {@code (1 - rowJitter) * rowSpacing} apart
 * @param largeOffsetBonus how much further back into the wall the largest disc sits than the smallest, as a fraction of its
 *                      radius; in between it grows with the radius
 * @param maxOvershoot  furthest a disc may reach past the middle of the chasm, in blocks. A disc that would reach further is
 *                      set back into the wall until it does not, so it stays a full round disc and the middle of the chasm
 *                      is never walled off from one side
 * @param sideStagger   how far the rows on the second wall are raised above those on the first, as a fraction of
 *                      {@code rowSpacing}; 0.5 puts a row on one wall halfway between two rows on the other, so overhangs
 *                      alternate from side to side instead of meeting in the middle
 * @param stemRadius    radius of the stem under each disc's ledge at its thinnest; 2.5 makes it 5 blocks across
 * @param funnelSlope   blocks of height the funnel under a ledge takes to narrow by one block of radius, so larger values give
 *                      a longer, gentler funnel
 */
public record RavineDiscs(
        float spacing, float rowSpacing, float minRadius, float maxRadius, float heightRatio, float minHeight,
        float ceilingMargin, float minOffset, float maxOffset, float floorThickness,
        float rowJitter, float largeOffsetBonus, float maxOvershoot, float sideStagger,
        float stemRadius, float funnelSlope) {

    public static final MapCodec<RavineDiscs> MAP_CODEC = RecordCodecBuilder.<RavineDiscs>mapCodec(i -> i.group(
            Codec.floatRange(8, 1024).fieldOf("spacing").forGetter(RavineDiscs::spacing),
            Codec.floatRange(2, 128).fieldOf("row_spacing").forGetter(RavineDiscs::rowSpacing),
            Codec.floatRange(4, 256).fieldOf("min_radius").forGetter(RavineDiscs::minRadius),
            Codec.floatRange(4, 256).fieldOf("max_radius").forGetter(RavineDiscs::maxRadius),
            Codec.floatRange(0.1F, 2).fieldOf("height_ratio").forGetter(RavineDiscs::heightRatio),
            Codec.floatRange(4, 128).fieldOf("min_height").forGetter(RavineDiscs::minHeight),
            Codec.floatRange(0, 128).fieldOf("ceiling_margin").forGetter(RavineDiscs::ceilingMargin),
            Codec.floatRange(0, 1).fieldOf("min_offset").forGetter(RavineDiscs::minOffset),
            Codec.floatRange(0, 1).fieldOf("max_offset").forGetter(RavineDiscs::maxOffset),
            Codec.floatRange(2, 32).fieldOf("floor_thickness").forGetter(RavineDiscs::floorThickness),
            Codec.floatRange(0, 0.9F).fieldOf("row_jitter").forGetter(RavineDiscs::rowJitter),
            Codec.floatRange(0, 0.9F).fieldOf("large_offset_bonus").forGetter(RavineDiscs::largeOffsetBonus),
            Codec.floatRange(0, 256).fieldOf("max_overshoot").forGetter(RavineDiscs::maxOvershoot),
            Codec.floatRange(0, 1).fieldOf("side_stagger").forGetter(RavineDiscs::sideStagger),
            Codec.floatRange(2.5F, 16).fieldOf("stem_radius").forGetter(RavineDiscs::stemRadius),
            Codec.floatRange(0.5F, 8).fieldOf("funnel_slope").forGetter(RavineDiscs::funnelSlope)
    ).apply(i, RavineDiscs::new)).validate(RavineDiscs::validate);
    public static final Codec<RavineDiscs> CODEC = MAP_CODEC.codec();

    /**
     * Furthest a disc can reach behind the wall it opens from, in blocks; ravine reach must allow for it. A disc's centre
     * is inside its wall by less than its radius, so it reaches less than two radii back.
     */
    public double extraReach() {
        return 2 * maxRadius;
    }

    /** Tallest a dome can be, which bounds how many rows below a point can still hold it. */
    public double maxDomeHeight() {
        return Math.max(heightRatio * maxRadius, minHeight);
    }

    private static DataResult<RavineDiscs> validate(RavineDiscs d) {
        if (d.minRadius > d.maxRadius) {
            return DataResult.error(() -> "min_radius must not exceed max_radius");
        }
        if (d.minOffset > d.maxOffset) {
            return DataResult.error(() -> "min_offset must not exceed max_offset");
        }
        // An offset of 1 would leave a disc with no reach into the shaft at all.
        if (d.maxOffset + d.largeOffsetBonus > 0.95F) {
            return DataResult.error(() -> "max_offset plus large_offset_bonus must not exceed 0.95");
        }
        return DataResult.success(d);
    }
}

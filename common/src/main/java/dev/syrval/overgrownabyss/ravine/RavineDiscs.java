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
 * @param maxLip        furthest a floor reaches out into the shaft as a ledge, as a fraction of the shaft's width; 1 lets a
 *                      floor reach the opposite wall and 2 lets it carry on through it, so a large disc can cross the chasm and
 *                      run under a room on the other side
 * @param rowJitter     how far a floor may drift from its row's height, as a fraction of {@code rowSpacing}; the floors of
 *                      neighbouring rows are always at least {@code (1 - rowJitter) * rowSpacing} apart
 * @param throughChance fraction of discs sized to run all the way through the chasm into the opposite wall, whatever the
 *                      chasm's width at their height; a disc that would need more than {@code throughMaxRadius} stays ordinary
 * @param throughMaxRadius largest radius a through disc may have, so very wide chasms are not crossed
 * @param largeOffsetBonus how much further back into the wall the largest ordinary disc sits than the smallest, as a fraction
 *                      of its radius; in between it grows with the radius. Large discs set back like this poke less far into
 *                      the shaft, so they do not wall off the middle of it. Through discs ignore it, as setting one back
 *                      makes it need a larger radius and cover more of the shaft
 */
public record RavineDiscs(
        float spacing, float rowSpacing, float minRadius, float maxRadius, float heightRatio, float minHeight,
        float ceilingMargin, float minOffset, float maxOffset, float floorThickness, float maxLip,
        float rowJitter, float throughChance, float throughMaxRadius,
        float largeOffsetBonus) {

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
            Codec.floatRange(0, 2).fieldOf("max_lip").forGetter(RavineDiscs::maxLip),
            Codec.floatRange(0, 0.9F).fieldOf("row_jitter").forGetter(RavineDiscs::rowJitter),
            Codec.floatRange(0, 1).fieldOf("through_chance").forGetter(RavineDiscs::throughChance),
            Codec.floatRange(4, 256).fieldOf("through_max_radius").forGetter(RavineDiscs::throughMaxRadius),
            Codec.floatRange(0, 0.9F).fieldOf("large_offset_bonus").forGetter(RavineDiscs::largeOffsetBonus)
    ).apply(i, RavineDiscs::new)).validate(RavineDiscs::validate);
    public static final Codec<RavineDiscs> CODEC = MAP_CODEC.codec();

    /** Furthest a disc can reach past the wall it opens from, in blocks; ravine reach must allow for it. */
    public double extraReach() {
        return Math.max(maxRadius * (1 + maxOffset + largeOffsetBonus), throughChance > 0 ? throughMaxRadius * (1 + minOffset) : 0);
    }

    /** The largest radius any disc can have. */
    public double largestRadius() {
        return throughChance > 0 ? Math.max(maxRadius, throughMaxRadius) : maxRadius;
    }

    /** Tallest a dome can be, which bounds how many rows below a point can still hold it. */
    public double maxDomeHeight() {
        return Math.max(heightRatio * largestRadius(), minHeight);
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
        // A through disc's radius is the chasm width divided by (1 - min_offset).
        if (d.throughChance > 0 && d.minOffset > 0.9F) {
            return DataResult.error(() -> "min_offset must not exceed 0.9 when through_chance is above 0");
        }
        return DataResult.success(d);
    }
}

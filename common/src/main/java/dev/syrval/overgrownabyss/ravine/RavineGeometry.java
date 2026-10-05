package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.InclusiveRange;

/**
 * A long, curving, leaning ravine. Lengths and widths are in blocks; each ravine takes a size from 0 to 1 (see
 * {@link RavineSettings#sizeBias()}) and that fraction of the way from the smallest length and width to the largest. A length
 * of 0 is a round hole above the cavern. {@code width} is the full width at the top, narrowing towards the floor by
 * {@code bottomWidthFactor}.
 *
 * @param length           range of lengths
 * @param width            range of widths at the top
 * @param bottomWidthFactor the width at the floor as a fraction of the width at the top
 * @param curvature        how far the centre line may bend and lean
 * @param placement        where its discs go
 */
public record RavineGeometry(
        InclusiveRange<Integer> length, InclusiveRange<Integer> width, float bottomWidthFactor,
        RavineCurvature curvature, RavinePlacement placement) {

    /** What a cone's cell uses: no length, no curves, and a placement nothing reads. */
    static final RavineGeometry ROUND_HOLE = new RavineGeometry(
            new InclusiveRange<>(0, 0), new InclusiveRange<>(2, 2), 1,
            new RavineCurvature(0, 0, 0, 0), new RavinePlacement(8, 2, 0, 0, 0, 0, 0, 0, 0));

    public static final MapCodec<RavineGeometry> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            InclusiveRange.codec(Codec.intRange(0, 4096)).fieldOf("length").forGetter(RavineGeometry::length),
            InclusiveRange.codec(Codec.intRange(2, 4096)).fieldOf("width").forGetter(RavineGeometry::width),
            Codec.floatRange(0, 1).fieldOf("bottom_width_factor").forGetter(RavineGeometry::bottomWidthFactor),
            RavineCurvature.CODEC.fieldOf("curvature").forGetter(RavineGeometry::curvature),
            RavinePlacement.CODEC.fieldOf("placement").forGetter(RavineGeometry::placement)
    ).apply(i, RavineGeometry::new));
    public static final Codec<RavineGeometry> CODEC = MAP_CODEC.codec();
}

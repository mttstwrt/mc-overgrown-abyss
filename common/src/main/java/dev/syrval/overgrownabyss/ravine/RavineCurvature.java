package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Largest sideways displacement, in blocks, of a ravine's centre line. Bend and wiggle curve it in plan view (scaled
 * down for short ravines); lean and bow shift it sideways with height, from zero at the cavern floor so the shaft
 * always meets the cavern.
 */
public record RavineCurvature(float maxBend, float maxWiggle, float maxLean, float maxBow) {

    public static final MapCodec<RavineCurvature> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 512).fieldOf("max_bend").forGetter(RavineCurvature::maxBend),
            Codec.floatRange(0, 512).fieldOf("max_wiggle").forGetter(RavineCurvature::maxWiggle),
            Codec.floatRange(0, 512).fieldOf("max_lean").forGetter(RavineCurvature::maxLean),
            Codec.floatRange(0, 512).fieldOf("max_bow").forGetter(RavineCurvature::maxBow)
    ).apply(i, RavineCurvature::new));
    public static final Codec<RavineCurvature> CODEC = MAP_CODEC.codec();

    /** Furthest the centre line can sit from the straight chord through the cell centre. */
    public double maxDisplacement() {
        return maxBend + maxWiggle + maxLean + maxBow;
    }
}

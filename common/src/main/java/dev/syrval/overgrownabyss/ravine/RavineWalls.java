package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How rough the walls are. Fine noise gives rock texture; broad noise (sampled with a stretched vertical axis) pushes
 * large lumps and undercuts out of the wall; strata cut the wall into horizontal layers, each pushed in or out by its
 * own amount along the ravine, which leaves vertical faces joined by flat steps; the width wobble makes each side
 * swell and narrow with height at its own rhythm; terraces add evenly spaced ledges whose heights wander along the
 * ravine. Wide shelves are separate, see {@link RavineLedges}.
 *
 * @param terraceStep       vertical spacing of ledges, 0 for none
 * @param terraceStrength   0 smooth taper, 1 hard steps
 * @param terraceWarp       blocks by which ledge heights vary along the ravine
 * @param noiseScale        fine noise frequency
 * @param noiseAmplitude    fine noise strength in blocks
 * @param overhangScale     broad noise frequency
 * @param overhangAmplitude broad noise strength in blocks
 * @param strataHeight      height of one wall layer in blocks
 * @param strataAmplitude   how far a layer is pushed in or out, in blocks
 * @param strataScale       how quickly a layer's offset changes along the ravine
 * @param widthWobble       largest fraction of the half-width a side swells or narrows by
 * @param edgeFalloff       blocks over which the wall fades from open to solid
 */
public record RavineWalls(
        int terraceStep,
        float terraceStrength,
        float terraceWarp,
        float noiseScale,
        float noiseAmplitude,
        float overhangScale,
        float overhangAmplitude,
        float strataHeight,
        float strataAmplitude,
        float strataScale,
        float widthWobble,
        float edgeFalloff) {

    public static final MapCodec<RavineWalls> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 256).fieldOf("terrace_step").forGetter(RavineWalls::terraceStep),
            Codec.floatRange(0, 1).fieldOf("terrace_strength").forGetter(RavineWalls::terraceStrength),
            Codec.floatRange(0, 64).fieldOf("terrace_warp").forGetter(RavineWalls::terraceWarp),
            Codec.floatRange(0, 1).fieldOf("noise_scale").forGetter(RavineWalls::noiseScale),
            Codec.floatRange(0, 256).fieldOf("noise_amplitude").forGetter(RavineWalls::noiseAmplitude),
            Codec.floatRange(0, 1).fieldOf("overhang_scale").forGetter(RavineWalls::overhangScale),
            Codec.floatRange(0, 256).fieldOf("overhang_amplitude").forGetter(RavineWalls::overhangAmplitude),
            Codec.floatRange(1, 64).fieldOf("strata_height").forGetter(RavineWalls::strataHeight),
            Codec.floatRange(0, 64).fieldOf("strata_amplitude").forGetter(RavineWalls::strataAmplitude),
            Codec.floatRange(0, 1).fieldOf("strata_scale").forGetter(RavineWalls::strataScale),
            Codec.floatRange(0, 0.9F).fieldOf("width_wobble").forGetter(RavineWalls::widthWobble),
            Codec.floatRange(0.5F, 64).fieldOf("edge_falloff").forGetter(RavineWalls::edgeFalloff)
    ).apply(i, RavineWalls::new));
    public static final Codec<RavineWalls> CODEC = MAP_CODEC.codec();

    /** Most the noise can move a wall, which bounds how far from the ideal shape any block can change. */
    public double maxNoiseDisplacement() {
        return noiseAmplitude + overhangAmplitude + strataAmplitude;
    }
}

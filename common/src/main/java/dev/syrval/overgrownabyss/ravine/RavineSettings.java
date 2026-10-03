package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.InclusiveRange;

/**
 * Datapack tunables for the ravine shape. Lengths and widths are in blocks; {@code width} is the full width at
 * {@code top_y}, narrowing towards {@code floor_y} by {@code bottom_width_factor}.
 */
public record RavineSettings(
        long salt,
        int cellSize,
        float chance,
        InclusiveRange<Integer> length,
        InclusiveRange<Integer> width,
        int floorY,
        int topY,
        float bottomWidthFactor,
        int cavernRadius,
        int cavernHeight,
        int terraceStep,
        float terraceStrength,
        float wallNoiseScale,
        float wallNoiseAmplitude,
        float edgeFalloff) {

    public static final MapCodec<RavineSettings> MAP_CODEC = RecordCodecBuilder.<RavineSettings>mapCodec(i -> i.group(
            Codec.LONG.fieldOf("salt").forGetter(RavineSettings::salt),
            Codec.intRange(64, 1 << 16).fieldOf("cell_size").forGetter(RavineSettings::cellSize),
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(RavineSettings::chance),
            InclusiveRange.codec(Codec.intRange(1, 4096)).fieldOf("length").forGetter(RavineSettings::length),
            InclusiveRange.codec(Codec.intRange(2, 4096)).fieldOf("width").forGetter(RavineSettings::width),
            Codec.INT.fieldOf("floor_y").forGetter(RavineSettings::floorY),
            Codec.INT.fieldOf("top_y").forGetter(RavineSettings::topY),
            Codec.floatRange(0, 1).fieldOf("bottom_width_factor").forGetter(RavineSettings::bottomWidthFactor),
            Codec.intRange(0, 4096).fieldOf("cavern_radius").forGetter(RavineSettings::cavernRadius),
            Codec.intRange(1, 1024).fieldOf("cavern_height").forGetter(RavineSettings::cavernHeight),
            Codec.intRange(0, 256).fieldOf("terrace_step").forGetter(RavineSettings::terraceStep),
            Codec.floatRange(0, 1).fieldOf("terrace_strength").forGetter(RavineSettings::terraceStrength),
            Codec.floatRange(0, 1).fieldOf("wall_noise_scale").forGetter(RavineSettings::wallNoiseScale),
            Codec.floatRange(0, 256).fieldOf("wall_noise_amplitude").forGetter(RavineSettings::wallNoiseAmplitude),
            Codec.floatRange(0.5F, 64).fieldOf("edge_falloff").forGetter(RavineSettings::edgeFalloff)
    ).apply(i, RavineSettings::new)).validate(RavineSettings::validate);

    /** Furthest horizontal distance from a ravine centre that the carve can reach. */
    public double maxReach() {
        double ravine = length.maxInclusive() / 2.0 + width.maxInclusive() / 2.0;
        return Math.max(ravine, cavernRadius) + wallNoiseAmplitude + edgeFalloff;
    }

    private static DataResult<RavineSettings> validate(RavineSettings s) {
        if (s.topY <= s.floorY) {
            return DataResult.error(() -> "top_y must be above floor_y");
        }
        // Each ravine must fit inside its own cell so a sample only ever has to look at one cell.
        if (s.cellSize < 2 * s.maxReach()) {
            return DataResult.error(() -> "cell_size " + s.cellSize + " is too small for a ravine reaching " + s.maxReach());
        }
        return DataResult.success(s);
    }
}

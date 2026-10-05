package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/**
 * Datapack tunables for the ravine shape. Lengths and widths are in blocks; each ravine draws one size from 0 to 1
 * (a uniform draw raised to {@code size_bias}: 1 is even, below 1 favours large ravines, above 1 small ones) and
 * takes that fraction of the way from the smallest length and width (a round hole above the cavern when the length is
 * 0) to the largest. {@code width} is the full width at {@code top}, narrowing towards {@code floor} by
 * {@code bottom_width_factor}. The vertical bounds are anchors so the cavern floor follows the world bottom of
 * whatever terrain source is in use (vanilla, Larion, ...). {@code edge_falloff} is the number of blocks over which the
 * carve fades from open to solid; it should be at least a noise cell high (8) so flat floors come out flat.
 *
 * <p>With a {@code cone} block the file describes a single cone-shaped hole instead (see {@link ConeSettings}); the ravine's
 * {@code width}, {@code bottom_width_factor}, {@code curvature} and {@code discs} are then unused, and {@code length} should be
 * 0 so every cell is a round hole.
 */
public record RavineSettings(
        long salt,
        int cellSize,
        float chance,
        float sizeBias,
        InclusiveRange<Integer> length,
        InclusiveRange<Integer> width,
        VerticalAnchor floor,
        VerticalAnchor top,
        float bottomWidthFactor,
        int cavernRadius,
        int cavernHeight,
        float edgeFalloff,
        RavineCurvature curvature,
        RavineDiscs discs,
        RavineEnvironment environment,
        Optional<ConeSettings> cone) {

    public static final MapCodec<RavineSettings> MAP_CODEC = RecordCodecBuilder.<RavineSettings>mapCodec(i -> i.group(
            Codec.LONG.fieldOf("salt").forGetter(RavineSettings::salt),
            Codec.intRange(64, 1 << 16).fieldOf("cell_size").forGetter(RavineSettings::cellSize),
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(RavineSettings::chance),
            Codec.floatRange(0.25F, 8).fieldOf("size_bias").forGetter(RavineSettings::sizeBias),
            InclusiveRange.codec(Codec.intRange(0, 4096)).fieldOf("length").forGetter(RavineSettings::length),
            InclusiveRange.codec(Codec.intRange(2, 4096)).fieldOf("width").forGetter(RavineSettings::width),
            VerticalAnchor.CODEC.fieldOf("floor").forGetter(RavineSettings::floor),
            VerticalAnchor.CODEC.fieldOf("top").forGetter(RavineSettings::top),
            Codec.floatRange(0, 1).fieldOf("bottom_width_factor").forGetter(RavineSettings::bottomWidthFactor),
            Codec.intRange(0, 4096).fieldOf("cavern_radius").forGetter(RavineSettings::cavernRadius),
            Codec.intRange(1, 1024).fieldOf("cavern_height").forGetter(RavineSettings::cavernHeight),
            Codec.floatRange(0.5F, 64).fieldOf("edge_falloff").forGetter(RavineSettings::edgeFalloff),
            RavineCurvature.CODEC.fieldOf("curvature").forGetter(RavineSettings::curvature),
            RavineDiscs.CODEC.fieldOf("discs").forGetter(RavineSettings::discs),
            RavineEnvironment.CODEC.fieldOf("environment").forGetter(RavineSettings::environment),
            ConeSettings.CODEC.optionalFieldOf("cone").forGetter(RavineSettings::cone)
    ).apply(i, RavineSettings::new)).validate(RavineSettings::validate);

    /**
     * Furthest horizontal distance from a ravine centre that the carve can reach: the longest ravine's half length plus
     * its widest wall, pushed out by the curves and by the rooms cut out of the wall, and then by the falloff.
     */
    public double maxReach() {
        // The cone is as wide as the cavern at the floor and narrower above, and its structures stay inside it.
        if (cone.isPresent()) {
            return cavernRadius + edgeFalloff;
        }
        double ravine = length.maxInclusive() / 2.0 + width.maxInclusive() / 2.0 + curvature.maxDisplacement() + discs.extraReach();
        return Math.max(ravine, cavernRadius) + edgeFalloff;
    }

    /** Resolves the anchors for one level; empty when the level is too short to hold the ravine. */
    public Optional<RavineBounds> resolveBounds(WorldGenerationContext context) {
        return RavineBounds.of(floor.resolveY(context), top.resolveY(context));
    }

    private static DataResult<RavineSettings> validate(RavineSettings s) {
        // Structure placement works in chunks, so a cell must be a whole number of them.
        if (s.cellSize % 16 != 0) {
            return DataResult.error(() -> "cell_size " + s.cellSize + " must be a multiple of 16");
        }
        // Each ravine must fit inside its own cell so a sample only ever has to look at one cell.
        if (s.cellSize < 2 * s.maxReach()) {
            return DataResult.error(() -> "cell_size " + s.cellSize + " is too small for a ravine reaching " + s.maxReach());
        }
        if (s.cone.isPresent() && s.cone.get().topRadius() > s.cavernRadius) {
            return DataResult.error(() -> "the cone's top_radius must not exceed cavern_radius, since it widens towards the floor");
        }
        return DataResult.success(s);
    }
}

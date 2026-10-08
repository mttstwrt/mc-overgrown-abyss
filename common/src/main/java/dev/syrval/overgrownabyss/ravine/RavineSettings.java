package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/**
 * Datapack tunables for one hole in the ground and the city cavern at its floor. The hole is either a {@code ravine} (a long
 * curving shaft, see {@link RavineGeometry}) or a {@code cone} (see {@link ConeSettings}); exactly one is given. Both are filled
 * with the same discs, which {@code discs} describes (see {@link DiscShape}). {@code disc_themes} are the kinds of disc there
 * are (see {@link DiscTheme}): each disc is given one, which sets its biome, what it is made of and what grows on it. Without
 * any, every disc is the terrain's own rock in the biome it lies in. {@code wall_noise} makes a cone's own wall uneven (see
 * {@link WallNoise}); without it the wall is an exact surface of revolution.
 *
 * <p>Each ravine draws one size from 0 to 1 (a uniform draw raised to {@code size_bias}: 1 is even, below 1 favours large
 * ravines, above 1 small ones). The vertical bounds are anchors so the cavern floor follows the world bottom of whatever
 * terrain source is in use (vanilla, Larion, ...). {@code edge_falloff} is the number of blocks over which the carve fades
 * from open to solid; it should be at least a noise cell high (8) so flat floors come out flat.
 */
public record RavineSettings(
        long salt,
        int cellSize,
        float chance,
        float sizeBias,
        VerticalAnchor floor,
        VerticalAnchor top,
        int cavernRadius,
        int cavernHeight,
        float edgeFalloff,
        DiscShape discs,
        List<DiscTheme> discThemes,
        RavineEnvironment environment,
        WallNoise wallNoise,
        Optional<RavineGeometry> ravine,
        Optional<ConeSettings> cone) {

    public static final MapCodec<RavineSettings> MAP_CODEC = RecordCodecBuilder.<RavineSettings>mapCodec(i -> i.group(
            Codec.LONG.fieldOf("salt").forGetter(RavineSettings::salt),
            Codec.intRange(64, 1 << 16).fieldOf("cell_size").forGetter(RavineSettings::cellSize),
            Codec.floatRange(0, 1).fieldOf("chance").forGetter(RavineSettings::chance),
            Codec.floatRange(0.25F, 8).fieldOf("size_bias").forGetter(RavineSettings::sizeBias),
            VerticalAnchor.CODEC.fieldOf("floor").forGetter(RavineSettings::floor),
            VerticalAnchor.CODEC.fieldOf("top").forGetter(RavineSettings::top),
            Codec.intRange(0, 4096).fieldOf("cavern_radius").forGetter(RavineSettings::cavernRadius),
            Codec.intRange(1, 1024).fieldOf("cavern_height").forGetter(RavineSettings::cavernHeight),
            Codec.floatRange(0.5F, 64).fieldOf("edge_falloff").forGetter(RavineSettings::edgeFalloff),
            DiscShape.CODEC.fieldOf("discs").forGetter(RavineSettings::discs),
            DiscTheme.CODEC.listOf().optionalFieldOf("disc_themes", List.of()).forGetter(RavineSettings::discThemes),
            RavineEnvironment.CODEC.fieldOf("environment").forGetter(RavineSettings::environment),
            WallNoise.CODEC.optionalFieldOf("wall_noise", WallNoise.NONE).forGetter(RavineSettings::wallNoise),
            RavineGeometry.CODEC.optionalFieldOf("ravine").forGetter(RavineSettings::ravine),
            ConeSettings.CODEC.optionalFieldOf("cone").forGetter(RavineSettings::cone)
    ).apply(i, RavineSettings::new)).validate(RavineSettings::validate);

    public RavineSettings {
        discThemes = List.copyOf(discThemes);
    }

    // A cone's cell is a round hole: no length and no curves.
    private RavineGeometry geometry() {
        return ravine.orElse(RavineGeometry.ROUND_HOLE);
    }

    public InclusiveRange<Integer> length() {
        return geometry().length();
    }

    public InclusiveRange<Integer> width() {
        return geometry().width();
    }

    public float bottomWidthFactor() {
        return geometry().bottomWidthFactor();
    }

    public RavineCurvature curvature() {
        return geometry().curvature();
    }

    public RavinePlacement placement() {
        return geometry().placement();
    }

    /**
     * Furthest horizontal distance from a hole's centre that the carve can reach. For a ravine: the longest ravine's half length
     * plus its widest wall, pushed out by the curves and by the discs cut out of the wall. For a cone: the largest of the
     * cavern's radius, the cone's at the floor and the outer radius its discs are held to. Then the falloff.
     */
    public double maxReach() {
        if (cone.isPresent()) {
            return Math.max(Math.max(cavernRadius, cone.get().baseRadius()), cone.get().outerRadius()) + ConeDiscLayout.PLACING_SLACK + edgeFalloff;
        }
        RavineGeometry g = geometry();
        double ravine = g.length().maxInclusive() / 2.0 + g.width().maxInclusive() / 2.0 + g.curvature().maxDisplacement()
                + g.placement().extraReach(discs);
        return Math.max(ravine, cavernRadius) + edgeFalloff;
    }

    /** Resolves the anchors for one level; empty when the level is too short to hold the ravine. */
    public Optional<RavineBounds> resolveBounds(WorldGenerationContext context) {
        return RavineBounds.of(floor.resolveY(context), top.resolveY(context));
    }

    // Height above the cavern floor of the underside of the lowest platform a cone can place.
    private static double lowestUnderside(RavineSettings s, ConeSettings cone) {
        return s.cavernHeight + cone.baseClearance() - cone.layerJitter() * cone.layerSpacing() / 2 - s.discs.floorThickness();
    }

    // Room the highest platform of a cone's top layer needs under the ceiling to still have the least dome.
    private static double highestDomeNeeds(RavineSettings s, ConeSettings cone) {
        return s.discs.minHeight() + cone.layerJitter() * cone.layerSpacing() / 2;
    }

    private static DataResult<RavineSettings> validate(RavineSettings s) {
        if (s.ravine.isPresent() == s.cone.isPresent()) {
            return DataResult.error(() -> "exactly one of ravine and cone must be given");
        }
        // Structure placement works in chunks, so a cell must be a whole number of them.
        if (s.cellSize % 16 != 0) {
            return DataResult.error(() -> "cell_size " + s.cellSize + " must be a multiple of 16");
        }
        // Each ravine must fit inside its own cell so a sample only ever has to look at one cell.
        if (s.cellSize < 2 * s.maxReach()) {
            return DataResult.error(() -> "cell_size " + s.cellSize + " is too small for a ravine reaching " + s.maxReach());
        }
        if (s.cone.isPresent() && s.cone.get().outerRadius() < s.cone.get().widestClearRadius() + 2 * s.discs.minRadius() + ConeDiscLayout.PLACING_SLACK) {
            return DataResult.error(() -> "the cone's outer_radius leaves no room for even the smallest disc beside the clear air round the axis");
        }
        if (s.discThemes.stream().anyMatch(theme -> theme.water().isPresent()) && s.discs.floorThickness() <= DiscWater.MAX_DEPTH) {
            return DataResult.error(() -> "floor_thickness must be above " + DiscWater.MAX_DEPTH + " for a disc theme to hold water, or the water has no bed");
        }
        if (s.cone.isPresent() && lowestUnderside(s, s.cone.get()) <= 0) {
            return DataResult.error(() -> "the cone's base_clearance puts its lowest discs at or under the cavern floor");
        }
        if (s.cone.flatMap(ConeSettings::rim).isPresent() && s.cone.get().rim().get().topRoom() < highestDomeNeeds(s, s.cone.get())) {
            return DataResult.error(() -> "the rim's top_room must be at least " + highestDomeNeeds(s, s.cone.get())
                    + " (min_height and half the layers' jitter), or the top layer's discs have no room for a dome");
        }
        double moved = s.wallNoise.maxDisplacement();
        if (moved > 0 && s.cone.isEmpty()) {
            return DataResult.error(() -> "wall_noise is only for a cone");
        }
        if (moved > 0 && s.cone.get().topRadius() - moved <= s.cone.get().widestClearRadius()) {
            return DataResult.error(() -> "wall_noise moves the wall by up to " + moved + " blocks, which at the top brings it into the clear air round the axis");
        }
        if (moved > 0 && Math.max(s.cone.get().baseRadius(), s.cavernRadius) + moved > s.cone.get().outerRadius()) {
            return DataResult.error(() -> "wall_noise moves the wall by up to " + moved + " blocks, which at the floor takes it past the cone's outer_radius");
        }
        return DataResult.success(s);
    }
}

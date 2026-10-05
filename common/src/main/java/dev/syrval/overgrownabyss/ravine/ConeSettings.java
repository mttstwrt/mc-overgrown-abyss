package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Turns a ravine settings file into a single round hole shaped like a cone, widest at the bottom where it meets the city
 * cavern (the radius is {@code cavern_radius} at the floor) and narrowing to {@code top_radius} at the top. The rooms
 * and curves of the ravine do not apply; instead the cone is filled with free-standing flat-topped stone structures, each
 * a thick cap on a stem, built in layers around the cone. A cylinder of {@code clear_radius} around the middle is never
 * touched by a structure, so there is always a clear line of sight straight down.
 *
 * @param topRadius      radius of the cone at the top, in blocks
 * @param flare          how the radius grows towards the floor: 1 is a straight cone, above 1 the walls stay steep near the
 *                       top and flare out near the bottom
 * @param clearRadius    radius of the cylinder around the axis that is always open
 * @param layerSpacing   blocks between the tops of successive layers of structures
 * @param layerJitter    how far a top may drift from its layer's height, as a fraction of {@code layerSpacing}
 * @param spacing        blocks of the ring's length per structure in a layer
 * @param minRadius      smallest cap radius in blocks
 * @param maxRadius      largest cap radius in blocks; a cap is also kept under 0.55 of the room between the clear cylinder
 *                       and the wall
 * @param capThickness   blocks of solid rock in a cap, below its flat top
 * @param stemFraction   the stem's radius as a fraction of its cap's radius
 * @param stemRadius     the least radius a stem may have; 2.5 makes it 5 blocks across
 * @param funnelScale    how quickly a cap narrows into its stem: the radius is the cap's times
 *                       {@code funnelScale / (depth + funnelScale)} until it reaches the stem's radius
 * @param stackChance    chance that a structure sits straight above the one in the layer below it, so its stem lands on that cap
 * @param ceilingMargin  blocks kept between the highest cap tops and the top of the cone
 * @param baseClearance  blocks between the cavern roof and the lowest layer's tops
 */
public record ConeSettings(
        float topRadius, float flare, float clearRadius, float layerSpacing, float layerJitter, float spacing,
        float minRadius, float maxRadius, float capThickness, float stemFraction, float stemRadius, float funnelScale,
        float stackChance, float ceilingMargin, float baseClearance) {

    public static final MapCodec<ConeSettings> MAP_CODEC = RecordCodecBuilder.<ConeSettings>mapCodec(i -> i.group(
            Codec.floatRange(8, 512).fieldOf("top_radius").forGetter(ConeSettings::topRadius),
            Codec.floatRange(0.5F, 4).fieldOf("flare").forGetter(ConeSettings::flare),
            Codec.floatRange(2, 64).fieldOf("clear_radius").forGetter(ConeSettings::clearRadius),
            Codec.floatRange(8, 128).fieldOf("layer_spacing").forGetter(ConeSettings::layerSpacing),
            Codec.floatRange(0, 0.8F).fieldOf("layer_jitter").forGetter(ConeSettings::layerJitter),
            Codec.floatRange(16, 512).fieldOf("spacing").forGetter(ConeSettings::spacing),
            Codec.floatRange(4, 128).fieldOf("min_radius").forGetter(ConeSettings::minRadius),
            Codec.floatRange(4, 128).fieldOf("max_radius").forGetter(ConeSettings::maxRadius),
            Codec.floatRange(2, 32).fieldOf("cap_thickness").forGetter(ConeSettings::capThickness),
            Codec.floatRange(0.05F, 1).fieldOf("stem_fraction").forGetter(ConeSettings::stemFraction),
            Codec.floatRange(2.5F, 32).fieldOf("stem_radius").forGetter(ConeSettings::stemRadius),
            Codec.floatRange(1, 64).fieldOf("funnel_scale").forGetter(ConeSettings::funnelScale),
            Codec.floatRange(0, 1).fieldOf("stack_chance").forGetter(ConeSettings::stackChance),
            Codec.floatRange(0, 128).fieldOf("ceiling_margin").forGetter(ConeSettings::ceilingMargin),
            Codec.floatRange(0, 128).fieldOf("base_clearance").forGetter(ConeSettings::baseClearance)
    ).apply(i, ConeSettings::new)).validate(ConeSettings::validate);
    public static final Codec<ConeSettings> CODEC = MAP_CODEC.codec();

    private static DataResult<ConeSettings> validate(ConeSettings c) {
        if (c.minRadius > c.maxRadius) {
            return DataResult.error(() -> "min_radius must not exceed max_radius");
        }
        if (c.clearRadius >= c.topRadius) {
            return DataResult.error(() -> "clear_radius must be below top_radius, or there is no room at the top");
        }
        return DataResult.success(c);
    }
}

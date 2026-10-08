package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * A single round hole shaped like a cone, widest at the bottom where it meets the city cavern ({@code base_radius} at the
 * floor) and narrowing to {@code top_radius} at the top. It is filled with the same discs as a
 * ravine ({@link DiscShape}), standing free in layers around the cone. A disc is as large as it is drawn, whatever room the
 * cone has at its height: what does not fit cuts its dome into the rock around the cone, and a large disc carries further
 * discs on top of it out there. A cylinder of {@code clear_radius} around the middle is never touched by a platform or stem,
 * so there is always a clear line of sight straight down.
 *
 * @param topRadius      radius of the cone at the top, in blocks
 * @param baseRadius     radius of the cone at the floor. The cavern has its own radius; where the cone is wider than the
 *                       cavern's dome it opens the ground beyond it
 * @param flare          how the radius grows towards the floor: 1 is a straight cone, above 1 the walls stay steep near the
 *                       top and flare out near the bottom
 * @param clearRadius    radius of the cylinder around the axis that is always open. With {@code upper} it is the radius at
 *                       the floor, and the clear air widens from there to the top
 * @param layerSpacing   blocks between the platforms of successive layers of discs
 * @param layerJitter    how far a platform may drift from its layer's height, as a fraction of {@code layerSpacing}
 * @param spacing        blocks of the ring's length per disc in a layer
 * @param stackChance    chance that a disc sits straight above the one in the layer below it, so its stem lands on that platform
 * @param ceilingMargin  blocks kept between the highest domes and the top of the cone
 * @param baseClearance  blocks between the cavern roof and the lowest layer's platforms. Below 0 the lowest layers are
 *                       inside the cavern's airspace, over the city
 * @param hangChance     chance that a disc hangs from a root instead of standing on a stem. It only hangs if it has a ceiling:
 *                       the platform of a disc above it, or the cone's wall. A disc under open sky always stands
 * @param outerRadius    nothing of any disc lies further than this from the axis, however far outside the cone discs carve
 * @param riderChance    chance for each place on top of a disc, outside the cone, that a further disc stands there, inside
 *                       the dome of the disc under it. Larger discs have more such places. 0 keeps every disc in the ring
 * @param riderScale     the most a disc standing on another may be, as a share of that disc's radius
 * @param rim            how the top follows the ground (see {@link RimSettings}). Without it the top is the level's, the same
 *                       for every hole
 * @param upper          how the hole opens to the sky (see {@link UpperSettings}). Without it the clear air round the axis is
 *                       a cylinder
 */
public record ConeSettings(
        float topRadius, float baseRadius, float flare, float clearRadius, float layerSpacing, float layerJitter, float spacing,
        float stackChance, float ceilingMargin, float baseClearance, float hangChance, float outerRadius, float riderChance,
        float riderScale, Optional<RimSettings> rim, Optional<UpperSettings> upper) {

    public static final MapCodec<ConeSettings> MAP_CODEC = RecordCodecBuilder.<ConeSettings>mapCodec(i -> i.group(
            Codec.floatRange(8, 512).fieldOf("top_radius").forGetter(ConeSettings::topRadius),
            Codec.floatRange(8, 2048).fieldOf("base_radius").forGetter(ConeSettings::baseRadius),
            Codec.floatRange(0.5F, 4).fieldOf("flare").forGetter(ConeSettings::flare),
            Codec.floatRange(2, 64).fieldOf("clear_radius").forGetter(ConeSettings::clearRadius),
            Codec.floatRange(8, 128).fieldOf("layer_spacing").forGetter(ConeSettings::layerSpacing),
            Codec.floatRange(0, 0.8F).fieldOf("layer_jitter").forGetter(ConeSettings::layerJitter),
            Codec.floatRange(16, 512).fieldOf("spacing").forGetter(ConeSettings::spacing),
            Codec.floatRange(0, 1).fieldOf("stack_chance").forGetter(ConeSettings::stackChance),
            Codec.floatRange(0, 128).fieldOf("ceiling_margin").forGetter(ConeSettings::ceilingMargin),
            Codec.floatRange(-1024, 128).fieldOf("base_clearance").forGetter(ConeSettings::baseClearance),
            Codec.floatRange(0, 1).fieldOf("hang_chance").forGetter(ConeSettings::hangChance),
            Codec.floatRange(32, 2048).fieldOf("outer_radius").forGetter(ConeSettings::outerRadius),
            Codec.floatRange(0, 1).fieldOf("rider_chance").forGetter(ConeSettings::riderChance),
            Codec.floatRange(0.2F, 1).fieldOf("rider_scale").forGetter(ConeSettings::riderScale),
            // These two make 16 fields, which is all the codec builder takes: the next cone setting goes inside one of them.
            RimSettings.CODEC.optionalFieldOf("rim").forGetter(ConeSettings::rim),
            UpperSettings.CODEC.optionalFieldOf("upper").forGetter(ConeSettings::upper)
    ).apply(i, ConeSettings::new)).validate(ConeSettings::validate);
    public static final Codec<ConeSettings> CODEC = MAP_CODEC.codec();

    /** The widest the clear air round the axis is at any height, which is at the top. */
    public float widestClearRadius() {
        return upper.map(UpperSettings::clearRadius).orElse(clearRadius);
    }

    private static DataResult<ConeSettings> validate(ConeSettings c) {
        if (c.upper.isPresent() && c.upper.get().clearRadius() < c.clearRadius) {
            return DataResult.error(() -> "upper.clear_radius must not be below clear_radius, since the clear air widens towards the top");
        }
        if (c.widestClearRadius() >= c.topRadius) {
            return DataResult.error(() -> "clear_radius and upper.clear_radius must be below top_radius, or there is no room at the top");
        }
        if (c.topRadius > c.baseRadius) {
            return DataResult.error(() -> "top_radius must not exceed base_radius, since the cone widens towards the floor");
        }
        return DataResult.success(c);
    }
}

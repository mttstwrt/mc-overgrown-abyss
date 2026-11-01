package dev.syrval.verdantdeep.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * How a cone's top follows the ground. Without this every hole has the level's fixed {@code top} and is a bore from there up.
 * With it each hole has a lip of its own, under the ground round its mouth and never above {@code top}, and above the lip it
 * opens as a bowl. Its layers of discs are then spread evenly between the lowest and the lip.
 *
 * @param minAboveSea blocks above the level's sea level that the ground must stand all round the mouth. A cell with lower
 *                    ground there holds no hole, so none opens in a plain or on a shore
 * @param dip         blocks between the ground the lip is under and the lip, and between the ground on each side of the
 *                    mouth and where the bowl starts on that side
 * @param lowShare    the share of the mouth's edge whose ground may be lower than the lip. At 0 the lip is under the lowest
 *                    ground round the mouth, at 0.5 under the middle ground, at 1 under the highest. A higher lip is a deeper
 *                    hole with more layers of discs; on its downhill side the mouth's edge is then the ground itself, lower
 *                    than the lip, and the top layers are left out there
 * @param topRoom     blocks between the top layer's platforms and the highest a dome may reach, so that the top layer has
 *                    domes however the lip's height divides into layers
 * @param collar      the bowl round the mouth
 */
public record RimSettings(int minAboveSea, float dip, float lowShare, float topRoom, Collar collar) {

    public static final MapCodec<RimSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(-2048, 2048).fieldOf("min_above_sea").forGetter(RimSettings::minAboveSea),
            Codec.floatRange(0, 32).fieldOf("dip").forGetter(RimSettings::dip),
            Codec.floatRange(0, 1).fieldOf("low_share").forGetter(RimSettings::lowShare),
            Codec.floatRange(4, 256).fieldOf("top_room").forGetter(RimSettings::topRoom),
            Collar.CODEC.fieldOf("collar").forGetter(RimSettings::collar)
    ).apply(i, RimSettings::new));
    public static final Codec<RimSettings> CODEC = MAP_CODEC.codec();

    /**
     * The bowl a cone opens into at its entrance. It joins the cone {@code depth} blocks below the lip and rises
     * by {@code height} blocks at {@code width} blocks out, and on beyond that. On an uphill side the difference
     * between the lip and that side's edge is eased in over {@code width}, so the entrance slopes down to the lip rather
     * than leaving a vertical wall between the lip and the higher ground.
     *
     * @param width               blocks out from the mouth at which the surface has risen by {@code height}
     * @param height              blocks the surface rises over {@code width}
     * @param profile             how the rise is spread: 1 is a straight slope, above 1 the surface leaves the mouth level
     *                            and steepens outwards
     * @param roughness           blocks the surface may lie above or below that, which makes the bowl's outline irregular
     * @param roughnessWavelength blocks from one rise of the roughness to the next
     * @param depth               blocks below the lip where the approach joins the cone; 0 keeps older packs' shallow bowl
     * @param bottomVariation     maximum seeded lift of the lower join, allowing a short drop to the upper platforms
     * @param weathering          strength in blocks of broad erosion and smaller gullies, fading in from the lower join
     * @param discBands           maximum extra arcs of terrain-following platforms out across the entrance
     * @param surfaceThemes       biome keys allowed for discs exposed to the entrance; empty leaves themes unrestricted
     * @param surfaceSizeBias     how strongly surface-disc sizes favor the largest radius, from 0 to 1
     * @param discSpacing         spacing around bowl shelf groups; 0 follows the cone's ordinary disc spacing
     * @param surfaceBowlDepth    maximum rise of a bowl shelf's rim above its middle, keeping feeder streams shallow
     * @param discLayerSpacing    height between surface tiers; 0 follows the cone's ordinary layer spacing
     * @param discGapShare        fraction of a tier's circumference left as a shared open wedge
     */
    public record Collar(float width, float height, float profile, float roughness, float roughnessWavelength, float depth,
                         float bottomVariation, float weathering, int discBands,
                         List<ResourceKey<Biome>> surfaceThemes, float surfaceSizeBias, float discSpacing, float surfaceBowlDepth,
                         float discLayerSpacing, float discGapShare) {

        public Collar {
            surfaceThemes = List.copyOf(surfaceThemes);
        }

        public Collar(float width, float height, float profile, float roughness, float roughnessWavelength, float depth,
                      float bottomVariation, float weathering, int discBands,
                      List<ResourceKey<Biome>> surfaceThemes, float surfaceSizeBias, float discSpacing, float surfaceBowlDepth) {
            this(width, height, profile, roughness, roughnessWavelength, depth, bottomVariation, weathering, discBands,
                    surfaceThemes, surfaceSizeBias, discSpacing, surfaceBowlDepth, 0, 0);
        }

        public Collar(float width, float height, float profile, float roughness, float roughnessWavelength, float depth,
                      float bottomVariation, float weathering, int discBands,
                      List<ResourceKey<Biome>> surfaceThemes, float surfaceSizeBias) {
            this(width, height, profile, roughness, roughnessWavelength, depth, bottomVariation, weathering, discBands,
                    surfaceThemes, surfaceSizeBias, 0, 16);
        }

        public Collar(float width, float height, float profile, float roughness, float roughnessWavelength, float depth,
                      float bottomVariation, float weathering, int discBands) {
            this(width, height, profile, roughness, roughnessWavelength, depth, bottomVariation, weathering, discBands, List.of(), 0);
        }

        public Collar(float width, float height, float profile, float roughness, float roughnessWavelength, float depth) {
            this(width, height, profile, roughness, roughnessWavelength, depth, 0, 0, 0);
        }

        public Collar(float width, float height, float profile, float roughness, float roughnessWavelength) {
            this(width, height, profile, roughness, roughnessWavelength, 0);
        }

        public static final MapCodec<Collar> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(4, 256).fieldOf("width").forGetter(Collar::width),
                Codec.floatRange(1, 256).fieldOf("height").forGetter(Collar::height),
                // Under 1 the surface would leave the mouth vertically.
                Codec.floatRange(1, 4).fieldOf("profile").forGetter(Collar::profile),
                Codec.floatRange(0, 16).fieldOf("roughness").forGetter(Collar::roughness),
                Codec.floatRange(4, 128).fieldOf("roughness_wavelength").forGetter(Collar::roughnessWavelength),
                Codec.floatRange(0, 128).optionalFieldOf("depth", 0F).forGetter(Collar::depth),
                Codec.floatRange(0, 64).optionalFieldOf("bottom_variation", 0F).forGetter(Collar::bottomVariation),
                Codec.floatRange(0, 16).optionalFieldOf("weathering", 0F).forGetter(Collar::weathering),
                Codec.intRange(0, 6).optionalFieldOf("disc_bands", 0).forGetter(Collar::discBands),
                ResourceKey.codec(Registries.BIOME).listOf().optionalFieldOf("surface_themes", List.of()).forGetter(Collar::surfaceThemes),
                Codec.floatRange(0, 1).optionalFieldOf("surface_size_bias", 0F).forGetter(Collar::surfaceSizeBias),
                Codec.floatRange(0, 512).optionalFieldOf("disc_spacing", 0F).forGetter(Collar::discSpacing),
                Codec.floatRange(0, 16).optionalFieldOf("surface_bowl_depth", 16F).forGetter(Collar::surfaceBowlDepth),
                Codec.floatRange(0, 128).optionalFieldOf("disc_layer_spacing", 0F).forGetter(Collar::discLayerSpacing),
                Codec.floatRange(0, 0.5F).optionalFieldOf("disc_gap_share", 0F).forGetter(Collar::discGapShare)
        ).apply(i, Collar::new));
        public static final Codec<Collar> CODEC = MAP_CODEC.codec();

        /** How far the surface is above the mouth's edge {@code out} blocks out from the mouth, before its roughness. */
        double riseAt(double out) {
            return height * Math.pow(out / width, profile);
        }

        /** How steeply the surface rises there, in blocks up for each block out. */
        double slopeAt(double out) {
            return height * profile * Math.pow(out / width, profile - 1) / width;
        }
    }
}

package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
     * The bowl a cone opens into above its lip. Outside the mouth the ground is opened only above a surface that rises from
     * the mouth's edge: by {@code height} blocks at {@code width} blocks out, and on beyond that. The edge is the lip, or on
     * the uphill side of a slope the ground there, so the hole's wall runs up to the ground on every side and only the ground
     * right at the mouth is cut back, to a slope and never to a wall.
     *
     * @param width               blocks out from the mouth at which the surface has risen by {@code height}
     * @param height              blocks the surface rises over {@code width}
     * @param profile             how the rise is spread: 1 is a straight slope, above 1 the surface leaves the mouth level
     *                            and steepens outwards
     * @param roughness           blocks the surface may lie above or below that, which makes the bowl's outline irregular
     * @param roughnessWavelength blocks from one rise of the roughness to the next
     */
    public record Collar(float width, float height, float profile, float roughness, float roughnessWavelength) {

        public static final MapCodec<Collar> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(4, 256).fieldOf("width").forGetter(Collar::width),
                Codec.floatRange(1, 256).fieldOf("height").forGetter(Collar::height),
                // Under 1 the surface would leave the mouth vertically.
                Codec.floatRange(1, 4).fieldOf("profile").forGetter(Collar::profile),
                Codec.floatRange(0, 16).fieldOf("roughness").forGetter(Collar::roughness),
                Codec.floatRange(4, 128).fieldOf("roughness_wavelength").forGetter(Collar::roughnessWavelength)
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

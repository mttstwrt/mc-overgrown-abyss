package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The upper part of a cone, where it opens to the sky.
 *
 * @param clearRadius radius of the clear air round the axis at the top of the hole. The clear air is then a cone of its own:
 *                    the cone's {@code clear_radius} at the floor, widening evenly to this at the top, so that the discs
 *                    frame an opening instead of covering the mouth
 */
public record UpperSettings(float clearRadius) {

    public static final MapCodec<UpperSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(2, 256).fieldOf("clear_radius").forGetter(UpperSettings::clearRadius)
    ).apply(i, UpperSettings::new));
    public static final Codec<UpperSettings> CODEC = MAP_CODEC.codec();
}

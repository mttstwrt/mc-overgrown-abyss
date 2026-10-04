package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Rock arches that span a ravine. Only ravines at least {@code minSize} (0 to 1) of the way from the smallest to the
 * largest get any, so a small hole stays open. Heights are fractions of the span between the cavern roof and the
 * rim; keep {@code maxHeight} below the terrain surface or the upper arches sit in open air, where the carve cannot
 * add rock.
 */
public record RavineBridges(int maxCount, float minSize, int width, int thickness, float minHeight, float maxHeight) {

    public static final MapCodec<RavineBridges> MAP_CODEC = RecordCodecBuilder.<RavineBridges>mapCodec(i -> i.group(
            Codec.intRange(0, 16).fieldOf("max_count").forGetter(RavineBridges::maxCount),
            Codec.floatRange(0, 1).fieldOf("min_size").forGetter(RavineBridges::minSize),
            Codec.intRange(1, 64).fieldOf("width").forGetter(RavineBridges::width),
            Codec.intRange(1, 64).fieldOf("thickness").forGetter(RavineBridges::thickness),
            Codec.floatRange(0, 1).fieldOf("min_height").forGetter(RavineBridges::minHeight),
            Codec.floatRange(0, 1).fieldOf("max_height").forGetter(RavineBridges::maxHeight)
    ).apply(i, RavineBridges::new)).validate(bridges -> bridges.minHeight > bridges.maxHeight
            ? DataResult.error(() -> "min_height must not exceed max_height")
            : DataResult.success(bridges));
    public static final Codec<RavineBridges> CODEC = MAP_CODEC.codec();
}

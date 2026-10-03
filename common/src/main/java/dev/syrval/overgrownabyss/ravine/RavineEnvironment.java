package dev.syrval.overgrownabyss.ravine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * How ravines relate to the world around them: biomes they must not open under, and the biome the cavern takes.
 * Kept apart from the shape numbers so both stay inside the codec's field limit.
 */
public record RavineEnvironment(TagKey<Biome> forbiddenBiomes, Optional<ResourceKey<Biome>> cavernBiome) {
    public static final MapCodec<RavineEnvironment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TagKey.hashedCodec(Registries.BIOME).fieldOf("forbidden_biomes").forGetter(RavineEnvironment::forbiddenBiomes),
            ResourceKey.codec(Registries.BIOME).optionalFieldOf("cavern_biome").forGetter(RavineEnvironment::cavernBiome)
    ).apply(i, RavineEnvironment::new));
    public static final Codec<RavineEnvironment> CODEC = MAP_CODEC.codec();
}

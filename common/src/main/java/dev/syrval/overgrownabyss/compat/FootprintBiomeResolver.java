package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.RavineFootprint;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;

/**
 * Gives each themed disc and the cavern their own biomes and leaves every other cell to the level's biome source. A biome set
 * here need not be one the source can produce: it colours grass, leaves and water, decides what spawns, and keeps the
 * surrounding biome's features out, but vanilla never runs its own feature list.
 */
public record FootprintBiomeResolver(BiomeResolver delegate, RavineFootprint footprint) implements BiomeResolver {

    public static BiomeResolver wrap(BiomeResolver resolver, RavineFootprint footprint) {
        return footprint == RavineFootprint.NONE ? resolver : new FootprintBiomeResolver(resolver, footprint);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        // Biomes are stored per 4x4x4 cell; test at the cell's centre.
        int x = QuartPos.toBlock(quartX) + 2;
        int y = QuartPos.toBlock(quartY) + 2;
        int z = QuartPos.toBlock(quartZ) + 2;
        return footprint.biomeAt(x, y, z).orElseGet(() -> delegate.getNoiseBiome(quartX, quartY, quartZ, sampler));
    }
}

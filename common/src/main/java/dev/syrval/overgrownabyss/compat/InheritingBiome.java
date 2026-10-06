package dev.syrval.overgrownabyss.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** A biome that can be told to take its spawn lists from another (see BiomeMixin). */
public interface InheritingBiome {
    void overgrownAbyss$inheritFrom(Holder<Biome> parent);
}

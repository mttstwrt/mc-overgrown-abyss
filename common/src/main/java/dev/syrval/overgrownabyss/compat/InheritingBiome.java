package dev.syrval.overgrownabyss.compat;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;

/** A biome that can be told to take its spawns from another, less the mobs named (see BiomeMixin and InheritedSpawns). */
public interface InheritingBiome {
    void overgrownAbyss$inheritFrom(Holder<Biome> parent, Set<EntityType<?>> withoutSpawns);
}

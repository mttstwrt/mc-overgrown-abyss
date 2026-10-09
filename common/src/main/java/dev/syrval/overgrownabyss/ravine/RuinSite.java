package dev.syrval.overgrownabyss.ravine;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * A place on a disc where a ruin stands: the block in the middle of its lowest layer, the pool its piece is drawn from, and
 * the seed of that draw, which is the site's own so that a ruin is the same whatever else its chunk holds.
 */
public record RuinSite(int x, int y, int z, ResourceKey<StructureTemplatePool> pool, long seed) {}

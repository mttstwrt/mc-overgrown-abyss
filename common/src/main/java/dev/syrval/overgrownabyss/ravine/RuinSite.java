package dev.syrval.overgrownabyss.ravine;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A place on a disc where a ruin stands: the block in the middle of its lowest layer, the pool its piece is from and which of
 * the pool's elements it is (see {@link RuinPieces.Piece}), the seed its turn is drawn from, which is the site's own so
 * that a ruin is the same whatever else its chunk holds, and the loot table of its chests if its theme gives it one (see
 * {@link DiscRuins.Loot}).
 */
public record RuinSite(int x, int y, int z, ResourceKey<StructureTemplatePool> pool, int element, long seed, Optional<ResourceKey<LootTable>> loot) {}

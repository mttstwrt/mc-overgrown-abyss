package dev.syrval.overgrownabyss.compat;

import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * The spawn settings of a disc biome that inherits from another: the parent's, as changed by the disc biome's own file. An
 * entry in the disc biome's file is added; if the parent has an entry for the same mob in the same category, the disc biome's
 * takes its place. Mobs the theme names are left out of what is inherited. Spawn costs follow the same rule, and the chance of
 * creatures at chunk generation is the parent's.
 *
 * @param parent   the parent's settings these were made from
 * @param own      the disc biome's own settings they were made from
 * @param settings the result
 */
public record InheritedSpawns(MobSpawnSettings parent, MobSpawnSettings own, MobSpawnSettings settings) {

    public static InheritedSpawns of(MobSpawnSettings parent, MobSpawnSettings own, Set<EntityType<?>> without) {
        return new InheritedSpawns(parent, own, merge(parent, own, without));
    }

    /**
     * Whether these were made from exactly these two objects. A loader that changes a biome hands out a new settings object
     * for it, so this is how such a change is noticed without merging on every read.
     */
    public boolean madeFrom(MobSpawnSettings parent, MobSpawnSettings own) {
        return this.parent == parent && this.own == own;
    }

    static MobSpawnSettings merge(MobSpawnSettings parent, MobSpawnSettings own, Set<EntityType<?>> without) {
        var merged = new MobSpawnSettings.Builder().creatureGenerationProbability(parent.getCreatureProbability());
        for (MobCategory category : MobCategory.values()) {
            List<MobSpawnSettings.SpawnerData> ours = own.getMobs(category).unwrap();
            for (MobSpawnSettings.SpawnerData inherited : parent.getMobs(category).unwrap()) {
                boolean replaced = ours.stream().anyMatch(entry -> entry.type == inherited.type);
                if (!replaced && !without.contains(inherited.type)) {
                    merged.addSpawn(category, inherited);
                }
            }
            ours.forEach(entry -> merged.addSpawn(category, entry));
        }
        // A biome's costs can only be asked for one mob at a time.
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            MobSpawnSettings.MobSpawnCost ours = own.getMobSpawnCost(type);
            MobSpawnSettings.MobSpawnCost cost = ours != null ? ours : parent.getMobSpawnCost(type);
            if (cost != null) {
                merged.addMobCharge(type, cost.charge(), cost.energyBudget());
            }
        }
        return merged.build();
    }
}

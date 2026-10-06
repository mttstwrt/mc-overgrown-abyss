package dev.syrval.overgrownabyss.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.junit.jupiter.api.Test;

/** A disc biome's own spawns as a change to the ones it inherits. */
class InheritedSpawnsTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static final MobSpawnSettings PARENT = new MobSpawnSettings.Builder()
            .creatureGenerationProbability(0.3F)
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PIG, 10, 4, 4))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 40, 1, 2))
            .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.COW, 8, 4, 4))
            .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.ZOMBIE, 95, 4, 4))
            .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.WITCH, 5, 1, 1))
            .addMobCharge(EntityType.ENDERMAN, 1.0, 0.12)
            .addMobCharge(EntityType.SKELETON, 0.7, 0.15)
            .build();

    private static List<String> mobs(MobSpawnSettings settings, MobCategory category) {
        return settings.getMobs(category).unwrap().stream()
                .map(entry -> EntityType.getKey(entry.type).getPath() + ":" + entry.getWeight().asInt() + ":" + entry.minCount + "-" + entry.maxCount).toList();
    }

    @Test
    void aBiomeWithNothingOfItsOwnSpawnsExactlyWhatItsParentDoes() {
        MobSpawnSettings merged = InheritedSpawns.merge(PARENT, MobSpawnSettings.EMPTY, Set.of());
        for (MobCategory category : MobCategory.values()) {
            assertEquals(mobs(PARENT, category), mobs(merged, category), category.getName());
        }
        assertEquals(0.3F, merged.getCreatureProbability(), "the chance of creatures at chunk generation is the parent's");
        assertEquals(PARENT.getMobSpawnCost(EntityType.ENDERMAN), merged.getMobSpawnCost(EntityType.ENDERMAN));
        assertNull(merged.getMobSpawnCost(EntityType.PIG), "no cost where the parent has none");
    }

    @Test
    void itsOwnEntriesAreAddedAndReplaceTheParentsForTheSameMob() {
        MobSpawnSettings own = new MobSpawnSettings.Builder()
                .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.FOX, 6, 1, 2))
                .addSpawn(MobCategory.CREATURE, new MobSpawnSettings.SpawnerData(EntityType.PARROT, 3, 1, 1))
                .addMobCharge(EntityType.SKELETON, 2.0, 0.5)
                .addMobCharge(EntityType.FOX, 0.5, 0.1)
                .build();
        MobSpawnSettings merged = InheritedSpawns.merge(PARENT, own, Set.of());
        assertEquals(List.of("pig:10:4-4", "cow:8:4-4", "fox:6:1-2", "parrot:3:1-1"), mobs(merged, MobCategory.CREATURE),
                "the parent's others, then ours: the fox is new and the parrot is ours in place of the parent's");
        assertEquals(mobs(PARENT, MobCategory.MONSTER), mobs(merged, MobCategory.MONSTER), "a category we say nothing about is untouched");
        assertEquals(own.getMobSpawnCost(EntityType.SKELETON), merged.getMobSpawnCost(EntityType.SKELETON), "our cost in place of the parent's");
        assertEquals(own.getMobSpawnCost(EntityType.FOX), merged.getMobSpawnCost(EntityType.FOX));
        assertEquals(PARENT.getMobSpawnCost(EntityType.ENDERMAN), merged.getMobSpawnCost(EntityType.ENDERMAN));
    }

    @Test
    void mobsLeftOutAreTakenFromWhatIsInheritedButNotFromOurOwn() {
        MobSpawnSettings own = new MobSpawnSettings.Builder()
                .addSpawn(MobCategory.MONSTER, new MobSpawnSettings.SpawnerData(EntityType.WITCH, 1, 1, 1))
                .build();
        MobSpawnSettings merged = InheritedSpawns.merge(PARENT, own, Set.of(EntityType.PIG, EntityType.COW, EntityType.WITCH, EntityType.ALLAY));
        assertEquals(List.of("parrot:40:1-2"), mobs(merged, MobCategory.CREATURE), "pig and cow are gone, in whatever category they were");
        assertEquals(List.of("zombie:95:4-4", "witch:1:1-1"), mobs(merged, MobCategory.MONSTER), "the witch we list ourselves stays");
    }

    @Test
    void aMergeIsRememberedUntilEitherBiomeHandsOutDifferentSettings() {
        MobSpawnSettings own = new MobSpawnSettings.Builder().build();
        InheritedSpawns spawns = InheritedSpawns.of(PARENT, own, Set.of());
        assertTrue(spawns.madeFrom(PARENT, own));
        assertSame(spawns.settings(), spawns.settings());
        // A loader that has changed the parent hands out a new object for it, even if it holds the same entries.
        MobSpawnSettings changedParent = InheritedSpawns.merge(PARENT, MobSpawnSettings.EMPTY, Set.of());
        assertTrue(!spawns.madeFrom(changedParent, own) && !spawns.madeFrom(PARENT, new MobSpawnSettings.Builder().build()));
    }
}

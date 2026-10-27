package dev.syrval.verdantdeep.fabric;

import dev.syrval.verdantdeep.VerdantDeep;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;

public final class VerdantDeepFabric implements ModInitializer {
    private static final ResourceKey<LootTable> GOLDEN_APPLES =
            ResourceKey.create(Registries.LOOT_TABLE, VerdantDeep.id("injected/golden_apples"));

    @Override
    public void onInitialize() {
        VerdantDeep.init();
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (BuiltInLootTables.JUNGLE_TEMPLE.equals(key)) {
                tableBuilder.withPool(LootPool.lootPool().add(NestedLootTable.lootTableReference(GOLDEN_APPLES)));
            }
        });
    }
}

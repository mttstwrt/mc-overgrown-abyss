package dev.syrval.verdantdeep.mixin;

import dev.syrval.verdantdeep.compat.InheritedSpawns;
import dev.syrval.verdantdeep.compat.InheritingBiome;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spawn inheritance. A disc biome that inherits from another answers with that biome's spawn settings as changed by its own
 * (see {@link InheritedSpawns}). The parent's are read each time, so that whatever other mods have added to the parent spawns
 * on the disc too. This accessor is the one place every spawn list, cost and probability is read from, on both loaders.
 */
@Mixin(Biome.class)
abstract class BiomeMixin implements InheritingBiome {
    // Set when a level loads, before any chunk task runs; volatile keeps the hand-off to worker threads safe.
    @Unique
    private volatile Holder<Biome> verdantDeep$parent;
    @Unique
    private volatile Set<EntityType<?>> verdantDeep$withoutSpawns = Set.of();
    // The last merge, kept because spawning asks often. Two threads may both make one; either is right.
    @Unique
    private volatile InheritedSpawns verdantDeep$spawns;

    @Override
    public void verdantDeep$inheritFrom(Holder<Biome> parent, Set<EntityType<?>> withoutSpawns) {
        verdantDeep$withoutSpawns = Set.copyOf(withoutSpawns);
        verdantDeep$spawns = null;
        verdantDeep$parent = parent;
    }

    // At the return, so that what this biome would have answered by itself, with any loader's changes to it, is in hand.
    @Inject(method = "getMobSettings", at = @At("RETURN"), cancellable = true)
    private void verdantDeep$spawnAsTheParentDoes(CallbackInfoReturnable<MobSpawnSettings> cir) {
        Holder<Biome> parent = verdantDeep$parent;
        if (parent == null) {
            return;
        }
        MobSpawnSettings inherited = parent.value().getMobSettings();
        MobSpawnSettings own = cir.getReturnValue();
        InheritedSpawns spawns = verdantDeep$spawns;
        if (spawns == null || !spawns.madeFrom(inherited, own)) {
            spawns = InheritedSpawns.of(inherited, own, verdantDeep$withoutSpawns);
            verdantDeep$spawns = spawns;
        }
        cir.setReturnValue(spawns.settings());
    }
}

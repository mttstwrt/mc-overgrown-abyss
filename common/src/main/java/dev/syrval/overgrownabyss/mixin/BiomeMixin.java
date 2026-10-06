package dev.syrval.overgrownabyss.mixin;

import dev.syrval.overgrownabyss.compat.InheritingBiome;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spawn inheritance. A disc biome that inherits from another answers with that biome's spawn settings, read each time, so
 * that whatever other mods have added to the parent spawns on the disc too. This accessor is the one place every spawn
 * list, cost and probability is read from, on both loaders.
 */
@Mixin(Biome.class)
abstract class BiomeMixin implements InheritingBiome {
    // Set when a level loads, before any chunk task runs; volatile keeps the hand-off to worker threads safe.
    @Unique
    private volatile Holder<Biome> overgrownAbyss$parent;

    @Override
    public void overgrownAbyss$inheritFrom(Holder<Biome> parent) {
        overgrownAbyss$parent = parent;
    }

    @Inject(method = "getMobSettings", at = @At("HEAD"), cancellable = true)
    private void overgrownAbyss$spawnAsTheParentDoes(CallbackInfoReturnable<MobSpawnSettings> cir) {
        Holder<Biome> parent = overgrownAbyss$parent;
        if (parent != null) {
            cir.setReturnValue(parent.value().getMobSettings());
        }
    }
}

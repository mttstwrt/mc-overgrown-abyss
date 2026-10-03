package dev.syrval.overgrownabyss.ravine;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides once per ravine cell whether all of its sample columns are land, so ravines never open under an ocean or
 * river. The check needs the level's biome source and climate sampler, which only exist after the carve has been
 * built into the noise settings, so it is installed afterwards by the density hook, before any chunk is generated.
 */
final class LandGate {
    // Cells are few and each verdict costs several biome lookups; the map is safe to share between worker threads.
    private final ConcurrentHashMap<RavineCell, Boolean> verdicts = new ConcurrentHashMap<>();
    private volatile LandCheck check = LandCheck.EVERYWHERE;

    void use(LandCheck check) {
        this.check = check;
        verdicts.clear();
    }

    boolean allows(RavineCell cell) {
        return verdicts.computeIfAbsent(cell, c -> {
            LandCheck current = check;
            return c.samplePoints().stream().allMatch(column -> current.isLand(column.x(), column.z()));
        });
    }
}

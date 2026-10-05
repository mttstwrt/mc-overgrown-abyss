package dev.syrval.overgrownabyss.ravine;

import java.util.concurrent.ConcurrentHashMap;

/**
 * What the carve and the rock decide together, for tests, with each cell's discs built once like the carve does. The original
 * terrain is taken to be solid everywhere.
 */
final class Carved {
    private record Key(RavineSettings settings, RavineBounds bounds, RavineCell cell) {}

    private static final ConcurrentHashMap<Key, DiscLayout> LAYOUTS = new ConcurrentHashMap<>();

    private Carved() {}

    static DiscLayout layout(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        return LAYOUTS.computeIfAbsent(new Key(settings, bounds, cell), key -> DiscLayouts.of(settings, bounds, cell));
    }

    static double distance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        return RavineShape.signedDistance(settings, bounds, cell, layout(settings, bounds, cell), x, y, z);
    }

    static double rock(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        return RavineShape.rockDistance(settings, bounds, cell, layout(settings, bounds, cell), x, y, z);
    }

    /** The density is {@code max(min(original, carve), rock)}: solid if the carve leaves the point or the rock adds it. */
    static boolean solid(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        return distance(settings, bounds, cell, x, y, z) > 0 || rock(settings, bounds, cell, x, y, z) < 0;
    }
}

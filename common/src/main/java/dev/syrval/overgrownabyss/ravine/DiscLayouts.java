package dev.syrval.overgrownabyss.ravine;

/**
 * Builds the discs of one cell, once: they depend only on the settings, the heights the cell's hole lies between, the cell
 * and, for a cone whose top follows the ground, the ground over it.
 */
final class DiscLayouts {
    private DiscLayouts() {}

    /** The discs of a hole whose ground is nowhere lower than its top. */
    static DiscLayout of(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        return of(settings, bounds, cell, SurfaceProbe.SOLID);
    }

    static DiscLayout of(RavineSettings settings, RavineBounds bounds, RavineCell cell, SurfaceProbe ground) {
        return settings.cone().isPresent()
                ? new ConeDiscLayout(settings, settings.cone().get(), bounds, cell, ground)
                : new RavineDiscLayout(settings, bounds, cell);
    }
}

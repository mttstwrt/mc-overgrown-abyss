package dev.syrval.overgrownabyss.ravine;

/** Builds the discs of one cell, once: they depend only on the settings, the level's bounds and the cell. */
final class DiscLayouts {
    private DiscLayouts() {}

    static DiscLayout of(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        return settings.cone().isPresent()
                ? new ConeDiscLayout(settings, settings.cone().get(), bounds, cell)
                : new RavineDiscLayout(settings, bounds, cell);
    }
}

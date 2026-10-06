package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;

/**
 * The discs of one cell and the theme of each. Both depend only on the settings, the level's bounds and the cell, so they are
 * built once for a cell and then only read, which makes them safe to share between worker threads.
 */
record CellDiscs(DiscLayout layout, List<Optional<DiscTheme>> themes) {

    static CellDiscs of(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        DiscLayout layout = DiscLayouts.of(settings, bounds, cell);
        return new CellDiscs(layout, DiscThemes.assign(settings, cell, layout.discs()));
    }
}

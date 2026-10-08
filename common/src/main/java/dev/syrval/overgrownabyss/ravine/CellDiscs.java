package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The discs of one cell and the theme of each. Both depend only on the settings, the heights the cell's hole lies between, the
 * cell and the ground over it, so they are built once for a cell and then only read, which makes them safe to share between
 * worker threads.
 */
record CellDiscs(DiscLayout layout, List<Optional<DiscTheme>> themes) {

    /** The discs of a hole whose ground is nowhere lower than its top. */
    static CellDiscs of(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        return of(settings, bounds, cell, SurfaceProbe.SOLID);
    }

    static CellDiscs of(RavineSettings settings, RavineBounds bounds, RavineCell cell, SurfaceProbe ground) {
        DiscLayout layout = DiscLayouts.of(settings, bounds, cell, ground);
        return new CellDiscs(layout, DiscThemes.assign(settings, cell, layout.discs()));
    }

    /**
     * The disc a point belongs to, among those whose theme has a biome: the point is in the disc's dome or platform or within
     * {@code margin} of them. Where the spaces of several discs overlap, the highest disc has it, which is the one a rider
     * stands on rather than its host's.
     */
    OptionalInt ownerAt(DiscShape shape, double x, double y, double z, double margin) {
        List<Disc> discs = layout.discs();
        int owner = -1;
        for (int i = 0; i < discs.size(); i++) {
            Disc disc = discs.get(i);
            if ((owner < 0 || disc.floor() > discs.get(owner).floor())
                    && themes.get(i).flatMap(DiscTheme::biome).isPresent() && disc.biomeContains(shape, x, y, z, margin)) {
                owner = i;
            }
        }
        return owner < 0 ? OptionalInt.empty() : OptionalInt.of(owner);
    }
}

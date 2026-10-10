package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The discs of one cell, the theme of each, the ruins that stand on them, and the roots that wind among them. All depend only on the settings, the heights the
 * cell's hole lies between, the cell, the ground over it and the pieces the level has for ruins, so they are built once for a cell and then only read, which makes
 * them safe to share between worker threads.
 */
record CellDiscs(DiscLayout layout, List<Optional<DiscTheme>> themes, List<RuinSite> ruins, RootLayout roots) {

    /** The discs of a hole whose ground is nowhere lower than its top, in a level that has no pieces for ruins. */
    static CellDiscs of(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        return of(settings, bounds, cell, SurfaceProbe.SOLID, RuinPieces.NONE);
    }

    static CellDiscs of(RavineSettings settings, RavineBounds bounds, RavineCell cell, SurfaceProbe ground, RuinPieces pieces) {
        DiscLayout layout = DiscLayouts.of(settings, bounds, cell, ground);
        List<DiscTraits> traits = DiscTraits.of(settings, cell, layout.discs());
        List<Optional<DiscTheme>> themes = DiscThemes.assign(settings, cell, traits);
        return new CellDiscs(
                layout, themes, DiscRuinSites.of(settings, bounds, cell, layout, themes, traits, pieces),
                RootLayout.of(settings, bounds, cell, layout, ground));
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

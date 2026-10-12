package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The discs of one cell, the theme of each, the ruins and the troves that stand on them, and the roots that wind among them. All depend only on the settings, the
 * heights the cell's hole lies between, the cell, the ground over it and the pieces the level has for ruins, so they are built once for a cell and then only read,
 * which makes them safe to share between worker threads.
 */
record CellDiscs(
        DiscLayout layout, List<Optional<DiscTheme>> themes, List<RuinSite> ruins, List<RuinSite> troves, List<DiscGround> grounds, RootLayout roots,
        DiscFalls falls) {

    /** The discs of a hole whose ground is nowhere lower than its top, in a level that has no pieces for ruins. */
    static CellDiscs of(RavineSettings settings, RavineBounds bounds, RavineCell cell) {
        return of(settings, bounds, cell, SurfaceProbe.SOLID, RuinPieces.NONE);
    }

    static CellDiscs of(RavineSettings settings, RavineBounds bounds, RavineCell cell, SurfaceProbe ground, RuinPieces pieces) {
        DiscLayout layout = DiscLayouts.of(settings, bounds, cell, ground);
        List<DiscTraits> traits = DiscTraits.of(settings, cell, layout.discs());
        List<Optional<DiscTheme>> themes = DiscThemes.assign(settings, cell, traits);
        RootLayout roots = RootLayout.of(settings, bounds, cell, layout, ground);
        DiscRuinSites.Stood stood = DiscRuinSites.onDiscs(settings, bounds, cell, layout, themes, traits, pieces, roots);
        List<List<DiscGround.Flat>> rounds = DiscGround.roundsOf(layout.discs().size(), stood.all());
        // Falls keep clear of ruins, troves and roots, and the ground lies level round all of them.
        DiscFalls falls = DiscFalls.of(settings, bounds, cell, layout, themes, traits, rounds, roots);
        return new CellDiscs(
                layout, themes, sitesOf(stood.ruins()), sitesOf(stood.troves()), DiscGround.of(cell, layout.discs(), themes, rounds, falls), roots, falls);
    }

    private static List<RuinSite> sitesOf(List<DiscRuinSites.Placed> stood) {
        return stood.stream().map(DiscRuinSites.Placed::site).toList();
    }

    /** One of the two lists of pieces that stand on the cell's discs. */
    List<RuinSite> sites(DiscSites which) {
        return which == DiscSites.RUINS ? ruins : troves;
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

package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** A disc's lava: that it is left out near what the game would have it set alight, and that what grows over it starts only where it lies. */
class DiscLavaTest {
    static {
        MinecraftBootstrap.init();
    }

    private record At(int x, int y, int z) {}

    /** The mod's own settings with the obsidian trees its lava disc had: the lava is the same, and a growth over it is still to be had by a pack. */
    private static RavineSettings withTreesOverLava() throws Exception {
        RavineSettings base = DiscThemeTest.shipped();
        var themes = new ArrayList<>(base.discThemes());
        DiscTheme lava = themes.get(4);
        var tree = new DiscTheme.Growth(DiscThemeTest.feature("overgrown_abyss:disc/obsidian_tree"), 30, DiscTheme.Surface.LAVA, Optional.empty(), Optional.empty());
        themes.set(4, new DiscTheme(lava.biome(), lava.inherits(), lava.weight(), lava.byHeight(), lava.byDistance(), lava.bySize(), lava.only(), lava.palette(),
                lava.water(), List.of(tree), lava.wildness(), lava.ground(), lava.ruins(), lava.troves(), lava.rootWood()));
        return new RavineSettings(base.salt(), base.cellSize(), 1F, base.sizeBias(), base.floor(), base.top(), base.cavernRadius(), base.cavernHeight(),
                base.edgeFalloff(), base.discs(), themes, base.environment(), base.wallNoise(), base.ravine(), base.cone(), base.dressing());
    }

    @Test
    void theModsOwnLavaKeepsClearOfTheGroundOfOtherDiscsAndOfRoots() throws Exception {
        RavineSettings settings = withTreesOverLava();
        int ponds = 0;
        int written = 0;
        int trees = 0;
        int lavaDiscs = 0;
        for (int cz = 0; cz < 6; cz++) {
            RavineCell cell = RavineCells.at(11L, settings, 0, cz).orElseThrow();
            CellDiscs cellDiscs = CellDiscs.of(settings, DiscThemeTest.LEVEL, cell);
            List<Disc> discs = cellDiscs.layout().discs();
            var chunks = new HashSet<At>();
            for (int i = 0; i < discs.size(); i++) {
                Optional<DiscTheme> theme = cellDiscs.themes().get(i);
                if (theme.isEmpty() || !theme.get().holdsLava()) {
                    continue;
                }
                lavaDiscs++;
                Disc d = discs.get(i);
                DiscWater water = theme.get().water().orElseThrow();
                for (int x = (int) Math.floor(d.x() - d.radius()); x <= d.x() + d.radius(); x++) {
                    for (int z = (int) Math.floor(d.z() - d.radius()); z <= d.z() + d.radius(); z++) {
                        ponds += water.depthAt(d, cell.hash(), i, x, z) > 0 ? 1 : 0;
                        chunks.add(new At(Math.floorDiv(x, 16), 0, Math.floorDiv(z, 16)));
                    }
                }
            }
            int clear = settings.discThemes().get(4).water().orElseThrow().clear();
            var lava = new HashSet<At>();
            for (At chunk : chunks) {
                DiscBlocks.forEachWater(cell, cellDiscs, chunk.x() * 16, chunk.z() * 16, (x, surface, z, depth, fluid) -> {
                    if (fluid == DiscWater.Fluid.LAVA) {
                        lava.add(new At(x, surface, z));
                    }
                });
            }
            written += lava.size();
            for (At at : lava) {
                for (int j = 0; j < discs.size(); j++) {
                    Disc other = discs.get(j);
                    double fromAxis = Math.hypot(at.x() - other.x(), at.z() - other.z());
                    boolean besideIt = fromAxis < other.radius() + clear && Math.abs(other.topBlockAt(fromAxis) - at.y()) <= 6;
                    // The disc the lava is on is level with it and is not kept from; nor is another that holds lava.
                    assertTrue(!besideIt || cellDiscs.themes().get(j).orElseThrow().holdsLava(), () -> "lava at " + at + " by the ground of " + other);
                }
                // Every knot of the hole, not only those of the chunks round the lava's own.
                for (Root root : cellDiscs.roots().roots()) {
                    for (Root.Knot knot : root.knots()) {
                        boolean inReach = knot.y() > at.y() - knot.radius() - 1 && knot.y() < at.y() + 4 + knot.radius()
                                && Math.hypot(knot.x() - at.x(), knot.z() - at.z()) < knot.radius() + clear;
                        assertTrue(!inReach, () -> "lava at " + at + " by a root at " + knot);
                    }
                }
            }
            for (At chunk : chunks) {
                int[] found = {0};
                DiscGrowth.forEach(settings, cell, cellDiscs, chunk.x() * 16, chunk.z() * 16, (x, y, z, feature, on) -> {
                    if (on == DiscTheme.Surface.LAVA) {
                        assertTrue(lava.contains(new At(x, y - 1, z)), () -> "a tree at " + x + " " + y + " " + z + " with no lava under it");
                        found[0]++;
                    }
                });
                trees += found[0];
            }
        }
        assertTrue(lavaDiscs >= 6, lavaDiscs + " lava discs in six holes");
        // The rule has to bite, since discs of one ring run into each other, and has to leave lava to look at.
        assertTrue(written > 0.3 * ponds && written < 0.8 * ponds, written + " columns of lava of " + ponds + " of pond on " + lavaDiscs + " discs");
        assertTrue(trees > lavaDiscs, trees + " places for trees on " + lavaDiscs + " discs");
    }
}

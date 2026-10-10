package dev.syrval.overgrownabyss.ravine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.junit.jupiter.api.Test;

/** The roots of a hole: where they run, what they keep out of, and where they end. */
class RootLayoutTest {
    static {
        MinecraftBootstrap.init();
    }

    static final RootWood WOOD = new RootWood(BlockStateProvider.simple(Blocks.JUNGLE_WOOD), Optional.empty());
    static final RootSettings ROOTS = new RootSettings(
            new RootSettings.Great(new InclusiveRange<>(3, 5), 4.5F, 3.5F, 0.5F, 0.5F),
            new RootSettings.Crossing(new InclusiveRange<>(1, 2), 3.5F),
            new RootSettings.Branches(150, new DiscTheme.Ramp(3, 0.5F), 0.65F, 2, 60),
            new RootSettings.Links(0.5F, 2, 0.6F),
            60, 0.6F, 1.5F, WOOD, 48, 16);
    /** The cone the mod ships, with roots, in a level of vanilla's height and in a taller one. */
    static final RavineSettings SETTINGS = rooted(ConeShapeTest.LOW_SETTINGS, ROOTS);
    static final RavineBounds VANILLA = new RavineBounds(-40, 100);
    static final RavineBounds TALL = new RavineBounds(-104, 190);
    static final int CELLS = 4;

    static RavineSettings rooted(RavineSettings s, RootSettings roots) {
        return new RavineSettings(s.salt(), s.cellSize(), s.chance(), s.sizeBias(), s.floor(), s.top(), s.cavernRadius(), s.cavernHeight(), s.edgeFalloff(),
                s.discs(), s.discThemes(), s.environment(), s.wallNoise(), s.ravine(), s.cone(), Optional.of(roots));
    }

    private static RavineCell cell(int cz) {
        return RavineCells.at(7L, SETTINGS, 1, cz).orElseThrow();
    }

    private static RootSpace space(RavineBounds bounds, RavineCell cell) {
        return new RootSpace(SETTINGS, SETTINGS.cone().orElseThrow(), bounds, cell, Carved.layout(SETTINGS, bounds, cell), new HoleGround(SurfaceProbe.SOLID, bounds.topY()));
    }

    private static List<Root> roots(RavineBounds bounds, RavineCell cell) {
        return RootLayout.of(SETTINGS, bounds, cell, Carved.layout(SETTINGS, bounds, cell), SurfaceProbe.SOLID).roots();
    }

    private static long count(List<Root> roots, Root.Kind kind) {
        return roots.stream().filter(root -> root.kind() == kind).count();
    }

    @Test
    void aHolesRootsComeOutTheSameTwice() {
        for (int cz = 0; cz < CELLS; cz++) {
            assertEquals(roots(VANILLA, cell(cz)), roots(VANILLA, cell(cz)));
        }
        assertTrue(!roots(VANILLA, cell(0)).equals(roots(VANILLA, cell(1))), "and each hole has its own");
    }

    @Test
    void aHoleHasRootsOfEveryKindAsManyAsItsSettingsSay() {
        long links = 0;
        long branches = 0;
        long crossing = 0;
        for (RavineBounds bounds : List.of(VANILLA, TALL)) {
            for (int cz = 0; cz < CELLS; cz++) {
                List<Root> roots = roots(bounds, cell(cz));
                long great = count(roots, Root.Kind.GREAT);
                assertTrue(great >= 3 && great <= 5, great + " great roots");
                assertTrue(count(roots, Root.Kind.CROSSING) <= 2, "no more crossing roots than the settings allow");
                assertTrue(roots.size() <= RootRoutes.MAX_ROOTS);
                crossing += count(roots, Root.Kind.CROSSING);
                links += count(roots, Root.Kind.LINK);
                branches += count(roots, Root.Kind.BRANCH);
            }
        }
        assertTrue(crossing >= CELLS, crossing + " crossing roots");
        assertTrue(links > 10 * CELLS, links + " links");
        assertTrue(branches > 20 * CELLS, branches + " branches");
    }

    @Test
    void everyKnotIsInsideTheHolesReachAndNoFurtherThanABlockOrSoFromTheLast() {
        for (RavineBounds bounds : List.of(VANILLA, TALL)) {
            for (int cz = 0; cz < CELLS; cz++) {
                RavineCell c = cell(cz);
                for (Root root : roots(bounds, c)) {
                    Root.Knot last = root.first();
                    for (Root.Knot knot : root.knots()) {
                        assertTrue(c.distanceToCentre(knot.x(), knot.z()) + knot.radius() <= SETTINGS.maxReach(), root.kind() + " leaves the hole's reach");
                        assertTrue(knot.y() <= bounds.topY() && knot.y() >= bounds.floorY() - 24, root.kind() + " at y=" + knot.y());
                        assertTrue(knot.distanceTo(last.x(), last.y(), last.z()) <= 2, root.kind() + " has a gap");
                        assertTrue(knot.radius() >= 0.75 && knot.radius() <= 4.5 + 1e-9, "a radius of " + knot.radius());
                        last = knot;
                    }
                }
            }
        }
    }

    @Test
    void onlyCrossingRootsEnterTheClearAirRoundTheAxis() {
        int entering = 0;
        for (RavineBounds bounds : List.of(VANILLA, TALL)) {
            for (int cz = 0; cz < CELLS; cz++) {
                RavineCell c = cell(cz);
                RootSpace space = space(bounds, c);
                for (Root root : roots(bounds, c)) {
                    boolean enters = root.knots().stream().anyMatch(knot -> space.fromAxis(knot.x(), knot.z()) - knot.radius() < space.clear(knot.y()));
                    if (root.kind() == Root.Kind.CROSSING) {
                        entering += enters ? 1 : 0;
                    } else {
                        assertTrue(!enters, "a " + root.kind() + " root is in the clear air");
                    }
                }
            }
        }
        assertTrue(entering >= CELLS, entering + " crossing roots in the clear air");
    }

    @Test
    void aGreatRootRunsFromTheWallUnderTheMouthRoundTheHoleToItsFloor() {
        for (RavineBounds bounds : List.of(VANILLA, TALL)) {
            for (int cz = 0; cz < CELLS; cz++) {
                RootSpace space = space(bounds, cell(cz));
                for (Root root : roots(bounds, cell(cz))) {
                    if (root.kind() != Root.Kind.GREAT) {
                        continue;
                    }
                    Root.Knot first = root.first();
                    assertTrue(space.air(first.x(), first.y(), first.z()) < 0, "it starts in the wall");
                    assertTrue(first.y() > bounds.topY() - 40, "under the mouth, not at y=" + first.y());
                    assertTrue(root.last().y() < bounds.floorY(), "it ends under the floor, not at y=" + root.last().y());
                    double round = 0;
                    for (int i = 1; i < root.knots().size(); i++) {
                        Root.Knot a = root.knots().get(i - 1);
                        Root.Knot b = root.knots().get(i);
                        double turn = space.angleOf(b.x(), b.z()) - space.angleOf(a.x(), a.z());
                        round += turn - 2 * Math.PI * Math.floor(turn / (2 * Math.PI) + 0.5);
                        assertTrue(b.y() <= first.y() + 8, "it does not climb above where it started");
                    }
                    assertTrue(Math.abs(round) > Math.PI / 2, "it goes round the hole, by " + Math.toDegrees(round) + " degrees");
                }
            }
        }
    }

    @Test
    void aLinkClimbsInTheOpenFromOneDiscToTheRimOfAnotherNoSteeperThanItsLimit() {
        int links = 0;
        for (RavineBounds bounds : List.of(VANILLA, TALL)) {
            for (int cz = 0; cz < CELLS; cz++) {
                RootSpace space = space(bounds, cell(cz));
                List<Disc> discs = space.layout().discs();
                for (Root root : roots(bounds, cell(cz))) {
                    if (root.kind() != Root.Kind.LINK) {
                        continue;
                    }
                    links++;
                    List<Root.Knot> knots = root.knots();
                    Root.Knot foot = root.first();
                    Root.Knot end = root.last();
                    assertTrue(discs.stream().anyMatch(disc -> {
                        double fromAxis = Math.hypot(foot.x() - disc.x(), foot.z() - disc.z());
                        return fromAxis < disc.radius() && Math.abs(disc.topAt(fromAxis) - foot.y()) < 2;
                    }), "it starts in the top of a disc");
                    assertTrue(discs.stream().anyMatch(disc -> {
                        double fromRim = Math.abs(Math.hypot(end.x() - disc.x(), end.z() - disc.z()) - disc.radius());
                        return fromRim < 6 && Math.abs(disc.floor() + disc.bowl() - end.y()) < 5;
                    }), "it ends at the rim of a disc");
                    assertTrue(end.y() - foot.y() >= 0.3 * 20 - 3, "it climbs, by " + (end.y() - foot.y()));
                    for (int i = 4; i < knots.size(); i++) {
                        Root.Knot a = knots.get(i - 4);
                        Root.Knot b = knots.get(i);
                        double slope = Math.abs(b.y() - a.y()) / Math.max(Math.hypot(b.x() - a.x(), b.z() - a.z()), 1e-6);
                        // Its heading is never steeper than the limit; where it is held out of the clear air the line
                        // from knot to knot can be, a little.
                        assertTrue(slope <= 0.6 * 1.15, "a slope of " + slope);
                        // All but where it leaves the ground of one disc and where it turns in to the rim of the other: its
                        // middle is never more than half a block into rock, so its top is clear all the way.
                        if (i >= 8 && i < knots.size() - 6) {
                            assertTrue(space.air(b.x(), b.y(), b.z()) >= -0.5, "knot " + i + " of " + knots.size() + " is in rock");
                        }
                    }
                }
            }
        }
        assertTrue(links > 10 * CELLS, links + " links");
    }

    @Test
    void nearlyEveryRootEndsInRockOrInAnotherRoot() {
        int roots = 0;
        int ended = 0;
        for (RavineBounds bounds : List.of(VANILLA, TALL)) {
            for (int cz = 0; cz < CELLS; cz++) {
                RootSpace space = space(bounds, cell(cz));
                List<Root> all = roots(bounds, cell(cz));
                for (Root root : all) {
                    Root.Knot end = root.last();
                    boolean inRock = space.air(end.x(), end.y(), end.z()) < 1;
                    boolean inARoot = all.stream().anyMatch(other -> other != root
                            && other.knots().stream().anyMatch(knot -> knot.distanceTo(end.x(), end.y(), end.z()) < knot.radius() + 1.5));
                    roots++;
                    ended += inRock || inARoot ? 1 : 0;
                }
            }
        }
        assertTrue(ended >= 0.95 * roots, ended + " of " + roots + " roots end in something");
    }

    @Test
    void rootsGatherTowardsTheFloor() {
        for (RavineBounds bounds : List.of(VANILLA, TALL)) {
            int low = 0;
            int high = 0;
            double third = (bounds.topY() - bounds.floorY()) / 3.0;
            for (int cz = 0; cz < CELLS; cz++) {
                for (Root root : roots(bounds, cell(cz))) {
                    for (Root.Knot knot : root.knots()) {
                        low += knot.y() < bounds.floorY() + third ? 1 : 0;
                        high += knot.y() >= bounds.topY() - third ? 1 : 0;
                    }
                }
            }
            assertTrue(low > 2 * high, low + " blocks of root in the lowest third and " + high + " in the highest");
        }
    }

    @Test
    void aHoleWhoseSettingsHaveNoRootsHasNone() {
        RavineSettings bare = ConeShapeTest.LOW_SETTINGS;
        RavineCell c = RavineCells.at(7L, bare, 1, 0).orElseThrow();
        RootLayout none = RootLayout.of(bare, VANILLA, c, Carved.layout(bare, VANILLA, c), SurfaceProbe.SOLID);
        assertSame(RootLayout.NONE, none);
        assertTrue(none.roots().isEmpty());
        assertTrue(none.near(0, 0).isEmpty());
        assertTrue(CellDiscs.of(bare, VANILLA, c).roots().roots().isEmpty());
    }

    @Test
    void eachChunkIsToldOfEveryStretchOfRootThatComesIntoIt() {
        RavineCell c = cell(0);
        RootLayout layout = RootLayout.of(SETTINGS, VANILLA, c, Carved.layout(SETTINGS, VANILLA, c), SurfaceProbe.SOLID);
        int stretches = 0;
        for (int r = 0; r < layout.roots().size(); r++) {
            List<Root.Knot> knots = layout.roots().get(r).knots();
            for (int k = 0; k + 1 < knots.size(); k++) {
                var stretch = new RootLayout.Stretch(r, k);
                for (Root.Knot knot : List.of(knots.get(k), knots.get(k + 1))) {
                    // The chunks its two ends lie in, and those their thickness reaches into.
                    for (double[] reach : new double[][] {{0, 0}, {knot.radius(), 0}, {-knot.radius(), 0}, {0, knot.radius()}, {0, -knot.radius()}}) {
                        int minX = Math.floorDiv((int) Math.floor(knot.x() + reach[0]), 16) * 16;
                        int minZ = Math.floorDiv((int) Math.floor(knot.z() + reach[1]), 16) * 16;
                        assertTrue(layout.near(minX, minZ).contains(stretch), "a chunk is not told of a stretch in it");
                    }
                }
                stretches++;
            }
        }
        assertTrue(stretches > 1000, stretches + " stretches");
        assertTrue(layout.near((int) c.centreX() + 4096, (int) c.centreZ()).isEmpty(), "none far from the hole");
    }
}

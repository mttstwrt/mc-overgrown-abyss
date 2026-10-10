package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;

/**
 * The roots of one hole (see {@link RootSettings}), and which of them come near each chunk. Like the hole's discs they
 * depend only on the settings, the heights the hole lies between, the cell and the ground over it, so they are grown once
 * for a cell and then only read, which makes them safe to share between worker threads.
 */
final class RootLayout {
    static final RootLayout NONE = new RootLayout(List.of());
    private static final int CHUNK_SIZE = 16;

    private final List<Root> roots;
    private final int firstX;
    private final int firstZ;
    private final int across;
    private final List<List<Stretch>> chunks;

    /** The part of a root from one knot to the next: {@code knot} is the first of the two. */
    record Stretch(int root, int knot) {}

    private RootLayout(List<Root> roots) {
        this.roots = List.copyOf(roots);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Root root : roots) {
            for (Root.Knot knot : root.knots()) {
                minX = Math.min(minX, chunkOf(knot.x() - knot.radius()));
                maxX = Math.max(maxX, chunkOf(knot.x() + knot.radius()));
                minZ = Math.min(minZ, chunkOf(knot.z() - knot.radius()));
                maxZ = Math.max(maxZ, chunkOf(knot.z() + knot.radius()));
            }
        }
        this.firstX = roots.isEmpty() ? 0 : minX;
        this.firstZ = roots.isEmpty() ? 0 : minZ;
        this.across = roots.isEmpty() ? 0 : maxX - minX + 1;
        int down = roots.isEmpty() ? 0 : maxZ - minZ + 1;
        var lists = new ArrayList<List<Stretch>>(across * down);
        for (int i = 0; i < across * down; i++) {
            lists.add(new ArrayList<>());
        }
        for (int r = 0; r < roots.size(); r++) {
            List<Root.Knot> knots = roots.get(r).knots();
            for (int k = 0; k + 1 < knots.size(); k++) {
                Root.Knot from = knots.get(k);
                Root.Knot to = knots.get(k + 1);
                double reach = Math.max(from.radius(), to.radius());
                for (int chunkX = chunkOf(Math.min(from.x(), to.x()) - reach); chunkX <= chunkOf(Math.max(from.x(), to.x()) + reach); chunkX++) {
                    for (int chunkZ = chunkOf(Math.min(from.z(), to.z()) - reach); chunkZ <= chunkOf(Math.max(from.z(), to.z()) + reach); chunkZ++) {
                        lists.get((chunkZ - firstZ) * across + chunkX - firstX).add(new Stretch(r, k));
                    }
                }
            }
        }
        this.chunks = lists.stream().map(List::copyOf).toList();
    }

    /** The roots of a hole whose discs are {@code layout}; none without a cone or without roots in the settings. */
    static RootLayout of(RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, SurfaceProbe ground) {
        if (settings.roots().isEmpty() || settings.cone().isEmpty()) {
            return NONE;
        }
        var space = new RootSpace(settings, settings.cone().get(), bounds, cell, layout, new HoleGround(ground, bounds.topY()));
        return new RootLayout(RootRoutes.grow(space, settings.roots().get()));
    }

    private static int chunkOf(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), CHUNK_SIZE);
    }

    /** Every root, in a fixed order. */
    List<Root> roots() {
        return roots;
    }

    /** The stretches of root that come into the chunk whose lowest corner is {@code (minX, minZ)}, in the roots' order. */
    List<Stretch> near(int minX, int minZ) {
        int chunkX = Math.floorDiv(minX, CHUNK_SIZE) - firstX;
        int chunkZ = Math.floorDiv(minZ, CHUNK_SIZE) - firstZ;
        if (chunkX < 0 || chunkX >= across || chunkZ < 0 || chunkZ * across + chunkX >= chunks.size()) {
            return List.of();
        }
        return chunks.get(chunkZ * across + chunkX);
    }
}

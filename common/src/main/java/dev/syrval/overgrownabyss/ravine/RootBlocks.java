package dev.syrval.overgrownabyss.ravine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The blocks of one chunk that are root (see {@link RootSettings}), and the wood of each. A block is root if it is within
 * the root's radius of the line down the root's middle, whatever was there: air, the wall, a platform or the floor. Each
 * chunk finds its own blocks from the hole's roots alone, so the result is the same whatever order chunks generate in.
 *
 * <p>A stretch of root takes the wood of the disc nearest to it whose theme names one (see {@link DiscTheme#rootWood()}), or
 * the settings' own wood where none is within reach. Where two different woods are about as near, it is one or the other in
 * patches along the root, more of the nearer the nearer it is.
 */
final class RootBlocks {
    private static final int CHUNK_SIZE = 16;
    private static final int WOOD_HASH_BASE = 1_200_000;
    // Blocks along a root from one patch of a wood to the next, about, where two woods share it.
    private static final double PATCH = 10;

    private RootBlocks() {}

    /** The two woods a place is between and how much of it is the first, which is never the lesser share. */
    record Mix(RootWood first, RootWood second, double share) {}

    /** The blocks of the chunk whose lowest corner is {@code (minX, minZ)}, between {@code minY} and {@code maxY} (exclusive). */
    static void forEach(RavineSettings settings, RavineCell cell, CellDiscs cellDiscs, int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
        RootLayout layout = cellDiscs.roots();
        List<RootLayout.Stretch> stretches = layout.near(minX, minZ);
        if (stretches.isEmpty() || settings.roots().isEmpty()) {
            return;
        }
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (RootLayout.Stretch stretch : stretches) {
            for (Root.Knot knot : endsOf(layout, stretch)) {
                lowest = Math.min(lowest, knot.y() - knot.radius());
                highest = Math.max(highest, knot.y() + knot.radius());
            }
        }
        int bottom = Math.max(minY, (int) Math.ceil(lowest));
        int top = Math.min(maxY - 1, (int) Math.floor(highest));
        if (top < bottom) {
            return;
        }
        var slab = new Slab(minX, minZ, bottom, top);
        for (int i = 0; i < stretches.size(); i++) {
            slab.take(endsOf(layout, stretches.get(i)), i);
        }
        // The wood of a stretch is the same for every block of it, so it is worked out once, and only for stretches that
        // turn out to hold a block of this chunk.
        RootWood[] woods = new RootWood[stretches.size()];
        slab.forEach((x, y, z, inside, stretch) -> {
            if (woods[stretch] == null) {
                woods[stretch] = woodOf(settings, cell, cellDiscs, layout, stretches.get(stretch));
            }
            sink.accept(x, y, z, woods[stretch].blockAt(inside));
        });
    }

    private static List<Root.Knot> endsOf(RootLayout layout, RootLayout.Stretch stretch) {
        List<Root.Knot> knots = layout.roots().get(stretch.root()).knots();
        return List.of(knots.get(stretch.knot()), knots.get(stretch.knot() + 1));
    }

    // The wood of one stretch: of the two its place is between, the first where the patch it lies in says so.
    private static RootWood woodOf(RavineSettings settings, RavineCell cell, CellDiscs cellDiscs, RootLayout layout, RootLayout.Stretch stretch) {
        Root.Knot knot = layout.roots().get(stretch.root()).knots().get(stretch.knot());
        Mix mix = mixAt(settings, cellDiscs, knot.x(), knot.y(), knot.z());
        // Whole rows of the smooth values are a root each, so no root's patches line up with another's.
        double patch = RavineCells.shareBelow(RavineCells.smoothAt(cell.hash(), WOOD_HASH_BASE, stretch.knot() / PATCH, stretch.root()));
        return patch < mix.share() ? mix.first() : mix.second();
    }

    /**
     * The woods a place is between. Each disc whose theme names a wood offers it from as far away as the place is from the
     * disc's platform and dome, and the settings' own wood is offered from {@code wood_reach} away everywhere. The nearest
     * offer is the first wood and the nearest of a different wood the second, and the first has all of the place where the
     * second is {@code wood_blend} further off or more, down to half where they are as near as each other.
     */
    static Mix mixAt(RavineSettings settings, CellDiscs cellDiscs, double x, double y, double z) {
        RootSettings roots = settings.roots().orElseThrow();
        RootWood first = roots.wood();
        double nearest = roots.woodReach();
        RootWood second = roots.wood();
        double next = Double.POSITIVE_INFINITY;
        List<Disc> discs = cellDiscs.layout().discs();
        for (int i = 0; i < discs.size(); i++) {
            Optional<RootWood> wood = cellDiscs.themes().get(i).flatMap(DiscTheme::rootWood);
            if (wood.isEmpty()) {
                continue;
            }
            double away = awayFrom(discs.get(i), settings.discs(), x, y, z);
            if (away < nearest) {
                // What was nearest is now the nearest of another wood, unless it was this same wood.
                if (!first.equals(wood.get())) {
                    second = first;
                    next = nearest;
                }
                first = wood.get();
                nearest = away;
            } else if (away < next && !first.equals(wood.get())) {
                second = wood.get();
                next = away;
            }
        }
        return new Mix(first, second, 0.5 + 0.5 * Math.clamp((next - nearest) / roots.woodBlend(), 0, 1));
    }

    // How far a point is from a disc's platform and the dome over it: 0 inside them.
    private static double awayFrom(Disc disc, DiscShape shape, double x, double y, double z) {
        double beside = Math.max(0, Math.hypot(x - disc.x(), z - disc.z()) - disc.radius());
        double under = disc.floor() - shape.floorThickness() - y;
        double over = y - (disc.floor() + disc.height());
        return Math.hypot(beside, Math.max(0, Math.max(under, over)));
    }

    @FunctionalInterface
    private interface SlabSink {
        void accept(int x, int y, int z, double inside, int stretch);
    }

    /**
     * The part of a chunk between two heights, and for each of its blocks how far inside a root its centre is and which
     * stretch that root is: the one the block is deepest in, where roots run into one another.
     */
    private static final class Slab {
        private final int minX;
        private final int minZ;
        private final int bottom;
        private final int top;
        private final float[] inside;
        private final int[] stretch;

        Slab(int minX, int minZ, int bottom, int top) {
            this.minX = minX;
            this.minZ = minZ;
            this.bottom = bottom;
            this.top = top;
            this.inside = new float[CHUNK_SIZE * CHUNK_SIZE * (top - bottom + 1)];
            this.stretch = new int[inside.length];
            Arrays.fill(inside, -1);
        }

        // Every block of the slab within the root's radius of the line from one knot to the next.
        void take(List<Root.Knot> ends, int index) {
            Root.Knot from = ends.get(0);
            Root.Knot to = ends.get(1);
            double reach = Math.max(from.radius(), to.radius());
            double alongX = to.x() - from.x();
            double alongY = to.y() - from.y();
            double alongZ = to.z() - from.z();
            double length = alongX * alongX + alongY * alongY + alongZ * alongZ;
            for (int y = Math.max(bottom, (int) Math.ceil(Math.min(from.y(), to.y()) - reach)); y <= Math.min(top, (int) Math.floor(Math.max(from.y(), to.y()) + reach)); y++) {
                for (int z = Math.max(minZ, (int) Math.ceil(Math.min(from.z(), to.z()) - reach)); z <= Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(Math.max(from.z(), to.z()) + reach)); z++) {
                    for (int x = Math.max(minX, (int) Math.ceil(Math.min(from.x(), to.x()) - reach)); x <= Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(Math.max(from.x(), to.x()) + reach)); x++) {
                        double along = length < 1e-12 ? 0 : Math.clamp(((x - from.x()) * alongX + (y - from.y()) * alongY + (z - from.z()) * alongZ) / length, 0, 1);
                        double dx = x - from.x() - along * alongX;
                        double dy = y - from.y() - along * alongY;
                        double dz = z - from.z() - along * alongZ;
                        double in = from.radius() + along * (to.radius() - from.radius()) - Math.sqrt(dx * dx + dy * dy + dz * dz);
                        int at = ((y - bottom) * CHUNK_SIZE + z - minZ) * CHUNK_SIZE + x - minX;
                        if (in >= 0 && in > inside[at]) {
                            inside[at] = (float) in;
                            stretch[at] = index;
                        }
                    }
                }
            }
        }

        // From the lowest level up, and each level a row at a time: the same order every time.
        void forEach(SlabSink sink) {
            for (int at = 0; at < inside.length; at++) {
                if (inside[at] >= 0) {
                    sink.accept(minX + at % CHUNK_SIZE, bottom + at / (CHUNK_SIZE * CHUNK_SIZE), minZ + at / CHUNK_SIZE % CHUNK_SIZE, inside[at], stretch[at]);
                }
            }
        }
    }
}

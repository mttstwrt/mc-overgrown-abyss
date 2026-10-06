package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;

/**
 * Where the themes of a cell's discs grow things in one chunk (see {@link DiscTheme.Growth}). Each block of a surface is drawn
 * for from the cell's hash and its own coordinates, so the places are the same whatever order chunks generate in.
 */
final class DiscGrowth {
    private static final int GROWTH_HASH_BASE = 300_000;
    // Hash indices kept apart for this many growths per theme.
    private static final int MAX_GROWTHS = 64;
    private static final int CHUNK_SIZE = 16;
    // Nothing is grown this close to the rim, where a tree would stand half over the edge.
    private static final double RIM = 1.5;

    private DiscGrowth() {}

    /** The places in the chunk whose lowest corner is {@code (minX, minZ)}. */
    static void forEach(RavineSettings settings, RavineCell cell, CellDiscs discs, int minX, int minZ, DiscGrowthSink sink) {
        List<Disc> all = discs.layout().discs();
        DiscShape shape = settings.discs();
        for (int i = 0; i < all.size(); i++) {
            Optional<DiscTheme> theme = discs.themes().get(i);
            if (theme.isEmpty() || theme.get().growth().isEmpty()) {
                continue;
            }
            Disc disc = all.get(i);
            List<DiscTheme.Growth> growth = theme.get().growth();
            int fromX = Math.max(minX, (int) Math.ceil(disc.x() - disc.radius()));
            int toX = Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(disc.x() + disc.radius()));
            int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - disc.radius()));
            int toZ = Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(disc.z() + disc.radius()));
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
                    if (fromAxis > disc.radius() - RIM) {
                        continue;
                    }
                    for (int g = 0; g < Math.min(growth.size(), MAX_GROWTHS); g++) {
                        DiscTheme.Growth each = growth.get(g);
                        if (RavineCells.unitAt(cell.hash(), GROWTH_HASH_BASE + i * MAX_GROWTHS + g, x, z) * each.every() >= 1) {
                            continue;
                        }
                        // The open block on the surface: the one over the top block, or the one under the lowest block.
                        int y = each.on() == DiscTheme.Surface.TOP
                                ? (int) Math.ceil(disc.topAt(fromAxis))
                                : (int) Math.floor(disc.undersideAt(shape, fromAxis));
                        sink.accept(x, y, z, each.feature(), each.on());
                    }
                }
            }
        }
    }
}

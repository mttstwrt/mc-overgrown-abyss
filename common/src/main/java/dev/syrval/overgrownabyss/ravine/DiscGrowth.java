package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where the themes of a cell's discs grow things in one chunk (see {@link DiscTheme.Growth}). Each block of a surface is drawn
 * for from the cell's hash and its own coordinates, so the places are the same whatever order chunks generate in. Within a
 * chunk a disc's growths come in the order its theme lists them.
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
            // One growth at a time, in the order the theme lists them, so that what is listed later finds what was listed
            // earlier already there: vines after the trees they hang from.
            for (int g = 0; g < Math.min(growth.size(), MAX_GROWTHS); g++) {
                DiscTheme.Growth each = growth.get(g);
                for (int x = fromX; x <= toX; x++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
                        if (fromAxis > disc.radius() - RIM || RavineCells.unitAt(cell.hash(), GROWTH_HASH_BASE + i * MAX_GROWTHS + g, x, z) * each.every() >= 1) {
                            continue;
                        }
                        OptionalInt y = placeOn(each.on(), theme.get(), disc, shape, cell.hash(), i, x, z, fromAxis);
                        if (y.isPresent()) {
                            sink.accept(x, y.getAsInt(), z, each.feature(), each.on());
                        }
                    }
                }
            }
        }
    }

    // The block a growth starts in: the open one over the top block or under the disc's lowest rock (the stem's flare under
    // most of a standing disc), or the lowest block of water.
    private static OptionalInt placeOn(DiscTheme.Surface on, DiscTheme theme, Disc disc, DiscShape shape, long hash, int index, int x, int z, double fromAxis) {
        return switch (on) {
            case TOP -> OptionalInt.of(disc.topBlockAt(fromAxis) + 1);
            case UNDERSIDE -> disc.hangBlockAt(shape, fromAxis);
            case WATER -> {
                int depth = theme.water().map(water -> water.depthAt(disc, hash, index, x, z)).orElse(0);
                yield depth > 0 ? OptionalInt.of(disc.topBlockAt(fromAxis) - depth + 1) : OptionalInt.empty();
            }
        };
    }
}

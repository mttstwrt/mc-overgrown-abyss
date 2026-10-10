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
    // Nothing is grown on a disc this close to its rim, where a tree would stand half over the edge.
    private static final double EDGE_CLEAR = 1.5;
    private static final int[][] BESIDE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

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
            double wildness = theme.get().wildness().factorFor(cell.hash(), i);
            DiscGround ground = discs.grounds().get(i);
            // One growth at a time, in the order the theme lists them, so that what is listed later finds what was listed
            // earlier already there: vines after the trees they hang from.
            for (int g = 0; g < Math.min(growth.size(), MAX_GROWTHS); g++) {
                DiscTheme.Growth each = growth.get(g);
                // The rim's places are the columns right outside the platform.
                double reach = disc.radius() + (each.on() == DiscTheme.Surface.RIM ? 1 : 0);
                int fromX = Math.max(minX, (int) Math.ceil(disc.x() - reach));
                int toX = Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(disc.x() + reach));
                int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - reach));
                int toZ = Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(disc.z() + reach));
                for (int x = fromX; x <= toX; x++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        double fromAxis = Math.hypot(x - disc.x(), z - disc.z());
                        if (each.on() != DiscTheme.Surface.RIM && fromAxis > disc.radius() - EDGE_CLEAR) {
                            continue;
                        }
                        double draw = RavineCells.unitAt(cell.hash(), GROWTH_HASH_BASE + i * MAX_GROWTHS + g, x, z) * each.every();
                        if (draw >= wildness * each.weightAt(cell.hash(), i, disc, x, z)) {
                            continue;
                        }
                        OptionalInt y = placeOn(each.on(), theme.get(), disc, ground, shape, cell.hash(), i, x, z, fromAxis);
                        if (y.isPresent()) {
                            sink.accept(x, y.getAsInt(), z, each.feature(), each.on());
                        }
                    }
                }
            }
        }
    }

    // The block a growth starts in: the open one over the ground or under the disc's lowest rock (the stem's flare under
    // most of a standing disc), the lowest block of water, or the open one beside the top block of the rim.
    private static OptionalInt placeOn(
            DiscTheme.Surface on, DiscTheme theme, Disc disc, DiscGround ground, DiscShape shape, long hash, int index, int x, int z, double fromAxis) {
        return switch (on) {
            case TOP -> OptionalInt.of(ground.topBlockAt(x, z) + 1);
            case UNDERSIDE -> disc.hangBlockAt(shape, fromAxis);
            case WATER -> {
                int depth = theme.water().map(water -> water.depthAt(disc, hash, index, x, z)).orElse(0);
                yield depth > 0 ? OptionalInt.of(disc.topBlockAt(fromAxis) - depth + 1) : OptionalInt.empty();
            }
            case RIM -> fromAxis < disc.radius() ? OptionalInt.empty() : besideRim(disc, x, z);
        };
    }

    // The height of the highest top block among the platform's columns next to this one, which is outside the platform; empty
    // if none of them is the platform's. The bowl's last step up may fall short of the rim, so this is asked of each column.
    private static OptionalInt besideRim(Disc disc, int x, int z) {
        OptionalInt top = OptionalInt.empty();
        for (int[] step : BESIDE) {
            double fromAxis = Math.hypot(x + step[0] - disc.x(), z + step[1] - disc.z());
            if (fromAxis < disc.radius() && (top.isEmpty() || disc.topBlockAt(fromAxis) > top.getAsInt())) {
                top = OptionalInt.of(disc.topBlockAt(fromAxis));
            }
        }
        return top;
    }
}

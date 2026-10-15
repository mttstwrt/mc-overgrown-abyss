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
    private static final int[][] BESIDE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private DiscGrowth() {}

    /** The places in the chunk whose lowest corner is {@code (minX, minZ)}. */
    static void forEach(RavineSettings settings, RavineCell cell, CellDiscs discs, int minX, int minZ, DiscGrowthSink sink) {
        List<Disc> all = discs.layout().discs();
        DiscShape shape = settings.discs();
        var lava = new DiscLava(discs, minX, minZ);
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
                int index = i;
                DiscScatter.forEachPick(cell.hash(), GROWTH_HASH_BASE + i * MAX_GROWTHS + g, disc, each.on(), each.every(), wildness,
                        (x, z) -> each.weightAt(cell.hash(), index, disc, x, z), minX, minZ, (x, z, fromAxis) -> {
                            OptionalInt y = placeOn(each.on(), theme.get(), disc, ground, shape, lava, cell.hash(), index, x, z, fromAxis);
                            // Nothing is started in a fall's water or by it, on a disc or over its lip, to stand in the water's way.
                            if (y.isPresent() && !(each.on() != DiscTheme.Surface.UNDERSIDE && discs.falls().isNear(x, y.getAsInt(), z))) {
                                sink.accept(x, y.getAsInt(), z, each.feature(), each.on());
                            }
                        });
            }
        }
    }

    // The block a growth starts in: the open one over the ground or under the disc's lowest rock (the stem's flare under
    // most of a standing disc), the lowest block of water, the open one over lava, or the open one beside the top block of
    // the rim.
    static OptionalInt placeOn(
            DiscTheme.Surface on, DiscTheme theme, Disc disc, DiscGround ground, DiscShape shape, DiscLava lava, long hash, int index, int x, int z,
            double fromAxis) {
        return switch (on) {
            case TOP -> OptionalInt.of(ground.topBlockAt(x, z) + 1);
            case UNDERSIDE -> disc.hangBlockAt(shape, fromAxis);
            case WATER -> {
                int depth = depthOf(DiscWater.Fluid.WATER, theme, disc, hash, index, x, z);
                yield depth > 0 ? OptionalInt.of(disc.topBlockAt(fromAxis) - depth + 1) : OptionalInt.empty();
            }
            case LAVA -> {
                int surface = disc.topBlockAt(fromAxis);
                boolean held = depthOf(DiscWater.Fluid.LAVA, theme, disc, hash, index, x, z) > 0
                        && lava.liesClear(index, x, surface, z, theme.water().map(DiscWater::clear).orElse(0));
                yield held ? OptionalInt.of(surface + 1) : OptionalInt.empty();
            }
            case RIM -> fromAxis < disc.radius() ? OptionalInt.empty() : besideRim(disc, x, z);
        };
    }

    // How deep a theme's ponds and streams are in a column, if that is what they hold.
    private static int depthOf(DiscWater.Fluid fluid, DiscTheme theme, Disc disc, long hash, int index, int x, int z) {
        return theme.water().filter(water -> water.fluid() == fluid).map(water -> water.depthAt(disc, hash, index, x, z)).orElse(0);
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

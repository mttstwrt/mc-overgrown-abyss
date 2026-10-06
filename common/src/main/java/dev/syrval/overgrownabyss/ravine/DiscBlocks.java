package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * The blocks of one chunk that take a disc's material in place of the terrain's own rock (see {@link DiscPalette}), each disc
 * by the palette of its theme. Each chunk finds its own blocks from the layout alone, so the result is the same whatever order
 * chunks generate in.
 *
 * <p>A platform is painted over its whole round footprint, wherever it lies: in open air, and inside the wall under its dome,
 * where the terrain may have left a cave. A stem or root is painted only where the terrain made it, which is inside the
 * hole. Nothing else here knows about the hole, so a disc placed anywhere is painted the same way.
 */
final class DiscBlocks {
    private static final int CHUNK_SIZE = 16;

    private DiscBlocks() {}

    /** The blocks of the chunk whose lowest corner is {@code (minX, minZ)}, between {@code minY} and {@code maxY} (exclusive). */
    static void forEach(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, CellDiscs cellDiscs,
            int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
        var painter = new Painter(settings, bounds, cell, cellDiscs.layout(), minX, minZ, minY, maxY, sink);
        List<Disc> discs = cellDiscs.layout().discs();
        // Stems and roots first, platforms second, so a platform keeps its own material where a stem passes through it.
        for (int i = 0; i < discs.size(); i++) {
            Disc disc = discs.get(i);
            if (painter.reaches(disc)) {
                cellDiscs.themes().get(i).ifPresent(theme -> painter.support(disc, theme.palette()));
            }
        }
        for (int i = 0; i < discs.size(); i++) {
            Disc disc = discs.get(i);
            if (painter.reaches(disc)) {
                cellDiscs.themes().get(i).ifPresent(theme -> painter.platform(disc, theme.palette()));
            }
        }
    }

    /** The columns of the chunk whose lowest corner is {@code (minX, minZ)} where a disc's theme puts water in its top (see {@link DiscWater}). */
    static void forEachWater(RavineCell cell, CellDiscs cellDiscs, int minX, int minZ, DiscWaterSink sink) {
        List<Disc> discs = cellDiscs.layout().discs();
        for (int i = 0; i < discs.size(); i++) {
            Optional<DiscWater> water = cellDiscs.themes().get(i).flatMap(DiscTheme::water);
            if (water.isEmpty()) {
                continue;
            }
            Disc disc = discs.get(i);
            int fromX = Math.max(minX, (int) Math.ceil(disc.x() - disc.radius()));
            int toX = Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(disc.x() + disc.radius()));
            int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - disc.radius()));
            int toZ = Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(disc.z() + disc.radius()));
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    int depth = water.get().depthAt(disc, cell.hash(), i, x, z);
                    if (depth > 0) {
                        sink.accept(x, disc.topBlockAt(Math.hypot(x - disc.x(), z - disc.z())), z, depth);
                    }
                }
            }
        }
    }

    private record Painter(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout,
            int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {

        private DiscShape shape() {
            return settings.discs();
        }

        // Nothing of a disc is further from its axis than its platform's rim.
        boolean reaches(Disc disc) {
            return disc.x() + disc.radius() >= minX && disc.x() - disc.radius() <= minX + CHUNK_SIZE - 1
                    && disc.z() + disc.radius() >= minZ && disc.z() - disc.radius() <= minZ + CHUNK_SIZE - 1;
        }

        void platform(Disc disc, DiscPalette palette) {
            int bottom = (int) Math.floor(disc.floor() - shape().floorThickness()) + 1;
            int top = (int) Math.ceil(disc.floor() + disc.bowl()) - 1;
            for (int y = Math.max(bottom, minY); y <= Math.min(top, maxY - 1); y++) {
                slice(disc, palette, y, disc.radius(), false);
            }
        }

        void support(Disc disc, DiscPalette palette) {
            switch (disc.support()) {
                case Disc.Support.Standing standing -> {
                    // The underside is lowest in the middle and rises with the bowl, and the stem's flare is under all of it.
                    double underside = disc.undersideAt(shape(), 0);
                    int bottom = (int) Math.ceil(Math.max(standing.bottom(), bounds.floorY()));
                    for (int y = Math.max(bottom, minY); y <= Math.min((int) Math.floor(underside + disc.bowl()), maxY - 1); y++) {
                        slice(disc, palette, y, y > underside ? disc.radius() : disc.stemRadiusAt(shape(), underside - y), true);
                    }
                }
                case Disc.Support.Hanging hanging -> {
                    int bottom = (int) Math.floor(disc.floor()) + 1;
                    for (int y = Math.max(bottom, minY); y <= Math.min((int) Math.floor(hanging.top()), maxY - 1); y++) {
                        slice(disc, palette, y, disc.rootRadiusAt(shape(), Math.abs(hanging.anchor() - y)), true);
                    }
                }
            }
        }

        // One level of a disc: the chunk's blocks within reach of its axis that are the wanted part of it.
        private void slice(Disc disc, DiscPalette palette, int y, double reach, boolean stem) {
            int fromX = Math.max(minX, (int) Math.ceil(disc.x() - reach));
            int toX = Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(disc.x() + reach));
            int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - reach));
            int toZ = Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(disc.z() + reach));
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    Optional<BlockStateProvider> block = blockAt(disc, palette, x, y, z, stem);
                    if (block.isPresent()) {
                        sink.accept(x, y, z, block.get());
                    }
                }
            }
        }

        private Optional<BlockStateProvider> blockAt(Disc disc, DiscPalette palette, int x, int y, int z, boolean stem) {
            Optional<DiscPoint> point = disc.pointAt(shape(), x, y, z);
            if (point.isEmpty() || (point.get() instanceof DiscPoint.Stem) != stem) {
                return Optional.empty();
            }
            Optional<BlockStateProvider> block = palette.blockAt(point.get());
            // The costly question, whether the terrain put this part of the stem here, is asked last.
            if (stem && block.isPresent() && RavineShape.rockDistance(settings, bounds, cell, layout, x, y, z) >= 0) {
                return Optional.empty();
            }
            return block;
        }
    }
}

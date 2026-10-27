package dev.syrval.verdantdeep.ravine;

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
                DiscGround ground = cellDiscs.grounds().get(i);
                cellDiscs.themes().get(i).ifPresent(theme -> painter.platform(disc, theme.palette(), ground));
            }
        }
    }

    /** The columns of the chunk whose lowest corner is {@code (minX, minZ)} where a disc's theme puts water in its top (see {@link DiscWater}). */
    static void forEachWater(RavineCell cell, CellDiscs cellDiscs, int minX, int minZ, DiscWaterSink sink) {
        List<Disc> discs = cellDiscs.layout().discs();
        var lava = new DiscLava(cellDiscs, minX, minZ);
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
                    // A fall's water takes the place of the disc's own where the two would meet: it may lie lower.
                    int depth = cellDiscs.falls().holds(i, x, z) ? 0 : water.get().depthAt(disc, cell.hash(), i, x, z);
                    int surface = disc.topBlockAt(Math.hypot(x - disc.x(), z - disc.z()));
                    if (depth > 0 && lava.liesClear(i, x, surface, z, water.get().clear())) {
                        sink.accept(x, surface, z, depth, water.get().fluid());
                    }
                }
            }
        }
    }

    /** The columns of the chunk whose lowest corner is {@code (minX, minZ)} where a fall's still water lies (see {@link DiscFalls}). */
    static void forEachFallWater(CellDiscs cellDiscs, int minX, int minZ, DiscFallSink sink) {
        cellDiscs.falls().forEachColumn(minX, minZ, column -> sink.accept(column.x(), column.level(), column.z(), column.depth(), column.cut(), column.lip()));
    }

    /**
     * The blocks of ground that stand over the platforms of the chunk whose lowest corner is {@code (minX, minZ)}, between
     * {@code minY} and {@code maxY} (exclusive): see {@link DiscGround}. A mound takes the palette's material for its depth
     * under the mound's own top, an outcrop its own block all the way down. They are meant for open air: where a stem comes
     * down on the platform, or the hole's wall lies on it, that stays, which is for whoever writes them to see to.
     */
    static void forEachMound(CellDiscs cellDiscs, int minX, int minZ, int minY, int maxY, DiscBlockSink sink) {
        List<Disc> discs = cellDiscs.layout().discs();
        for (int i = 0; i < discs.size(); i++) {
            Optional<DiscTheme> theme = cellDiscs.themes().get(i);
            DiscGround ground = cellDiscs.grounds().get(i);
            if (theme.isEmpty() || ground.relief().equals(DiscRelief.FLAT)) {
                continue;
            }
            Disc disc = discs.get(i);
            DiscPalette palette = theme.get().palette();
            int fromX = Math.max(minX, (int) Math.ceil(disc.x() - disc.radius()));
            int toX = Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(disc.x() + disc.radius()));
            int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - disc.radius()));
            int toZ = Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(disc.z() + disc.radius()));
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    int rise = ground.riseAt(x, z);
                    if (rise == 0) {
                        continue;
                    }
                    int rock = disc.topBlockAt(Math.hypot(x - disc.x(), z - disc.z()));
                    Optional<BlockStateProvider> outcrop = ground.isOutcropAt(x, z) ? ground.relief().outcrops().map(DiscRelief.Outcrops::block) : Optional.empty();
                    for (int y = Math.max(rock + 1, minY); y <= Math.min(rock + rise, maxY - 1); y++) {
                        double belowTop = rock + rise - y + 0.5;
                        Optional<BlockStateProvider> block = outcrop.or(() -> palette.blockAt(new DiscPoint.Platform(belowTop, Double.MAX_VALUE)));
                        if (block.isPresent()) {
                            sink.accept(x, y, z, block.get());
                        }
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

        void platform(Disc disc, DiscPalette palette, DiscGround ground) {
            int bottom = Math.max((int) Math.floor(disc.floor() - shape().floorThickness()) + 1, minY);
            int top = Math.min((int) Math.ceil(disc.floor() + disc.bowl()) - 1, maxY - 1);
            int fromX = Math.max(minX, (int) Math.ceil(disc.x() - disc.radius()));
            int toX = Math.min(minX + CHUNK_SIZE - 1, (int) Math.floor(disc.x() + disc.radius()));
            int fromZ = Math.max(minZ, (int) Math.ceil(disc.z() - disc.radius()));
            int toZ = Math.min(minZ + CHUNK_SIZE - 1, (int) Math.floor(disc.z() + disc.radius()));
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    // Under a mound the platform's own top is that much deeper in the ground, and takes the layer of that depth.
                    int rise = ground.riseAt(x, z);
                    for (int y = bottom; y <= top; y++) {
                        if (disc.pointAt(shape(), x, y, z).orElse(null) instanceof DiscPoint.Platform(double belowTop, double aboveUnderside)) {
                            Optional<BlockStateProvider> block = palette.blockAt(new DiscPoint.Platform(belowTop + rise, aboveUnderside));
                            if (block.isPresent()) {
                                sink.accept(x, y, z, block.get());
                            }
                        }
                    }
                }
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

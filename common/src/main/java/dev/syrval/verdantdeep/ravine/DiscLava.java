package dev.syrval.verdantdeep.ravine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a disc's lava is left out, for the columns of one chunk. The game's lava sets fire to what burns within three blocks
 * of it, and a fire once lit runs through a disc's trees and along the hole's roots. So lava keeps {@link DiscWater#clear()}
 * blocks from the platform of every other disc that lies about level with it, where that disc's growth would stand beside
 * it, and from every root. Two discs that both hold lava have nothing to keep from each other.
 *
 * <p>Only what writes the lava and what grows over it ask here. To the rest the ponds are where {@link DiscWater} says, so
 * ground and ruins keep off a few columns that hold no lava after all.
 */
final class DiscLava {
    // The most a theme may ask for: what comes within this of a chunk's columns is in the chunks round it.
    static final int MAX_CLEAR = 16;
    // A disc whose top is within this many blocks of the lava's height, over or under, grows things beside it.
    private static final int LEVEL_WITH = 6;
    // Lava lights what burns from its own height to this far over it.
    private static final int FIRE_RISE = 3;
    private static final int CHUNK_SIZE = 16;

    private final CellDiscs discs;
    private final Set<Root.Knot> knots = new LinkedHashSet<>();

    /** For the chunk whose lowest corner is {@code (minX, minZ)}. */
    DiscLava(CellDiscs discs, int minX, int minZ) {
        this.discs = discs;
        RootLayout roots = discs.roots();
        for (int chunkX = -1; chunkX <= 1; chunkX++) {
            for (int chunkZ = -1; chunkZ <= 1; chunkZ++) {
                for (RootLayout.Stretch stretch : roots.near(minX + chunkX * CHUNK_SIZE, minZ + chunkZ * CHUNK_SIZE)) {
                    List<Root.Knot> all = roots.roots().get(stretch.root()).knots();
                    knots.add(all.get(stretch.knot()));
                    knots.add(all.get(stretch.knot() + 1));
                }
            }
        }
    }

    /** Whether lava with its top block at {@code y}, in a column of the {@code index}-th disc, is {@code clear} blocks from what burns. */
    boolean liesClear(int index, int x, int y, int z, int clear) {
        if (clear == 0) {
            return true;
        }
        List<Disc> all = discs.layout().discs();
        for (int i = 0; i < all.size(); i++) {
            Disc other = all.get(i);
            double fromAxis = Math.hypot(x - other.x(), z - other.z());
            if (i == index || fromAxis >= other.radius() + clear || discs.themes().get(i).filter(DiscTheme::holdsLava).isPresent()) {
                continue;
            }
            if (Math.abs(other.topBlockAt(fromAxis) - y) <= LEVEL_WITH) {
                return false;
            }
        }
        for (Root.Knot knot : knots) {
            if (knot.y() > y - knot.radius() - 1 && knot.y() < y + FIRE_RISE + 1 + knot.radius()
                    && Math.hypot(knot.x() - x, knot.z() - z) < knot.radius() + clear) {
                return false;
            }
        }
        return true;
    }
}

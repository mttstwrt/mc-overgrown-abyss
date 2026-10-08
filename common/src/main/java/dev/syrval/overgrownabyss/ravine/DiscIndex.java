package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;

/**
 * The discs near each column of a hole, so that a sample looks only at those: a tall hole has over a hundred discs, and
 * nothing of a disc is further from its axis than its platform's rim. The columns are taken a square of them at a time, each
 * square with one list in the layout's own order. Built once with the layout and then only read.
 */
final class DiscIndex {
    // Blocks along one square of columns.
    private static final int SQUARE = 16;

    private final int firstX;
    private final int firstZ;
    private final int across;
    private final List<List<Disc>> squares;

    /** The discs within {@code margin} blocks of each column; further from a column than that a disc is left out for it. */
    DiscIndex(List<Disc> discs, double margin) {
        double minX = discs.stream().mapToDouble(disc -> disc.x() - disc.radius() - margin).min().orElse(0);
        double maxX = discs.stream().mapToDouble(disc -> disc.x() + disc.radius() + margin).max().orElse(0);
        double minZ = discs.stream().mapToDouble(disc -> disc.z() - disc.radius() - margin).min().orElse(0);
        double maxZ = discs.stream().mapToDouble(disc -> disc.z() + disc.radius() + margin).max().orElse(0);
        this.firstX = squareOf(minX);
        this.firstZ = squareOf(minZ);
        this.across = discs.isEmpty() ? 0 : squareOf(maxX) - firstX + 1;
        int down = discs.isEmpty() ? 0 : squareOf(maxZ) - firstZ + 1;
        var lists = new ArrayList<List<Disc>>(across * down);
        for (int squareZ = firstZ; squareZ < firstZ + down; squareZ++) {
            for (int squareX = firstX; squareX < firstX + across; squareX++) {
                lists.add(reaching(discs, margin, squareX * SQUARE, squareZ * SQUARE));
            }
        }
        this.squares = List.copyOf(lists);
    }

    private static int squareOf(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), SQUARE);
    }

    // The discs that come within the margin of any column of the square whose lowest corner is given.
    private static List<Disc> reaching(List<Disc> discs, double margin, int minX, int minZ) {
        return discs.stream().filter(disc -> {
            double dx = disc.x() - Math.clamp(disc.x(), minX, minX + SQUARE);
            double dz = disc.z() - Math.clamp(disc.z(), minZ, minZ + SQUARE);
            return Math.hypot(dx, dz) <= disc.radius() + margin;
        }).toList();
    }

    /** The discs near a column, in the layout's order; none where no disc is near. */
    List<Disc> near(double x, double z) {
        int squareX = squareOf(x) - firstX;
        int squareZ = squareOf(z) - firstZ;
        if (squareX < 0 || squareX >= across || squareZ < 0 || squareZ * across + squareX >= squares.size()) {
            return List.of();
        }
        return squares.get(squareZ * across + squareX);
    }
}

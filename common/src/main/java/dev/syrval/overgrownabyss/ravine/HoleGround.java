package dev.syrval.overgrownabyss.ravine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ground over one hole, as far as it is below the hole's top. The discs of a hole lie over one another, so they ask about
 * the same columns: the ground is read on a grid and each column kept. A layout reads it while it lays out its discs and
 * keeps it, so like the layout it is safe to share between worker threads.
 */
final class HoleGround {
    // Blocks between the columns read. The ground between two of them is taken to be no lower than the ceiling margin covers,
    // which on a cliff it is not: at 16 blocks twice as many domes came out through steep ground as at 8.
    static final int GRID = 8;

    private final SurfaceProbe probe;
    private final int top;
    private final Map<Long, Integer> heights = new ConcurrentHashMap<>();

    HoleGround(SurfaceProbe probe, int top) {
        this.probe = probe;
        this.top = top;
    }

    /**
     * The tallest dome a disc may have for its roof to stay {@code margin} blocks under the ground everywhere over it: read at
     * its centre and at the grid's columns inside its rim. A dome's roof comes down towards the rim, so ground there limits it
     * less than ground over the middle. Below 0 where the ground is not even that far above the platform.
     */
    double domeRoom(double x, double z, double radius, double floor, double margin) {
        double room = heightAt((int) Math.floor(x), (int) Math.floor(z)) - margin - floor;
        for (int gridX = Math.floorDiv((int) Math.floor(x - radius), GRID); gridX * GRID <= x + radius; gridX++) {
            for (int gridZ = Math.floorDiv((int) Math.floor(z - radius), GRID); gridZ * GRID <= z + radius; gridZ++) {
                double share = Math.hypot(gridX * GRID - x, gridZ * GRID - z) / radius;
                if (share < 1) {
                    room = Math.min(room, (heightAt(gridX * GRID, gridZ * GRID) - margin - floor) / Math.sqrt(1 - share * share));
                }
            }
        }
        return room;
    }

    /** The ground's height at a column, or the hole's top where the ground is at least that high. */
    int heightAt(int x, int z) {
        return heights.computeIfAbsent((long) x << 32 | z & 0xFFFFFFFFL, column -> probe.heightAt(x, z, top));
    }
}

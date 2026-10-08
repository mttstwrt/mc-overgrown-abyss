package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.Optional;

/**
 * The resolved vertical extent of the ravines in one level, or of one hole: the flat cavern floor and the rim height, in
 * blocks. A cone whose top follows the ground (see {@link RimSettings}) also has an {@code edge}: how high its mouth stands
 * at each of a number of evenly spaced angles round the axis, the first to the east and turning towards the south. On a slope
 * the uphill side of the mouth stands above the top, and the bowl above the top starts from the edge on each side. Without
 * an edge the mouth is level, at the top.
 */
public record RavineBounds(int floorY, int topY, List<Integer> edge) {

    public RavineBounds {
        if (topY <= floorY) {
            throw new IllegalArgumentException("top " + topY + " must be above floor " + floorY);
        }
        edge = List.copyOf(edge);
        for (int height : edge) {
            if (height < topY) {
                throw new IllegalArgumentException("the mouth's edge at " + height + " must not be below the top " + topY);
            }
        }
    }

    /** Bounds with a level mouth. */
    public RavineBounds(int floorY, int topY) {
        this(floorY, topY, List.of());
    }

    public static Optional<RavineBounds> of(int floorY, int topY) {
        return of(floorY, topY, List.of());
    }

    static Optional<RavineBounds> of(int floorY, int topY, List<Integer> edge) {
        return topY > floorY ? Optional.of(new RavineBounds(floorY, topY, edge)) : Optional.empty();
    }

    /** The highest the mouth's edge stands. */
    public int highestEdge() {
        return edge.stream().mapToInt(Integer::intValue).max().orElse(topY);
    }

    /** How high the mouth's edge stands on the side of the axis that {@code (dx, dz)} points to, blended between the two angles nearest. */
    double edgeAt(double dx, double dz) {
        if (edge.isEmpty()) {
            return topY;
        }
        double turn = Math.atan2(dz, dx) / (2 * Math.PI);
        double at = (turn - Math.floor(turn)) * edge.size();
        int from = (int) at % edge.size();
        int to = (from + 1) % edge.size();
        return edge.get(from) + (edge.get(to) - edge.get(from)) * (at - Math.floor(at));
    }
}

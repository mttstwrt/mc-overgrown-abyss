package dev.syrval.overgrownabyss.ravine;

import java.util.Optional;

/** The resolved vertical extent of the ravines in one level: the flat cavern floor and the rim height, in blocks. */
public record RavineBounds(int floorY, int topY) {

    public RavineBounds {
        if (topY <= floorY) {
            throw new IllegalArgumentException("top " + topY + " must be above floor " + floorY);
        }
    }

    public static Optional<RavineBounds> of(int floorY, int topY) {
        return topY > floorY ? Optional.of(new RavineBounds(floorY, topY)) : Optional.empty();
    }
}

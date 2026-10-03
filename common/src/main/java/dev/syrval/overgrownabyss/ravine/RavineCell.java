package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.stream.Stream;

/**
 * One ravine: a horizontal segment through {@code (centreX, centreZ)} along the unit vector {@code (dirX, dirZ)},
 * widened to {@code halfWidth} at the top. All values in blocks.
 */
public record RavineCell(double centreX, double centreZ, double dirX, double dirZ, double halfLength, double halfWidth) {

    public RavineCell {
        if (halfLength < 0 || halfWidth <= 0) {
            throw new IllegalArgumentException("ravine size must be positive");
        }
        if (Math.abs(dirX * dirX + dirZ * dirZ - 1) > 1e-6) {
            throw new IllegalArgumentException("direction must be a unit vector");
        }
    }

    /** Horizontal distance from {@code (x, z)} to the ravine's centre line. */
    public double distanceToAxis(double x, double z) {
        double dx = x - centreX;
        double dz = z - centreZ;
        double along = Math.clamp(dx * dirX + dz * dirZ, -halfLength, halfLength);
        double px = dx - along * dirX;
        double pz = dz - along * dirZ;
        return Math.sqrt(px * px + pz * pz);
    }

    /** Horizontal distance from {@code (x, z)} to the ravine's centre point. */
    public double distanceToCentre(double x, double z) {
        double dx = x - centreX;
        double dz = z - centreZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A block column. */
    public record Column(int x, int z) {}

    /** Columns along the centre line, from one end to the other, that must all be land for the ravine to exist. */
    public List<Column> samplePoints() {
        return Stream.of(-1.0, -0.5, 0.0, 0.5, 1.0)
                .map(t -> new Column(
                        (int) Math.floor(centreX + dirX * halfLength * t),
                        (int) Math.floor(centreZ + dirZ * halfLength * t)))
                .toList();
    }
}

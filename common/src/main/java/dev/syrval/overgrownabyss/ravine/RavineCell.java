package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.stream.Stream;

/**
 * One ravine. Its straight chord runs through {@code (centreX, centreZ)} along the unit vector {@code (dirX, dirZ)},
 * {@code halfLength} each way (0 makes a round hole), widened to {@code halfWidth} at the top. The centre line then
 * curves away from the chord ({@code bend}) and sways sideways with height ({@code lean}). {@code hash} identifies the
 * cell and seeds the positions of its disc rooms, see {@link RavineDomes}. All values in blocks.
 */
public record RavineCell(
        double centreX,
        double centreZ,
        double dirX,
        double dirZ,
        double halfLength,
        double halfWidth,
        Bend bend,
        Lean lean,
        long hash) {

    public RavineCell {
        if (halfLength < 0 || halfWidth <= 0) {
            throw new IllegalArgumentException("ravine size must be positive");
        }
        if (Math.abs(dirX * dirX + dirZ * dirZ - 1) > 1e-6) {
            throw new IllegalArgumentException("direction must be a unit vector");
        }
    }

    /** A ravine with the given curves and no disc rooms of its own (hash 0). */
    public RavineCell(double centreX, double centreZ, double dirX, double dirZ, double halfLength, double halfWidth, Bend bend, Lean lean) {
        this(centreX, centreZ, dirX, dirZ, halfLength, halfWidth, bend, lean, 0);
    }

    /** A straight, plain ravine. */
    public RavineCell(double centreX, double centreZ, double dirX, double dirZ, double halfLength, double halfWidth) {
        this(centreX, centreZ, dirX, dirZ, halfLength, halfWidth, Bend.NONE, Lean.NONE);
    }

    /** A point seen from the ravine: how far along the chord, and how far to the left of it. */
    public record Frame(double along, double sideways) {}

    public Frame frame(double x, double z) {
        double dx = x - centreX;
        double dz = z - centreZ;
        return new Frame(dx * dirX + dz * dirZ, -dx * dirZ + dz * dirX);
    }

    /** Horizontal distance from {@code (x, z)} to the ravine's centre point. */
    public double distanceToCentre(double x, double z) {
        double dx = x - centreX;
        double dz = z - centreZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A block column. */
    public record Column(int x, int z) {}

    /** Columns along the chord, from one end to the other, that must all be land for the ravine to exist. */
    public List<Column> samplePoints() {
        return Stream.of(-1.0, -0.5, 0.0, 0.5, 1.0)
                .map(t -> new Column(
                        (int) Math.floor(centreX + dirX * halfLength * t),
                        (int) Math.floor(centreZ + dirZ * halfLength * t)))
                .toList();
    }

    /**
     * Sideways offset of the centre line from the chord, as a function of {@code u} in [-1, 1] along it: a parabola
     * that bends both ends to one side, plus a sine that swings one end left and the other right. Both vanish at the
     * centre so the shaft stays above the cavern.
     */
    public record Bend(double bend, double wiggle) {
        public static final Bend NONE = new Bend(0, 0);

        public double offset(double u) {
            return bend * u * u + wiggle * Math.sin(Math.PI * u);
        }

        /** Rate of change of {@link #offset} with {@code u}. */
        public double slope(double u) {
            return 2 * bend * u + wiggle * Math.PI * Math.cos(Math.PI * u);
        }
    }

    /**
     * Sideways shift of the whole shaft along the unit vector {@code (dirX, dirZ)} at height fraction {@code t} in
     * [0, 1] from floor to rim: a lean that grows with height, plus a bow that is largest halfway. Zero at the floor.
     */
    public record Lean(double dirX, double dirZ, double lean, double bow) {
        public static final Lean NONE = new Lean(1, 0, 0, 0);

        public double shift(double t) {
            return lean * t * t + bow * Math.sin(Math.PI * t);
        }
    }
}

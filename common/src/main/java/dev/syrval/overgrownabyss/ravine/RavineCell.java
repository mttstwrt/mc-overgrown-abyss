package dev.syrval.overgrownabyss.ravine;

import java.util.List;
import java.util.stream.Stream;

/**
 * One ravine. Its straight chord runs through {@code (centreX, centreZ)} along the unit vector {@code (dirX, dirZ)},
 * {@code halfLength} each way (0 makes a round hole), widened to {@code halfWidth} at the top. The centre line then
 * curves away from the chord ({@code bend}), sways sideways with height ({@code lean}), each wall swells and narrows
 * with height ({@code wobble}), {@code bridges} cross it and {@code ledges} stand out from its walls. All values in blocks.
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
        Wobble wobble,
        List<Bridge> bridges,
        List<Ledge> ledges,
        Tiers tiers,
        List<Disc> discs) {

    public RavineCell {
        if (halfLength < 0 || halfWidth <= 0) {
            throw new IllegalArgumentException("ravine size must be positive");
        }
        if (Math.abs(dirX * dirX + dirZ * dirZ - 1) > 1e-6) {
            throw new IllegalArgumentException("direction must be a unit vector");
        }
        bridges = List.copyOf(bridges);
        ledges = List.copyOf(ledges);
        discs = List.copyOf(discs);
    }

    /** A ravine without discs. */
    public RavineCell(
            double centreX, double centreZ, double dirX, double dirZ, double halfLength, double halfWidth,
            Bend bend, Lean lean, Wobble wobble, List<Bridge> bridges, List<Ledge> ledges, Tiers tiers) {
        this(centreX, centreZ, dirX, dirZ, halfLength, halfWidth, bend, lean, wobble, bridges, ledges, tiers, List.of());
    }

    /** A ravine with every level at the normal width. */
    public RavineCell(
            double centreX, double centreZ, double dirX, double dirZ, double halfLength, double halfWidth,
            Bend bend, Lean lean, Wobble wobble, List<Bridge> bridges, List<Ledge> ledges) {
        this(centreX, centreZ, dirX, dirZ, halfLength, halfWidth, bend, lean, wobble, bridges, ledges, Tiers.NONE);
    }

    /** A straight, plain ravine. */
    public RavineCell(double centreX, double centreZ, double dirX, double dirZ, double halfLength, double halfWidth) {
        this(centreX, centreZ, dirX, dirZ, halfLength, halfWidth, Bend.NONE, Lean.NONE, Wobble.NONE, List.of(), List.of());
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

    /** The two walls swell and narrow independently, which leaves overhangs where one side narrows below a bulge. */
    public record Wobble(Side left, Side right) {
        public static final Wobble NONE = new Wobble(Side.NONE, Side.NONE);
    }

    /** One wall's width factor offset as a function of height: two out-of-step sine waves, each at most {@code amplitude}. */
    public record Side(double amplitude, double period, double phase) {
        public static final Side NONE = new Side(0, 1, 0);

        public Side {
            if (period <= 0) {
                throw new IllegalArgumentException("period must be positive");
            }
        }

        public double at(double y) {
            double slow = Math.sin(2 * Math.PI * y / period + phase);
            double quick = Math.sin(2 * Math.PI * y / (period * 0.618) + phase * 1.7);
            return amplitude * (0.6 * slow + 0.4 * quick);
        }
    }

    /**
     * Stacked levels of the shaft above the cavern. A level starts at {@code start}, a fraction of the way up from the
     * cavern roof to the rim; the opening there is moved {@code shift} half widths to the left (negative: right) and
     * scaled by {@code width}. The first level always starts at 0 and is neutral so the shaft meets the cavern.
     */
    public record Tiers(List<Level> levels) {
        public static final Tiers NONE = new Tiers(List.of(Level.NEUTRAL));

        public Tiers {
            levels = List.copyOf(levels);
            if (levels.isEmpty() || levels.get(0).start() != 0) {
                throw new IllegalArgumentException("the first level must start at 0");
            }
        }

        /** The shift and width at {@code y}, blending from the level below over {@code ramp} blocks. */
        public Level at(double y, double low, double high, double ramp) {
            double span = high - low;
            if (span <= 0 || levels.size() == 1) {
                return levels.get(0);
            }
            int current = 0;
            while (current + 1 < levels.size() && low + levels.get(current + 1).start() * span <= y) {
                current++;
            }
            if (current == 0) {
                return levels.get(0);
            }
            Level above = levels.get(current);
            Level below = levels.get(current - 1);
            double t = Math.clamp((y - (low + above.start() * span)) / ramp, 0, 1);
            t = t * t * (3 - 2 * t);
            return new Level(0, below.shift() + (above.shift() - below.shift()) * t, below.width() + (above.width() - below.width()) * t);
        }

        public record Level(double start, double shift, double width) {
            public static final Level NEUTRAL = new Level(0, 0, 1);
        }
    }

    /**
     * A round plate on the shelf at the start of level {@code level} (an index into {@link Tiers#levels()}), rooted in
     * the {@code side} wall of the level below. {@code along} is its position along the chord, {@code yOffset} how far its
     * top sits from the boundary, {@code radius} and {@code thickness} its size.
     */
    public record Disc(int level, double along, int side, double yOffset, double radius, double thickness) {}

    /** A rock arch across the ravine: where along the chord, how high (fraction of the allowed span), and its size. */
    public record Bridge(double along, double height, double width, double thickness) {}

    /**
     * A flat shelf on one wall. {@code side} is +1 for the left wall (positive {@code sideways}) or -1 for the right,
     * {@code height} is a fraction of the allowed span (the same scale as a bridge's, so the two can share a top),
     * {@code length} runs along the wall, {@code depth} reaches into the shaft and {@code yaw} turns it away from the
     * wall's direction, in radians.
     */
    public record Ledge(double along, int side, double height, double length, double depth, double thickness, double yaw) {}
}

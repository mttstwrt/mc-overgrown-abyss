package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Grows one root: the line down its middle, a block at a time from where it starts, through the stops it was given. At each
 * step it turns, by no more than a root of its thickness can bend, towards what it wants: the stop it is making for, a slow
 * wander to one side and the other, the wall it runs along, and away from the clear air round the axis and, if it is one
 * that dodges, from rock that is in its way. So it winds, and a root that dodges bends round the discs and stems it meets
 * without being told where they are.
 *
 * <p>Every draw comes from the hole's hash, the root's own row and how far along it the root is, so a root grows the same
 * way every time.
 */
final class RootWalk {
    // Blocks of air a root tries to keep between itself and rock it is not making for.
    private static final double CLEARANCE = 3;
    // How hard rock in its way turns a root aside, against the pull of its stop, which is 1.
    private static final double AVOIDANCE = 2.5;
    // Blocks along a root from one swing of its wander to the next, about, and from one swing away from the wall to the next.
    private static final double WANDER_LENGTH = 30;
    private static final double SWING_LENGTH = 70;
    // The steepest a root climbs to a rim if nothing else limits it.
    private static final double STEEPEST_TO_A_RIM = 1;
    // Blocks from the end of a root that ends in rock within which the rock no longer turns it aside.
    private static final double BURIAL = 8;
    // Further from a rim than this a root makes straight for the disc; nearer, it goes round the rim.
    private static final double FAR_FROM_A_RIM = 24;
    // How far from the nearest air the middle of a root counts as inside the rock.
    private static final double IN_ROCK = -0.5;
    private static final int MAX_KNOTS = 4000;
    // Blocks a root goes on without coming any nearer to its stop before it gives that stop up, and half as far again as the
    // stop was when it set out for it: going round a disc in its way takes it no nearer for a while.
    private static final int STALLED = 40;
    private static final int NOISE_BASE = 1_100_000;

    private RootWalk() {}

    /** What a root does about the rock between it and a point it is making for. */
    enum Ground {
        /** The point is in the air and so is the way to it. */
        AIR,
        /**
         * The point is on rock, in it or in another root: the root turns aside from rock until it is nearly there, then goes
         * straight to the point.
         */
        MEETS_ROCK,
        /** The way to the point lies in rock, such as along the floor, and the root goes through whatever is there. */
        THROUGH_ROCK
    }

    /** Somewhere a root makes for, and the radius it has when it gets there. */
    sealed interface Stop {
        double radius();

        /** A point to go to. */
        record Point(double x, double y, double z, double radius, Ground ground) implements Stop {}

        /**
         * The rim of a disc, for a root that climbs to it: the root goes round the outside of the rim, rising as it goes,
         * until its highest block is level with the rim's, and ends there, turned in to the platform's edge.
         */
        record Rim(Disc disc, double radius) implements Stop {}
    }

    /**
     * One root to grow.
     *
     * @param row      which of the hole's roots this is, for its draws
     * @param hand     which way round it goes where it has the choice: 1 or -1
     * @param wallPull how strongly it keeps to the wall of the hole on the way; 0 not at all
     * @param dodges   whether rock in its way turns it aside. One that does not goes through whatever it meets, a platform
     *                 or a stem as much as the wall
     * @param maxSlope the most it climbs or falls for each block forward, for a root that is to be walked
     */
    record Plan(
            Root.Kind kind, int row, double x, double y, double z, double headingX, double headingY, double headingZ, double radius,
            List<Stop> stops, int hand, double wallPull, boolean dodges, OptionalDouble maxSlope) {

        Plan {
            stops = List.copyOf(stops);
        }
    }

    /** A root as far as it grew, and whether it met its last stop. One that did not ends in a tip. */
    record Grown(List<Root.Knot> knots, boolean arrived) {}

    static Grown grow(RootSpace space, RootSettings roots, Plan plan) {
        return new Walker(space, roots, plan).walk();
    }

    // Which way to go for a stop, how far it still is, and whether the root is there.
    private record Aim(double x, double y, double z, double left, boolean arrived) {}

    // Where a root was when it was nearest to the stop it is making for: how many knots it had, and its place and heading.
    private record Nearest(double left, int knots, double x, double y, double z, double headingX, double headingY, double headingZ, double radius) {}

    private static final class Walker {
        private final RootSpace space;
        private final RootSettings roots;
        private final Plan plan;
        private final boolean crossing;
        private final List<Root.Knot> knots = new ArrayList<>();
        private double x;
        private double y;
        private double z;
        private double headingX;
        private double headingY;
        private double headingZ;
        private double radius;

        Walker(RootSpace space, RootSettings roots, Plan plan) {
            this.space = space;
            this.roots = roots;
            this.plan = plan;
            this.crossing = plan.kind() == Root.Kind.CROSSING;
            this.x = plan.x();
            this.y = plan.y();
            this.z = plan.z();
            this.radius = plan.radius();
            double length = Math.sqrt(plan.headingX() * plan.headingX() + plan.headingY() * plan.headingY() + plan.headingZ() * plan.headingZ());
            this.headingX = plan.headingX() / length;
            this.headingY = plan.headingY() / length;
            this.headingZ = plan.headingZ() / length;
        }

        Grown walk() {
            knots.add(new Root.Knot(x, y, z, radius));
            List<Stop> stops = plan.stops();
            for (int at = 0; at < stops.size(); at++) {
                boolean last = at == stops.size() - 1;
                // A stop on the way that could not be met is passed by; the last one is where the root was going.
                if (!reach(stops.get(at), last) && last) {
                    return new Grown(tapered(), false);
                }
            }
            return new Grown(List.copyOf(knots), true);
        }

        private boolean reach(Stop stop, boolean last) {
            double from = radius;
            double length = Math.max(aimAt(stop).left(), 1);
            int budget = (int) (3 * length + 60) + (stop instanceof Stop.Rim rim ? (int) (Math.PI * rim.disc().radius()) : 0);
            Nearest nearest = here(Double.POSITIVE_INFINITY);
            for (int step = 0; step < budget && knots.size() < MAX_KNOTS; step++) {
                Aim aim = aimAt(stop);
                if (aim.arrived()) {
                    finishAt(stop);
                    return true;
                }
                if (aim.left() < nearest.left()) {
                    nearest = here(aim.left());
                }
                // Going away from a point it has been near: a root cannot turn on the spot, so it has missed it.
                boolean missedAPoint = stop instanceof Stop.Point && aim.left() > nearest.left() + 1.5 && nearest.left() < 16;
                // No nearer for a long while: rock it will not go into lies between it and the stop.
                if (missedAPoint || knots.size() - nearest.knots() > STALLED + length / 2) {
                    return giveUp(stop, nearest) || missedAPoint && !last;
                }
                Ground ground = stop instanceof Stop.Point point ? point.ground() : Ground.AIR;
                Root.Knot before = knots.getLast();
                step(stop, aim, ground);
                radius = from + (stop.radius() - from) * Math.clamp(1 - aim.left() / length, 0, 1);
                keepInBounds();
                knots.add(new Root.Knot(x, y, z, radius));
                // Well into the rock it was making for, which is as good as the point itself.
                if (ground == Ground.MEETS_ROCK && nearItsEnd(aim) && space.air(x, y, z) < -(radius / 2 + 1)) {
                    return true;
                }
                // A root that is to be walked is no use where it runs inside the rock, nor where being held out of the clear
                // air has made it steeper than its limit, so it is given up there.
                if (plan.maxSlope().isPresent() && (knots.size() > plan.radius() + 4 && space.air(x, y, z) < IN_ROCK
                        || Math.abs(y - before.y()) > 1.1 * plan.maxSlope().getAsDouble() * Math.hypot(x - before.x(), z - before.z()))) {
                    return false;
                }
            }
            return false;
        }

        /**
         * What a root does about a stop it will not reach by growing on: it goes back to where it was nearest, and if the
         * stop is on rock and that was near enough, straight to it from there. Whether it met the stop after all.
         */
        private boolean giveUp(Stop stop, Nearest nearest) {
            backTo(nearest);
            if (stop instanceof Stop.Point point && point.ground() != Ground.AIR && nearest.left() <= Math.max(10, 2.5 * radius)) {
                straightTo(point);
                return true;
            }
            return false;
        }

        // Within the last blocks before a stop on rock, where the rock no longer turns the root aside.
        private boolean nearItsEnd(Aim aim) {
            return aim.left() < Math.max(BURIAL, 3 * radius);
        }

        // One block on, turned as far towards everything the root wants as it can turn.
        private void step(Stop stop, Aim aim, Ground ground) {
            double[] want = {aim.x(), aim.y(), aim.z()};
            double along = knots.size();
            double fromAxis = space.fromAxis(x, z);
            double outX = fromAxis > 1e-6 ? (x - space.cell().centreX()) / fromAxis : 1;
            double outZ = fromAxis > 1e-6 ? (z - space.cell().centreZ()) / fromAxis : 0;
            wander(want, along, aim, ground);
            if (plan.wallPull() > 0 && ground != Ground.THROUGH_ROCK && y > space.bounds().floorY() && y < space.bounds().topY()) {
                double pull = wallPull(fromAxis, along) * Math.min(1, aim.left() / 20);
                want[0] += outX * pull;
                want[2] += outZ * pull;
            }
            boolean buried = ground == Ground.THROUGH_ROCK || ground == Ground.MEETS_ROCK && nearItsEnd(aim);
            // A root starts in the wall, in a platform or in another root, and has to come out of it first.
            if (plan.dodges() && along >= plan.radius() + 3 && !buried) {
                // The rim it is closing with is not in its way; the wall that rim may run into still is.
                dodge(want, stop instanceof Stop.Rim rim && aim.left() < 12 ? Optional.of(rim.disc()) : Optional.empty());
            }
            if (!crossing) {
                double keep = space.clear(y) + radius + 1;
                double push = Math.max(0, 3 * (keep + 6 - fromAxis) / 6);
                want[0] += outX * push;
                want[2] += outZ * push;
            }
            turnTowards(want[0], want[1], want[2]);
            x += headingX;
            y += headingY;
            z += headingZ;
        }

        // To one side and the other of the way the root is going, and a little up and down; less at its start and near a stop.
        private void wander(double[] want, double along, Aim aim, Ground ground) {
            double fade = Math.min(1, along / 8) * Math.min(1, aim.left() / 12) * roots.winding();
            double flat = Math.hypot(headingX, headingZ);
            if (flat > 1e-6) {
                double sideways = 2 * RavineCells.smoothAt(space.cell().hash(), NOISE_BASE, along / WANDER_LENGTH, plan.row()) - 1;
                want[0] += fade * 1.6 * sideways * headingZ / flat;
                want[2] -= fade * 1.6 * sideways * headingX / flat;
            }
            if (plan.maxSlope().isEmpty() && ground != Ground.THROUGH_ROCK) {
                want[1] += fade * 0.5 * (2 * RavineCells.smoothAt(space.cell().hash(), NOISE_BASE + 1, along / WANDER_LENGTH, plan.row()) - 1);
            }
        }

        /**
         * Away from rock a little way ahead that the root would otherwise come too near, along the line on which the air
         * opens fastest. Rock of the disc it is {@code makingFor} is let be where nothing else is nearer.
         */
        private void dodge(double[] want, Optional<Disc> makingFor) {
            double look = radius + 2;
            double aheadX = x + headingX * look;
            double aheadY = y + headingY * look;
            double aheadZ = z + headingZ * look;
            double room = space.air(aheadX, aheadY, aheadZ) - radius;
            if (room >= CLEARANCE) {
                return;
            }
            if (makingFor.isPresent() && makingFor.get().rockDistance(space.settings().discs(), aheadX, aheadY, aheadZ) - radius <= room + 0.25) {
                return;
            }
            double awayX = space.air(aheadX + 1, aheadY, aheadZ) - space.air(aheadX - 1, aheadY, aheadZ);
            double awayY = space.air(aheadX, aheadY + 1, aheadZ) - space.air(aheadX, aheadY - 1, aheadZ);
            double awayZ = space.air(aheadX, aheadY, aheadZ + 1) - space.air(aheadX, aheadY, aheadZ - 1);
            double steepness = Math.sqrt(awayX * awayX + awayY * awayY + awayZ * awayZ);
            if (steepness > 1e-6) {
                double push = AVOIDANCE * Math.min(2, (CLEARANCE - room) / CLEARANCE) / steepness;
                want[0] += awayX * push;
                want[1] += awayY * push;
                want[2] += awayZ * push;
            }
        }

        private Nearest here(double left) {
            return new Nearest(left, knots.size(), x, y, z, headingX, headingY, headingZ, radius);
        }

        // Takes the root back to where it was nearest its stop, dropping what it has grown since.
        private void backTo(Nearest nearest) {
            knots.subList(nearest.knots(), knots.size()).clear();
            x = nearest.x();
            y = nearest.y();
            z = nearest.z();
            headingX = nearest.headingX();
            headingY = nearest.headingY();
            headingZ = nearest.headingZ();
            radius = nearest.radius();
        }

        // How hard the wall draws the root to it: towards a lane that runs along the wall and swings out from it now and then.
        private double wallPull(double fromAxis, double along) {
            double wall = space.wall(x, z, y);
            double room = Math.max(0, wall - space.clear(y) - 3 * radius);
            double swing = RavineCells.smoothAt(space.cell().hash(), NOISE_BASE + 2, along / SWING_LENGTH, plan.row());
            double lane = wall - radius - 1 - swing * swing * room * 0.6;
            return Math.clamp((lane - fromAxis) / 10, -0.6, 0.6) * plan.wallPull();
        }

        private Aim aimAt(Stop stop) {
            return switch (stop) {
                case Stop.Point point -> aimAt(point);
                case Stop.Rim rim -> aimAt(rim);
            };
        }

        private Aim aimAt(Stop.Point point) {
            double dx = point.x() - x;
            double dy = point.y() - y;
            double dz = point.z() - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // A point in the air is only somewhere to pass; one on rock is met.
            if (distance <= (point.ground() == Ground.AIR ? Math.max(4, radius + 2) : 1.5)) {
                return new Aim(0, 0, 0, distance, true);
            }
            return new Aim(dx / distance, dy / distance, dz / distance, distance, false);
        }

        // What a root does on reaching a stop: it ends on a rim turned in to it, and goes the last blocks to a point on rock.
        private void finishAt(Stop stop) {
            switch (stop) {
                case Stop.Rim rim -> turnIn(rim.disc());
                case Stop.Point point -> {
                    if (point.ground() != Ground.AIR) {
                        straightTo(point);
                    }
                }
            }
        }

        private void straightTo(Stop.Point point) {
            double dx = point.x() - x;
            double dy = point.y() - y;
            double dz = point.z() - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 1e-6) {
                return;
            }
            int steps = (int) Math.ceil(distance);
            double from = radius;
            for (int step = 1; step <= steps; step++) {
                x += dx / steps;
                y += dy / steps;
                z += dz / steps;
                radius = from + (point.radius() - from) * step / steps;
                keepInBounds();
                knots.add(new Root.Knot(x, y, z, radius));
            }
            headingX = dx / distance;
            headingY = dy / distance;
            headingZ = dz / distance;
        }

        private Aim aimAt(Stop.Rim rim) {
            Disc disc = rim.disc();
            double fromX = x - disc.x();
            double fromZ = z - disc.z();
            double apart = Math.hypot(fromX, fromZ);
            double outX = apart > 1e-6 ? fromX / apart : headingX;
            double outZ = apart > 1e-6 ? fromZ / apart : headingZ;
            // The height of the root's middle that puts its highest block level with the rim's.
            double rise = disc.topBlockAt(disc.radius()) + 0.5 - radius - y;
            // While it still has height to gain or lose it keeps off the rim, and closes with it as it comes level.
            double gap = apart - (disc.radius() + radius - 1 + Math.min(Math.abs(rise) / 2, 8));
            double left = Math.max(gap, 0) + Math.abs(rise);
            if (Math.abs(rise) < 0.75 && Math.abs(gap) < 1.5) {
                return new Aim(0, 0, 0, left, true);
            }
            double roundX = -plan.hand() * outZ;
            double roundZ = plan.hand() * outX;
            double wantX;
            double wantZ;
            if (gap > FAR_FROM_A_RIM) {
                wantX = -outX + 0.35 * roundX;
                wantZ = -outZ + 0.35 * roundZ;
            } else {
                double inward = Math.clamp(gap / 5, -1, 1);
                wantX = roundX * (1 - Math.abs(inward) / 2) - outX * inward;
                wantZ = roundZ * (1 - Math.abs(inward) / 2) - outZ * inward;
            }
            double flat = Math.hypot(wantX, wantZ);
            double steepest = plan.maxSlope().orElse(STEEPEST_TO_A_RIM);
            double pitch = Math.clamp(rise / Math.max(Math.abs(gap) + 4, 4), -steepest, steepest);
            return new Aim(wantX / flat, pitch, wantZ / flat, left, false);
        }

        // A root bends no tighter than its thickness lets it, and one that is to be walked is never steeper than its limit.
        private void turnTowards(double wantX, double wantY, double wantZ) {
            double length = Math.sqrt(wantX * wantX + wantY * wantY + wantZ * wantZ);
            if (length < 1e-9) {
                return;
            }
            if (plan.maxSlope().isPresent()) {
                double steepest = plan.maxSlope().getAsDouble() * Math.hypot(wantX, wantZ);
                wantY = Math.clamp(wantY, -steepest, steepest);
                length = Math.sqrt(wantX * wantX + wantY * wantY + wantZ * wantZ);
            }
            wantX /= length;
            wantY /= length;
            wantZ /= length;
            double angle = Math.acos(Math.clamp(headingX * wantX + headingY * wantY + headingZ * wantZ, -1, 1));
            double most = 1 / Math.max(2.2 * radius, 6);
            if (angle > most) {
                double sine = Math.sin(angle);
                // Straight back the way it came there is no one way to turn; sideways is as good as any.
                if (sine < 1e-6) {
                    double flat = Math.hypot(headingX, headingZ);
                    wantX = flat > 1e-6 ? -plan.hand() * headingZ / flat : 1;
                    wantY = 0;
                    wantZ = flat > 1e-6 ? plan.hand() * headingX / flat : 0;
                    angle = Math.PI / 2;
                    sine = 1;
                }
                double keep = Math.sin(angle - most) / sine;
                double take = Math.sin(most) / sine;
                wantX = keep * headingX + take * wantX;
                wantY = keep * headingY + take * wantY;
                wantZ = keep * headingZ + take * wantZ;
            }
            if (plan.maxSlope().isPresent()) {
                double steepest = plan.maxSlope().getAsDouble() * Math.hypot(wantX, wantZ);
                wantY = Math.clamp(wantY, -steepest, steepest);
            }
            double turned = Math.sqrt(wantX * wantX + wantY * wantY + wantZ * wantZ);
            headingX = wantX / turned;
            headingY = wantY / turned;
            headingZ = wantZ / turned;
        }

        // Out of the clear air round the axis, which only a crossing root enters, and inside the reach of the hole.
        private void keepInBounds() {
            double fromAxis = space.fromAxis(x, z);
            double least = crossing ? 0 : space.clear(y) + radius + 0.5;
            double most = space.settings().maxReach() - radius - 2;
            if (fromAxis >= least && fromAxis <= most) {
                return;
            }
            double to = Math.clamp(fromAxis, least, most);
            double outX = fromAxis > 1e-6 ? (x - space.cell().centreX()) / fromAxis : 1;
            double outZ = fromAxis > 1e-6 ? (z - space.cell().centreZ()) / fromAxis : 0;
            x = space.cell().centreX() + outX * to;
            z = space.cell().centreZ() + outZ * to;
        }

        // The last few blocks of a root that ends on a rim: in to the platform's edge and a little down, so it ends in the disc.
        private void turnIn(Disc disc) {
            double fromX = x - disc.x();
            double fromZ = z - disc.z();
            double apart = Math.max(Math.hypot(fromX, fromZ), 1e-6);
            for (int step = 1; step <= 3; step++) {
                x -= fromX / apart;
                z -= fromZ / apart;
                y -= 0.3;
                keepInBounds();
                knots.add(new Root.Knot(x, y, z, radius));
            }
        }

        // A root that did not get where it was going thins to a tip over its last blocks.
        private List<Root.Knot> tapered() {
            int tip = Math.min(12, knots.size() / 3);
            var thinned = new ArrayList<>(knots);
            for (int i = 0; i < tip; i++) {
                int at = thinned.size() - 1 - i;
                Root.Knot knot = thinned.get(at);
                double share = (i + 1.0) / (tip + 1);
                thinned.set(at, new Root.Knot(knot.x(), knot.y(), knot.z(), Math.max(0.75, knot.radius() * share)));
            }
            return List.copyOf(thinned);
        }
    }
}

package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.util.InclusiveRange;

/**
 * Where each root of a hole starts and what it makes for, by its kind (see {@link RootSettings}); {@link RootWalk} grows it
 * from there.
 *
 * <ul>
 * <li>A great root leaves the wall under the mouth and goes round the hole as it comes down, now out along the wall and
 * now in to lie against the rim of a disc. In the bell at the foot of the hole it keeps to the wall, comes down to the floor
 * and runs on along it, half buried, before it dives. The discs of a layer leave little of the ring open between them, so
 * a great root does not go round what is in its way: it grows through a platform or a stem as it does through the floor.
 * <li>A crossing root spans the hole from wall to wall, sagging through the clear air round the axis, and likewise goes
 * through what it meets.
 * <li>A link climbs from the top of one disc, round the outside of a disc above it, to that disc's rim, in the open all
 * the way.
 * <li>A branch leaves another root for the nearest of several ends: another root, a disc's rim, the floor or the wall. It
 * bends round what is in its way.
 * </ul>
 *
 * <p>Every choice is drawn from the hole's hash and the root's place among the others, so a hole's roots are the same
 * every time.
 */
final class RootRoutes {
    static final int MAX_GREAT = 16;
    static final int MAX_CROSSING = 8;
    // Roots of one hole in all; branches stop being grown at this many.
    static final int MAX_ROOTS = 400;
    private static final int HASH_BASE = 1_000_000;
    // Hash indices kept apart for each root.
    private static final int DRAWS = 64;
    // The draw of a row that says how many roots of its kind the hole has.
    private static final int COUNT_DRAW = DRAWS - 1;
    // The rows of draws: great roots first, then crossing ones, then a link for each disc, then branches.
    private static final int FIRST_CROSSING = MAX_GREAT;
    private static final int FIRST_LINK = FIRST_CROSSING + MAX_CROSSING;
    private static final int FIRST_BRANCH = FIRST_LINK + ConeDiscLayout.MAX_DISCS;
    // A root shorter than this is not worth having.
    private static final int FEWEST_KNOTS = 12;
    // Blocks kept between a root's start or end in the wall and the ground over it.
    private static final double UNDER_GROUND = 3;
    // A branch leaves its parent no nearer than this to either of the parent's ends.
    private static final int CLEAR_OF_ENDS = 12;
    // Places round a disc's rim that are tried for a root to lie against.
    private static final int RIM_PLACES = 24;
    // The most a disc may be further round the hole, or less far, than a great root's fall carries it, in radians, for the
    // root to turn aside to it.
    private static final double OFF_ITS_FALL = 0.6;
    // Places tried for a root's start or end in the wall before the root is given up.
    private static final int ANCHOR_TRIES = 6;

    private final RootSpace space;
    private final RootSettings roots;
    private final long hash;
    private final List<Root> grown = new ArrayList<>();
    // How many times over each root in the list is a branch: 0 for one that is not.
    private final List<Integer> depths = new ArrayList<>();
    private int branches;

    private RootRoutes(RootSpace space, RootSettings roots) {
        this.space = space;
        this.roots = roots;
        this.hash = space.cell().hash();
    }

    /** The roots of a hole, in a fixed order. */
    static List<Root> grow(RootSpace space, RootSettings roots) {
        var routes = new RootRoutes(space, roots);
        routes.great();
        routes.crossing();
        routes.links();
        routes.branches();
        return List.copyOf(routes.grown);
    }

    private double draw(int row, int index) {
        return RavineCells.unit(hash, HASH_BASE + row * DRAWS + index);
    }

    private static int countFor(InclusiveRange<Integer> range, double unit) {
        return range.minInclusive() + (int) (unit * (range.maxInclusive() - range.minInclusive() + 1));
    }

    private void keep(RootWalk.Plan plan, RootWalk.Grown walked, int depth) {
        if (walked.knots().size() >= FEWEST_KNOTS) {
            grown.add(new Root(plan.kind(), walked.knots()));
            depths.add(depth);
        }
    }

    // A point at an angle round the axis and a height, a given distance in from the wall there (or into it, below 0).
    private RootWalk.Stop.Point byTheWall(double angle, double y, double in, double radius, RootWalk.Ground ground) {
        // The wall's unevenness is read where the point will be, which is near enough along the same line out from the axis.
        double wall = space.wall(space.xAt(angle, 1), space.zAt(angle, 1), y);
        return new RootWalk.Stop.Point(space.xAt(angle, wall - in), y, space.zAt(angle, wall - in), radius, ground);
    }

    // The highest a root may be anchored in the wall at an angle: under the ceiling margin, and under the ground there.
    private double highestAnchor(double angle, double radius) {
        double y = space.bounds().topY() - space.cone().ceilingMargin();
        RootWalk.Stop.Point there = byTheWall(angle, y, -radius, radius, RootWalk.Ground.MEETS_ROCK);
        return Math.min(y, space.groundOver(there.x(), there.z()) - radius - UNDER_GROUND);
    }

    /**
     * A place in the wall for a root to start from or end in, at an angle and a height: {@code in} blocks into the rock, or
     * empty where there is no rock to hold it, which is where a disc's dome has opened the wall or the ground over it is
     * too low.
     */
    private Optional<RootWalk.Stop.Point> anchor(double angle, double y, double in, double radius) {
        RootWalk.Stop.Point there = byTheWall(angle, y, -in, radius, RootWalk.Ground.MEETS_ROCK);
        boolean inRock = space.air(there.x(), there.y(), there.z()) < 0;
        boolean underGround = y <= space.groundOver(there.x(), there.z()) - radius - UNDER_GROUND;
        return inRock && underGround ? Optional.of(there) : Optional.empty();
    }

    private static double rimLevel(Disc disc) {
        return disc.floor() + disc.bowl();
    }

    private void great() {
        int count = Math.min(countFor(roots.great().count(), draw(0, COUNT_DRAW)), MAX_GREAT);
        for (int row = 0; row < count; row++) {
            int hand = draw(row, 0) < 0.5 ? 1 : -1;
            Optional<RootWalk.Stop.Point> found = Optional.empty();
            // Round the hole from where it was drawn, a step at a time and then a little lower, to the first place that has
            // rock to hold it.
            for (int place = 0; place < 3 * ANCHOR_TRIES && found.isEmpty(); place++) {
                double at = 2 * Math.PI * (row + draw(row, 1)) / count + hand * (place % ANCHOR_TRIES) * 2 * Math.PI / count / ANCHOR_TRIES;
                double y = highestAnchor(at, roots.great().radius()) - draw(row, 2) * 8 - 8 * (place / ANCHOR_TRIES);
                // Lower than this, too little of the wall stands under the ground on this side to hang a root from.
                if (y >= space.bellTop() + space.cone().layerSpacing()) {
                    found = anchor(at, y, roots.great().radius() / 2, roots.great().radius());
                }
            }
            if (found.isEmpty()) {
                continue;
            }
            RootWalk.Stop.Point start = found.get();
            double angle = space.angleOf(start.x(), start.z());
            double top = start.y();
            var stops = new ArrayList<RootWalk.Stop>();
            stops.add(start);
            comeDownTheCone(row, hand, top, stops);
            comeDownTheBell(row, hand, top, stops);
            stops.removeFirst();
            double outX = Math.cos(angle);
            double outZ = Math.sin(angle);
            var plan = new RootWalk.Plan(
                    Root.Kind.GREAT, row, start.x(), start.y(), start.z(),
                    -0.6 * outX - 0.7 * hand * outZ, -0.3, -0.6 * outZ + 0.7 * hand * outX,
                    roots.great().radius(), stops, hand, 1, false, OptionalDouble.empty());
            keep(plan, RootWalk.grow(space, roots, plan), 0);
        }
    }

    // A great root's radius at a height: what it starts with at its top, down to what it ends with at the floor.
    private double greatRadiusAt(double y, double top) {
        double share = Math.clamp((y - space.bounds().floorY()) / (top - space.bounds().floorY()), 0, 1);
        return roots.great().endRadius() + (roots.great().radius() - roots.great().endRadius()) * share;
    }

    /**
     * Down through the discs, a leg at a time from the last stop. Each leg comes down part of the way to the next layer and
     * goes as far round the hole as the root's fall carries it: in to a disc's rim about there, if its draw says so and one
     * has a place for it, and otherwise out to the wall.
     */
    private void comeDownTheCone(int row, int hand, double top, List<RootWalk.Stop> stops) {
        double gap = space.cone().layerSpacing();
        double lowest = space.layout().discs().stream().mapToDouble(RootRoutes::rimLevel).min().orElse(Double.POSITIVE_INFINITY);
        double until = Math.max(Math.min(lowest, space.bellTop()), space.bounds().floorY() + gap);
        for (int leg = 0; leg < 2 * ConeDiscLayout.MAX_LAYERS; leg++) {
            RootWalk.Stop.Point from = (RootWalk.Stop.Point) stops.getLast();
            if (from.y() - 0.3 * gap <= until) {
                return;
            }
            Optional<RootWalk.Stop.Point> touch = draw(row, 8 + leg % 16) < roots.great().touchChance()
                    ? discToTouch(hand, from, greatRadiusAt(from.y() - gap / 2, top))
                    : Optional.empty();
            stops.add(touch.isPresent() ? touch.get() : outByTheWall(row, hand, top, from, leg));
        }
    }

    // A great root's next stop where it touches no disc: lower, further round the hole, and out near the wall.
    private RootWalk.Stop.Point outByTheWall(int row, int hand, double top, RootWalk.Stop.Point from, int leg) {
        double drop = space.cone().layerSpacing() * (0.45 + 0.35 * draw(row, 24 + leg % 8));
        double round = Math.clamp(drop / roots.great().fall() / space.wall(from.x(), from.z(), from.y()), 0.2, 0.9);
        double radius = greatRadiusAt(from.y() - drop, top);
        return byTheWall(
                space.angleOf(from.x(), from.z()) + hand * round, from.y() - drop, radius + 1 + 8 * draw(row, 32 + leg % 8), radius, RootWalk.Ground.AIR);
    }

    // Where a great root coming from a stop lies against a disc next: the disc about a layer lower that is as far round the
    // hole as the root's fall would carry it, if any there has a place for it.
    private Optional<RootWalk.Stop.Point> discToTouch(int hand, RootWalk.Stop.Point from, double radius) {
        double gap = space.cone().layerSpacing();
        double angle = space.angleOf(from.x(), from.z());
        RootWalk.Stop.Point best = null;
        // A disc much further round than that, or much less far, would have the root run level or drop straight down.
        double least = OFF_ITS_FALL;
        for (Disc disc : space.layout().discs()) {
            double drop = from.y() - rimLevel(disc);
            if (drop < 0.3 * gap || drop > 1.4 * gap) {
                continue;
            }
            Optional<RootWalk.Stop.Point> touch = touchOn(disc, radius, from.x(), from.z());
            if (touch.isEmpty()) {
                continue;
            }
            double turn = hand * (space.angleOf(touch.get().x(), touch.get().z()) - angle);
            turn -= 2 * Math.PI * Math.floor(turn / (2 * Math.PI) + 0.5);
            double carried = drop / roots.great().fall() / Math.max(space.fromAxis(touch.get().x(), touch.get().z()), 20);
            if (turn >= 0.05 && Math.abs(turn - carried) < least) {
                best = touch.get();
                least = Math.abs(turn - carried);
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Where a root of this radius may lie against a disc's rim, its highest block level with the rim's: the place round the
     * rim nearest to where the root comes from that is in the open with air over it, and outside the clear air round the
     * axis and inside the hole's own wall. Empty if the rim has no such place.
     */
    private Optional<RootWalk.Stop.Point> touchOn(Disc disc, double radius, double fromX, double fromZ) {
        double level = disc.topBlockAt(disc.radius()) + 0.5 - radius;
        // A block into the rim, so that no gap is left between the two.
        double out = disc.radius() + radius - 1;
        RootWalk.Stop.Point best = null;
        double least = Double.POSITIVE_INFINITY;
        for (int place = 0; place < RIM_PLACES; place++) {
            double x = disc.x() + out * Math.cos(2 * Math.PI * place / RIM_PLACES);
            double z = disc.z() + out * Math.sin(2 * Math.PI * place / RIM_PLACES);
            double apart = Math.hypot(x - fromX, z - fromZ);
            double fromAxis = space.fromAxis(x, z);
            // In the hole itself, not back in the room a dome opens behind its wall, where a root would be out of sight.
            // And nothing but the disc's own platform may be as near as that is.
            if (apart < least && fromAxis >= space.clear(level) + radius + 1 && fromAxis <= space.wall(x, z, level)
                    && space.air(x, level, z) > radius - 2.5 && space.air(x, level + radius + 2.5, z) > 0) {
                best = new RootWalk.Stop.Point(x, level, z, radius, RootWalk.Ground.MEETS_ROCK);
                least = apart;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Down the bell at the foot of the hole: round the wall in two or more swoops to the floor, then along the floor towards
     * the middle, and into it.
     */
    private void comeDownTheBell(int row, int hand, double top, List<RootWalk.Stop> stops) {
        double floor = space.bounds().floorY();
        RootWalk.Stop.Point from = (RootWalk.Stop.Point) stops.getLast();
        double radius = roots.great().endRadius();
        // The last swoop ends just over the floor; those before hang off the wall by a different amount each.
        double low = floor + radius + 2;
        int swoops = Math.max(2, (int) Math.round((from.y() - low) / 18));
        double round = space.angleOf(from.x(), from.z());
        for (int swoop = 1; swoop <= swoops; swoop++) {
            round += hand * (0.45 + 0.5 * draw(row, 40 + swoop % 8));
            double y = from.y() + (low - from.y()) * swoop / swoops;
            stops.add(byTheWall(round, y, greatRadiusAt(y, top) + 1 + 14 * draw(row, 48 + swoop % 8), greatRadiusAt(y, top), RootWalk.Ground.AIR));
        }
        RootWalk.Stop.Point landing = (RootWalk.Stop.Point) stops.getLast();
        double run = roots.floorRun() * (0.5 + 0.5 * draw(row, 56));
        double fromAxis = space.fromAxis(landing.x(), landing.z());
        double inner = Math.max(fromAxis - run, space.clear(floor) + radius + 4);
        double angle = round + hand * (0.2 + 0.5 * draw(row, 57));
        if (run > 1) {
            // It comes down onto the floor a third of the way along its run, and sinks to half its depth by the end of it.
            double down = fromAxis + (inner - fromAxis) / 3;
            double downAt = round + (angle - round) / 3;
            stops.add(new RootWalk.Stop.Point(space.xAt(downAt, down), floor + 0.4 * radius, space.zAt(downAt, down), radius, RootWalk.Ground.THROUGH_ROCK));
            stops.add(new RootWalk.Stop.Point(space.xAt(angle, inner), floor - 0.1 * radius, space.zAt(angle, inner), radius, RootWalk.Ground.THROUGH_ROCK));
        }
        // Still going the same way, and down.
        double further = Math.max(inner - 6, space.clear(floor) + radius + 1);
        stops.add(new RootWalk.Stop.Point(
                space.xAt(angle + hand * 0.05, further), floor - radius - 5, space.zAt(angle + hand * 0.05, further), roots.minRadius(),
                RootWalk.Ground.THROUGH_ROCK));
    }

    private void crossing() {
        int count = Math.min(countFor(roots.crossing().count(), draw(FIRST_CROSSING, COUNT_DRAW)), MAX_CROSSING);
        for (int i = 0; i < count; i++) {
            int row = FIRST_CROSSING + i;
            // A span needs rock at both its ends, and a dome may have opened the wall at either.
            for (int place = 0; place < ANCHOR_TRIES; place++) {
                Optional<RootWalk.Plan> plan = span(row, 8 * place);
                if (plan.isPresent()) {
                    keep(plan.get(), RootWalk.grow(space, roots, plan.get()), 0);
                    break;
                }
            }
        }
    }

    // A crossing root from its own draws, the first of which is {@code first}; empty if either end has no rock to hold it.
    private Optional<RootWalk.Plan> span(int row, int first) {
        double radius = roots.crossing().radius();
        double angle = 2 * Math.PI * draw(row, first);
        double far = angle + Math.PI + (draw(row, first + 1) - 0.5) * 1.2;
        double lowest = space.bellTop() + 12;
        double from = lowest + draw(row, first + 2) * (Math.min(highestAnchor(angle, radius), highestAnchor(far, radius) + 30) - lowest);
        double to = from - 5 - 25 * draw(row, first + 3);
        if (from < lowest || to < space.bellTop()) {
            return Optional.empty();
        }
        Optional<RootWalk.Stop.Point> start = anchor(angle, from, radius / 2, radius);
        Optional<RootWalk.Stop.Point> end = anchor(far, to, radius + 2, radius);
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        double acrossX = end.get().x() - start.get().x();
        double acrossZ = end.get().z() - start.get().z();
        double span = Math.hypot(acrossX, acrossZ);
        // It sags in the middle, and passes to one side of the axis rather than through it.
        double aside = (draw(row, first + 4) - 0.5) * 1.6 * space.clear((from + to) / 2);
        var middle = new RootWalk.Stop.Point(
                (start.get().x() + end.get().x()) / 2 - aside * acrossZ / span, (from + to) / 2 - 0.12 * span,
                (start.get().z() + end.get().z()) / 2 + aside * acrossX / span, radius, RootWalk.Ground.AIR);
        return Optional.of(new RootWalk.Plan(
                Root.Kind.CROSSING, row, start.get().x(), start.get().y(), start.get().z(),
                middle.x() - start.get().x(), middle.y() - start.get().y(), middle.z() - start.get().z(),
                radius, List.of(middle, end.get()), draw(row, first + 5) < 0.5 ? 1 : -1, 0, false, OptionalDouble.empty()));
    }

    /** For each disc drawn to have one, a link up to it from the disc under it that lies nearest. */
    private void links() {
        List<Disc> discs = space.layout().discs();
        double radius = roots.links().radius();
        for (int upper = 0; upper < discs.size(); upper++) {
            int row = FIRST_LINK + upper;
            if (draw(row, 0) >= roots.links().chance()) {
                continue;
            }
            Disc to = discs.get(upper);
            Disc from = null;
            double nearest = Double.POSITIVE_INFINITY;
            for (Disc lower : discs) {
                double apart = Math.hypot(to.x() - lower.x(), to.z() - lower.z()) - to.radius() - lower.radius();
                if (lower != to && apart < nearest && footOf(to, lower, radius).isPresent()) {
                    from = lower;
                    nearest = apart;
                }
            }
            if (from == null) {
                continue;
            }
            double[] foot = footOf(to, from, radius).orElseThrow();
            int first = draw(row, 1) < 0.5 ? 1 : -1;
            // One way round the upper disc's rim may run into the wall where the other does not.
            for (int hand : new int[] {first, -first}) {
                double outX = foot[0] - to.x();
                double outZ = foot[2] - to.z();
                var plan = new RootWalk.Plan(
                        Root.Kind.LINK, row, foot[0], foot[1], foot[2], -hand * outZ, 0, hand * outX, radius,
                        List.of(new RootWalk.Stop.Rim(to, radius)), hand, 0, true, OptionalDouble.of(roots.links().maxSlope()));
                RootWalk.Grown walked = RootWalk.grow(space, roots, plan);
                if (walked.arrived()) {
                    keep(plan, walked, 0);
                    break;
                }
            }
        }
    }

    /**
     * Where a link up to {@code to} leaves the top of {@code from}: on the line from the upper disc's axis towards the lower
     * one's, clear of the upper disc's rim and inside the lower one's, with open air over it and a layer or so under the
     * upper disc's rim. Empty if the lower disc has no such place.
     */
    private Optional<double[]> footOf(Disc to, Disc from, double radius) {
        double apart = Math.hypot(from.x() - to.x(), from.z() - to.z());
        // Straight under one another, any side would do; the side of the hole's axis is the open one.
        double towardsX = apart > 1e-3 ? (from.x() - to.x()) / apart : -Math.cos(space.angleOf(to.x(), to.z()));
        double towardsZ = apart > 1e-3 ? (from.z() - to.z()) / apart : -Math.sin(space.angleOf(to.x(), to.z()));
        double out = Math.max(to.radius() + radius + 6, apart - from.radius() + 3);
        if (out > apart + from.radius() - 3) {
            return Optional.empty();
        }
        double x = to.x() + towardsX * out;
        double z = to.z() + towardsZ * out;
        double ground = from.topAt(Math.hypot(x - from.x(), z - from.z()));
        double rise = rimLevel(to) - ground;
        double gap = space.cone().layerSpacing();
        // Less of a rise than this is a step between two discs that run into one another, and more is two layers.
        if (rise < 0.3 * gap || rise > 2.2 * gap || space.air(x, ground + radius + 2, z) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new double[] {x, ground - 0.3 * radius, z});
    }

    /**
     * Branches of every root grown so far, and of those branches, as deep as the settings allow. A root is gone along from its
     * start, and a branch leaves it wherever a count that grows faster towards the floor passes the next of its draws.
     */
    private void branches() {
        RootSettings.Branches settings = roots.branches();
        double floor = space.bounds().floorY();
        double height = space.bounds().topY() - floor;
        for (int parent = 0; parent < grown.size() && grown.size() < MAX_ROOTS; parent++) {
            Root root = grown.get(parent);
            if (root.kind() == Root.Kind.LINK || depths.get(parent) >= settings.depth()) {
                continue;
            }
            double count = 0;
            int drawn = 0;
            double next = 0.5 + draw(FIRST_BRANCH + parent, 0);
            for (int at = CLEAR_OF_ENDS; at < root.knots().size() - CLEAR_OF_ENDS && grown.size() < MAX_ROOTS; at++) {
                Root.Knot knot = root.knots().get(at);
                count += settings.byHeight().at((knot.y() - floor) / height) / settings.every();
                if (count < next) {
                    continue;
                }
                count -= next;
                drawn++;
                next = 0.5 + draw(FIRST_BRANCH + parent, drawn % DRAWS);
                double radius = knot.radius() * settings.shrink();
                // A crossing root has no branches where it is in the clear air, which they may not be in.
                boolean inTheOpen = space.fromAxis(knot.x(), knot.z()) >= space.clear(knot.y()) + radius + 2;
                if (radius >= roots.minRadius() && inTheOpen) {
                    branch(parent, at, radius, depths.get(parent) + 1);
                }
            }
        }
    }

    // One branch, from a knot of its parent to the first of its possible ends that it can reach.
    private void branch(int parent, int at, double radius, int depth) {
        int row = FIRST_BRANCH + ConeDiscLayout.MAX_DISCS + branches++;
        Root root = grown.get(parent);
        Root.Knot knot = root.knots().get(at);
        Root.Knot before = root.knots().get(at - 1);
        RootWalk.Plan tip = null;
        RootWalk.Grown tipWalked = null;
        for (List<RootWalk.Stop.Point> stops : endsFor(row, parent, knot, radius)) {
            RootWalk.Stop.Point first = stops.getFirst();
            double away = Math.max(Math.sqrt(square(first.x() - knot.x()) + square(first.y() - knot.y()) + square(first.z() - knot.z())), 1e-6);
            // It leaves the way its parent is going and turns towards its end.
            var plan = new RootWalk.Plan(
                    Root.Kind.BRANCH, row, knot.x(), knot.y(), knot.z(),
                    0.5 * (knot.x() - before.x()) + (first.x() - knot.x()) / away,
                    0.5 * (knot.y() - before.y()) + (first.y() - knot.y()) / away,
                    0.5 * (knot.z() - before.z()) + (first.z() - knot.z()) / away,
                    radius, List.copyOf(stops), draw(row, 0) < 0.5 ? 1 : -1, 0.5, true, OptionalDouble.empty());
            RootWalk.Grown walked = RootWalk.grow(space, roots, plan);
            if (walked.arrived()) {
                keep(plan, walked, depth);
                return;
            }
            tip = plan;
            tipWalked = walked;
        }
        // None of its ends could be reached: it hangs where the last try left it.
        if (tip != null) {
            keep(tip, tipWalked, depth);
        }
    }

    private static double square(double value) {
        return value * value;
    }

    /**
     * The ends a branch may make for from a knot, in the order it tries them: the order of a draw for each, stretched by how
     * much that kind of end is wanted there. In the bell the floor is wanted most, which is what takes roots in among the
     * city; higher up, another root or a disc.
     */
    private List<List<RootWalk.Stop.Point>> endsFor(int row, int parent, Root.Knot knot, double radius) {
        boolean inTheBell = knot.y() < space.bellTop() + 6;
        record Offered(List<RootWalk.Stop.Point> stops, double key) {}
        var offered = new ArrayList<Offered>();
        anotherRoot(parent, knot, radius).ifPresent(stop -> offered.add(new Offered(List.of(stop), draw(row, 1) / (inTheBell ? 3 : 4))));
        aDisc(knot, radius).ifPresent(stops -> offered.add(new Offered(stops, draw(row, 2) / 3)));
        if (inTheBell) {
            offered.add(new Offered(theFloor(row, knot, radius), draw(row, 3) / 5));
        }
        theWall(row, knot, radius).ifPresent(stop -> offered.add(new Offered(List.of(stop), draw(row, 4) / 2)));
        offered.sort(Comparator.comparingDouble(Offered::key));
        return offered.stream().map(Offered::stops).toList();
    }

    // The nearest knot of any other root that is no higher than this one, neither too near nor out of reach, and not in the
    // clear air round the axis.
    private Optional<RootWalk.Stop.Point> anotherRoot(int parent, Root.Knot knot, double radius) {
        Root.Knot nearest = null;
        double least = roots.branches().reach();
        for (int other = 0; other < grown.size(); other++) {
            if (other == parent) {
                continue;
            }
            for (Root.Knot there : grown.get(other).knots()) {
                double apart = there.distanceTo(knot.x(), knot.y(), knot.z());
                boolean inTheOpen = space.fromAxis(there.x(), there.z()) >= space.clear(there.y()) + radius + 2;
                if (apart >= 14 && apart < least && there.y() <= knot.y() + 6 && inTheOpen) {
                    nearest = there;
                    least = apart;
                }
            }
        }
        return Optional.ofNullable(nearest).map(there -> new RootWalk.Stop.Point(there.x(), there.y(), there.z(), radius, RootWalk.Ground.MEETS_ROCK));
    }

    // Against the rim of the nearest disc that is no higher than this knot and within reach, and from there into its platform.
    private Optional<List<RootWalk.Stop.Point>> aDisc(Root.Knot knot, double radius) {
        Disc nearest = null;
        double least = roots.branches().reach();
        for (Disc disc : space.layout().discs()) {
            double drop = knot.y() - rimLevel(disc);
            double apart = Math.hypot(Math.max(Math.hypot(disc.x() - knot.x(), disc.z() - knot.z()) - disc.radius(), 0), drop);
            if (drop >= -4 && apart >= 8 && apart < least) {
                nearest = disc;
                least = apart;
            }
        }
        if (nearest == null) {
            return Optional.empty();
        }
        Disc disc = nearest;
        return touchOn(disc, radius, knot.x(), knot.z()).map(touch -> {
            double out = Math.hypot(touch.x() - disc.x(), touch.z() - disc.z());
            double in = (out - radius - 2) / out;
            return List.of(touch, new RootWalk.Stop.Point(
                    disc.x() + (touch.x() - disc.x()) * in, touch.y() - 0.5, disc.z() + (touch.z() - disc.z()) * in, radius, RootWalk.Ground.THROUGH_ROCK));
        });
    }

    // Down to the floor, somewhere towards the middle of the hole from here, and into it.
    private List<RootWalk.Stop.Point> theFloor(int row, Root.Knot knot, double radius) {
        double floor = space.bounds().floorY();
        double inwards = space.angleOf(knot.x(), knot.z()) + Math.PI + (draw(row, 5) - 0.5) * 2.4;
        double away = (0.4 + 0.6 * draw(row, 6)) * roots.branches().reach();
        double x = knot.x() + away * Math.cos(inwards);
        double z = knot.z() + away * Math.sin(inwards);
        return List.of(
                new RootWalk.Stop.Point(x, floor + 0.2 * radius, z, radius, RootWalk.Ground.MEETS_ROCK),
                new RootWalk.Stop.Point(x + 4 * Math.cos(inwards), floor - radius - 4, z + 4 * Math.sin(inwards), roots.minRadius(), RootWalk.Ground.THROUGH_ROCK));
    }

    // Into the wall, a little round the hole from here and lower, where there is rock to hold it.
    private Optional<RootWalk.Stop.Point> theWall(int row, Root.Knot knot, double radius) {
        double angle = space.angleOf(knot.x(), knot.z()) + (draw(row, 7) - 0.5);
        double y = Math.max(knot.y() - 4 - 16 * draw(row, 8), space.bounds().floorY() + radius);
        return anchor(angle, y, radius + 2, radius)
                .filter(there -> Math.hypot(there.x() - knot.x(), there.z() - knot.z()) < roots.branches().reach());
    }
}

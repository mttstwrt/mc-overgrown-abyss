package dev.syrval.overgrownabyss.ravine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The discs of a cone. Most are in the ring: free-standing, in layers from the lowest layer's height to under the top of the
 * cone, with a number of slots round each layer. A disc may sit straight above the one in the layer below, which makes them
 * stack: its stem then lands on that platform. A disc is as large as it is drawn, so one too large for the cone at its height
 * cuts its dome into the rock around the cone. The rest are riders: discs standing on top of other discs, outside the cone, in
 * the room those domes open (see {@link ConeSettings#riderChance()}). Some discs hang from a root instead of standing on a stem
 * (see {@link ConeSettings#hangChance()}).
 *
 * <p>Each disc is a pure function of the hole's hash and its place (layer and slot, or host and place on it), and each is given
 * its stem or root once, here, among all the others.
 */
final class ConeDiscLayout implements DiscLayout {
    static final int MAX_LAYERS = 32;
    static final int MAX_SLOTS = 64;
    // Places on top of one disc that may each hold a rider.
    static final int MAX_RIDERS = 8;
    // Riders carry riders, so the whole is capped. Hash indices are spaced for this many hosts.
    static final int MAX_DISCS = 512;
    // A disc's centre may sit this fraction of its radius past the wall, so it joins the wall instead of touching it.
    static final double EMBED = 0.3;
    // A disc's centre moves by under a block when it is put halfway between block coordinates (see Disc.placed).
    static final double PLACING_SLACK = 1;
    private static final int HASH_BASE = 9000;
    private static final int RIDER_HASH_BASE = 600_000;
    private static final double JITTER = 0.8;
    // A rider's floor is between these shares of the way up its host's dome: high enough to walk under, low enough to stand in it.
    private static final double RIDER_LOWEST = 0.5;
    private static final double RIDER_HIGHEST = 0.8;
    // Clear air kept between a host's top and the underside of a disc standing on it.
    private static final double RIDER_HEADROOM = 5;
    // A rider's centre is between these shares of the way from its host's centre to as far out as its stem still lands on the host.
    private static final double RIDER_NEAREST = 0.3;
    private static final double RIDER_FURTHEST = 0.8;

    private final RavineSettings settings;
    private final ConeSettings cone;
    private final RavineBounds bounds;
    private final RavineCell cell;
    private final DiscShape shape;
    private final List<Disc> discs;

    // A disc as placed, and whether it was drawn to hang, which it does only if it then turns out to have a ceiling.
    private record Drawn(Disc disc, boolean toHang) {}

    ConeDiscLayout(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell) {
        this.settings = settings;
        this.cone = cone;
        this.bounds = bounds;
        this.cell = cell;
        this.shape = settings.discs();
        var drawn = new ArrayList<Drawn>();
        for (int layer = 0; layer < layers(); layer++) {
            for (int slot = 0; slot < slots(layer); slot++) {
                boolean toHang = RavineCells.unit(cell.hash(), base(layer, slot) + 5) < cone.hangChance();
                at(layer, slot).ifPresent(disc -> drawn.add(new Drawn(disc, toHang)));
            }
        }
        // Riders are added behind the discs they stand on, so each gets its turn as a host too.
        for (int host = 0; host < drawn.size(); host++) {
            for (int place = 0; place < riderPlaces(drawn.get(host).disc()) && drawn.size() < MAX_DISCS; place++) {
                rider(drawn.get(host).disc(), host, place).ifPresent(drawn::add);
            }
        }
        List<Disc> placed = drawn.stream().map(Drawn::disc).toList();
        var supported = new ArrayList<Disc>(placed.size());
        for (Drawn each : drawn) {
            Disc disc = each.disc();
            Optional<Disc.Support> root = each.toHang() ? rootOf(placed, disc) : Optional.empty();
            supported.add(disc.withSupport(root.orElseGet(() -> new Disc.Support.Standing(Discs.bottomOf(placed, shape, disc)))));
        }
        this.discs = List.copyOf(supported);
    }

    /**
     * The root a disc can hang from, if it has a ceiling within reach: the lowest platform above that holds its axis, or
     * else the cone's wall. A ceiling closer than the least dome height is no room to hang in, so the disc stands instead.
     */
    private Optional<Disc.Support> rootOf(List<Disc> placed, Disc disc) {
        OptionalDouble platform = Discs.platformAbove(placed, shape, disc);
        OptionalDouble wall = wallAbove(disc);
        boolean underPlatform = platform.isPresent() && (wall.isEmpty() || platform.getAsDouble() <= wall.getAsDouble());
        if (!underPlatform && wall.isEmpty()) {
            return Optional.empty();
        }
        double anchor = underPlatform ? platform.getAsDouble() : wall.getAsDouble();
        if (anchor - disc.floor() < shape.minHeight()) {
            return Optional.empty();
        }
        // A platform's underside curves, so under one the root runs on into it and leaves no gap. The wall slopes away, so
        // there it narrows again above the anchor.
        double top = anchor + (underPlatform ? shape.floorThickness() / 2 : shape.rootRadiusFor(disc.radius()));
        return Optional.of(new Disc.Support.Hanging(anchor, top));
    }

    /**
     * Where the wall is over a disc's axis: where the axis meets the cone, or the top of the disc's own dome if the axis is
     * already inside the wall there. Empty under open sky, and where the wall is too near the top to be sure of rock behind it.
     */
    private OptionalDouble wallAbove(Disc disc) {
        OptionalDouble meets = ConeShape.heightAt(settings, cone, bounds, cell.distanceToCentre(disc.x(), disc.z()));
        if (meets.isEmpty()) {
            return OptionalDouble.empty();
        }
        double anchor = Math.max(meets.getAsDouble(), disc.floor() + disc.height());
        return anchor > bounds.topY() - cone.ceilingMargin() ? OptionalDouble.empty() : OptionalDouble.of(anchor);
    }

    private static double lowestFloor(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        return bounds.floorY() + settings.cavernHeight() + cone.baseClearance();
    }

    /** How many layers of discs fit between the lowest layer and the ceiling margin; the same for every cone of a level. */
    static int layers(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        double span = bounds.topY() - cone.ceilingMargin() - settings.discs().minHeight() - lowestFloor(settings, cone, bounds);
        return span < 0 ? 0 : (int) Math.min(MAX_LAYERS, Math.floor(span / cone.layerSpacing()) + 1);
    }

    int layers() {
        return layers(settings, cone, bounds);
    }

    private double nominalFloor(int layer) {
        return lowestFloor(settings, cone, bounds) + layer * cone.layerSpacing();
    }

    int slots(int layer) {
        double wall = ConeShape.radiusAt(settings, cone, bounds, nominalFloor(layer));
        double ring = (cone.clearRadius() + wall) / 2;
        return Math.clamp(Math.round(2 * Math.PI * ring / cone.spacing()), 3, MAX_SLOTS);
    }

    private int base(int layer, int slot) {
        return HASH_BASE + 8 * (layer * MAX_SLOTS + slot);
    }

    private boolean stacked(int layer, int slot) {
        return layer > 0 && slot < slots(layer - 1) && RavineCells.unit(cell.hash(), base(layer, slot) + 4) < cone.stackChance();
    }

    /** The dome over a disc of this radius with its floor at this height, kept under the ceiling margin. */
    private double domeHeight(double radius, double floor) {
        return Math.min(shape.heightFor(radius), bounds.topY() - cone.ceilingMargin() - floor);
    }

    /**
     * The ring's disc at a position as placed, before it is given its stem or root among the others ({@link #discs()} has those),
     * or empty if its dome would not fit under the ceiling margin.
     */
    Optional<Disc> at(int layer, int slot) {
        long hash = cell.hash();
        int base = base(layer, slot);
        double floor = nominalFloor(layer) + (RavineCells.unit(hash, base) - 0.5) * cone.layerJitter() * cone.layerSpacing();
        double wall = ConeShape.radiusAt(settings, cone, bounds, floor);
        // The cone's width at this height does not limit the disc: what does not fit carves into the rock around the cone.
        // Only the outer radius holds it, with the disc's inner edge at the clear cylinder.
        double radius = Math.min(shape.radiusFor(RavineCells.unit(hash, base + 1)), (cone.outerRadius() - cone.clearRadius() - PLACING_SLACK) / 2);
        double height = domeHeight(radius, floor);
        if (radius < shape.minRadius() / 2 || height < shape.minHeight()) {
            return Optional.empty();
        }
        // A stacked disc keeps the angle and the relative distance out of the disc it sits on.
        int originLayer = layer;
        while (stacked(originLayer, slot)) {
            originLayer--;
        }
        int originBase = base(originLayer, slot);
        double stagger = (originLayer % 2) * 0.5;
        double angle = 2 * Math.PI * (slot + 0.5 + (RavineCells.unit(hash, originBase + 3) - 0.5) * JITTER + stagger) / slots(originLayer);
        // The inner edge stays outside the clear cylinder. The centre stays within a little of the wall if the disc is small
        // enough for that; a larger one sits against the clear cylinder and reaches out past the wall.
        double inner = cone.clearRadius() + radius + PLACING_SLACK;
        double furthest = Math.min(wall + EMBED * radius, cone.outerRadius() - radius);
        double distance = inner + RavineCells.unit(hash, originBase + 2) * Math.max(0, furthest - inner);
        return Optional.of(Disc.placed(
                cell.centreX() + distance * Math.cos(angle), cell.centreZ() + distance * Math.sin(angle), floor, radius, height,
                shape.bowlDepthFor(radius, RavineCells.unit(hash, base + 6))));
    }

    /** How many places on top of a disc may each hold a rider: one for every {@code spacing} blocks of the circle halfway out. */
    private int riderPlaces(Disc host) {
        return cone.riderChance() <= 0 ? 0 : Math.clamp(Math.round(Math.PI * host.radius() / cone.spacing()), 0, MAX_RIDERS);
    }

    /**
     * The disc standing on {@code host} at one of its places, or empty if the place stays free. A rider is smaller than its
     * host, stands inside the host's dome with its stem on the host's platform, and has its centre outside the cone, where the
     * ring has no discs. Its own dome then opens the rock above and beyond its host's.
     */
    private Optional<Drawn> rider(Disc host, int hostIndex, int place) {
        long hash = cell.hash();
        int base = RIDER_HASH_BASE + 8 * (hostIndex * MAX_RIDERS + place);
        if (RavineCells.unit(hash, base) >= cone.riderChance()) {
            return Optional.empty();
        }
        double radius = Math.min(shape.radiusFor(RavineCells.unit(hash, base + 1)), cone.riderScale() * host.radius());
        if (radius < shape.minRadius() / 2) {
            return Optional.empty();
        }
        // As far out on the host as the rider's stem still lands on its platform, wherever the centre is then put.
        double landing = host.radius() - shape.stemRadiusFor(radius) - 1 - PLACING_SLACK;
        double out = lerp(RavineCells.unit(hash, base + 2), RIDER_NEAREST, RIDER_FURTHEST) * landing;
        double angle = 2 * Math.PI * (place + 0.5 + (RavineCells.unit(hash, base + 3) - 0.5) * JITTER) / riderPlaces(host);
        double x = host.x() + out * Math.cos(angle);
        double z = host.z() + out * Math.sin(angle);
        double floor = host.floor() + lerp(RavineCells.unit(hash, base + 4), RIDER_LOWEST, RIDER_HIGHEST) * host.height();
        double fromAxis = cell.distanceToCentre(x, z);
        double height = domeHeight(radius, floor);
        boolean outsideTheCone = fromAxis > ConeShape.radiusAt(settings, cone, bounds, floor);
        boolean withinBounds = fromAxis - radius >= cone.clearRadius() + PLACING_SLACK && fromAxis + radius <= cone.outerRadius();
        boolean roomUnderIt = floor - shape.floorThickness() - host.topAt(out) >= RIDER_HEADROOM;
        boolean inTheHostsDome = host.domeDistance(x, floor, z) < 0;
        if (!outsideTheCone || !withinBounds || !roomUnderIt || !inTheHostsDome || height < shape.minHeight()) {
            return Optional.empty();
        }
        Disc rider = Disc.placed(x, z, floor, radius, height, shape.bowlDepthFor(radius, RavineCells.unit(hash, base + 6)));
        return Optional.of(new Drawn(rider, RavineCells.unit(hash, base + 5) < cone.hangChance()));
    }

    private static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }

    @Override
    public List<Disc> discs() {
        return discs;
    }
}

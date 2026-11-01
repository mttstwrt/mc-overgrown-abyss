package dev.syrval.verdantdeep.ravine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The discs of a cone. Most are in the ring: free-standing, in layers from the lowest layer's height to under the top of the
 * cone, with a number of slots round each layer. A disc may sit straight above the one in the layer below, which makes them
 * stack: its stem then lands on that platform. A disc is as large as it is drawn, so one too large for the cone at its height
 * cuts its dome into the rock around the cone. The rest are riders: discs standing on top of other discs, outside the cone, in
 * the room those domes open (see {@link ConeSettings#riderChance()}). Some discs hang from a root instead of standing on a stem
 * (see {@link ConeSettings#hangChance()}).
 *
 * <p>Without a rim the layers are {@code layer_spacing} apart from the lowest up, as many as fit under the top. With one (see
 * {@link RimSettings}) the top differs from hole to hole, so they are spread evenly from the lowest to {@code top_room} under
 * the highest a dome may reach, no closer than {@code layer_spacing}, and each dome also stays under the ground over it.
 *
 * <p>Each disc is a pure function of the hole's hash, its place (layer and slot, or host and place on it) and, with a rim, the
 * ground over it, and each is given its stem or root once, here, among all the others.
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
    // A surface shelf projects most of its width into the bowl, leaving its back in the retained slope.
    private static final double SHELF_EMBED = 0.65;
    // A disc's centre moves by under a block when it is put halfway between block coordinates (see Disc.placed).
    static final double PLACING_SLACK = 1;
    private static final int HASH_BASE = 9000;
    private static final int RIDER_HASH_BASE = 600_000;
    private static final int BOWL_HASH_BASE = 800_000;
    private static final double JITTER = 0.8;
    // Riders stand on the far side of their host from the cone's axis, within this angle either way of straight out, so that
    // the host and what grows on it are between them and anyone looking from the middle.
    private static final double RIDER_ARC = Math.toRadians(100);
    // A rider's centre is between these shares of the way from its host's centre to as far out as its stem still lands on the host.
    private static final double RIDER_NEAREST = 0.5;
    private static final double RIDER_FURTHEST = 0.9;
    // Clear air kept between a host's top and the underside of a disc standing on it.
    private static final double RIDER_HEADROOM = 5;
    // A rider's floor is no higher than this share of the way up its host's dome, and stays this share of the dome's height
    // under the host's roof where it stands, so that its middle is inside the dome.
    private static final double RIDER_HIGHEST = 0.8;
    private static final double RIDER_UNDER_ROOF = 0.08;

    private final RavineSettings settings;
    private final ConeSettings cone;
    private final RavineBounds bounds;
    private final RavineCell cell;
    private final DiscShape shape;
    private final HoleGround ground;
    private final double layerGap;
    private final List<Disc> discs;
    private final Set<Disc> surfaceDiscs;
    private final DiscIndex index;

    // A disc as placed, and whether it was drawn to hang, which it does only if it then turns out to have a ceiling.
    private record Drawn(Disc disc, boolean toHang, boolean surface) {
        Drawn(Disc disc, boolean toHang) {
            this(disc, toHang, false);
        }
    }

    /** The discs of a hole whose ground is nowhere lower than its top. */
    ConeDiscLayout(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell) {
        this(settings, cone, bounds, cell, SurfaceProbe.SOLID);
    }

    ConeDiscLayout(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, SurfaceProbe ground) {
        this.settings = settings;
        this.cone = cone;
        this.bounds = bounds;
        this.cell = cell;
        this.shape = settings.discs();
        this.ground = new HoleGround(ground, bounds.topY());
        this.layerGap = layerGap(settings, cone, bounds);
        var drawn = new ArrayList<Drawn>();
        for (int layer = 0; layer < layers(); layer++) {
            for (int slot = 0; slot < slots(layer); slot++) {
                boolean toHang = RavineCells.unit(cell.hash(), base(layer, slot) + 5) < cone.hangChance();
                at(layer, slot).ifPresent(disc -> drawn.add(new Drawn(surfaceShape(disc), toHang)));
            }
        }
        // Riders are added behind the discs they stand on, so each gets its turn as a host too.
        for (int host = 0; host < drawn.size(); host++) {
            for (int place = 0; place < riderPlaces(drawn.get(host).disc()) && drawn.size() < MAX_DISCS; place++) {
                rider(drawn.get(host).disc(), host, place).ifPresent(drawn::add);
            }
        }
        addBowlDiscs(drawn, ground);
        List<Disc> placed = drawn.stream().map(Drawn::disc).toList();
        var supported = new ArrayList<Disc>(placed.size());
        var surface = new HashSet<Disc>();
        for (Drawn each : drawn) {
            Disc disc = each.disc();
            Optional<Disc.Support> root = each.toHang() ? rootOf(placed, disc) : Optional.empty();
            Disc supportedDisc = disc.withSupport(each.surface() ? new Disc.Support.Attached()
                    : root.orElseGet(() -> new Disc.Support.Standing(Discs.bottomOf(placed, shape, disc))));
            supported.add(supportedDisc);
            if (each.surface() || exposedAtEntrance(supportedDisc)) {
                surface.add(supportedDisc);
            }
        }
        this.discs = List.copyOf(supported);
        this.surfaceDiscs = Set.copyOf(surface);
        this.index = new DiscIndex(discs, settings.edgeFalloff());
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
        // The clear air widens upwards, so a root is held to it where the root is highest.
        if (cell.distanceToCentre(disc.x(), disc.z()) - shape.rootRadiusFor(disc.radius()) < ConeShape.clearAt(cone, bounds, top)) {
            return Optional.empty();
        }
        return Optional.of(new Disc.Support.Hanging(anchor, top));
    }

    /**
     * Where the wall is over a disc's axis: where the axis meets the cone's wall as its unevenness leaves it, or the top of the
     * disc's own dome if the axis is already inside the wall there. Empty under open sky, and where the wall is too near the
     * top, or with a rim too near the ground over the axis, to be sure of rock behind it.
     */
    private OptionalDouble wallAbove(Disc disc) {
        OptionalDouble meets = ConeShape.wallOver(settings, cone, bounds, cell, disc.x(), disc.z());
        if (meets.isEmpty()) {
            return OptionalDouble.empty();
        }
        double anchor = Math.max(meets.getAsDouble(), disc.floor() + disc.height());
        double over = cone.rim().isPresent() ? ground.heightAt((int) Math.floor(disc.x()), (int) Math.floor(disc.z())) : bounds.topY();
        return anchor > over - cone.ceilingMargin() ? OptionalDouble.empty() : OptionalDouble.of(anchor);
    }

    private static double lowestFloor(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        return bounds.floorY() + settings.cavernHeight() + cone.baseClearance();
    }

    // The highest a layer may be: with a rim its top_room under the highest a dome may reach, without one the least dome.
    private static double highestFloor(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        double room = cone.rim().map(RimSettings::topRoom).orElse(settings.discs().minHeight());
        return bounds.topY() - cone.ceilingMargin() - room;
    }

    /** How many layers of discs fit between the lowest layer and the highest; with a rim that differs from hole to hole. */
    static int layers(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        double span = highestFloor(settings, cone, bounds) - lowestFloor(settings, cone, bounds);
        return span < 0 ? 0 : (int) Math.min(MAX_LAYERS, Math.floor(span / cone.layerSpacing()) + 1);
    }

    // Blocks from one layer to the next: layer_spacing, or with a rim what spreads the layers evenly up to the highest.
    private static double layerGap(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        int layers = layers(settings, cone, bounds);
        if (cone.rim().isEmpty() || layers < 2) {
            return cone.layerSpacing();
        }
        return (highestFloor(settings, cone, bounds) - lowestFloor(settings, cone, bounds)) / (layers - 1);
    }

    int layers() {
        return layers(settings, cone, bounds);
    }

    double nominalFloor(int layer) {
        return lowestFloor(settings, cone, bounds) + layer * layerGap;
    }

    // The clear air's radius for a platform at this height, taken at the highest its rim may stand, where it is widest.
    private double clearFor(double floor) {
        return ConeShape.clearAt(cone, bounds, floor + shape.maxBowlDepth());
    }

    int slots(int layer) {
        double wall = ConeShape.radiusAt(settings, cone, bounds, nominalFloor(layer));
        double ring = (clearFor(nominalFloor(layer)) + wall) / 2;
        return Math.clamp(Math.round(2 * Math.PI * ring / cone.spacing()), 3, MAX_SLOTS);
    }

    private int base(int layer, int slot) {
        return HASH_BASE + 8 * (layer * MAX_SLOTS + slot);
    }

    private boolean stacked(int layer, int slot) {
        return layer > 0 && slot < slots(layer - 1) && RavineCells.unit(cell.hash(), base(layer, slot) + 4) < cone.stackChance();
    }

    /**
     * The dome over a disc of this radius with its floor at this height, kept under the ceiling margin: that far under the top,
     * and with a rim that far under the ground over the disc too, wherever the ground is lower than the top.
     */
    private double domeHeight(double x, double z, double radius, double floor) {
        double height = Math.min(shape.heightFor(radius), bounds.topY() - cone.ceilingMargin() - floor);
        return cone.rim().isPresent() ? Math.min(height, ground.domeRoom(x, z, radius, floor, cone.ceilingMargin())) : height;
    }

    private record BowlSpan(double angle, double inner, double outer) {}

    /** Shared floor heights make overlapping discs read as broken amphitheatre tiers rather than scattered gardens. */
    private void addBowlDiscs(List<Drawn> drawn, SurfaceProbe probe) {
        if (cone.rim().isEmpty()) {
            return;
        }
        RimSettings.Collar collar = cone.rim().get().collar();
        if (collar.discBands() == 0 || collar.depth() == 0) {
            return;
        }
        int surveyTop = bounds.highestEdge() + (int) Math.ceil(collar.depth() + collar.height());
        HoleGround bowlGround = new HoleGround(probe, surveyTop);
        double join = ConeShape.entranceFloor(cone, bounds, cell);
        double joinRadius = ConeShape.radiusAt(settings, cone, bounds, join);
        double spacing = collar.discSpacing() > 0 ? collar.discSpacing() : cone.spacing();
        double layerSpacing = collar.discLayerSpacing() > 0 ? collar.discLayerSpacing() : cone.layerSpacing();
        int slots = Math.clamp((int) Math.ceil(2 * Math.PI * (cone.outerRadius() - shape.minRadius()) / spacing), 3, MAX_SLOTS);
        double gap = RavineCells.unit(cell.hash(), BOWL_HASH_BASE + MAX_LAYERS * MAX_SLOTS * 8);
        // Keep whole-block samples off the platform's exact SDF boundary so the open block above it is strictly air.
        double lowest = Math.ceil(join + shape.floorThickness() + collar.surfaceBowlDepth()) + 0.5;
        for (int band = 0; band < collar.discBands() && drawn.size() < MAX_DISCS; band++) {
            double floor = lowest + band * layerSpacing;
            for (int slot = 0; slot < slots && drawn.size() < MAX_DISCS; slot++) {
                double turn = (slot + 0.5 + (band % 2) * 0.15
                        + (RavineCells.unit(cell.hash(), BOWL_HASH_BASE + 8 * slot) - 0.5) * 0.2) / slots;
                double fromGap = Math.abs(turn - gap);
                fromGap = Math.min(fromGap, 1 - fromGap);
                if (fromGap < collar.discGapShare() / 2) {
                    continue;
                }
                Optional<BowlSpan> span = bowlSpan(bowlGround, joinRadius, turn * 2 * Math.PI);
                if (span.isPresent()) {
                    bowlDisc(bowlGround, span.get(), floor, band, slot)
                            .ifPresent(disc -> drawn.add(new Drawn(disc, false, true)));
                }
            }
        }
    }

    private Optional<BowlSpan> bowlSpan(HoleGround ground, double joinRadius, double angle) {
        double inner = Double.NaN;
        double outer = Double.NaN;
        // A radial survey avoids sampling every block of the bowl with the world's expensive terrain density.
        for (double distance = joinRadius + shape.minRadius() / 2;
                distance <= cone.outerRadius(); distance += HoleGround.GRID) {
            double x = cell.centreX() + distance * Math.cos(angle);
            double z = cell.centreZ() + distance * Math.sin(angle);
            double surface = ConeShape.entranceSurface(settings, cone, bounds, cell, x, z);
            if (ground.heightAt((int) Math.floor(x), (int) Math.floor(z)) <= surface + 1) {
                if (Double.isFinite(inner)) {
                    break;
                }
                continue;
            }
            if (!Double.isFinite(inner)) {
                inner = distance;
            }
            outer = distance;
        }
        return Double.isFinite(inner) ? Optional.of(new BowlSpan(angle, inner, outer)) : Optional.empty();
    }

    private Optional<Disc> bowlDisc(HoleGround bowlGround, BowlSpan span, double floor, int band, int slot) {
        double dx = Math.cos(span.angle());
        double dz = Math.sin(span.angle());
        double inner = span.inner();
        double outer = cone.outerRadius() - PLACING_SLACK;
        if (floor < ConeShape.entranceSurface(settings, cone, bounds, cell,
                cell.centreX() + inner * dx, cell.centreZ() + inner * dz)
                || floor > ConeShape.entranceSurface(settings, cone, bounds, cell,
                cell.centreX() + span.outer() * dx, cell.centreZ() + span.outer() * dz)) {
            return Optional.empty();
        }
        double wall = wallAtFloor(span, floor);
        if (wall < inner || wall > outer) {
            return Optional.empty();
        }
        int base = BOWL_HASH_BASE + 8 * (band * MAX_SLOTS + slot);
        double radius = shape.radiusFor(surfaceDraw(RavineCells.unit(cell.hash(), base + 1)));
        // Constant-width shelves recede with their tier: enlarging every higher disc to the clear axis hides that stepping.
        radius = Math.min(radius, (span.outer() - span.inner()) / 2);
        double clear = clearFor(floor) + PLACING_SLACK;
        // Place the front in air and the back in the wall at this floor; fit the drawn size without moving the tier up or down.
        radius = Math.min(radius, Math.min((wall - clear) / (1 + SHELF_EMBED), (outer - wall) / (1 - SHELF_EMBED)));
        double distance;
        double x;
        double z;
        while (true) {
            if (radius < shape.minRadius()) {
                return Optional.empty();
            }
            distance = wall - SHELF_EMBED * radius;
            x = cell.centreX() + distance * dx;
            z = cell.centreZ() + distance * dz;
            if (insideBowlRim(bowlGround, x, z, floor, radius)) {
                break;
            }
            if (radius <= shape.minRadius()) {
                return Optional.empty();
            }
            radius = Math.max(shape.minRadius(), radius - HoleGround.GRID);
        }
        RimSettings.Collar collar = cone.rim().orElseThrow().collar();
        double bowl = Math.min(collar.surfaceBowlDepth(), shape.bowlDepthFor(radius, RavineCells.unit(cell.hash(), base + 2)));
        double rim = bowlGround.heightAt((int) Math.floor(cell.centreX() + span.outer() * dx),
                (int) Math.floor(cell.centreZ() + span.outer() * dz));
        double backX = x + radius * dx * 0.75;
        double backZ = z + radius * dz * 0.75;
        if (floor + bowl > rim - cone.ceilingMargin()
                || bowlGround.heightAt((int) Math.floor(backX), (int) Math.floor(backZ)) <= floor + bowl
                || ConeShape.entranceSurface(settings, cone, bounds, cell, backX, backZ) <= floor + bowl) {
            return Optional.empty();
        }
        // Same-tier neighbors deliberately overlap into arcs; distinct floors provide the receding steps and waterfall drops.
        return Optional.of(Disc.placed(x, z, floor, radius, shape.heightFor(radius), bowl));
    }

    /** Radial position of this tier's back wall, using the same bowl surface as the carve. */
    private double wallAtFloor(BowlSpan span, double floor) {
        double inner = span.inner();
        double outer = span.outer();
        for (int i = 0; i < 14; i++) {
            double middle = (inner + outer) / 2;
            double x = cell.centreX() + middle * Math.cos(span.angle());
            double z = cell.centreZ() + middle * Math.sin(span.angle());
            if (ConeShape.entranceSurface(settings, cone, bounds, cell, x, z) < floor) {
                inner = middle;
            } else {
                outer = middle;
            }
        }
        return (inner + outer) / 2;
    }

    private boolean insideBowlRim(HoleGround ground, double x, double z, double floor, double radius) {
        double reach = radius + PLACING_SLACK;
        double joinRadius = ConeShape.radiusAt(settings, cone, bounds, ConeShape.entranceFloor(cone, bounds, cell));
        for (int sample = 0; sample < 8; sample++) {
            double angle = sample * Math.PI / 4;
            double px = x + reach * Math.cos(angle);
            double pz = z + reach * Math.sin(angle);
            double radial = cell.distanceToCentre(px, pz);
            if (radial <= joinRadius) {
                continue;
            }
            if (radial > cone.outerRadius()) {
                return false;
            }
            double over = ground.heightAt((int) Math.floor(px), (int) Math.floor(pz));
            double surface = ConeShape.entranceSurface(settings, cone, bounds, cell, px, pz);
            // With no dome a buried back can cross the exposed rim safely, but it must stay below the natural ground there.
            if (over <= surface + 1 && floor + cone.rim().orElseThrow().collar().surfaceBowlDepth() > over - cone.ceilingMargin()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The ring's disc at a position as placed, before it is given its stem or root among the others ({@link #discs()} has those),
     * or empty if its dome would not fit under the ceiling margin.
     */
    Optional<Disc> at(int layer, int slot) {
        double unit = RavineCells.unit(cell.hash(), base(layer, slot) + 1);
        Optional<Disc> original = ringDisc(layer, slot, unit);
        if (original.isEmpty() || !exposedAtEntrance(original.get()) || surfaceDraw(unit) == unit) {
            return original;
        }
        // Refit the larger platform against both radial bounds and local terrain, keeping the original when it cannot fit.
        return ringDisc(layer, slot, surfaceDraw(unit)).filter(this::exposedAtEntrance).or(() -> original);
    }

    private Optional<Disc> ringDisc(int layer, int slot, double sizeDraw) {
        long hash = cell.hash();
        int base = base(layer, slot);
        double floor = nominalFloor(layer) + (RavineCells.unit(hash, base) - 0.5) * cone.layerJitter() * cone.layerSpacing();
        double wall = ConeShape.radiusAt(settings, cone, bounds, floor);
        double clear = clearFor(floor);
        // The cone's width at this height does not limit the disc: what does not fit carves into the rock around the cone.
        // Only the outer radius holds it, with the disc's inner edge at the clear air round the axis.
        double radius = Math.min(shape.radiusFor(sizeDraw), (cone.outerRadius() - clear - PLACING_SLACK) / 2);
        if (radius < shape.minRadius() / 2) {
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
        // The inner edge stays outside the clear air. The centre stays within a little of the wall if the disc is small
        // enough for that; a larger one sits against the clear air and reaches out past the wall.
        double inner = clear + radius + PLACING_SLACK;
        double furthest = Math.min(wall + EMBED * radius, cone.outerRadius() - radius);
        double distance = inner + RavineCells.unit(hash, originBase + 2) * Math.max(0, furthest - inner);
        double x = cell.centreX() + distance * Math.cos(angle);
        double z = cell.centreZ() + distance * Math.sin(angle);
        double height = domeHeight(x, z, radius, floor);
        if (height < shape.minHeight()) {
            return Optional.empty();
        }
        return Optional.of(Disc.placed(x, z, floor, radius, height, shape.bowlDepthFor(radius, RavineCells.unit(hash, base + 6))));
    }

    /** How many places on top of a disc may each hold a rider: one for every {@code spacing} blocks of the arc they stand on. */
    private int riderPlaces(Disc host) {
        double arc = 2 * RIDER_ARC * (RIDER_NEAREST + RIDER_FURTHEST) / 2 * host.radius();
        return cone.riderChance() <= 0 ? 0 : Math.clamp(Math.round(arc / cone.spacing()), 0, MAX_RIDERS);
    }

    /**
     * The disc standing on {@code host} at one of its places, or empty if the place stays free. A rider is smaller than its
     * host and stands near the host's outer edge, on the side away from the cone's axis: inside the host's dome, with its stem
     * on the host's platform and its centre outside the cone, where the ring has no discs. The host's roof is low there, so
     * the rider's own dome rises through it and opens the rock above and beyond, out of sight of the middle of the cone.
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
        double outward = Math.atan2(host.z() - cell.centreZ(), host.x() - cell.centreX());
        double turn = 2 * (place + 0.5 + (RavineCells.unit(hash, base + 3) - 0.5) * JITTER) / riderPlaces(host) - 1;
        double x = host.x() + out * Math.cos(outward + turn * RIDER_ARC);
        double z = host.z() + out * Math.sin(outward + turn * RIDER_ARC);
        // The floor is between the least that leaves headroom over the host's top there and the most that fits under its roof.
        double share = out / host.radius();
        double lowest = host.topAt(out) + shape.floorThickness() + RIDER_HEADROOM;
        double highest = host.floor() + Math.min(RIDER_HIGHEST, Math.sqrt(1 - share * share) - RIDER_UNDER_ROOF) * host.height();
        if (lowest > highest) {
            return Optional.empty();
        }
        double floor = lerp(RavineCells.unit(hash, base + 4), lowest, highest);
        double fromAxis = cell.distanceToCentre(x, z);
        double height = domeHeight(x, z, radius, floor);
        boolean outsideTheCone = fromAxis > ConeShape.radiusAt(settings, cone, bounds, floor);
        boolean behindItsHost = fromAxis > cell.distanceToCentre(host.x(), host.z());
        boolean withinBounds = fromAxis - radius >= clearFor(floor) + PLACING_SLACK && fromAxis + radius <= cone.outerRadius();
        boolean inTheHostsDome = host.domeDistance(x, floor, z) < 0;
        if (!outsideTheCone || !behindItsHost || !withinBounds || !inTheHostsDome || height < shape.minHeight()) {
            return Optional.empty();
        }
        Disc rider = Disc.placed(x, z, floor, radius, height, shape.bowlDepthFor(radius, RavineCells.unit(hash, base + 6)));
        return Optional.of(new Drawn(surfaceShape(rider), RavineCells.unit(hash, base + 5) < cone.hangChance()));
    }

    private static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }

    private double surfaceDraw(double unit) {
        double bias = cone.rim().map(rim -> rim.collar().surfaceSizeBias()).orElse(0F);
        return bias == 0 ? unit : 1 - Math.pow(1 - unit, 1 + 3 * bias);
    }

    private Disc surfaceShape(Disc disc) {
        if (!exposedAtEntrance(disc)) {
            return disc;
        }
        double bowl = Math.min(disc.bowl(), cone.rim().orElseThrow().collar().surfaceBowlDepth());
        return new Disc(disc.x(), disc.z(), disc.floor(), disc.radius(), disc.height(), bowl, disc.support());
    }

    private boolean exposedAtEntrance(Disc disc) {
        if (cone.rim().isEmpty()) {
            return false;
        }
        // Include ordinary upper platforms whose dome opens through the bowl, not just the added concentric arcs.
        for (int sample = 0; sample < 9; sample++) {
            double share = sample == 0 ? 0 : 0.7;
            double angle = (sample - 1) * Math.PI / 4;
            double x = disc.x() + share * disc.radius() * Math.cos(angle);
            double z = disc.z() + share * disc.radius() * Math.sin(angle);
            double roof = disc.floor() + disc.height() * Math.sqrt(1 - share * share);
            if (roof > ConeShape.entranceSurface(settings, cone, bounds, cell, x, z) + 1) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSurface(Disc disc) {
        return surfaceDiscs.contains(disc);
    }

    @Override
    public boolean biomeContains(Disc disc, DiscShape shape, double x, double y, double z, double margin) {
        if (!disc.biomeContains(shape, x, y, z, margin)) {
            return false;
        }
        return !isSurface(disc) || ConeShape.entranceSurface(settings, cone, bounds, cell, x, z)
                <= disc.topAt(Math.hypot(x - disc.x(), z - disc.z())) + margin;
    }

    @Override
    public List<Disc> discs() {
        return discs;
    }

    @Override
    public List<Disc> near(double x, double z) {
        return index.near(x, z);
    }
}

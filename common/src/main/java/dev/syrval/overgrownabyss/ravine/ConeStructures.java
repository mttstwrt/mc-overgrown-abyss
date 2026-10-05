package dev.syrval.overgrownabyss.ravine;

import java.util.Optional;

/**
 * The free-standing structures in a cone: a thick flat-topped cap on a stem, like a mushroom. They are laid out in layers
 * from just above the cavern roof to just under the top of the cone, with a number of slots round each layer's ring. Each
 * is a pure function of the hole's hash and its (layer, slot), so nothing is stored. A structure may sit straight above
 * the one in the layer below, which makes them stack: its stem then lands on that cap. Otherwise a stem runs down to the
 * cavern. The cavern is not subtracted from, so the stems end on its dome.
 *
 * <p>Like the ravine's floor slabs, the structures are rock put back into the cone's air; {@link ConeShape} subtracts them
 * from it.
 */
final class ConeStructures {
    static final int MAX_LAYERS = 32;
    static final int MAX_SLOTS = 64;
    private static final int HASH_BASE = 9000;
    private static final double JITTER = 0.8;
    // A cap may sit this fraction of its radius past the wall, into the rock, so it joins the wall instead of touching it.
    private static final double EMBED = 0.3;
    // Fraction of the room between the clear cylinder and the wall that a cap may take.
    private static final double MAX_SHARE = 0.55;

    private ConeStructures() {}

    /** One structure: where its axis is, the height of its flat top, and its cap and stem radii. */
    record Structure(double x, double z, double top, double radius, double stemRadius, int layer) {}

    static double lowestTop(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        return bounds.floorY() + settings.cavernHeight() + cone.baseClearance();
    }

    static int layers(RavineSettings settings, ConeSettings cone, RavineBounds bounds) {
        double span = bounds.topY() - cone.ceilingMargin() - lowestTop(settings, cone, bounds);
        return span < 0 ? 0 : (int) Math.min(MAX_LAYERS, Math.floor(span / cone.layerSpacing()) + 1);
    }

    static double nominalTop(RavineSettings settings, ConeSettings cone, RavineBounds bounds, int layer) {
        return lowestTop(settings, cone, bounds) + layer * cone.layerSpacing();
    }

    static int slots(RavineSettings settings, ConeSettings cone, RavineBounds bounds, int layer) {
        double wall = ConeShape.radiusAt(settings, cone, bounds, nominalTop(settings, cone, bounds, layer));
        double ring = (cone.clearRadius() + wall) / 2;
        return Math.clamp(Math.round(2 * Math.PI * ring / cone.spacing()), 3, MAX_SLOTS);
    }

    private static int base(int layer, int slot) {
        return HASH_BASE + 8 * (layer * MAX_SLOTS + slot);
    }

    private static boolean stacked(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, int layer, int slot) {
        return layer > 0
                && slot < slots(settings, cone, bounds, layer - 1)
                && RavineCells.unit(cell.hash(), base(layer, slot) + 4) < cone.stackChance();
    }

    /** The structure at a position, or empty if the cone is too narrow there or its top would pass the ceiling margin. */
    static Optional<Structure> at(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, int layer, int slot) {
        long hash = cell.hash();
        int base = base(layer, slot);
        double top = nominalTop(settings, cone, bounds, layer) + (RavineCells.unit(hash, base) - 0.5) * cone.layerJitter() * cone.layerSpacing();
        if (top > bounds.topY() - cone.ceilingMargin()) {
            return Optional.empty();
        }
        double wall = ConeShape.radiusAt(settings, cone, bounds, top);
        double room = wall - cone.clearRadius();
        // Raising the draw to a power keeps most caps modest and a few large.
        double radius = Math.min(lerp(Math.pow(RavineCells.unit(hash, base + 1), 1.3), cone.minRadius(), cone.maxRadius()), MAX_SHARE * room);
        if (radius < cone.minRadius() / 2) {
            return Optional.empty();
        }
        // A stacked structure keeps the angle and the relative distance out of the structure it sits on.
        int originLayer = layer;
        while (stacked(settings, cone, bounds, cell, originLayer, slot)) {
            originLayer--;
        }
        int originBase = base(originLayer, slot);
        int count = slots(settings, cone, bounds, originLayer);
        double stagger = (originLayer % 2) * 0.5;
        double angle = 2 * Math.PI * (slot + 0.5 + (RavineCells.unit(hash, originBase + 3) - 0.5) * JITTER + stagger) / count;
        // The inner edge stays outside the clear cylinder, and the centre stays within a little of the wall.
        double inner = cone.clearRadius() + radius;
        double distance = inner + RavineCells.unit(hash, originBase + 2) * Math.max(0, wall + EMBED * radius - inner);
        double stem = Math.min(Math.max(cone.stemRadius(), cone.stemFraction() * radius), radius);
        return Optional.of(new Structure(
                cell.centreX() + distance * Math.cos(angle), cell.centreZ() + distance * Math.sin(angle), top, radius, stem, layer));
    }

    /** Distance to the nearest structure near a point: negative inside, infinity if none is close. Never inside the clear cylinder. */
    static double distance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        int layers = layers(settings, cone, bounds);
        double lowest = lowestTop(settings, cone, bounds);
        // A stem hangs from its cap down, so every structure whose top is above this point can reach it. Jitter moves a
        // top by less than a layer.
        int first = Math.max(0, (int) Math.floor((y - lowest) / cone.layerSpacing()) - 1);
        double nearest = Double.POSITIVE_INFINITY;
        for (int layer = first; layer < layers; layer++) {
            int count = slots(settings, cone, bounds, layer);
            for (int slot = 0; slot < count; slot++) {
                var structure = at(settings, cone, bounds, cell, layer, slot);
                if (structure.isPresent() && y <= structure.get().top()) {
                    nearest = Math.min(nearest, structureDistance(settings, cone, bounds, cell, structure.get(), x, y, z));
                }
            }
        }
        return Math.max(nearest, cone.clearRadius() - cell.distanceToCentre(x, z));
    }

    /** The cap: a flat-topped disc. Below it the stem: as wide as the cap, narrowing along a hyperbola to the stem's radius. */
    private static double structureDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, Structure s, double x, double y, double z) {
        double horizontal = Math.hypot(x - s.x(), z - s.z());
        double cap = Math.max(horizontal - s.radius(), Math.max(y - s.top(), s.top() - cone.capThickness() - y));
        double depth = s.top() - cone.capThickness() - y;
        if (depth < 0) {
            return cap;
        }
        double stemDistance = horizontal - Math.max(s.stemRadius(), s.radius() * cone.funnelScale() / (depth + cone.funnelScale()));
        // Past the carve's falloff a stem makes no difference, so skip the search for what it lands on.
        if (stemDistance < settings.edgeFalloff() && y < bottomOf(settings, cone, bounds, cell, s)) {
            return cap;
        }
        return Math.min(cap, stemDistance);
    }

    /** Height of the underside of the highest lower cap that holds the stem's axis, where the stem ends; none if it reaches the cavern. */
    static double bottomOf(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, Structure s) {
        double bottom = Double.NEGATIVE_INFINITY;
        for (int layer = 0; layer < s.layer(); layer++) {
            int count = slots(settings, cone, bounds, layer);
            for (int slot = 0; slot < count; slot++) {
                var below = at(settings, cone, bounds, cell, layer, slot);
                if (below.isPresent()
                        && below.get().top() < s.top() - cone.capThickness()
                        && Math.hypot(s.x() - below.get().x(), s.z() - below.get().z()) <= below.get().radius() - s.stemRadius() - 1) {
                    bottom = Math.max(bottom, below.get().top() - cone.capThickness());
                }
            }
        }
        return bottom;
    }

    private static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }
}

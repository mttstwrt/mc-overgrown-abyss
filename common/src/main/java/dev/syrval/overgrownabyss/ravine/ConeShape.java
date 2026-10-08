package dev.syrval.overgrownabyss.ravine;

import java.util.OptionalDouble;

/**
 * Signed distance, in blocks, from a point to the open volume of a cone-shaped hole (see {@link ConeSettings}): negative
 * inside (air), positive outside. The volume is the cone, widest at the floor, the city cavern's dome, and the domes of the
 * discs ({@link ConeDiscLayout}), which cut pockets into the wall. The discs' platforms, stems and roots are separate: they are rock to be
 * added back, which the original terrain cannot always provide, so they have their own distance, {@link #rockDistance}. Rooms
 * are carved first and the rock put back after, as in a ravine, so a dome stays clear except for the stems and platforms of
 * other discs. The cone has no roof over the cavern, so unlike a ravine's the rock is not cut off by the cavern's dome: stems run
 * down to the floor, and a low disc may stand in the cavern's airspace. No rock ever enters the clear air around the axis.
 *
 * <p>Above its top a cone without a rim is a bore of its top radius. With one (see {@link RimSettings}) it opens as a bowl:
 * everything inside the mouth, and outside it only what lies above a surface that rises away from the mouth's edge.
 */
final class ConeShape {
    // The cell hash's index for the roughness of the bowl round the mouth.
    private static final int COLLAR_HASH = 700_000;

    private ConeShape() {}

    /**
     * The height at which the wall is {@code radius} from the axis, the inverse of {@link #radiusAt}: the floor if the wall is
     * never further out than that, empty if it is never that close, which is anywhere inside the top radius.
     */
    static OptionalDouble heightAt(RavineSettings settings, ConeSettings cone, RavineBounds bounds, double radius) {
        if (radius >= cone.baseRadius()) {
            return OptionalDouble.of(bounds.floorY());
        }
        if (radius <= cone.topRadius()) {
            return OptionalDouble.empty();
        }
        double t = 1 - Math.pow((radius - cone.topRadius()) / (cone.baseRadius() - cone.topRadius()), 1.0 / cone.flare());
        return OptionalDouble.of(bounds.floorY() + t * (bounds.topY() - bounds.floorY()));
    }

    static double signedDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, DiscLayout layout, double x, double y, double z) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        return openDistance(settings, cone, bounds, cell, layout, cell.distanceToCentre(x, z), x, y, z);
    }

    /**
     * Distance to the nearest platform, stem or root, counted only where the cone, a dome or the cavern opened the ground: the
     * rock that is added back.
     */
    static double rockDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, DiscLayout layout, double x, double y, double z) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double radial = cell.distanceToCentre(x, z);
        // Low discs reach past the cone into the cavern's airspace, which is just as open.
        double open = openDistance(settings, cone, bounds, cell, layout, radial, x, y, z);
        // A disc only changes anything in open air or within the carve's falloff of it, so skip the search elsewhere.
        if (open >= settings.edgeFalloff()) {
            return Double.POSITIVE_INFINITY;
        }
        double rock = Math.max(Discs.rockDistance(layout, settings.discs(), x, y, z), open);
        return Math.max(rock, clearAt(cone, bounds, y) - radial);
    }

    // The hole itself, the discs' domes and the cavern.
    private static double openDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, DiscLayout layout,
            double radial, double x, double y, double z) {
        double hole = y > bounds.topY() && cone.rim().isPresent()
                ? bowlDistance(cone, cone.rim().get().collar(), bounds, cell, radial, x, y, z)
                : coneDistance(settings, cone, bounds, radial, y);
        double open = Math.min(hole, domes(settings, cone, bounds, layout, radial, x, y, z));
        return Math.min(open, RavineShape.cavernDistance(settings, bounds, radial, y));
    }

    private static double domes(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, DiscLayout layout, double radial, double x, double y, double z) {
        // Discs carve well outside the cone, but none lies past the outer radius.
        double reach = cone.outerRadius() + ConeDiscLayout.PLACING_SLACK + settings.edgeFalloff();
        return radial > reach ? Double.POSITIVE_INFINITY : Discs.domeDistance(layout, x, y, z);
    }

    /** Radius of the cone at height {@code y}: the base radius at the floor, narrowing to the top radius. */
    static double radiusAt(RavineSettings settings, ConeSettings cone, RavineBounds bounds, double y) {
        double t = Math.clamp((y - bounds.floorY()) / (double) (bounds.topY() - bounds.floorY()), 0, 1);
        return cone.topRadius() + (cone.baseRadius() - cone.topRadius()) * Math.pow(1 - t, cone.flare());
    }

    /**
     * Radius of the clear air round the axis at height {@code y}, which no platform, stem or root enters: the clear radius, or
     * with an {@code upper} that at the floor, widening evenly to the upper one at the top.
     */
    static double clearAt(ConeSettings cone, RavineBounds bounds, double y) {
        if (cone.upper().isEmpty()) {
            return cone.clearRadius();
        }
        double t = Math.clamp((y - bounds.floorY()) / (double) (bounds.topY() - bounds.floorY()), 0, 1);
        return cone.clearRadius() + (cone.upper().get().clearRadius() - cone.clearRadius()) * t;
    }

    /**
     * The bowl above the top: open inside the mouth, and outside it above the collar's surface, which rises from the mouth's
     * edge on that side. The surface has no upper end, so however high the ground stands it is cut back to a slope and never
     * to a wall. Dividing by the slope's length makes this the distance to the surface rather than the vertical gap.
     */
    private static double bowlDistance(
            ConeSettings cone, RimSettings.Collar collar, RavineBounds bounds, RavineCell cell, double radial, double x, double y, double z) {
        double out = radial - cone.topRadius();
        if (out <= 0) {
            return out;
        }
        // The hole's reach is counted to the outer radius (see RavineSettings.maxReach), so nothing is opened past it.
        if (radial > cone.outerRadius()) {
            return Double.POSITIVE_INFINITY;
        }
        double rough = RavineCells.smoothOver(cell.hash(), COLLAR_HASH, (int) Math.floor(x), (int) Math.floor(z), collar.roughnessWavelength()) * 2 - 1;
        double surface = bounds.edgeAt(x - cell.centreX(), z - cell.centreZ()) + collar.riseAt(out) + rough * collar.roughness();
        double slope = collar.slopeAt(out);
        double under = (surface - y) / Math.sqrt(1 + slope * slope);
        // Where the edge stands above the top, the mouth beside this rock is open too, and may be the nearer air.
        return under > 0 ? Math.min(under, out) : under;
    }

    // Dividing by the slope's length makes this the distance to the wall's surface rather than just the horizontal gap.
    private static double coneDistance(RavineSettings settings, ConeSettings cone, RavineBounds bounds, double radial, double y) {
        double height = bounds.topY() - bounds.floorY();
        double t = Math.clamp((y - bounds.floorY()) / height, 0, 1);
        double slope = (cone.baseRadius() - cone.topRadius()) * cone.flare() * Math.pow(1 - t, cone.flare() - 1) / height;
        return (radial - radiusAt(settings, cone, bounds, y)) / Math.sqrt(1 + slope * slope);
    }
}

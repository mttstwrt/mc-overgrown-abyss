package dev.syrval.overgrownabyss.ravine;

import java.util.OptionalDouble;

/**
 * Signed distance, in blocks, from a point to the open volume of a cone-shaped hole (see {@link ConeSettings}): negative
 * inside (air), positive outside. The volume is the cone, widest at the floor, the city cavern's dome, and the domes of the
 * discs ({@link ConeDiscLayout}), which cut pockets into the wall. The discs' platforms, stems and roots are separate: they are rock to be
 * added back, which the original terrain cannot always provide, so they have their own distance, {@link #rockDistance}. Rooms
 * are carved first and the rock put back after, as in a ravine, so a dome stays clear except for the stems and platforms of
 * other discs. The cone has no roof over the cavern, so unlike a ravine's the rock is not cut off by the cavern's dome: stems run
 * down to the floor, and a low disc may stand in the cavern's airspace. No rock ever enters the clear cylinder around the axis.
 */
final class ConeShape {
    private ConeShape() {}

    /**
     * The height at which the wall is {@code radius} from the axis, the inverse of {@link #radiusAt}: the floor if the wall is
     * never further out than that, empty if it is never that close, which is anywhere inside the top radius.
     */
    static OptionalDouble heightAt(RavineSettings settings, ConeSettings cone, RavineBounds bounds, double radius) {
        if (radius >= settings.cavernRadius()) {
            return OptionalDouble.of(bounds.floorY());
        }
        if (radius <= cone.topRadius()) {
            return OptionalDouble.empty();
        }
        double t = 1 - Math.pow((radius - cone.topRadius()) / (settings.cavernRadius() - cone.topRadius()), 1.0 / cone.flare());
        return OptionalDouble.of(bounds.floorY() + t * (bounds.topY() - bounds.floorY()));
    }

    static double signedDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, DiscLayout layout, double x, double y, double z) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double radial = cell.distanceToCentre(x, z);
        double open = Math.min(coneDistance(settings, cone, bounds, radial, y), domes(settings, cone, bounds, layout, radial, x, y, z));
        return Math.min(open, RavineShape.cavernDistance(settings, bounds, radial, y));
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
        double open = Math.min(coneDistance(settings, cone, bounds, radial, y), domes(settings, cone, bounds, layout, radial, x, y, z));
        // Low discs reach past the cone into the cavern's airspace, which is just as open.
        open = Math.min(open, RavineShape.cavernDistance(settings, bounds, radial, y));
        // A disc only changes anything in open air or within the carve's falloff of it, so skip the search elsewhere.
        if (open >= settings.edgeFalloff()) {
            return Double.POSITIVE_INFINITY;
        }
        double rock = Math.max(Discs.rockDistance(layout, settings.discs(), x, y, z), open);
        return Math.max(rock, cone.clearRadius() - radial);
    }

    private static double domes(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, DiscLayout layout, double radial, double x, double y, double z) {
        // Discs carve well outside the cone, but none lies past the outer radius.
        double reach = cone.outerRadius() + ConeDiscLayout.PLACING_SLACK + settings.edgeFalloff();
        return radial > reach ? Double.POSITIVE_INFINITY : Discs.domeDistance(layout, x, y, z);
    }

    /** Radius of the cone at height {@code y}: the cavern's at the floor, narrowing to the top radius. */
    static double radiusAt(RavineSettings settings, ConeSettings cone, RavineBounds bounds, double y) {
        double t = Math.clamp((y - bounds.floorY()) / (double) (bounds.topY() - bounds.floorY()), 0, 1);
        return cone.topRadius() + (settings.cavernRadius() - cone.topRadius()) * Math.pow(1 - t, cone.flare());
    }

    // Dividing by the slope's length makes this the distance to the wall's surface rather than just the horizontal gap.
    private static double coneDistance(RavineSettings settings, ConeSettings cone, RavineBounds bounds, double radial, double y) {
        double height = bounds.topY() - bounds.floorY();
        double t = Math.clamp((y - bounds.floorY()) / height, 0, 1);
        double slope = (settings.cavernRadius() - cone.topRadius()) * cone.flare() * Math.pow(1 - t, cone.flare() - 1) / height;
        return (radial - radiusAt(settings, cone, bounds, y)) / Math.sqrt(1 + slope * slope);
    }
}

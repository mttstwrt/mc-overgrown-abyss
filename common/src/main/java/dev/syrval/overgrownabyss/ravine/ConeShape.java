package dev.syrval.overgrownabyss.ravine;

/**
 * Signed distance, in blocks, from a point to the open volume of a cone-shaped hole (see {@link ConeSettings}): negative
 * inside (air), positive outside. The volume is the cone, widest at the floor, and the city cavern's dome. The
 * free-standing structures ({@link ConeStructures}) are separate: they are rock to be added back, which the original terrain
 * cannot always provide, so they have their own distance, {@link #rockDistance}. They end on the cavern's dome and never
 * enter the clear cylinder around the axis.
 */
final class ConeShape {
    private ConeShape() {}

    static double signedDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double radial = cell.distanceToCentre(x, z);
        return Math.min(coneDistance(settings, cone, bounds, radial, y), RavineShape.cavernDistance(settings, bounds, radial, y));
    }

    /**
     * Distance to the nearest structure, counted only inside the cone and outside the cavern: the rock that is added back.
     * Structures end on the cavern's dome and never reach the clear cylinder.
     */
    static double rockDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double radial = cell.distanceToCentre(x, z);
        double inside = Math.max(coneDistance(settings, cone, bounds, radial, y), -RavineShape.cavernDistance(settings, bounds, radial, y));
        // A structure only changes anything inside the cone or within the carve's falloff of it, so skip the search elsewhere.
        if (inside >= settings.edgeFalloff()) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(ConeStructures.distance(settings, cone, bounds, cell, x, y, z), inside);
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

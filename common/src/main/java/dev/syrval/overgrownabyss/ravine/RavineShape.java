package dev.syrval.overgrownabyss.ravine;

/**
 * Signed distance, in blocks, from a point to the open volume of one ravine: negative inside (air), positive outside.
 * The volume is the ravine shaft from {@code bounds.floorY()} upwards, narrowing towards the floor in terraced steps, joined
 * to a domed cavern centred on the ravine whose floor is flat at the floor.
 */
public final class RavineShape {
    private RavineShape() {}

    public static double signedDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double shaft = cell.distanceToAxis(x, z) - halfWidthAt(settings, bounds, cell, y);
        double cavern = cavernDistance(settings, bounds, cell.distanceToCentre(x, z), y);
        return Math.min(shaft, cavern);
    }

    /** Horizontal distance from {@code (x, z)} to the widest extent of the open volume, at any height. */
    public static double horizontalDistance(RavineSettings settings, RavineCell cell, double x, double z) {
        double shaft = cell.distanceToAxis(x, z) - cell.halfWidth();
        double cavern = cell.distanceToCentre(x, z) - settings.cavernRadius();
        return Math.min(shaft, cavern);
    }

    /**
     * Whether a point is inside the cavern dome grown outwards by {@code margin} blocks. The margin reaches the floor,
     * walls and roof surfaces, so features placed on them are placed in the cavern's biome too.
     */
    public static boolean cavernContains(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z, double margin) {
        double above = y - bounds.floorY();
        if (above < -margin || above >= settings.cavernHeight() + margin) {
            return false;
        }
        double t = Math.clamp(above / settings.cavernHeight(), 0, 1);
        return cell.distanceToCentre(x, z) <= settings.cavernRadius() * Math.sqrt(1 - t * t) + margin;
    }

    static double halfWidthAt(RavineSettings settings, RavineBounds bounds, RavineCell cell, double y) {
        double t = Math.clamp((terraced(settings, bounds, y) - bounds.floorY()) / (bounds.topY() - bounds.floorY()), 0, 1);
        double bottom = settings.bottomWidthFactor();
        return cell.halfWidth() * (bottom + (1 - bottom) * t);
    }

    // Holding the width constant within each step turns a smooth taper into ledges at every step boundary.
    private static double terraced(RavineSettings settings, RavineBounds bounds, double y) {
        int step = settings.terraceStep();
        if (step == 0) {
            return y;
        }
        double stepped = Math.floor((y - bounds.floorY()) / step) * step + bounds.floorY();
        return y + (stepped - y) * settings.terraceStrength();
    }

    private static double cavernDistance(RavineSettings settings, RavineBounds bounds, double radial, double y) {
        double t = (y - bounds.floorY()) / settings.cavernHeight();
        if (t >= 1) {
            return Double.POSITIVE_INFINITY;
        }
        return radial - settings.cavernRadius() * Math.sqrt(1 - t * t);
    }
}

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
 *
 * <p>The cone, the bowl and the cavern are the main cut, and its wall is uneven where the settings say so (see
 * {@link WallNoise}). The discs' domes are not.
 */
final class ConeShape {
    // The cell hash's index for the roughness of the bowl round the mouth.
    private static final int COLLAR_HASH = 700_000;

    private ConeShape() {}

    /**
     * The height at which the wall is {@code radius} from the axis before any unevenness, the inverse of {@link #radiusAt}: the
     * floor if the wall is never further out than that, empty if it is never that close, which is anywhere inside the top
     * radius.
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

    /**
     * The height at which a column meets the cone's wall going up, unevenness and all: the lowest rock over it. Empty where
     * the column is open all the way to the top.
     */
    static OptionalDouble wallOver(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, double x, double z) {
        double radial = cell.distanceToCentre(x, z);
        WallNoise noise = settings.wallNoise();
        double reach = noise.maxDisplacement();
        if (reach == 0) {
            return heightAt(settings, cone, bounds, radial);
        }
        // The wall is moved by no more than its reach, so it meets the column between where it would if it were that much
        // further in and that much further out.
        OptionalDouble lowest = heightAt(settings, cone, bounds, radial + reach);
        if (lowest.isEmpty()) {
            return OptionalDouble.empty();
        }
        OptionalDouble highest = heightAt(settings, cone, bounds, radial - reach);
        double turn = turnOf(cell, x, z);
        for (double y = lowest.getAsDouble(); y < highest.orElse(bounds.topY()); y++) {
            if (radial - noise.offset(cell.hash(), around(cone), turn, y) >= radiusAt(settings, cone, bounds, y)) {
                return OptionalDouble.of(y);
            }
        }
        return highest;
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

    // The main cut and the discs' domes.
    private static double openDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, DiscLayout layout,
            double radial, double x, double y, double z) {
        double cut = cutDistance(settings, cone, bounds, cell, radial, radial, x, y, z);
        WallNoise noise = settings.wallNoise();
        // Unevenness moves the wall by no more than its reach, so further from the wall than that and the carve's falloff it
        // changes nothing, and is not worked out.
        if (!noise.layers().isEmpty() && Math.abs(cut) < settings.edgeFalloff() + noise.maxDisplacement()) {
            double moved = radial - noise.offset(cell.hash(), around(cone), turnOf(cell, x, z), y);
            cut = cutDistance(settings, cone, bounds, cell, radial, moved, x, y, z);
        }
        return Math.min(cut, domes(settings, cone, bounds, layout, radial, x, y, z));
    }

    /**
     * The main cut: the hole itself and the cavern at its foot. {@code moved} is the distance from the axis as the wall's
     * unevenness makes it count: moving the point inwards by as much as the wall there is moved outwards.
     */
    private static double cutDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, double radial, double moved, double x, double y, double z) {
        double hole = y > bounds.topY() && cone.rim().isPresent()
                ? bowlDistance(cone, cone.rim().get().collar(), bounds, cell, radial, moved, x, y, z)
                : coneDistance(settings, cone, bounds, moved, y);
        return Math.min(hole, RavineShape.cavernDistance(settings, bounds, moved, y));
    }

    private static double domes(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, DiscLayout layout, double radial, double x, double y, double z) {
        // Discs carve well outside the cone, but none lies past the outer radius.
        double reach = cone.outerRadius() + ConeDiscLayout.PLACING_SLACK + settings.edgeFalloff();
        return radial > reach ? Double.POSITIVE_INFINITY : Discs.domeDistance(layout, x, y, z);
    }

    /** Radius of the cone at height {@code y} before any unevenness: the base radius at the floor, narrowing to the top radius. */
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

    // The distance round the hole that the wall's unevenness counts its wavelengths along: where the cone is of middling width.
    private static double around(ConeSettings cone) {
        return Math.PI * (cone.topRadius() + cone.baseRadius());
    }

    // How far round the axis a column is, from 0 to 1.
    private static double turnOf(RavineCell cell, double x, double z) {
        double turn = Math.atan2(z - cell.centreZ(), x - cell.centreX()) / (2 * Math.PI);
        return turn - Math.floor(turn);
    }

    /**
     * The bowl above the top: open inside the mouth, and outside it above the collar's surface, which rises from the mouth's
     * edge on that side. The surface has no upper end, so however high the ground stands it is cut back to a slope and never
     * to a wall. Dividing by the slope's length makes this the distance to the surface rather than the vertical gap.
     */
    private static double bowlDistance(
            ConeSettings cone, RimSettings.Collar collar, RavineBounds bounds, RavineCell cell, double radial, double moved, double x, double y, double z) {
        double out = moved - cone.topRadius();
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

package dev.syrval.verdantdeep.ravine;

import java.util.OptionalDouble;

/**
 * Signed distance, in blocks, from a point to the open volume of a cone-shaped hole (see {@link ConeSettings}): negative
 * inside (air), positive outside. The volume is the cone, widest at the floor, the city cavern's dome, and the domes of the
 * underground discs ({@link ConeDiscLayout}), which cut pockets into the wall. Surface shelves use the open bowl instead
 * of carving domes. The discs' platforms, stems and roots are separate: they are rock to be
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
        if (cone.rim().map(rim -> rim.collar().depth() > 0).orElse(false)) {
            // The entrance removes some of the old wall. Hanging supports must find the rock actually left behind.
            double turn = turnOf(cell, x, z);
            for (double y = bounds.floorY(); y <= bounds.topY(); y++) {
                double moved = radial - noise.offset(cell.hash(), around(cone), turn, y);
                if (cutDistance(settings, cone, bounds, cell, radial, moved, x, y, z) > 0) {
                    return OptionalDouble.of(y);
                }
            }
            return OptionalDouble.empty();
        }
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

    /**
     * How far a point is from the nearest rock of any kind, which a root asks as it grows: above 0 in open air, below 0 in
     * the ground the hole left, in a platform or a stem, or under the floor.
     */
    static double airDistance(
            RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, DiscLayout layout, double x, double y, double z) {
        double overFloor = y - bounds.floorY();
        if (overFloor < 0) {
            return overFloor;
        }
        double radial = cell.distanceToCentre(x, z);
        double open = openDistance(settings, cone, bounds, cell, layout, radial, x, y, z);
        // As in rockDistance: a disc's rock counts only where the ground was opened, and never in the clear air.
        double rock = open >= settings.edgeFalloff()
                ? Double.POSITIVE_INFINITY
                : Math.max(Discs.rockDistance(layout, settings.discs(), x, y, z), clearAt(cone, bounds, y) - radial);
        return Math.min(Math.min(-open, rock), overFloor);
    }

    /**
     * Radius of the main cut's wall, unevenness and all, on the side of the axis that a column is on, at a height between the
     * floor and the top: the cone's, or the cavern's where that is wider.
     */
    static double wallRadiusAt(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, double x, double z, double y) {
        if (cone.rim().map(rim -> rim.collar().depth() > 0).orElse(false) && y > entranceFloor(cone, bounds, cell)) {
            double angle = Math.atan2(z - cell.centreZ(), x - cell.centreX());
            double offset = settings.wallNoise().offset(cell.hash(), around(cone), turnOf(cell, x, z), y);
            double inside = 0;
            double outside = cone.outerRadius();
            // Roots ask for a wall anchor infrequently; invert the same entrance surface rather than anchoring them in air.
            for (int i = 0; i < 14; i++) {
                double radius = (inside + outside) / 2;
                double distance = cutDistance(settings, cone, bounds, cell, radius, radius - offset,
                        cell.centreX() + radius * Math.cos(angle), y, cell.centreZ() + radius * Math.sin(angle));
                if (distance < 0) {
                    inside = radius;
                } else {
                    outside = radius;
                }
            }
            return (inside + outside) / 2;
        }
        double t = (y - bounds.floorY()) / settings.cavernHeight();
        double cavern = t >= 0 && t < 1 ? settings.cavernRadius() * Math.sqrt(1 - t * t) : 0;
        double even = Math.max(radiusAt(settings, cone, bounds, y), cavern);
        return even + settings.wallNoise().offset(cell.hash(), around(cone), turnOf(cell, x, z), y);
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
        double hole = coneDistance(settings, cone, bounds, moved, y);
        if (cone.rim().isPresent() && y > entranceFloor(cone, bounds, cell)) {
            double bowl = bowlDistance(settings, cone, cone.rim().get().collar(), bounds, cell, radial, moved, x, y, z);
            hole = Math.min(hole, bowl);
        }
        return Math.min(hole, RavineShape.cavernDistance(settings, bounds, moved, y));
    }

    static double entranceFloor(ConeSettings cone, RavineBounds bounds, RavineCell cell) {
        double depth = cone.rim().map(rim -> {
            RimSettings.Collar collar = rim.collar();
            // Some entrances reach below the upper platforms; others leave a short rock drop to that layer.
            return Math.max(0, collar.depth() - collar.bottomVariation() * RavineCells.unit(cell.hash(), COLLAR_HASH + 1));
        }).orElse(0.0);
        return Math.max(bounds.floorY() + cone.ceilingMargin(), bounds.topY() - depth);
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
     * The entrance bowl: open inside its join with the cone, and outside it above the collar's surface, which rises from
     * the configured depth below the lip towards the ground on that side. The surface has no upper end, so however high
     * the ground stands it is cut back to a slope and never to a wall. Dividing by the slope's length makes this the distance
     * to the surface rather than the vertical gap.
     */
    private static double bowlDistance(
            RavineSettings settings, ConeSettings cone, RimSettings.Collar collar, RavineBounds bounds, RavineCell cell, double radial, double moved, double x, double y, double z) {
        double bottom = entranceFloor(cone, bounds, cell);
        double out = moved - radiusAt(settings, cone, bounds, bottom);
        if (out <= 0) {
            return out;
        }
        // The hole's reach is counted to the outer radius (see RavineSettings.maxReach), so nothing is opened past it.
        if (radial > cone.outerRadius()) {
            return Double.POSITIVE_INFINITY;
        }
        double uphill = bounds.edgeAt(x - cell.centreX(), z - cell.centreZ()) - bounds.topY();
        double t = Math.clamp(out / collar.width(), 0, 1);
        double surface = entranceSurface(collar, bounds, cell, bottom, out, x, z);
        double slope = collar.slopeAt(out) + uphill * 6 * t * (1 - t) / collar.width();
        double under = (surface - y) / Math.sqrt(1 + slope * slope);
        // Where the edge stands above the top, the mouth beside this rock is open too, and may be the nearer air.
        return under > 0 ? Math.min(under, out) : under;
    }

    /** The same bowl surface used by carving and by terrain-following disc placement. */
    static double entranceSurface(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, double x, double z) {
        RimSettings.Collar collar = cone.rim().orElseThrow().collar();
        double bottom = entranceFloor(cone, bounds, cell);
        double out = Math.max(0, cell.distanceToCentre(x, z) - radiusAt(settings, cone, bounds, bottom));
        return entranceSurface(collar, bounds, cell, bottom, out, x, z);
    }

    private static double entranceSurface(RimSettings.Collar collar, RavineBounds bounds, RavineCell cell,
                                          double bottom, double out, double x, double z) {
        double rough = RavineCells.smoothOver(cell.hash(), COLLAR_HASH, (int) Math.floor(x), (int) Math.floor(z), collar.roughnessWavelength()) * 2 - 1;
        double uphill = bounds.edgeAt(x - cell.centreX(), z - cell.centreZ()) - bounds.topY();
        double t = Math.clamp(out / collar.width(), 0, 1);
        // Starting at the uphill edge's height leaves a vertical cliff beside the mouth. Ease that height in across the
        // collar instead, with a level tangent at both ends so the approach joins the cone without a step.
        double blend = t * t * (3 - 2 * t);
        // Fade small roughness in from the join so it cannot cut a step into the cone below the entrance.
        double roughBlend = collar.depth() == 0 ? 1 : blend;
        double surface = bottom + uphill * blend + collar.riseAt(out) + rough * collar.roughness() * roughBlend;
        if (collar.weathering() == 0) {
            return surface;
        }
        // Broad worn shelves and finer gullies share the lower join's fade, so erosion cannot open a seam below it.
        double broad = RavineCells.smoothOver(cell.hash(), COLLAR_HASH + 2, (int) Math.floor(x), (int) Math.floor(z),
                collar.roughnessWavelength() * 2) * 2 - 1;
        double fine = RavineCells.smoothOver(cell.hash(), COLLAR_HASH + 3, (int) Math.floor(x), (int) Math.floor(z),
                Math.max(4, collar.roughnessWavelength() / 3)) * 2 - 1;
        double weathered = collar.weathering() * (broad + 0.35 * fine) * blend;
        return surface + weathered;
    }

    // Dividing by the slope's length makes this the distance to the wall's surface rather than just the horizontal gap.
    private static double coneDistance(RavineSettings settings, ConeSettings cone, RavineBounds bounds, double radial, double y) {
        double height = bounds.topY() - bounds.floorY();
        double t = Math.clamp((y - bounds.floorY()) / height, 0, 1);
        double slope = (cone.baseRadius() - cone.topRadius()) * cone.flare() * Math.pow(1 - t, cone.flare() - 1) / height;
        return (radial - radiusAt(settings, cone, bounds, y)) / Math.sqrt(1 + slope * slope);
    }
}

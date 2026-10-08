package dev.syrval.overgrownabyss.ravine;

/**
 * Signed distance, in blocks, from a point to the open volume of one ravine: negative inside (air), positive outside.
 * The volume is the ravine shaft from the floor upwards, a domed cavern centred on the cell whose floor is flat, and the
 * domes of the discs cut sideways into the shaft's walls (see {@link RavineDiscLayout}). The discs' platforms and stems are
 * rock put back afterwards ({@link #rockDistance}), so a stem is never cut off by the dome of a disc it passes through, and the
 * part of a platform that lies in the shaft is a ledge. A disc's reach into the shaft is limited by setting its centre back,
 * never by cutting it off. The shaft follows a centre line that bends in plan view and sways sideways with height, and narrows
 * towards the floor. A cone is the same with a different hole (see {@link ConeShape}); the discs are the same.
 *
 * <p>These are distance estimates, exact for a straight shaft and close for gentle curves, which is all the carve
 * needs: it only uses the sign and a few blocks of falloff either side of the wall. Each platform's top is exactly the
 * plane {@code y = floor}, so floors come out flat.
 */
public final class RavineShape {
    private RavineShape() {}

    /** The carve at a point. This builds the cell's discs for the call; use the overload with a layout to sample many points. */
    public static double signedDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        return signedDistance(settings, bounds, cell, DiscLayouts.of(settings, bounds, cell), x, y, z);
    }

    public static double signedDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, double x, double y, double z) {
        if (settings.cone().isPresent()) {
            return ConeShape.signedDistance(settings, settings.cone().get(), bounds, cell, layout, x, y, z);
        }
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double radial = cell.distanceToCentre(x, z);
        double open = openDistance(settings, bounds, cell, layout, radial, x, y, z);
        return Math.min(open, cavernDistance(settings, bounds, radial, y));
    }

    /** The rock at a point. This builds the cell's discs for the call; use the overload with a layout to sample many points. */
    public static double rockDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        return rockDistance(settings, bounds, cell, DiscLayouts.of(settings, bounds, cell), x, y, z);
    }

    /**
     * Distance to the nearest platform or stem, counted only where the carve opened the ground and outside the cavern, whose
     * dome has a rock roof here: negative inside, infinity where there is none. The rock is added back after the carve, as
     * {@code max(min(original, carve), rock)}, so it does not depend on there being terrain to restore.
     */
    public static double rockDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, double x, double y, double z) {
        if (settings.cone().isPresent()) {
            return ConeShape.rockDistance(settings, settings.cone().get(), bounds, cell, layout, x, y, z);
        }
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double radial = cell.distanceToCentre(x, z);
        double inside = Math.max(openDistance(settings, bounds, cell, layout, radial, x, y, z), -cavernDistance(settings, bounds, radial, y));
        // A disc only changes anything in open air or within the carve's falloff of it, so skip the search elsewhere.
        if (inside >= settings.edgeFalloff()) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(Discs.rockDistance(layout, settings.discs(), x, y, z), inside);
    }

    // The shaft and the discs' domes.
    private static double openDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, DiscLayout layout, double radial, double x, double y, double z) {
        RavineCell.Frame frame = leanedFrame(bounds, cell, x, y, z);
        double shaft = shaftDistance(settings, bounds, cell, frame, y);
        double domes = radial > settings.maxReach() || y > bounds.topY() ? Double.POSITIVE_INFINITY : Discs.domeDistance(layout, x, y, z);
        return Math.min(shaft, domes);
    }

    /**
     * Whether a point is inside the cavern dome grown outwards by {@code margin} blocks. The margin reaches the floor,
     * walls and roof surfaces, so features placed on them are placed in the cavern's biome too. Sideways it also covers as far
     * as an uneven wall may be moved out.
     */
    public static boolean cavernContains(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z, double margin) {
        double above = y - bounds.floorY();
        if (above < -margin || above >= settings.cavernHeight() + margin) {
            return false;
        }
        double t = Math.clamp(above / settings.cavernHeight(), 0, 1);
        return cell.distanceToCentre(x, z) <= settings.cavernRadius() * Math.sqrt(1 - t * t) + margin + settings.wallNoise().maxDisplacement();
    }

    /** How many rows of discs (or layers, in a cone) fit between the cavern roof and the ceiling margin in this level. */
    public static int discRows(RavineSettings settings, RavineBounds bounds) {
        return settings.cone().map(cone -> ConeDiscLayout.layers(settings, cone, bounds)).orElseGet(() -> RavineDomes.rows(settings, bounds));
    }

    /** Half the shaft's width at height {@code y}. */
    static double halfWidthAt(RavineSettings settings, RavineBounds bounds, RavineCell cell, double y) {
        double t = Math.clamp((y - bounds.floorY()) / (bounds.topY() - bounds.floorY()), 0, 1);
        double bottom = settings.bottomWidthFactor();
        return cell.halfWidth() * (bottom + (1 - bottom) * t);
    }

    /** Where the wall the room opens from sits, sideways of the chord, at the room's floor height. */
    static double wallSideways(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome dome) {
        return cell.bend().offset(unitAlong(cell, dome.along())) + dome.side() * halfWidthAt(settings, bounds, cell, dome.floor());
    }

    static double centreSideways(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineDomes.Dome dome) {
        return wallSideways(settings, bounds, cell, dome) + dome.side() * dome.offset() * dome.radius();
    }

    private static double shaftDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, RavineCell.Frame frame, double y) {
        double u = unitAlong(cell, frame.along());
        double lateral = frame.sideways() - cell.bend().offset(u);
        // The curve is longer than its chord, so a step along the chord covers less of the wall than it seems to.
        double slope = cell.halfLength() == 0 ? 0 : cell.bend().slope(u) / cell.halfLength();
        double across = lateral / Math.sqrt(1 + slope * slope);
        double beyond = Math.max(Math.abs(frame.along()) - cell.halfLength(), 0);
        return Math.sqrt(across * across + beyond * beyond) - halfWidthAt(settings, bounds, cell, y);
    }

    // The whole shaft slides sideways with height, so view the point from where the shaft is at that height.
    static RavineCell.Frame leanedFrame(RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        double t = Math.clamp((y - bounds.floorY()) / (double) (bounds.topY() - bounds.floorY()), 0, 1);
        double shift = cell.lean().shift(t);
        return cell.frame(x - shift * cell.lean().dirX(), z - shift * cell.lean().dirZ());
    }

    /** The inverse of {@link #leanedFrame}: the world {@code {x, z}} of a position in the leaned frame at height {@code y}. */
    static double[] worldOf(RavineBounds bounds, RavineCell cell, double along, double sideways, double y) {
        double t = Math.clamp((y - bounds.floorY()) / (double) (bounds.topY() - bounds.floorY()), 0, 1);
        double shift = cell.lean().shift(t);
        return new double[] {
            cell.centreX() + along * cell.dirX() - sideways * cell.dirZ() + shift * cell.lean().dirX(),
            cell.centreZ() + along * cell.dirZ() + sideways * cell.dirX() + shift * cell.lean().dirZ()};
    }

    /** Position along the chord as a fraction of the half length in [-1, 1]; a round hole has no length, so 0. */
    static double unitAlong(RavineCell cell, double along) {
        return cell.halfLength() == 0 ? 0 : Math.clamp(along / cell.halfLength(), -1, 1);
    }

    static double cavernDistance(RavineSettings settings, RavineBounds bounds, double radial, double y) {
        double t = (y - bounds.floorY()) / settings.cavernHeight();
        if (t >= 1) {
            return Double.POSITIVE_INFINITY;
        }
        return radial - settings.cavernRadius() * Math.sqrt(1 - t * t);
    }
}

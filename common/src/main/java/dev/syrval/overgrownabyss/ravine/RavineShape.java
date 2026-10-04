package dev.syrval.overgrownabyss.ravine;

/**
 * Signed distance, in blocks, from a point to the open volume of one ravine: negative inside (air), positive outside.
 * The volume is the ravine shaft from the floor upwards, joined to a domed cavern centred on the cell whose floor is
 * flat. The shaft follows a centre line that bends in plan view and sways sideways with height, narrows towards the
 * floor in wandering terraces, and has two walls that swell and narrow independently. Bridges are separate: they are
 * rock inside the open volume, see {@link #bridgeDistance}.
 *
 * <p>These are distance estimates, exact for a straight shaft and close for gentle curves, which is all the carve
 * needs: it only uses the sign and a few blocks of falloff either side of the wall.
 */
public final class RavineShape {
    private RavineShape() {}

    /** {@code terraceShift} moves the ledge heights at this position; it comes from noise, so the caller supplies it. */
    public static double signedDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z, double terraceShift) {
        if (y < bounds.floorY()) {
            return Double.POSITIVE_INFINITY;
        }
        double shaft = shaftDistance(settings, bounds, cell, x, y, z, terraceShift);
        double cavern = cavernDistance(settings, bounds, cell.distanceToCentre(x, z), y);
        return Math.min(shaft, cavern);
    }

    /**
     * Signed distance to the nearest bridge, or infinity if the ravine has none: negative inside the rock. Each is an
     * arch, a slab {@code width} long along the ravine, flat on top and thin in the middle, thickening towards the
     * walls where it springs from. Subtract it from the open volume with {@code max(open, -bridge)}.
     */
    public static double bridgeDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        if (cell.bridges().isEmpty()) {
            return nearest;
        }
        RavineCell.Frame frame = leanedFrame(bounds, cell, x, y, z);
        for (RavineCell.Bridge bridge : cell.bridges()) {
            double low = bounds.floorY() + settings.cavernHeight() + bridge.thickness();
            double high = bounds.topY() - bridge.thickness();
            if (high <= low) {
                continue;
            }
            double centreY = low + bridge.height() * (high - low);
            double u = unitAlong(cell, bridge.along());
            double sideways = Math.abs(frame.sideways() - cell.bend().offset(u));
            double span = Math.min(sideways / halfWidthAt(settings, bounds, cell, centreY, 0), 1.2);
            double top = centreY + bridge.thickness() * 0.5;
            double bottom = centreY - bridge.thickness() * (0.5 + 1.5 * span * span);
            double distance = Math.max(Math.abs(frame.along() - bridge.along()) - bridge.width() / 2, Math.max(y - top, bottom - y));
            nearest = Math.min(nearest, distance);
        }
        return nearest;
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

    /** Half the shaft's width at height {@code y}, before the walls swell or narrow. */
    static double halfWidthAt(RavineSettings settings, RavineBounds bounds, RavineCell cell, double y, double terraceShift) {
        double t = Math.clamp(
                (terraced(settings.walls(), bounds, y + terraceShift) - bounds.floorY()) / (bounds.topY() - bounds.floorY()), 0, 1);
        double bottom = settings.bottomWidthFactor();
        return cell.halfWidth() * (bottom + (1 - bottom) * t);
    }

    private static double shaftDistance(
            RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z, double terraceShift) {
        RavineCell.Frame frame = leanedFrame(bounds, cell, x, y, z);
        double u = unitAlong(cell, frame.along());
        double lateral = frame.sideways() - cell.bend().offset(u);
        // The curve is longer than its chord, so a step along the chord covers less of the wall than it seems to.
        double slope = cell.halfLength() == 0 ? 0 : cell.bend().slope(u) / cell.halfLength();
        double across = lateral / Math.sqrt(1 + slope * slope);
        double beyond = Math.max(Math.abs(frame.along()) - cell.halfLength(), 0);
        RavineCell.Side side = lateral >= 0 ? cell.wobble().left() : cell.wobble().right();
        double halfWidth = halfWidthAt(settings, bounds, cell, y, terraceShift) * (1 + side.at(y));
        return Math.sqrt(across * across + beyond * beyond) - halfWidth;
    }

    // The whole shaft slides sideways with height, so view the point from where the shaft is at that height.
    private static RavineCell.Frame leanedFrame(RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        double t = Math.clamp((y - bounds.floorY()) / (double) (bounds.topY() - bounds.floorY()), 0, 1);
        double shift = cell.lean().shift(t);
        return cell.frame(x - shift * cell.lean().dirX(), z - shift * cell.lean().dirZ());
    }

    /** Position along the chord as a fraction of the half length in [-1, 1]; a round hole has no length, so 0. */
    private static double unitAlong(RavineCell cell, double along) {
        return cell.halfLength() == 0 ? 0 : Math.clamp(along / cell.halfLength(), -1, 1);
    }

    // Holding the width constant within each step turns a smooth taper into ledges at every step boundary.
    private static double terraced(RavineWalls walls, RavineBounds bounds, double y) {
        int step = walls.terraceStep();
        if (step == 0) {
            return y;
        }
        double stepped = Math.floor((y - bounds.floorY()) / step) * step + bounds.floorY();
        return y + (stepped - y) * walls.terraceStrength();
    }

    private static double cavernDistance(RavineSettings settings, RavineBounds bounds, double radial, double y) {
        double t = (y - bounds.floorY()) / settings.cavernHeight();
        if (t >= 1) {
            return Double.POSITIVE_INFINITY;
        }
        return radial - settings.cavernRadius() * Math.sqrt(1 - t * t);
    }
}

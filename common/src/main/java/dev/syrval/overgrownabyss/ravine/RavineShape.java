package dev.syrval.overgrownabyss.ravine;

/**
 * Signed distance, in blocks, from a point to the open volume of one ravine: negative inside (air), positive outside.
 * The volume is the ravine shaft from the floor upwards, joined to a domed cavern centred on the cell whose floor is
 * flat. The shaft follows a centre line that bends in plan view and sways sideways with height, narrows towards the
 * floor in wandering terraces, and has two walls that swell and narrow independently. Bridges and ledges are separate:
 * they are rock inside the open volume, see {@link #bridgeDistance} and {@link #ledgeDistance}.
 *
 * <p>These are distance estimates, exact for a straight shaft and close for gentle curves, which is all the carve
 * needs: it only uses the sign and a few blocks of falloff either side of the wall.
 */
public final class RavineShape {
    /** How far a ledge reaches into the rock behind the wall, so wall noise cannot leave it floating. */
    static final double LEDGE_ROOT = 24;
    private static final double LEDGE_SLACK = 12;
    private static final double MAX_LEDGE_REACH = 0.45;
    private static final double FEATURE_MARGIN_ABOVE_ROOF = 8;
    private static final double FEATURE_MARGIN_BELOW_RIM = 10;

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
     * arch, a slab {@code width} long along the ravine, level on top and thin in the middle, thickening towards the
     * walls where it springs from. Its top is at {@link #featureTop}, the same height as the ledges it lands on.
     * Add it to the rock with {@code max(open, -bridge)}.
     */
    public static double bridgeDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        if (cell.bridges().isEmpty()) {
            return nearest;
        }
        RavineCell.Frame frame = leanedFrame(bounds, cell, x, y, z);
        for (RavineCell.Bridge bridge : cell.bridges()) {
            double top = featureTop(settings, bounds, bridge.height());
            if (Double.isNaN(top)) {
                continue;
            }
            double u = unitAlong(cell, bridge.along());
            double sideways = Math.abs(frame.sideways() - cell.bend().offset(u));
            double span = Math.min(sideways / halfWidthAt(settings, bounds, cell, top, 0), 1.2);
            double bottom = top - bridge.thickness() * (1 + 1.5 * span * span);
            double distance = Math.max(Math.abs(frame.along() - bridge.along()) - bridge.width() / 2, Math.max(y - top, bottom - y));
            nearest = Math.min(nearest, distance);
        }
        return nearest;
    }

    /**
     * Signed distance to the nearest ledge, or infinity if none is near: negative inside the rock. A ledge is a slab with
     * a level top, standing out from its wall by {@code depth} and reaching {@link #LEDGE_ROOT} blocks into the rock
     * behind so it always joins the wall; it is thickest at the root and thinner towards the lip. Add it to the rock with
     * {@code max(open, -ledge)}.
     */
    public static double ledgeDistance(RavineSettings settings, RavineBounds bounds, RavineCell cell, double x, double y, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        RavineCell.Frame frame = null;
        for (RavineCell.Ledge ledge : cell.ledges()) {
            double top = featureTop(settings, bounds, ledge.height());
            // Skip ledges the point is clearly above or below; this runs for every block near the wall.
            if (Double.isNaN(top) || y > top + LEDGE_SLACK || y < top - ledge.thickness() * 1.4 - LEDGE_SLACK) {
                continue;
            }
            if (frame == null) {
                frame = leanedFrame(bounds, cell, x, y, z);
            }
            RavineCell.Side wobble = ledge.side() > 0 ? cell.wobble().left() : cell.wobble().right();
            double halfWidth = halfWidthAt(settings, bounds, cell, top, 0) * (1 + wobble.at(top));
            double wall = cell.bend().offset(unitAlong(cell, ledge.along())) + ledge.side() * halfWidth;
            double along = frame.along() - ledge.along();
            double inward = ledge.side() > 0 ? wall - frame.sideways() : frame.sideways() - wall;
            double cos = Math.cos(ledge.yaw());
            double sin = Math.sin(ledge.yaw());
            // However it is turned, a ledge may reach at most this fraction of the way across; shrinking it keeps the
            // centre line open and leaves room to get past, even in a narrow ravine.
            double reach = ledge.depth() * cos + ledge.length() / 2 * Math.abs(sin);
            double fit = Math.min(1, MAX_LEDGE_REACH * halfWidth / reach);
            double depth = ledge.depth() * fit;
            double alongLedge = along * cos + inward * sin;
            double fromWall = -along * sin + inward * cos;
            double bottom = top - ledge.thickness() * (0.6 + 0.8 * Math.clamp(1 - fromWall / depth, 0, 1));
            double horizontal = Math.max(Math.abs(alongLedge) - ledge.length() * fit / 2, Math.max(-LEDGE_ROOT - fromWall, fromWall - depth));
            nearest = Math.min(nearest, Math.max(horizontal, Math.max(y - top, bottom - y)));
        }
        return nearest;
    }

    /**
     * Height of a bridge or ledge placed at {@code fraction} of the span from just above the cavern roof to just below
     * the rim, or NaN if the shaft is too short to hold one. Bridges and ledges share this so a bridge's top is level
     * with the ledges it lands on whatever the world height.
     */
    static double featureTop(RavineSettings settings, RavineBounds bounds, double fraction) {
        double low = bounds.floorY() + settings.cavernHeight() + FEATURE_MARGIN_ABOVE_ROOF;
        double high = bounds.topY() - FEATURE_MARGIN_BELOW_RIM;
        return high <= low ? Double.NaN : low + fraction * (high - low);
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

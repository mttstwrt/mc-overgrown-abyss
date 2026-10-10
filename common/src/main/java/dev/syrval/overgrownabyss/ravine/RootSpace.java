package dev.syrval.overgrownabyss.ravine;

/**
 * A cone as its roots see it: where there is air and where rock, where the wall is, and how far the clear air round the axis
 * reaches. It only reads the hole's own shape and discs, so like them it is safe to share between worker threads.
 */
record RootSpace(RavineSettings settings, ConeSettings cone, RavineBounds bounds, RavineCell cell, DiscLayout layout, HoleGround ground) {

    /** How far a point is from the nearest rock: above 0 in open air, below 0 inside rock. */
    double air(double x, double y, double z) {
        return ConeShape.airDistance(settings, cone, bounds, cell, layout, x, y, z);
    }

    /** Radius of the hole's wall on the side of the axis that a column is on, at a height. */
    double wall(double x, double z, double y) {
        return ConeShape.wallRadiusAt(settings, cone, bounds, cell, x, z, Math.clamp(y, bounds.floorY(), bounds.topY()));
    }

    /** Radius of the clear air round the axis at a height. */
    double clear(double y) {
        return ConeShape.clearAt(cone, bounds, y);
    }

    double fromAxis(double x, double z) {
        return cell.distanceToCentre(x, z);
    }

    /** How far round the axis a column is, in radians. */
    double angleOf(double x, double z) {
        return Math.atan2(z - cell.centreZ(), x - cell.centreX());
    }

    double xAt(double angle, double fromAxis) {
        return cell.centreX() + fromAxis * Math.cos(angle);
    }

    double zAt(double angle, double fromAxis) {
        return cell.centreZ() + fromAxis * Math.sin(angle);
    }

    /** The height of the ground over a column, or the hole's top where the ground is at least that high. */
    int groundOver(double x, double z) {
        return ground.heightAt((int) Math.floor(x), (int) Math.floor(z));
    }

    /** The height where the bell at the foot of the hole ends and the cone above it begins: the cavern's roof. */
    double bellTop() {
        return bounds.floorY() + settings.cavernHeight();
    }
}

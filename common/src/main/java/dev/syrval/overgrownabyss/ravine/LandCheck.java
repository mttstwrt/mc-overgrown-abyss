package dev.syrval.overgrownabyss.ravine;

/** Whether the surface at a block column is land, as opposed to ocean, river or beach. */
@FunctionalInterface
public interface LandCheck {
    LandCheck EVERYWHERE = (x, z) -> true;

    boolean isLand(int blockX, int blockZ);
}

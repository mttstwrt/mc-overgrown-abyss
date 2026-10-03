package dev.syrval.overgrownabyss.ravine;

import java.util.List;

/** The columns a world's ravines can open up, used to keep vanilla fluids out of them. */
@FunctionalInterface
public interface RavineFootprint {
    RavineFootprint NONE = (x, z) -> false;

    boolean contains(int x, int z);

    static RavineFootprint of(List<RavineCarve> carves) {
        List<RavineCarve> copy = List.copyOf(carves);
        if (copy.isEmpty()) {
            return NONE;
        }
        // Called for every open block during noise fill, so avoid streams here.
        return (x, z) -> {
            for (RavineCarve carve : copy) {
                if (carve.isInFootprint(x, z)) {
                    return true;
                }
            }
            return false;
        };
    }
}

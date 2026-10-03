package dev.syrval.overgrownabyss.compat;

import dev.syrval.overgrownabyss.ravine.RavineFootprint;

/** Added to {@code RandomState} by mixin so each level's noise fill can see that level's ravines. */
public interface RavineFootprintHolder {
    RavineFootprint overgrownAbyss$footprint();

    void overgrownAbyss$setFootprint(RavineFootprint footprint);
}

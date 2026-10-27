package dev.syrval.verdantdeep.compat;

import dev.syrval.verdantdeep.ravine.RavineFootprint;
import net.minecraft.server.level.ServerLevel;

/** Added to {@code RandomState} by mixin so each level's noise fill can see that level's ravines. */
public interface RavineFootprintHolder {
    RavineFootprint verdantDeep$footprint();

    void verdantDeep$setFootprint(RavineFootprint footprint);

    /** The ravines of a level, for what runs once the level does: the curse, and the gas sent to players. */
    static RavineFootprint of(ServerLevel level) {
        return ((RavineFootprintHolder) (Object) level.getChunkSource().randomState()).verdantDeep$footprint();
    }
}

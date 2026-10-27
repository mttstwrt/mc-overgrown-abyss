package dev.syrval.verdantdeep.compat.client;

import dev.syrval.verdantdeep.compat.HoleGasPayload;
import net.minecraft.world.level.Level;

/**
 * What draws the gas a client is sent. The one way there is goes through the gas library, behind this so that nothing which
 * runs without that library ever loads a class of it.
 */
interface GasDrawer {
    /** Gives the hole the sources the payload has, in place of any it had; none takes its gas away. Client thread only. */
    void accept(Level level, HoleGasPayload payload);

    /** Forgets every hole, as when the player leaves the world. */
    void clear();
}
